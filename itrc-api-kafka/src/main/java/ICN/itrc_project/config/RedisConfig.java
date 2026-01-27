package ICN.itrc_project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
// RedisConfig를 통해 데이터를 인메모리에 객체 형태로 저장할 수 있음
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // key는 문자열로 저장
        template.setKeySerializer(new StringRedisSerializer());

        // value는 JSON으로 저장해서 DTO 받음
        template.setValueSerializer(RedisSerializer.json());
        return template;
    }
}

