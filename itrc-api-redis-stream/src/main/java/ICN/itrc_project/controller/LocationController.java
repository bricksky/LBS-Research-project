package ICN.itrc_project.controller;

import ICN.itrc_project.dto.LocationRequest;
import ICN.itrc_project.dto.LocationResponse;
import ICN.itrc_project.consumer.RedisSearchService;
import ICN.itrc_project.producer.LocationEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/redis")
public class LocationController {

    private final RedisSearchService searchService;
    private final LocationEventProducer producer;

    /**
     * 위치 정보 수신 및 Redis Stream 발행 (Producer 역할)
     */
    @PostMapping("/update")
    public ResponseEntity<Void> receiveLocation(@RequestBody LocationRequest request) {
        producer.sendLocationEvent(request);
        return ResponseEntity.ok().build();
    }

    /**
     * OGC 표준 반경 검색 (Range Query)
     */
    @PostMapping("/search/range")
    public ResponseEntity<List<LocationResponse>> searchByRange(
            @RequestBody LocationRequest center,
            @RequestParam(defaultValue = "1.0") double radius) {
        return ResponseEntity.ok(searchService.searchByRange(center, radius));
    }

    /**
     * OGC 표준 최근접 검색 (KNN Query)
     */
    @PostMapping("/search/knn")
    public ResponseEntity<List<LocationResponse>> searchByKnn(
            @RequestBody LocationRequest center,
            @RequestParam(defaultValue = "10") int k) {
        return ResponseEntity.ok(searchService.searchByKnn(center, k));
    }
}