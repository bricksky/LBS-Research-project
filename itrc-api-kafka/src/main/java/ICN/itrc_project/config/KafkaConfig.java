package ICN.itrc_project.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Infrastructure Configuration: 실시간 위치 이벤트 처리를 위한 메시지 브로커 설정
 */
@Configuration
public class KafkaConfig {

    /**
     * 위치 데이터 수집을 위한 전용 토픽(Topic) 정의
     */
    @Bean
    public NewTopic locationTopic() {
        return TopicBuilder.name("location-events")
                .partitions(1)  // 병렬 처리 및 컨슈머 확장의 기본 단위 (Parallelism)
                .replicas(1)    // 데이터 가용성 및 결함 허용을 위한 복제본 수 (Fault Tolerance)
                .build();
    }
}
