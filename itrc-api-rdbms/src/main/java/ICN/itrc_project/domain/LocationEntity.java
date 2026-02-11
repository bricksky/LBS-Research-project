package ICN.itrc_project.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "location_data", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_location", columnList = "location")
})

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location;

    private Double speed;
    private Double accuracy;
    private String serviceType;
    private Long timestamp;

    private LocalDateTime updatedAt;

    @Builder
    public LocationEntity(String userId, Point location, Double speed, Double accuracy, String serviceType, Long timestamp) {
        this.userId = userId;
        this.location = location;
        this.speed = speed;
        this.accuracy = accuracy;
        this.serviceType = serviceType;
        this.timestamp = timestamp;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateLocation(Point location, Double speed, Double accuracy, Long timestamp) {
        this.location = location;
        this.speed = speed;
        this.accuracy = accuracy;
        this.timestamp = timestamp;
        this.updatedAt = LocalDateTime.now();
    }
}
