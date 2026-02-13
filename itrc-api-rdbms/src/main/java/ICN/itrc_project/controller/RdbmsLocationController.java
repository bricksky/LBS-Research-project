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

    // 0️⃣ 데이터 저장/업데이트
    @PostMapping("/update")
    @Transactional
    public ResponseEntity<String> updateLocation(@Valid @RequestBody LocationRequest request) {
        Point point = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));

        LocationEntity entity = locationRepository.findByUserId(request.getUserId());
        if (entity == null) {
            locationRepository.save(LocationEntity.builder()
                    .userId(request.getUserId())
                    .location(point)
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