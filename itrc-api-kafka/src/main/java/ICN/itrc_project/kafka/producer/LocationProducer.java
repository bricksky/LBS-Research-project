package ICN.itrc_project.kafka.producer;

import ICN.itrc_project.dto.LocationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka Producer: 위치 이벤트를 직렬화하여 지정된 토픽으로 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "location-events";

    public void sendLocation(LocationRequest request) {
        // 1. 데이터 정합성 검증
        if (request == null || request.getUserId() == null) {
            log.warn(">>> [⚠️ 발송 실패] 데이터가 유효하지 않습니다.");
            return;
        }

        // 2. 데이터 품질 측정 (GPS 오차 기반 신뢰도 점수 산출)
        String accuracyPercent = convertToPercentage(request.getAccuracy());

        /**
         * 3. Kafka 메시지 비동기 전송
         * - Message Key: userId (동일 사용자의 이벤트 순서 보장을 위해 동일 파티션 할당)
         * - Payload: LocationRequest (JSON 직렬화)
         */
        kafkaTemplate.send(TOPIC, request.getUserId(), request)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        // 4. 전송 성공: 브로커로부터 수신 확인(ACK) 및 파티션 정보 로깅
                        log.info(">>> [🚀 발송] 유저(trj):{} | 서비스:{} | 정확도:{}m({}) | 파티션:{}번",
                                request.getUserId(),
                                request.getServiceType(),
                                request.getAccuracy(),
                                accuracyPercent,
                                result.getRecordMetadata().partition());
                    } else {
                        // 5. 전송 실패: 네트워크 장애 또는 브로커 오류 발생 시 예외 처리
                        log.error(">>> [⚠️ 발송 실패] 유저:{} | 사유:{}",
                                request.getUserId(), ex.getMessage());
                    }
                });
    }

    /**
     * GPS 정확도(m)를 기반으로 데이터 신뢰도(%)를 산출하는 휴리스틱 모델
     * 오차 범위가 커질수록 가중치를 부여하여 신뢰도 급감 처리
     */
    private String convertToPercentage(Double accuracy) {
        if (accuracy == null) return "0%";
        if (accuracy < 0) return "100%";
        double score;
        if (accuracy <= 5) score = 100 - (accuracy * 2);
        else if (accuracy <= 20) score = 90 - ((accuracy - 5) * 2.67);
        else if (accuracy <= 50) score = 50 - ((accuracy - 20) * 1.67);
        else score = 0;
        return String.format("%.0f%%", Math.min(100.0, Math.max(0.0, score)));
    }
}
