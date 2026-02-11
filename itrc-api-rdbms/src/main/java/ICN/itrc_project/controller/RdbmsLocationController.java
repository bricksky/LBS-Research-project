package ICN.itrc_project.controller;

import ICN.itrc_project.domain.LocationEntity;
import ICN.itrc_project.dto.LocationRequest;
import ICN.itrc_project.dto.RdbmsLocationResponse;
import ICN.itrc_project.repository.LocationRepository;
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
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("api/v1/rdbms")
@RequiredArgsConstructor
public class RdbmsLocationController {
    private final LocationRepository locationRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // ✅ 데이터 저장/업데이트
    @PostMapping("/update")
    @Transactional
    public ResponseEntity<String> updateLocation(@Valid @RequestBody LocationRequest request) {
        // 입력받은 위경도로 Point 객체 생성 (순서: 경도 X, 위도 Y)
        Point point = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));

        LocationEntity entity = locationRepository.findByUserId(request.getUserId());
        if (entity == null) {
            locationRepository.save(LocationEntity.builder()
                    .userId(request.getUserId())
                    .location(point) // Point 저장
                    .speed(request.getSpeed())
                    .accuracy(request.getAccuracy())
                    .serviceType(request.getServiceType())
                    .timestamp(request.getTimestamp())
                    .build());
        } else {
            entity.updateLocation(point, request.getSpeed(), request.getAccuracy(), request.getTimestamp());
        }
        return ResponseEntity.ok("Saved (PostGIS)");
    }

    // 1️⃣ Range Query API
    @GetMapping("/search/range")
    public ResponseEntity<List<RdbmsLocationResponse>> searchRange(
            @RequestParam double lat, @RequestParam double lng, @RequestParam double radius) {
        return ResponseEntity.ok(
                locationRepository.findByRadius(lat, lng, radius).stream()
                        .map(RdbmsLocationResponse::from) // DTO로 변환
                        .collect(Collectors.toList())
        );
    }

    // 2️⃣ KNN API
    @GetMapping("/search/knn")
    public ResponseEntity<List<RdbmsLocationResponse>> searchKnn(
            @RequestParam double lat, @RequestParam double lng, @RequestParam int k) {
        return ResponseEntity.ok(
                locationRepository.findNearest(lat, lng, k).stream()
                        .map(RdbmsLocationResponse::from) // DTO로 변환
                        .collect(Collectors.toList())
        );
    }

    // 3️⃣ PIP API (구역 검색)
    @GetMapping("/search/pip")
    public ResponseEntity<List<RdbmsLocationResponse>> searchPip(@RequestParam String wkt) {
        return ResponseEntity.ok(
                locationRepository.findInPolygon(wkt).stream()
                        .map(RdbmsLocationResponse::from) // DTO로 변환
                        .collect(Collectors.toList())
        );
    }
}
