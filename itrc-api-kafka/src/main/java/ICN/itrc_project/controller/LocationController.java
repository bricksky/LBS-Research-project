package ICN.itrc_project.controller;

import ICN.itrc_project.dto.LocationRequest;
import ICN.itrc_project.kafka.producer.LocationProducer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실시간 위치 데이터 유입을 담당하는 진입점
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationProducer locationProducer;

    @PostMapping
    public ResponseEntity<String> receiveLocation(@Valid @RequestBody LocationRequest request) {
        log.info(">>> [💌 Controller] 위치 정보 수신: userId={}", request.getUserId());

        // 1. 수신된 위치 데이터를 Kafka로 전달
        locationProducer.sendLocation(request);

        // 2. 비동기 처리를 위해 즉시 성공 응답 반환
        return ResponseEntity.ok("Location event streaming has started.");
    }
}
