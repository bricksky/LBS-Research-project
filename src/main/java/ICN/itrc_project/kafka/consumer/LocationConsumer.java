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
    private static final String GEO_KEY = "mobility:locations";           // 주변 몇 km 이내 찾을때 묶기 위함
    private static final String STATUS_PREFIX = "mobility:status:";       // 사용자의 상세 정보

    @KafkaListener(topics = "location-events", groupId = "lbs-group")
    public void consumeLocation(LocationRequest request) {

        // 유효하지 않은 데이터
        if (request == null || request.getUserId() == null ||
                request.getLatitude() == null || request.getLongitude() == null) {
            log.warn(">>> [🧑‍💻 Consumer] 유효하지 않은 이벤트 수신: 데이터 누락");
            return;
        }

        // 좌표 범위 검증
        Double lat = request.getLatitude();
        Double lon = request.getLongitude();
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            log.warn(">>> [🧑‍💻 Consumer] 좌표 범위 오류 | 사용자 ID: {}, 위도: {}, 경도: {}",
                    request.getUserId(), lat, lon);
            return;
        }

        // 1. 계산 및 로그용 시간 설정
        Long eventTimestamp = (request.getTimestamp() != null) ? request.getTimestamp() : System.currentTimeMillis();
        String readableTime = java.time.LocalTime.now().toString();

        // 2. Redis Geo 저장 (안전하게 검증된 위경도 사용)
        redisTemplate.opsForGeo().add(
                GEO_KEY,
                new Point(lon, lat),
                request.getUserId()
        );

        // 3. 사용자별 전체 상태 정보(속도, 방향, 정확도 등)를 JSON 형태로 저장
        // RediSearch와 결합하여 복합 질의가 가능하도록 구성함
        redisTemplate.opsForValue().set(STATUS_PREFIX + request.getUserId(), request);

        // 4. 처리 지연 계산 및 출력
        long processingLag = System.currentTimeMillis() - eventTimestamp;

        log.info(">>> [🧑‍💻 Consumer] 이벤트 처리 완료 | 사용자 ID: {}, 완료 시각: {}, 처리 지연: {}ms",
                request.getUserId(), readableTime, processingLag);
    }
}
