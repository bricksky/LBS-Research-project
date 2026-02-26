package ICN.itrc_project.dto;

import lombok.*;
import redis.clients.jedis.search.Document;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class LocationResponse {
    private String userId;
    private Double latitude;
    private Double longitude;
    private Double distanceMeter;
    private String h3Index;
    private String status;

    /**
     * Redis Search의 Document 결과를 우리 DTO로 변환하는 메서드
     */
    public static LocationResponse fromDocument(Document doc) {
        return LocationResponse.builder()
                // 1. Redis에는 "trj_id"로 저장되어 있으므로 이를 꺼내서 userId에 넣습니다.
                .userId(String.valueOf(doc.get("trj_id")))

                // 2. 위도/경도 역시 Redis 저장 명칭인 "rawlat", "rawlng"을 사용합니다.
                .latitude(Double.parseDouble(String.valueOf(doc.get("rawlat"))))
                .longitude(Double.parseDouble(String.valueOf(doc.get("rawlng"))))

                // 3. H3 인덱스와 상태(driving_mode)도 매칭합니다.
                .h3Index(String.valueOf(doc.get("h3_index")))
                .status(String.valueOf(doc.get("driving_mode")))

                // 4. 거리 정보는 검색 쿼리 결과에 포함될 경우 가져옵니다 (기본값 0.0)
                .distanceMeter(doc.get("dist") != null ? Double.parseDouble(String.valueOf(doc.get("dist"))) : 0.0)
                .build();
    }
}