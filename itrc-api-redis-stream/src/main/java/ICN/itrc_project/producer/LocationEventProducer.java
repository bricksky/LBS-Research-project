package ICN.itrc_project.producer;

import ICN.itrc_project.dto.LocationRequest; // 마스터 DTO 사용
import com.uber.h3core.H3Core;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class LocationEventProducer {
    private final RedisTemplate<String, Object> redisTemplate;
    private final H3Core h3;

    public LocationEventProducer(RedisTemplate<String, Object> redisTemplate) throws Exception {
        this.redisTemplate = redisTemplate;
        this.h3 = H3Core.newInstance();
    }

    public void sendLocationEvent(LocationRequest request) {
        log.info("Redis Stream Publish Start - User: {}", request.getUserId());

        // 1. 위경도를 H3 Index(해상도 9)로 변환 (공간 인덱싱 최적화)
        String h3Index = h3.latLngToCellAddress(request.getLatitude(), request.getLongitude(), 9);

        // 2. Redis Stream에 넣을 메시지 데이터 구성
        Map<String, String> fields = new HashMap<>();
        fields.put("trj_id", request.getUserId());
        fields.put("driving_mode", request.getServiceType());
        fields.put("pingtimestamp", String.valueOf(request.getTimestamp()));
        fields.put("rawlat", String.valueOf(request.getLatitude()));
        fields.put("rawlng", String.valueOf(request.getLongitude()));
        fields.put("speed", String.valueOf(request.getSpeed()));
        fields.put("bearing", String.valueOf(request.getHeading()));
        fields.put("accuracy", String.valueOf(request.getAccuracy()));

        // 3. Redis Search(공간 쿼리)를 위한 전용 필드 추가 (lon,lat 순서 중요)
        fields.put("location", request.getLongitude() + "," + request.getLatitude());
        fields.put("h3_index", h3Index);

        // 4. lbs_stream 키로 메시지 발행
        ObjectRecord<String, Map<String, String>> record = StreamRecords.newRecord()
                .in("lbs_stream")
                .ofObject(fields);

        this.redisTemplate.opsForStream().add(record);
        log.info("Redis Stream Publish Success - ID: {}", request.getUserId());
    }
}