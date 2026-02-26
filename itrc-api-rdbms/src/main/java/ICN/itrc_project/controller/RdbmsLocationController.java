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

    /** * JTS GeometryFactory: EPSG:4326(WGS84) 좌표계 설정
     */
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * 위치 정보 업데이트 및 데이터 신선도(Freshness Lag) 측정
     */
    @PostMapping("/update")
    @Transactional
    public ResponseEntity<String> updateLocation(@Valid @RequestBody LocationRequest request) {

        // 1. 위경도 좌표를 JTS Point 객체로 변환
        Point point = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));

        // 2. Upsert(Insert or Update) 로직 수행
        LocationEntity entity = locationRepository.findByUserId(request.getUserId());

        if (entity == null) {
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
            // JPA Dirty Checking을 통한 기존 정보 갱신
            entity.updateLocation(point, request.getSpeed(), request.getAccuracy(), request.getTimestamp());
        }

        // 3. Prometheus 모니터링을 위한 데이터 신선도(Lag) 기록
        if (request.getTimestamp() > 0) {
            long now = System.currentTimeMillis();
            long lagMs = now - request.getTimestamp(); // 밀리초 단위 차이 계산

            Timer.builder("location.event.freshness")
                    .description("데이터 생성 시점부터 DB 저장 완료까지의 지연 시간")
                    .tags("application", "itrc-api-rdbms")
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(lagMs, TimeUnit.MILLISECONDS);

            if (log.isDebugEnabled()) {
                log.debug("사용자: {}, 데이터 신선도 지연(Lag): {}s", request.getUserId(), lagMs / 1000.0);
            }
        }

        return ResponseEntity.ok("Saved (PostGIS)");
    }

    /**
     * 공간 검색 API 그룹 (Range, KNN, PIP)
     */

    // 반경 검색:	"내 주변 500m 안에 누가 있어?"
    @PostMapping("/search/range")
    public ResponseEntity<List<RdbmsLocationResponse>> searchRange(
            @RequestBody LocationRequest request, //
            @RequestParam(defaultValue = "1.0") double radius) {
        return ResponseEntity.ok(
                locationRepository.findByRadius(request.getLatitude(), request.getLongitude(), radius).stream()
                        .map(RdbmsLocationResponse::from)
                        .collect(Collectors.toList())
        );
    }

    // 최근접 검색: "거기서 제일 가까운 사람 10명만!"
    @PostMapping("/search/knn")
    public ResponseEntity<List<RdbmsLocationResponse>> searchKnn(
            @RequestBody LocationRequest request,
            @RequestParam(defaultValue = "10") int k) {
        return ResponseEntity.ok(
                locationRepository.findNearest(request.getLatitude(), request.getLongitude(), k).stream()
                        .map(RdbmsLocationResponse::from)
                        .collect(Collectors.toList())
        );
    }

    // 구역 검색:	"이 구역 안에 누구 있어?"
    @PostMapping("/search/pip")
    public ResponseEntity<List<RdbmsLocationResponse>> searchPip(@RequestBody String wkt) {
        return ResponseEntity.ok(
                locationRepository.findInPolygon(wkt).stream()
                        .map(RdbmsLocationResponse::from)
                        .collect(Collectors.toList())
        );
    }
}