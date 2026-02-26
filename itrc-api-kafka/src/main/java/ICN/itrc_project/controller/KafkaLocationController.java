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
 * Kafka Ingestion Controller: 고빈도 위치 데이터 수집을 위한 비동기 스트리밍 진입점
 * CQRS(Command Query Responsibility Segregation) 패턴의 Write(Command) 영역 담당
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/update/kafka")
@RequiredArgsConstructor
public class KafkaLocationController {

    private final LocationProducer locationProducer;

    /**
     * 위치 데이터를 수신하여 Kafka 메시지 브로커로 즉시 위임 (Non-blocking Handoff)
     */
    @PostMapping
    public ResponseEntity<String> streamLocation(@Valid @RequestBody LocationRequest request) {
        log.info(">>> [💌 위치 정보 수신] 유저(trj):{}", request.getUserId());

        // 1. Kafka Producer 발행: 무거운 처리 로직을 Consumer 레이어로 격리하여 스레드 점유 최소화
        locationProducer.sendLocation(request);

        /**
         * 2. HTTP 202 Accepted 반환
         * 요청 수락과 실제 처리 완료 시점을 분리하여 클라이언트의 Blocking Time 최적화
         */
        return ResponseEntity.accepted().body("위치 정보가 Kafka로 전달되었습니다.");
    }
}
