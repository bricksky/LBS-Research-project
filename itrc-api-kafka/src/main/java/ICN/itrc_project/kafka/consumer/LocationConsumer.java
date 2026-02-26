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
 * Kafka Consumer: 수신된 이벤트를 가공하여 Redis 고속 공간 인덱싱 및 상태 동기화 수행
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


    /**
     * 'location-events' 토픽 구독 및 Redis Dual-Write 수행
     * - 고속 공간 검색(Spatial Query)과 개체 상태(Metadata) 관리를 위한 저장소 분리 전략 채택
     */
    @KafkaListener(topics = "location-events", groupId = "lbs-group")
    public void consumeLocation(LocationRequest request) {

        // 1. 페이로드 유효성 검증 및 식별자 정규화
        if (isInvalid(request)) return;
        String cleanUserId = request.getUserId().replaceAll("[^a-zA-Z0-9_]", "");

        /**
         * 2. Redis Dual-Write Strategy
         * (1) Spatial Indexing: GEOADD 명령을 통한 인메모리 위경도 인덱스 생성 (주변 검색 최적화)
         * (2) State Persistence: JSON 직렬화 기반의 개체 상세 정보 캐싱 (TTL 적용을 통한 리소스 관리)
         */
        stringRedisTemplate.opsForGeo().add(GEO_KEY,
                new Point(request.getLongitude(), request.getLatitude()),
                cleanUserId);

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