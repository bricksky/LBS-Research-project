package ICN.itrc_project.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "location_data", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_lat_lng", columnList = "latitude, longitude")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double accuracy;
    private String serviceType;
    private Long timestamp;

    private LocalDateTime updatedAt;

    @Builder
    public LocationEntity(Long id, String userId, Double latitude, Double longitude, Double speed, Double accuracy, String serviceType, Long timestamp, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.accuracy = accuracy;
        this.serviceType = serviceType;
        this.timestamp = timestamp;
        this.updatedAt = updatedAt;
    }

    public void updateLocation(Double latitude, Double longitude, Double speed, Double accuracy, Long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.accuracy = accuracy;
        this.timestamp = timestamp;
        this.updatedAt = LocalDateTime.now();
    }
}
