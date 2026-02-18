package ICN.itrc_project.kafka.consumer;

import ICN.itrc_project.dto.LocationRequest;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private final RedisTemplate<String, Object> redisTemplate;  // JSON용 (상세정보)
    private final StringRedisTemplate stringRedisTemplate;      // String용 (지도좌표)
    private final MeterRegistry meterRegistry;

    // Redis 저장 키
    private static final String GEO_KEY = "mobility:locations";         // 주변 몇 km 이내 찾을때 묶기 위함
    private static final String STATUS_PREFIX = "mobility:status:";     // 사용자의 상세 정보
    private static final Duration STATUS_TTL = Duration.ofMinutes(30);

    @KafkaListener(topics = "location-events", groupId = "lbs-group")
    public void consumeLocation(LocationRequest request) {

        // 1. 데이터 검증
        if (isInvalid(request)) return;

        // 2. ID 정제 (특수문자 제거)
        String cleanUserId = request.getUserId().replaceAll("[^a-zA-Z0-9_]", "");

        // [Geo 저장] StringRedisTemplate 사용 -> ID에 따옴표 없이 저장됨 (지도 시각화용)
        stringRedisTemplate.opsForGeo().add(GEO_KEY,
                new Point(request.getLongitude(), request.getLatitude()),
                cleanUserId);

        // [상세 저장] RedisTemplate 사용 -> 객체를 JSON으로 저장 (상세 조회용)
        redisTemplate.opsForValue().set(STATUS_PREFIX + cleanUserId, request, STATUS_TTL);

        // 3. 로그 출력 (성능 측정)
        long lag = System.currentTimeMillis() - request.getTimestamp();
        String readableLag = formatDuration(lag);

        if (lag >= 0) {
            io.micrometer.core.instrument.Timer.builder("location.event.freshness")
                    .description("Time from Producer to Consumer Redis Update")
                    // ⚠️ 주의: "itrc-api-kafka" 부분은 application.yml의 앱 이름과 일치해야 합니다!
                    .tags("application", "itrc-api-kafka")
                    .register(meterRegistry)
                    .record(lag, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        // 4. 정확도(m)와 퍼센티지(%)를 모두 로그에 남김
        String convertToPercentage = convertToPercentage(request.getAccuracy());

        // 5. 프로듀서와 통일된 핵심 로그 출력
        log.info(">>> [⚙️ 처리] 유저(trj):{} | 서비스:{} | 정확도:{}m({}) | 속도:{} | 지연:{} \n",
                cleanUserId,
                request.getServiceType(),
                request.getAccuracy(),
                convertToPercentage,
                String.format("%5.1fkm/h", request.getSpeed()),
                readableLag);


    }

    /**
     * * [구간별 설계]
     * 1. 0~5m (Excellent): 하락폭 최소화 (2점/m). 정지 또는 미세 이동 상태.
     * 2. 5~20m (Good/Fair): 완만한 하락 (약 2.67점/m). 일반적인 도로 주행 환경.
     * 3. 20~50m (Poor): 급격한 하락 (약 1.67점/m). 인접 도로와의 혼선 가능성 구간.
     * 4. 50m 초과 (Invalid): 신뢰 불가 구간. 0% 처리.
     */
    private String convertToPercentage(Double accuracy) {
        if (accuracy == null) return "0%";
        if (accuracy < 0) return "100%";
        double score;
        if (accuracy <= 5) {
            // 0~5m: 100% ~ 90% (최상급 품질)
            score = 100 - (accuracy * 2);
        } else if (accuracy <= 20) {
            // 5~20m: 90% ~ 50% (일반 도심 품질)
            score = 90 - ((accuracy - 5) * 2.67);
        } else if (accuracy <= 50) {
            // 20~50m: 50% ~ 0% (신뢰도 낮음)
            score = 50 - ((accuracy - 20) * 1.67);
        } else {
            score = 0;
        }
        return String.format("%.0f%%", Math.min(100.0, Math.max(0.0, score)));
    }

    /**
     * 지연 시간을 가독성 있게 변환
     */
    private String formatDuration(long ms) {
        if (ms < 0) return "0ms";
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.2fs", ms / 1000.0);
        Duration duration = Duration.ofMillis(ms);
        return duration.toMinutes() + "분" + (duration.toSeconds() % 60) + "초";
    }

    /**
     * 데이터 유효성 검사 로직
     */
    private boolean isInvalid(LocationRequest request) {
        if (request == null || request.getUserId() == null ||
                request.getLatitude() == null || request.getLongitude() == null || request.getTimestamp() == null || request.getSpeed() == null) {
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