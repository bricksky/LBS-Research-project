package ICN.itrc_project.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.json.Path2;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationEventConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final UnifiedJedis jedis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        log.info(">>>>>> [Redis Stream 컨슈머] 메시지 수신 성공! ID: {}", message.getId());

        try {
            // 1. 스트림에서 수신한 메시지 본문 추출
            Map<String, String> body = message.getValue();

            // 2. 검색 인덱싱을 위한 키 생성 (기존 인덱스 PREFIX 'rider:' 사용)
            String trjId = body.get("trj_id");
            String redisKey = "rider:" + trjId;

            // 3. RedisJSON 저장을 위해 데이터 타입 변환 (String -> Numeric)
            Map<String, Object> numericBody = new HashMap<>(body);
            try {
                if (body.get("pingtimestamp") != null) numericBody.put("pingtimestamp", Long.parseLong(body.get("pingtimestamp")));
                if (body.get("speed") != null) numericBody.put("speed", Double.parseDouble(body.get("speed")));
                if (body.get("bearing") != null) numericBody.put("bearing", Integer.parseInt(body.get("bearing")));
                if (body.get("accuracy") != null) numericBody.put("accuracy", Double.parseDouble(body.get("accuracy")));
            } catch (Exception e) {
                log.warn("데이터 타입 변환 중 경미한 오류 발생 (원본 유지): {}", e.getMessage());
            }

            // 4. RedisJSON 타입으로 데이터 저장 (검색 인덱스에 자동 반영됨)
            String jsonString = objectMapper.writeValueAsString(numericBody);
            jedis.jsonSet(redisKey, Path2.ROOT_PATH, jsonString);

        } catch (Exception e) {
            log.error("메시지 처리 실패: ID {}", message.getId(), e);
        }
    }
}