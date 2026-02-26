package ICN.itrc_project.repository;

import ICN.itrc_project.domain.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationRepository extends JpaRepository<LocationEntity, Long> {

    /**
     * [Range Query] 반경 내 개체 검색
     * geography 캐스팅을 통해 미터(m) 단위의 정밀한 거리 연산 수행
     */
    @Query(value = """
            SELECT * FROM location_data
            WHERE ST_DWithin(
                location::geography, 
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, 
                :radius
            )
            """, nativeQuery = true)
    List<LocationEntity> findByRadius(@Param("lat") double lat, @Param("lng") double lng, @Param("radius") double radiusMeter);

    /**
     * [KNN Search] 최근접 k개 개체 검색
     * <-> 연산자를 활용하여 GIST 인덱스 기반의 고속 거리 정렬 수행(geography 기준)
     */
    @Query(value = """
            SELECT * FROM location_data
            ORDER BY location::geography <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
              LIMIT :k
            """, nativeQuery = true)
    List<LocationEntity> findNearest(@Param("lat") double lat, @Param("lng") double lng, @Param("k") int k);

    /**
     * [PIP Search] 특정 영역(Polygon) 내 개체 필터링
     * WKT(Well-Known Text) 기반 폴리곤 내 지점 포함 여부 판별
     */
    @Query(value = """
            SELECT * FROM location_data
            WHERE ST_Contains(
                ST_GeomFromText(:polygonWkt, 4326), 
                location
            )
            """, nativeQuery = true)
    List<LocationEntity> findInPolygon(@Param("polygonWkt") String polygonWkt);

    LocationEntity findByUserId(String userId);
}