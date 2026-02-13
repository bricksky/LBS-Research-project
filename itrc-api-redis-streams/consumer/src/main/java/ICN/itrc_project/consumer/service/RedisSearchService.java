package ICN.itrc_project.consumer.service;

import ICN.itrc_project.consumer.dto.LocationRequest;
import ICN.itrc_project.consumer.dto.LocationResponse;
import com.uber.h3core.H3Core;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RedisSearchService {

    private final UnifiedJedis jedis; // Config에서 만든 Bean이 주입됨
    private final H3Core h3;         // H3 변환용
    public RedisSearchService(UnifiedJedis jedis) throws Exception {
        this.jedis = jedis;
        this.h3 = H3Core.newInstance();
    }
    /**
     * 1. OGC 표준 Range Query (반경 검색)
     */
    public List<LocationResponse> searchByRange(LocationRequest center, double radiusKm) {
        // RediSearch 공간 쿼리 포맷 @location:[경도 위도 반경 단위]
        log.info(this.getClass().getName()+".searchByRange Start");
        String queryStr = String.format("@location:[%f %f %f km]",
                center.rawlng(), center.rawlat(), radiusKm);

        Query query = new Query(queryStr);
        return executeSearch(query);
    }

    /**
     * 2. OGC 표준 KNN Query (최근접 K명 검색)
     */
    public List<LocationResponse> searchByKnn(LocationRequest center, int k) {
        log.info(this.getClass().getName()+".searchByKnn Start");
        // KNN 검색. 모든 대상을 찾되 좌표 기준 거리순 정렬 및 개수 제한
        Query query = new Query("*")
                .addFilter(new Query.GeoFilter("location", center.rawlng(), center.rawlat(), 50, "km"))
                .setSortBy("location", true) // 거리순 정렬
                .limit(0, k);               // 상위 K개 추출

        return executeSearch(query);
    }

    /**
     * 3. H3 기반 PIP(Point-in-Polygon) 최적화 검색
     */
    public List<LocationResponse> searchByH3(LocationRequest center) {
        log.info(this.getClass().getName()+".searchByH3 Start");
        // 입력받은 좌표를 즉시 H3 인덱스로 변환하여 TAG 검색
        // 해상도 9는 하나의 육각형 면적을 약 0.1km^2 넓이로 세팅함
        String h3Index = h3.latLngToCellAddress(center.rawlat(), center.rawlng(), 9);

        // 복잡한 공간 연산 없이 문자열 일치 여부로만 검색하여 성능 극대화
        Query query = new Query("@h3_index:{" + h3Index + "}");

        return executeSearch(query);
    }

    private List<LocationResponse> executeSearch(Query query) {
        // rider_idx 인덱스를 사용하여 검색 실행
        SearchResult result = jedis.ftSearch("rider_idx", query);
        log.info(this.getClass().getName()+".executeSearch's len : "+result.getDocuments().size());
        return result.getDocuments().stream()
                .map(doc -> LocationResponse.fromDocument(doc))
                .collect(Collectors.toList());
    }
}