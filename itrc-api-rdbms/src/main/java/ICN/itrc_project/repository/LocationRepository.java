package ICN.itrc_project.repository;

import ICN.itrc_project.domain.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<LocationEntity, Long> {
    Optional<LocationEntity> findByUserId(String userId);

    /**
     * RDBMS 공간 검색 쿼리
     */
    @Query(value = """
            SELECT *, 
                (6371000 * acos(cos(radians(:lat)) * cos(radians(latitude)) * cos(radians(longitude) - radians(:lng)) + sin(radians(:lat)) * sin(radians(latitude)))) 
                AS distance
            FROM location_data
            HAVING distance <= :radius
            ORDER BY distance ASC
            """, nativeQuery = true)
    List<LocationEntity> findNearbyUsers(
            @Param("lat") double latitude,
            @Param("lng") double longitude,
            @Param("radius") double radiusMeter
    );
}