package ICN.itrc_project.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class LocationRequest {

    // 1. 식별 정보
    @NotBlank(message = "사용자 ID는 필수입니다.")
    private String userId;          // Grab: trj_id

    @NotBlank(message = "서비스 타입은 필수입니다.")
    private String serviceType;     // Grab: driving_mode (car/motorcycle)

    // 2. 공간 정보
    @NotNull(message = "위도는 필수입니다.")
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;        // Grab: rawlat

    @NotNull(message = "경도는 필수입니다.")
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;       // Grab: rawlng

    @Min(0)
    @Max(360)
    private Double heading;         // Grab: bearing

    @PositiveOrZero
    private Double speed;           // Grab: speed

    // 3. 품질 및 상태 정보
    private Double accuracy;        // Grab: accuracy
    private String status;          // 기본값: ON_TASK

    // 4. 시간 정보
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();    // Grab: pingtimestamp

    /**
     * Grab-Posisi 데이터셋의 원본 로우(Row) 데이터를 우리 DTO 규격으로 변환하는 생성 메서드
     *
     * @param trjId        Grab의 trj_id
     * @param mode         Grab의 driving_mode (car, motorcycle)
     * @param lat          Grab의 rawlat
     * @param lng          Grab의 rawlng
     * @param bearing      Grab의 bearing
     * @param speedMs      Grab의 speed (m/s 단위)
     * @param acc          Grab의 accuracy
     * @param timestampSec Grab의 pingtimestamp (Unix seconds)
     */
    public static LocationRequest fromGrabDataset(
            String trjId, String mode, double lat, double lng,
            double bearing, double speedMs, double acc, long timestampSec) {

        String serviceType = switch (mode != null ? mode.toLowerCase() : "") {
            case "car" -> "TAXI";
            case "motorcycle" -> "BIKE";
            default -> "UNKNOWN";
        };

        return LocationRequest.builder()
                .userId(trjId)
                .serviceType(serviceType)
                .latitude(lat)
                .longitude(lng)
                .heading(bearing)
                .speed(speedMs * 3.6)
                .accuracy(acc)
                .status("ON_TASK")
                .timestamp(timestampSec * 1000)
                .build();
    }
}
