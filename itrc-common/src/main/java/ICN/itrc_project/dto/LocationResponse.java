package ICN.itrc_project.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LocationResponse {
    private String userId;
    private Double latitude;
    private Double longitude;
    private Double distanceMeter;
}
