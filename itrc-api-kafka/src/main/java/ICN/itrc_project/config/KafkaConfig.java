package ICN.itrc_project.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
// KafkaConfig를 통해 이벤트가 흐를 통로를 확보
public class KafkaConfig {
    @Bean
    public NewTopic locationTopic() {
        // 이벤트 수집을 위한 토픽 생성
        return TopicBuilder.name("location-events")
                .partitions(1)  // 데이터 이동 차선의 개수
                .replicas(1)    // 복재본의 개수
                .build();
    }
}
