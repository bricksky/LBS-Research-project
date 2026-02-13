package ICN.itrc_project.consumer.config;

import ICN.itrc_project.consumer.service.LocationEventConsumer;
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
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions; // 추가
import redis.clients.jedis.UnifiedJedis;

import java.time.Duration;

@Slf4j
@Configuration
public class RedisStreamConfig {

    // [에러 해결 1] RedisConnectionFactory 빈 직접 생성
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        // [중요] 수동 빈 생성 시 반드시 초기화 메소드를 호출해야 연결이 시작됩니다.
        factory.afterPropertiesSet();
        log.info(">>>>>> RedisConnectionFactory 초기화 완료!");
        return factory;
    }
    @Bean
    public UnifiedJedis unifiedJedis() {
        return new UnifiedJedis("redis://localhost:6379");
    }

    @Bean
    @SuppressWarnings("unchecked") // 제네릭 경고를 무시하고 컴파일을 강제합니다.
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> container(
            RedisConnectionFactory factory, LocationEventConsumer consumer) {

        // 1. 시리얼라이저 준비
        RedisSerializer<String> s = RedisSerializer.string();

        // 2. [필살기] Raw Type으로 캐스팅하여 컴파일러의 입을 막아버립니다.
        // 사진에 나온 'Incompatible types' 에러를 원천 봉쇄하는 유일한 방법입니다.
        StreamMessageListenerContainerOptions options = StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(1))
                .serializer(s) // 키, 해시키, 해시값을 모두 String으로 세팅
                .build();

        // 3. 컨테이너 생성 (위에서 만든 options를 강제로 주입)
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(factory, options);

        container.receive(
                Consumer.from("lbs_group", "consumer_1"),
                StreamOffset.create("lbs_stream", ReadOffset.lastConsumed()),
                consumer
        );

        container.start();
        return container;
    }
}