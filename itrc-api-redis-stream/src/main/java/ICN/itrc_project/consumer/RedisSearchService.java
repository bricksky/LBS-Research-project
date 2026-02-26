package ICN.itrc_project.consumer;

import ICN.itrc_project.dto.LocationRequest;  // common 모듈 DTO
import ICN.itrc_project.dto.LocationResponse; // common 모듈 DTO
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

    private final UnifiedJedis jedis;
    private final H3Core h3;

    public RedisSearchService(UnifiedJedis jedis) throws Exception {
        this.jedis = jedis;
        this.h3 = H3Core.newInstance();
    }

    // 1. 반경 검색 (Range Query)
    public List<LocationResponse> searchByRange(LocationRequest center, double radiusKm) {
        log.info("Redis Range Search Start - Radius: {}km", radiusKm);

        // @location:[경도 위도 반경 단위]
        String queryStr = String.format("@location:[%f %f %f km]",
                center.getLongitude(), center.getLatitude(), radiusKm);

        return executeSearch(new Query(queryStr));
    }

    // 2. 최근접 K명 검색 (KNN Query)
    public List<LocationResponse> searchByKnn(LocationRequest center, int k) {
        log.info("Redis KNN Search Start - K: {}", k);

        // 필터링 후 거리순 정렬하여 상위 K개 추출
        Query query = new Query("*")
                .addFilter(new Query.GeoFilter("location", center.getLongitude(), center.getLatitude(), 50, "km"))
                .setSortBy("location", true)
                .limit(0, k);

        return executeSearch(query);
    }

    // 3. H3 인덱스 기반 구역 검색 (PIP 최적화)
    public List<LocationResponse> searchByH3(LocationRequest center) {
        log.info("Redis H3 Search Start");

        // 좌표를 H3 인덱스(해상도 9)로 변환하여 문자열 검색
        String h3Index = h3.latLngToCellAddress(center.getLatitude(), center.getLongitude(), 9);
        Query query = new Query("@h3_index:{" + h3Index + "}");

        return executeSearch(query);
    }

    // 공통 검색 실행 로직
    private List<LocationResponse> executeSearch(Query query) {
        // rider_idx 인덱스 사용
        SearchResult result = jedis.ftSearch("rider_idx", query);

        return result.getDocuments().stream()
                .map(LocationResponse::fromDocument)
                .collect(Collectors.toList());
    }
}