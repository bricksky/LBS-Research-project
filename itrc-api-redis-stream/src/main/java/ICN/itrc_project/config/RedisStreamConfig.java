package ICN.itrc_project.config;

import ICN.itrc_project.consumer.LocationEventConsumer; // 위치 변경 반영
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import redis.clients.jedis.UnifiedJedis;

import java.time.Duration;

@Slf4j
@Configuration
public class RedisStreamConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        log.info(">>>>>> RedisConnectionFactory 초기화 완료!");
        return factory;
    }

    @Bean
    public UnifiedJedis unifiedJedis() {
        return new UnifiedJedis("redis://localhost:6379");
    }

    @Bean
    @SuppressWarnings("unchecked")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> container(
            RedisConnectionFactory factory, LocationEventConsumer consumer) {

        RedisSerializer<String> s = RedisSerializer.string();

        // 타입 에러 방지를 위한 로우 타입(Raw Type) 캐스팅 처리
        StreamMessageListenerContainerOptions options = StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(1))
                .serializer(s)
                .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(factory, options);

        // 리스너 등록: lbs_group 그룹의 consumer_1 이름으로 메시지 수신
        container.receive(
                Consumer.from("lbs_group", "consumer_1"),
                StreamOffset.create("lbs_stream", ReadOffset.lastConsumed()),
                consumer
        );

        container.start();
        return container;
    }
}