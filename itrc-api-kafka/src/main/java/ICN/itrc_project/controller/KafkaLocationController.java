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
 * 실시간 위치 데이터 수집 및 Kafka 스트리밍 전용 진입점
 * CQRS 아키텍처의 Command(쓰기) 파트를 담당
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/update/kafka")
@RequiredArgsConstructor
public class KafkaLocationController {

    private final LocationProducer locationProducer;

    @PostMapping
    public ResponseEntity<String> streamLocation(@Valid @RequestBody LocationRequest request) {
        log.info(">>> [💌 수신] 유저(trj):{}", request.getUserId());

        // Kafka로 비동기 전송
        locationProducer.sendLocation(request);
        return ResponseEntity.accepted().body("위치 정보가 Kafka로 전달되었습니다.");
    }
}
