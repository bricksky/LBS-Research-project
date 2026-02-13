package ICN.itrc_project.consumer.dto;

import redis.clients.jedis.search.Document;

/**
 * 검색 결과를 클라이언트에 반환하기 위한 응답 객체
 */
public record LocationResponse(
        String trj_id,
        String driving_mode,
        String osname,
        String pingtimestamp,
        String rawlat,
        String rawlng,
        String speed,
        String bearing,
        String accuracy,
        String h3_index,
        String location
) {
    //Redis Document를 LocationResponse 객체로 변환하는 정적 팩토리 메서드
    public static LocationResponse fromDocument(Document doc) {
        return new LocationResponse(
                String.valueOf(doc.get("trj_id")),
                String.valueOf(doc.get("driving_mode")),
                String.valueOf(doc.get("osname")),
                String.valueOf(doc.get("pingtimestamp")),
                String.valueOf(doc.get("rawlat")),
                String.valueOf(doc.get("rawlng")),
                String.valueOf(doc.get("speed")),
                String.valueOf(doc.get("bearing")),
                String.valueOf(doc.get("accuracy")),
                String.valueOf(doc.get("h3_index")),
                String.valueOf(doc.get("location"))
        );
    }
}
