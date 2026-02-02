package ICN.itrc_project.controller;

import ICN.itrc_project.dto.LocationResponse;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/search/redis")
@RequiredArgsConstructor
@Validated
public class RedisLocationController {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String GEO_KEY = "mobility:locations";

    /**
     * [Range Query]
     * 핵심 질문: "내 주변 1km 원 안에 누가 있어?"
     * 판단 기준: 거리 중심
     */
    @GetMapping("/range")
    public ResponseEntity<List<LocationResponse>> searchByRange(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
            @RequestParam @DecimalMin(value = "0.0", inclusive = false) BigDecimal radiusMeter
    ) {
        long startTime = System.currentTimeMillis();
        double dLat = lat.doubleValue();
        double dLng = lng.doubleValue();
        double dRadius = radiusMeter.doubleValue();
        log.info(">>> [🔎 공간 검색] 반경 내 검색 실행 | 위도: {}, 경도: {}) | 반경: {}m", dLat, dLng, (int) dRadius);

        Circle circle = new Circle(new Point(dLng, dLat), new Distance(dRadius, Metrics.METERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending();

        GeoResults<RedisGeoCommands.GeoLocation<Object>> results = redisTemplate.opsForGeo().radius(GEO_KEY, circle, args);

        if (results == null) {
            log.warn(">>> [⚠️ 결과 없음] Redis 검색 결과가 null입니다.");
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<LocationResponse> response = results.getContent().stream()
                .map(result -> LocationResponse.builder()
                        .userId(result.getContent().getName().toString())
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceMeter(result.getDistance().getValue())
                        .build())
                .collect(Collectors.toUnmodifiableList());

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info(">>> [✅ 검색 결과] 주변 차량 {}대 발견 (소요시간: {}ms \n)", response.size(), elapsedTime);

        return ResponseEntity.ok(response);
    }

    /**
     * [KNN Query]
     * 핵심 질문: "나랑 제일 가까운 3명이 누구야?"
     * 판단 기준: 순위 중심
     */
    @GetMapping("/knn")
    public ResponseEntity<List<LocationResponse>> searchByKnn(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
            @RequestParam @Positive int n
    ) {
        long startTime = System.currentTimeMillis();
        double dLat = lat.doubleValue();
        double dLng = lng.doubleValue();
        log.info(">>> [🔎 공간 검색] 최근접 N명 탐색 실행 | 위도: {}, 경도: {} | 목표: 상위 {}명", lat, lng, n);

        Circle circle = new Circle(new Point(dLng, dLat), new Distance(5000, Metrics.METERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending().limit(n);

        GeoResults<RedisGeoCommands.GeoLocation<Object>> results = redisTemplate.opsForGeo().radius(GEO_KEY, circle, args);

        if (results == null) {
            log.warn(">>> [⚠️ 결과 없음] 최근접 탐색 결과가 null입니다.");
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<LocationResponse> response = results.getContent().stream()
                .map(result -> LocationResponse.builder()
                        .userId(result.getContent().getName().toString())
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceMeter(result.getDistance().getValue())
                        .build())
                .collect(Collectors.toUnmodifiableList());

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info(">>> [✅ 검색 결과] 최접점 차량 {}대 발견 (소요시간: {}ms \n)", response.size(), elapsedTime);

        return ResponseEntity.ok(response);
    }

    /**
     * [PIP Query]
     * 핵심 질문: "이 차가 내가 설정한 구역(영역) 안에 있어?"
     * 판단 기준: 경계 중심 + MBR 필터링
     */
    @GetMapping("/pip")
    public ResponseEntity<List<LocationResponse>> searchByPolygon(
            @RequestParam List<Double> lats, @RequestParam List<Double> lngs
    ) {
        long startTime = System.currentTimeMillis();
        log.info(">>> [🔎 공간 검색] 다각형 구역 필터링 실행 | 꼭짓점 수: {}개", lats.size());

        if (lats.size() != lngs.size() || lats.size() < 3) {
            log.error(">>> [❌ 요청 오류] 다각형 좌표 리스트가 유효하지 않습니다.");
            return ResponseEntity.badRequest().build();
        }

        boolean isInvalidLat = lats.stream().anyMatch(lat -> lat < -90 || lat > 90);
        boolean isInvalidLng = lngs.stream().anyMatch(lng -> lng < -180 || lng > 180);

        if (isInvalidLat || isInvalidLng) {
            log.error(">>> [❌ 요청 오류] 위도(-90~90) 또는 경도(-180~180) 범위를 벗어난 좌표가 포함되어 있습니다.");
            return ResponseEntity.badRequest().build();
        }

        // 1. 다각형의 바운딩 박스(MBR) 중심 및 대각선 거리 계산
        double minLat = lats.stream().min(Double::compareTo).orElse(0.0);
        double maxLat = lats.stream().max(Double::compareTo).orElse(0.0);
        double minLng = lngs.stream().min(Double::compareTo).orElse(0.0);
        double maxLng = lngs.stream().max(Double::compareTo).orElse(0.0);

        double centerLat = (minLat + maxLat) / 2;
        double centerLng = (minLng + maxLng) / 2;

        // 바운딩 박스 대각선 거리 계산 (전체 영역을 포함하기 위해 1.1배 여유분 추가)
        double diagonal = calculateDistance(minLat, minLng, maxLat, maxLng) * 1.1;

        // 2. 다각형 형태 정의 (Path2D)
        Path2D polygon = new Path2D.Double();
        polygon.moveTo(lngs.get(0), lats.get(0));
        for (int i = 1; i < lats.size(); i++) {
            polygon.lineTo(lngs.get(i), lats.get(i));
        }
        polygon.closePath();

        // 3. Filter: 계산된 중심점과 반지름(대각선/2)으로 1차 후보군 추출
        Circle filterArea = new Circle(new Point(centerLng, centerLat), new Distance(diagonal / 2, Metrics.METERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending();

        GeoResults<RedisGeoCommands.GeoLocation<Object>> results = redisTemplate.opsForGeo().radius(GEO_KEY, filterArea, args);

        if (results == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        // 4. Refine: 2차 수학적 판정
        List<LocationResponse> response = results.getContent().stream()
                .filter(result -> {
                    Point p = result.getContent().getPoint();
                    return p != null && polygon.contains(p.getX(), p.getY());
                })
                .map(result -> LocationResponse.builder()
                        .userId(result.getContent().getName().toString())
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceMeter(result.getDistance().getValue())
                        .build())
                .collect(Collectors.toUnmodifiableList());

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info(">>> [✅ 검색 결과] 구역 내 차량 {}대 발견 (소요시간: {}ms \n)", response.size(), elapsedTime);

        return ResponseEntity.ok(response);
    }

    /**
     * 하버사인 공식을 이용한 두 지점 사이의 거리(m) 계산
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371e3; // 지구 반지름 (m)
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                        Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}