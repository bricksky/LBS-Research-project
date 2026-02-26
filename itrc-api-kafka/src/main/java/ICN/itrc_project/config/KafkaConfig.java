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
     * 위치 데이터 인제스션(Ingestion)을 위한 전용 토픽 정의
     */
    @Bean
    public NewTopic locationTopic() {
        return TopicBuilder.name("location-events")
                /**
                 * Partitions: 메시지 병렬 처리의 기본 단위
                 * - 컨슈머 그룹 내의 컨슈머 수와 매핑되어 시스템의 처리량(Throughput)을 결정함
                 */
                .partitions(1)

                /**
                 * Replicas: 데이터 고가용성(High Availability) 및 내결함성(Fault Tolerance) 설정
                 * - 브로커 장애 시 데이터 유실 방지를 위한 복제본 수 정의
                 */
                .replicas(1)
                .build();
    }
}
