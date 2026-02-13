package ICN.itrc_project.dto;

import ICN.itrc_project.domain.LocationEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RdbmsLocationResponse {
    private Long id;
    private String userId;
    private double latitude;
    private double longitude;
    private double speed;
    private double accuracy;
    private String serviceType;
    private Long timestamp;
    private LocalDateTime updatedAt;

    public static RdbmsLocationResponse from(LocationEntity entity) {
        return RdbmsLocationResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .latitude(entity.getLocation().getY())
                .longitude(entity.getLocation().getX())
                .speed(entity.getSpeed())
                .accuracy(entity.getAccuracy())
                .serviceType(entity.getServiceType())
                .timestamp(entity.getTimestamp())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
