package ICN.itrc_project.kafka.consumer;

import ICN.itrc_project.dto.LocationRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * [통합 컨슈머] Kafka 이벤트를 구독하여 Redis(Geo + JSON) 반영 및 성능 지표 측정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationConsumer {

    private final RedisTemplate<String, Object> redisTemplate;  // 상세정보용 (JSON)
    private final StringRedisTemplate stringRedisTemplate;      // 지도좌표용 (String)
    private final MeterRegistry meterRegistry;

    private static final String GEO_KEY = "mobility:locations";
    private static final String STATUS_PREFIX = "mobility:status:";
    private static final Duration STATUS_TTL = Duration.ofMinutes(30);

    @KafkaListener(topics = "location-events", groupId = "lbs-group")
    public void consumeLocation(LocationRequest request) {

        // 1. 데이터 검증 및 ID 정제
        if (isInvalid(request)) return;
        String cleanUserId = request.getUserId().replaceAll("[^a-zA-Z0-9_]", "");

        // 2. Redis 이중 저장
        // (1) GeoSpatial Index 저장 (공간 검색용)
        stringRedisTemplate.opsForGeo().add(GEO_KEY,
                new Point(request.getLongitude(), request.getLatitude()),
                cleanUserId);

        // (2) 상세 상태 정보 저장 (JSON 객체)
        redisTemplate.opsForValue().set(STATUS_PREFIX + cleanUserId, request, STATUS_TTL);

        // 3.  데이터 신선도(Freshness Lag) 측정 및 기록
        if (request.getTimestamp() > 0) {
            long lag = System.currentTimeMillis() - request.getTimestamp();

            Timer.builder("location.event.freshness")
                    .description("End-to-End Latency: Creation to Redis Update")
                    .tags("application", "itrc-api-kafka")
                    .publishPercentileHistogram() // 🌟 그라파나 히스토그램 필수 설정
                    .register(meterRegistry)
                    .record(lag, TimeUnit.MILLISECONDS);

            // 4. 통합 로그 출력
            log.info(">>> [⚙️ 처리] 유저:{} | 정확도:{}m | 지연:{}ms",
                    cleanUserId, request.getAccuracy(), lag);
        }
    }

    private boolean isInvalid(LocationRequest request) {
        return request == null || request.getUserId() == null ||
                request.getLatitude() == null || request.getLongitude() == null ||
                request.getTimestamp() == null;
    }
}