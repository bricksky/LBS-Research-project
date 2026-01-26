package ICN.itrc_project.kafka.producer;

import ICN.itrc_project.dto.LocationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 위치 이벤트를 Kafka 토픽으로 발행하는 프로듀서
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationProducer {

    // KafkaConfig에서 설정된 연결을 통해 메시지를 보냄
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "location-events";

    public void sendLocation(LocationRequest request) {
        if (request == null || request.getUserId() == null) {
            log.warn(">>> [⚠️ Producer] 유효하지 않은 요청 - request 또는 userId가 null");
            return;
        }

        String readableTime = java.time.LocalTime.now().toString();

        kafkaTemplate.send(TOPIC, request.getUserId(), request)
                // 1. TOPIC: 어디로 보낼 것인가
                // 2. request.getUserId(): 어떤 파티션으로 보낼 것인가 (메시지 키)
                // 3. request: 무엇을 보낼 것인가 (메시지 값/페이로드)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        // 전송 성공 시: 실제 파티션 정보와 오프셋까지 로그로 기록
                        log.info(">>> [🤖 Producer] 위치 이벤트 발행 성공 | 사용자 ID: {}, 파티션: {}, 오프셋: {}, 시각: {}",
                                request.getUserId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                readableTime);
                    } else {
                        // 전송 실패 시: 에러 메시지와 함께 원인 기록
                        log.error(">>> [⚠️ Producer] 위치 이벤트 발행 실패 | 사용자 ID: {}, 사유: {}",
                                request.getUserId(), ex.getMessage());
                    }
                });
    }
}
