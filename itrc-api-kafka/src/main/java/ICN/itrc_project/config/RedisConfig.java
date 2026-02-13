package ICN.itrc_project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
// RedisConfig를 통해 데이터를 인메모리에 객체 형태로 저장할 수 있음
public class RedisConfig {

    /**
     * [JSON 템플릿]
     * 용도: 사용자의 상세 정보(LocationRequest 객체) 저장
     * 설정: ValueSerializer를 JSON으로 설정하여 객체를 자동으로 직렬화
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 모든 Key는 String
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());

        // 기본 Value는 JSON (객체 저장용)
        template.setValueSerializer(RedisSerializer.json());
        template.setHashValueSerializer(RedisSerializer.json());

        template.afterPropertiesSet();
        return template;
    }

    /**
     * [String 템플릿]
     * 용도: GeoData(좌표+ID) 저장 및 조회
     * 설정: Key/Value 모두 String으로 처리하여 "따옴표 지옥" 방지
     */
    @Bean(name = "stringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
