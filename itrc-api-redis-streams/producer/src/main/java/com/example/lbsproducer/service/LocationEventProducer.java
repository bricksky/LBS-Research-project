package com.example.lbsproducer.service;

import com.example.lbsproducer.dto.LocationRequest;
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

    private final RedisTemplate<String, String> redisTemplate;
    private final H3Core h3;

    public LocationEventProducer(RedisTemplate<String, String> redisTemplate) throws Exception {
        this.redisTemplate = redisTemplate;
        this.h3 = H3Core.newInstance(); // H3 인스턴스 초기화
    }

    public void sendLocationEvent(LocationRequest request) {
        log.info(this.getClass().getName()+".sendLocationEvent Start!");
        log.info("lat: "+request.rawlat()+", lng: "+request.rawlng());
        // 위경도를 H3 Index(해상도 9)로 변환
        String h3Index = h3.latLngToCellAddress(request.rawlat(), request.rawlng(), 9);

        // Redis Stream에 넣을 메시지 생성
        Map<String, String> fields = new HashMap<>();
        fields.put("trj_id", request.trj_id());           // 원본 규격 유지
        fields.put("driving_mode", request.driving_mode());
        fields.put("osname", request.osname());
        fields.put("pingtimestamp", String.valueOf(request.pingtimestamp()));
        fields.put("rawlat", String.valueOf(request.rawlat()));
        fields.put("rawlng", String.valueOf(request.rawlng()));
        fields.put("speed", String.valueOf(request.speed()));
        fields.put("bearing", String.valueOf(request.bearing()));
        fields.put("accuracy", String.valueOf(request.accuracy()));

        // Redis Stack 공간 검색을 위한 전용 필드 (추가)
        fields.put("location", request.rawlng() + "," + request.rawlat()); //
        fields.put("h3_index", h3Index); // H3 최적화용

        ObjectRecord<String, Map<String, String>> record = StreamRecords.newRecord()
                .in("lbs_stream")
                .ofObject(fields);

        this.redisTemplate.opsForStream().add(record);
        log.info(this.getClass().getName()+".sendLocationEvent End!");
    }
}