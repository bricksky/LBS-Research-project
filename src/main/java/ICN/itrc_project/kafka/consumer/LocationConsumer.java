package ICN.itrc_project.kafka.consumer;

import ICN.itrc_project.dto.LocationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Kafka 토픽으로부터 위치 이벤트를 구독하여 Redis에 실시간 반영하는 컨슈머
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationConsumer {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis에 저장할 키 명칭 정의
    private static final String GEO_KEY = "mobility:locations";     // 주변 몇 km 이내 찾을때 묶기 위함
    private static String STATUS_PREFIX = "mobility:status:";       // 사용자의 상세 정보

    @KafkaListener(topics = "location-events", groupId = "lbs-group")
    public void consumeLocation(LocationRequest request) {
        // 1. Redis Geo 기능을 활용한 공간 인덱싱 저장
        redisTemplate.opsForGeo().add(
                GEO_KEY,
                new Point(request.getLongitude(), request.getLatitude()),
                request.getUserId()
        );

        // 2. 사용자별 전체 상태 정보(속도, 방향, 정확도 등)를 JSON 형태로 저장
        // RediSearch와 결합하여 복합 질의가 가능하도록 구성함
        redisTemplate.opsForValue().set(STATUS_PREFIX + request.getUserId(), request);

        long processingLag = Instant.now().toEpochMilli() - request.getTimestamp();

        log.info("[🧑‍Consumer] 이벤트 처리 완료: userId={}, 지연시간={}ms",
                request.getUserId(), processingLag);
    }
}
