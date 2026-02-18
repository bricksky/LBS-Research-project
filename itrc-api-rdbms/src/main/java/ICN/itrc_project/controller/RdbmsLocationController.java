package ICN.itrc_project.controller;

import ICN.itrc_project.domain.LocationEntity;
import ICN.itrc_project.dto.LocationRequest;
import ICN.itrc_project.dto.RdbmsLocationResponse;
import ICN.itrc_project.repository.LocationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("api/v1/rdbms")
@RequiredArgsConstructor
public class RdbmsLocationController {
    private final LocationRepository locationRepository;
    private final MeterRegistry meterRegistry;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // 0️⃣ 데이터 저장/업데이트
    @PostMapping("/update")
    @Transactional
    public ResponseEntity<String> updateLocation(@Valid @RequestBody LocationRequest request) {
        long startTime = System.currentTimeMillis(); // 처리 시작 시간

        // 1. DB 로직 수행
        Point point = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));

        LocationEntity entity = locationRepository.findByUserId(request.getUserId());

        if (entity == null) {
            // 신규 생성
            LocationEntity newEntity = LocationEntity.builder()
                    .userId(request.getUserId())
                    .location(point)
                    .speed(request.getSpeed())
                    .accuracy(request.getAccuracy())
                    .serviceType(request.getServiceType())
                    .timestamp(request.getTimestamp())
                    .build();
            locationRepository.save(newEntity);
        } else {
            // 기존 업데이트 (Dirty Checking)
            entity.updateLocation(point, request.getSpeed(), request.getAccuracy(), request.getTimestamp());
        }

        // 2. ✅ 데이터 신선도(Freshness Lag) 기록 (핵심 코드)
        // k6에서 보낸 timestamp가 존재해야 함
        if (request.getTimestamp() > 0) {
            long now = System.currentTimeMillis();
            long lag = now - request.getTimestamp(); // (현재 서버 시간 - 데이터 생성 시간)

            Timer.builder("location.event.freshness")
                    .description("Time from event creation to DB save")
                    .tags("application", "itrc-api-rdbms") // application.yml과 일치
                    .register(meterRegistry)
                    .record(lag, TimeUnit.MILLISECONDS);

            // (선택) 로그 레벨이 DEBUG일 때만 출력하여 성능 저하 방지
            log.debug("User: {}, Lag: {}ms", request.getUserId(), lag);
        }

        return ResponseEntity.ok("Saved (PostGIS)");
    }

    // 1️⃣ Range Query API
    @PostMapping("/search/range") // ✅ GET -> POST
    public ResponseEntity<List<RdbmsLocationResponse>> searchRange(
            @RequestBody LocationRequest request, // ✅ Body로 위경도 수신
            @RequestParam(defaultValue = "1.0") double radius) {
        return ResponseEntity.ok(
                locationRepository.findByRadius(request.getLatitude(), request.getLongitude(), radius).stream()
                        .map(RdbmsLocationResponse::from)
                        .collect(Collectors.toList())
        );
    }

    // 2️⃣ KNN API (POST로 변경)
    @PostMapping("/search/knn")
    public ResponseEntity<List<RdbmsLocationResponse>> searchKnn(
            @RequestBody LocationRequest request, // ✅ Body로 위경도 수신
            @RequestParam(defaultValue = "10") int k) {
        return ResponseEntity.ok(
                locationRepository.findNearest(request.getLatitude(), request.getLongitude(), k).stream()
                        .map(RdbmsLocationResponse::from)
                        .collect(Collectors.toList())
        );
    }

    // 3️⃣ PIP API (POST로 변경)
    @PostMapping("/search/pip")
    public ResponseEntity<List<RdbmsLocationResponse>> searchPip(@RequestBody String wkt) {
        // WKT(Well-Known Text) 문자열을 직접 Body로 수신
        return ResponseEntity.ok(
                locationRepository.findInPolygon(wkt).stream()
                        .map(RdbmsLocationResponse::from)
                        .collect(Collectors.toList())
        );
    }
}