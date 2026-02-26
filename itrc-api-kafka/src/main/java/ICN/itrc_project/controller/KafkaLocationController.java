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
 * Kafka Ingestion Controller: 고빈도 위치 데이터 수집을 위한 스트리밍 진입점
 * CQRS 패턴의 Command(Write) 영역을 담당하여 수집과 처리를 분리
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

        // 1. 비동기 메시지 발행: 처리 로직을 Consumer 레이어로 위임하여 응답 지연 최소화
        locationProducer.sendLocation(request);

        /**
         * 2. HTTP 202 Accepted 반환
         * 요청이 수락되었으나 최종 처리는 비동기적으로 수행됨을 클라이언트에 명시
         */
        return ResponseEntity.accepted().body("위치 정보가 Kafka로 전달되었습니다.");
    }
}
