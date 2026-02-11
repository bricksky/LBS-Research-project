package ICN.itrc_project.repository;

import ICN.itrc_project.domain.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationRepository extends JpaRepository<LocationEntity, Long> {

    // 1️⃣ Range Query (반경 검색)
    // 특정 지점(lng, lat) 반경 radius 미터(m) 내의 데이터 검색
    @Query(value = """
            SELECT * FROM location_data
            WHERE ST_DWithin(
                location::geography, 
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, 
                :radius
            )
            """, nativeQuery = true)
    List<LocationEntity> findByRadius(@Param("lat") double lat, @Param("lng") double lng, @Param("radius") double radiusMeter);

    // 2️⃣ KNN (K-Nearest Neighbors, 최근접 이웃)
    @Query(value = """
            SELECT * FROM location_data
            ORDER BY location::geography <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
              LIMIT :k
            """, nativeQuery = true)
    List<LocationEntity> findNearest(@Param("lat") double lat, @Param("lng") double lng, @Param("k") int k);

    // 3️⃣ PIP (Point In Polygon, 구역 필터링)
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