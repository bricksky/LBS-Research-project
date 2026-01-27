package ICN.itrc_project.kafka.consumer;

import ICN.itrc_project.dto.LocationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Kafka 토픽으로부터 위치 이벤트를 구독하여 Redis에 실시간 반영하는 컨슈머
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationConsumer {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis 저장 키
    private static final String GEO_KEY = "mobility:locations";         // 주변 몇 km 이내 찾을때 묶기 위함
    private static final String STATUS_PREFIX = "mobility:status:";     // 사용자의 상세 정보

    private static final Duration STATUS_TTL = Duration.ofMinutes(30);

    @KafkaListener(topics = "location-events", groupId = "lbs-group")
    public void consumeLocation(LocationRequest request) {

        // 1. 유효성 및 좌표 범위 검증
        if (isInvalid(request)) return;

        // 2. Redis 실시간 데이터 저장 (Geo + 상세 상태)
        redisTemplate.opsForGeo().add(GEO_KEY, new Point(request.getLongitude(), request.getLatitude()), request.getUserId());
        redisTemplate.opsForValue().set(STATUS_PREFIX + request.getUserId(), request, STATUS_TTL);

        // 3. 성능 측정을 위한 지연 시간(Lag) 계산
        long eventTime = (request.getTimestamp() != null) ? request.getTimestamp() : System.currentTimeMillis();
        String readableLag = formatDuration(System.currentTimeMillis() - eventTime);

        // 4. 정확도(m)를 퍼센티지(%)로 변환
        String accuracyPercent = convertToPercentage(request.getAccuracy());

        // 5. 프로듀서와 통일된 핵심 로그 출력
        log.info(">>> [⚙️ 처리] 유저:{} | 정확도:{} | 속도:{}km/h | 상태:{} | 지연:{}",
                request.getUserId(),
                accuracyPercent,
                request.getSpeed(),
                request.getStatus(),
                readableLag);
    }

    /**
     * GPS 정확도(m)를 신뢰도(%)로 변환하는 로직
     * 공식: $Score = \max(0, 100 - (accuracy \times 5))$
     * (예: 0m = 100%, 10m = 50%, 20m 이상 = 0%)
     */
    private String convertToPercentage(Double accuracy) {
        if (accuracy == null) return "0%";
        double score = Math.max(0, 100 - (accuracy * 5));
        return String.format("%.0f%%", score);
    }

    /**
     * 밀리초를 읽기 쉬운 형식으로 변환 (예: 12ms, 1.5s, 2m 30s)
     */
    private String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        }
        if (ms < 60000) {
            return String.format("%.2fs", ms / 1000.0);
        } else {
            Duration duration = Duration.ofMillis(ms);
            return duration.toMinutes() + "분" + (duration.toSeconds() % 60) + "초";
        }
    }

    /**
     * 데이터 유효성 검사 로직
     */
    private boolean isInvalid(LocationRequest request) {
        if (request == null || request.getUserId() == null ||
                request.getLatitude() == null || request.getLongitude() == null) {
            log.warn(">>> [⚠️ 처리 실패] 데이터 누락");
            return true;
        }

        Double lat = request.getLatitude();
        Double lon = request.getLongitude();
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            log.warn(">>> [⚠️ 처리 실패] 좌표 범위 오류: {}", request.getUserId());
            return true;
        }
        return false;
    }
}