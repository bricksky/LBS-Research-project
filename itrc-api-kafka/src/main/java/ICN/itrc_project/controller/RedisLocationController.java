package ICN.itrc_project.controller;

import ICN.itrc_project.dto.LocationRequest;
import ICN.itrc_project.dto.LocationResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.geom.Path2D;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/search/redis")
@RequiredArgsConstructor
public class RedisLocationController {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String GEO_KEY = "mobility:locations";

    /**
     * [Range Query] - POST 방식
     * Body: LocationRequest (lat, lng 포함)
     * Query: ?radiusMeter=1000
     */
    @PostMapping("/range")
    public ResponseEntity<List<LocationResponse>> searchByRange(
            @RequestBody LocationRequest request,
            @RequestParam(defaultValue = "1000") double radiusMeter
    ) {
        long startTime = System.currentTimeMillis();
        double dLat = request.getLatitude();
        double dLng = request.getLongitude();

        log.info(">>> [🔎 공간 검색] 반경 내 검색 | 위도: {}, 경도: {} | 반경: {}m", dLat, dLng, radiusMeter);

        Circle circle = new Circle(new Point(dLng, dLat), new Distance(radiusMeter, Metrics.METERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending();

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(GEO_KEY, circle, args);

        if (results == null) return ResponseEntity.ok(Collections.emptyList());

        List<LocationResponse> response = results.getContent().stream()
                .map(result -> LocationResponse.builder()
                        .userId(result.getContent().getName())
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceMeter(result.getDistance().getValue())
                        .build())
                .collect(Collectors.toList());

        log.info(">>> [✅ 검색 결과] 주변 차량 {}대 발견 (소요시간: {}ms)", response.size(), System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    /**
     * [KNN Query] - POST 방식
     * Body: LocationRequest (lat, lng 포함)
     * Query: ?n=10
     */
    @PostMapping("/knn")
    public ResponseEntity<List<LocationResponse>> searchByKnn(
            @RequestBody LocationRequest request,
            @RequestParam(defaultValue = "10") int n
    ) {
        long startTime = System.currentTimeMillis();
        double dLat = request.getLatitude();
        double dLng = request.getLongitude();

        log.info(">>> [🔎 공간 검색] 최근접 탐색 | 위도: {}, 경도: {} | 상위 {}명", dLat, dLng, n);

        Circle circle = new Circle(new Point(dLng, dLat), new Distance(5000, Metrics.METERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending().limit(n);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(GEO_KEY, circle, args);

        if (results == null) return ResponseEntity.ok(Collections.emptyList());

        List<LocationResponse> response = results.getContent().stream()
                .map(result -> LocationResponse.builder()
                        .userId(result.getContent().getName())
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceMeter(result.getDistance().getValue())
                        .build())
                .collect(Collectors.toList());

        log.info(">>> [✅ 검색 결과] 최접점 차량 {}대 발견 (소요시간: {}ms)", response.size(), System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    /**
     * [PIP Query 전용 DTO]
     * 다각형 좌표를 받기 위한 내부 클래스
     */
    @Data
    public static class PipRequest {
        private List<Double> lats;
        private List<Double> lngs;
    }

    /**
     * [PIP Query] - POST 방식
     * Body: PipRequest (lats 배열, lngs 배열 포함)
     */
    @PostMapping("/pip")
    public ResponseEntity<List<LocationResponse>> searchByPolygon(@RequestBody PipRequest request) {
        long startTime = System.currentTimeMillis();
        List<Double> lats = request.getLats();
        List<Double> lngs = request.getLngs();

        log.info(">>> [🔎 공간 검색] 다각형 구역 필터링 | 꼭짓점 수: {}개", lats != null ? lats.size() : 0);

        if (lats == null || lngs == null || lats.size() != lngs.size() || lats.size() < 3) {
            return ResponseEntity.badRequest().build();
        }

        double minLat = lats.stream().min(Double::compareTo).orElse(0.0);
        double maxLat = lats.stream().max(Double::compareTo).orElse(0.0);
        double minLng = lngs.stream().min(Double::compareTo).orElse(0.0);
        double maxLng = lngs.stream().max(Double::compareTo).orElse(0.0);

        double centerLat = (minLat + maxLat) / 2;
        double centerLng = (minLng + maxLng) / 2;
        double diagonal = calculateDistance(minLat, minLng, maxLat, maxLng) * 1.1;

        Path2D polygon = new Path2D.Double();
        polygon.moveTo(lngs.get(0), lats.get(0));
        for (int i = 1; i < lats.size(); i++) {
            polygon.lineTo(lngs.get(i), lats.get(i));
        }
        polygon.closePath();

        Circle filterArea = new Circle(new Point(centerLng, centerLat), new Distance(diagonal / 2, Metrics.METERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending();

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(GEO_KEY, filterArea, args);

        if (results == null) return ResponseEntity.ok(Collections.emptyList());

        List<LocationResponse> response = results.getContent().stream()
                .filter(result -> {
                    Point p = result.getContent().getPoint();
                    return p != null && polygon.contains(p.getX(), p.getY());
                })
                .map(result -> LocationResponse.builder()
                        .userId(result.getContent().getName())
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceMeter(result.getDistance().getValue())
                        .build())
                .collect(Collectors.toList());

        log.info(">>> [✅ 검색 결과] 구역 내 차량 {}대 발견 (소요시간: {}ms)", response.size(), System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371e3;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}