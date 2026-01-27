package ICN.itrc_project.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class LocationRequest {

    // 1. 식별 정보
    @NotBlank(message = "사용자 ID는 필수입니다.")
    private String userId;          // 라이더 또는 차량 고유 ID
    @NotBlank(message = "서비스 타입은 필수입니다.")
    private String serviceType;     // 예: DELIVERY, TAXI, BIKE

    // 2. 공간 정보
    @NotNull(message = "위도는 필수입니다.")
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;    // 위도

    @NotNull(message = "경도는 필수입니다.")
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;   // 경도

    @Min(0)
    @Max(360)
    private Double heading;    // 이동 방향 (0~360도)

    @PositiveOrZero
    private Double speed;      // 현재 속도 (km/h)

    // 3. 품질 및 상태 정보
    private Double accuracy;   // GPS 정확도
    private String status;     // 객체 상태 (AVAILABLE, ON_TASK, OFF_LINE)

    // 4. 시간 정보
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();
}
