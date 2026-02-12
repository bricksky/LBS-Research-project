package com.example.lbsproducer.service;

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
    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON 변환기

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            // 1. 스트림에서 메시지 본문을 꺼냄
            Map<String, String> body = message.getValue();

            // 2. 검색에 활용할 핵심 키인 trj_id를 추출
            String trjId = body.get("trj_id");
            String redisKey = "rider:" + trjId; // 인덱스 PREFIX(rider:)와 일치시켜야 함

            // 3. 스트림 데이터를 RedisJSON 타입으로 저장
// Redis 인덱스가 NUMERIC으로 인식할 수 있도록 숫자로 변환하여 저장
            Map<String, Object> numericBody = new HashMap<>(body);
            try {
                if (body.get("pingtimestamp") != null) numericBody.put("pingtimestamp", Long.parseLong(body.get("pingtimestamp")));
                if (body.get("speed") != null) numericBody.put("speed", Double.parseDouble(body.get("speed")));
                if (body.get("bearing") != null) numericBody.put("bearing", Integer.parseInt(body.get("bearing")));
                if (body.get("accuracy") != null) numericBody.put("accuracy", Double.parseDouble(body.get("accuracy")));
                // rawlat, rawlng은 어차피 location 문자열로 GEO 검색하므로 그대로 두어도 무방합니다.
            } catch (Exception e) {
                // 변환 실패 시 로그만 남기고 원본 유지
            }
            String jsonString = objectMapper.writeValueAsString(numericBody);
            jedis.jsonSet(redisKey, Path2.ROOT_PATH, jsonString);

        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생: {}", message.getId(), e);
        }
    }

}
