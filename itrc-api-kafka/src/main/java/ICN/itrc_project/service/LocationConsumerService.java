package ICN.itrc_project.service;

import ICN.itrc_project.dto.LocationRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationConsumerService {

    private final StringRedisTemplate stringRedisTemplate;
    private final MeterRegistry meterRegistry;
    private static final String GEO_KEY = "mobility:locations";

    @KafkaListener(topics = "location-topic", groupId = "lbs-group")
    public void consumeLocation(LocationRequest message) {
        long now = System.currentTimeMillis();

        // 1. Redis에 위치 정보 업데이트 (GeoSpatial Index)
        // StringRedisTemplate을 사용하므로 좌표만 저장 (추가 메타데이터 필요 시 Hash 등 활용)
        stringRedisTemplate.opsForGeo().add(GEO_KEY, new Point(message.getLongitude(), message.getLatitude()), message.getUserId());

        // 2. 데이터 신선도(Freshness Lag) 측정 및 기록
        // message.getTimestamp()는 클라이언트(k6)가 보낸 생성 시간이어야 함
        if (message.getTimestamp() > 0) {
            long lag = now - message.getTimestamp();

            // 그라파나 "데이터 신선도 추이" 패널을 위한 핵심 코드!
            Timer.builder("location.event.freshness")
                    .description("Time from event creation to consumption")
                    .tags("application", "itrc-api-kafka") // application.yml과 일치시켜야 함
                    .register(meterRegistry)
                    .record(lag, TimeUnit.MILLISECONDS);

            log.debug(">>> [Consumer] User:{} | Lag: {}ms", message.getUserId(), lag);
        }
    }
}