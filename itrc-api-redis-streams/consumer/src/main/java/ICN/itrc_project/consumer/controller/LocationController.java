package ICN.itrc_project.consumer.controller;

import ICN.itrc_project.consumer.dto.LocationRequest;
import ICN.itrc_project.consumer.dto.LocationResponse;
import ICN.itrc_project.consumer.service.RedisSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class LocationController {
    private final RedisSearchService searchService;

    /**
     * 1. OGC 표준 반경 검색 (Range Query)
     * k6 요청 예시: POST /api/v1/search/range?radius=2.0
     */
    @PostMapping("/range")
    public ResponseEntity<List<LocationResponse>> searchByRange(
            @RequestBody LocationRequest center,
            @RequestParam(defaultValue = "1.0") double radius) {

        return ResponseEntity.ok(searchService.searchByRange(center, radius));
    }

    /**
     * 2. OGC 표준 최근접 검색 (KNN Query)
     * k6 요청 예시: POST /api/v1/search/knn?k=10
     */
    @PostMapping("/knn")
    public ResponseEntity<List<LocationResponse>> searchByKnn(
            @RequestBody LocationRequest center,
            @RequestParam(defaultValue = "10") int k) {

        return ResponseEntity.ok(searchService.searchByKnn(center, k));
    }

    /**
     * 3. H3 기반 구역 검색 (PIP 최적화)
     * k6 요청 예시: POST /api/v1/search/h3
     */
    @PostMapping("/pip")
    public ResponseEntity<List<LocationResponse>> searchByH3(@RequestBody LocationRequest center) {

        return ResponseEntity.ok(searchService.searchByH3(center));
    }
}
