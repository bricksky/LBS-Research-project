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
        // 1. 데이터 유효성 검사
        if (request == null || request.getUserId() == null) {
            log.warn(">>> [⚠️ 발송 실패] 데이터가 유효하지 않습니다.");
            return;
        }

        // 2. 정확도(m)와 퍼센티지(%)를 모두 로그에 남김
        String accuracyPercent = convertToPercentage(request.getAccuracy());

        // 3. Kafka 메시지 전송
        /**
         * TOPIC: 어디로 보낼 것인가
         * request.getUserId(): 어떤 파티션으로 보낼 것인가 (메시지 키)
         * request: 무엇을 보낼 것인가 (메시지 값/페이로드)
         */
        kafkaTemplate.send(TOPIC, request.getUserId(), request)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        // 4. 전송 성공 로그 (단위 중복 제거 및 형식 통일)
                        log.info(">>> [🚀 발송] 유저(trj):{} | 서비스:{} | 정확도:{}m({}) | 파티션:{}번",
                                request.getUserId(),
                                request.getServiceType(),
                                request.getAccuracy(),
                                accuracyPercent,
                                result.getRecordMetadata().partition());
                    } else {
                        // 5. 전송 실패 로그
                        log.error(">>> [⚠️ 발송 실패] 유저:{} | 사유:{}",
                                request.getUserId(), ex.getMessage());
                    }
                });
    }

    /**
     * GPS 정확도(m)를 신뢰도(%)로 변환하는 로직
     */
    private String convertToPercentage(Double accuracy) {
        if (accuracy == null) return "0%";
        double score;
        if (accuracy == null) return "0%";
        if (accuracy <= 5) score = 100 - (accuracy * 2);
        else if (accuracy <= 20) score = 90 - ((accuracy - 5) * 2.67);
        else if (accuracy <= 50) score = 50 - ((accuracy - 20) * 1.67);
        else score = 0;
        return String.format("%.0f%%", Math.min(100.0, Math.max(0.0, score)));
    }
}
