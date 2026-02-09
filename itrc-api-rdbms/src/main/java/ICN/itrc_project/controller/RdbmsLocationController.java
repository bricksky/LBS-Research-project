package ICN.itrc_project.controller;

import ICN.itrc_project.domain.LocationEntity;
import ICN.itrc_project.dto.LocationRequest;
import ICN.itrc_project.dto.LocationResponse;
import ICN.itrc_project.repository.LocationRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("api/v1/rdbms")
@RequiredArgsConstructor
public class RdbmsLocationController {
    private final LocationRepository locationRepository;

    @PostMapping("/update")
    @Transactional
    public ResponseEntity<String> updateLocation(@Valid @RequestBody LocationRequest request) {

        locationRepository.findByUserId(request.getUserId())
                .ifPresentOrElse(
                        entity -> entity.updateLocation(
                                request.getLatitude(), request.getLongitude(), request.getSpeed(), request.getAccuracy(), request.getTimestamp()
                        ),
                        () -> locationRepository.save(LocationEntity.builder()
                                .userId(request.getUserId())
                                .latitude(request.getLatitude())
                                .longitude(request.getLongitude())
                                .speed(request.getSpeed())
                                .accuracy(request.getAccuracy())
                                .serviceType(request.getServiceType())
                                .timestamp(request.getTimestamp())
                                .build())
                );
        return ResponseEntity.ok("RDBMS Saved");
    }

    @GetMapping("/range")
    public ResponseEntity<List<LocationResponse>> searchByRange(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam double radiusMeter
    ) {
        long start = System.currentTimeMillis();

        List<LocationEntity> entities = locationRepository.findNearbyUsers(lat, lng, radiusMeter);

        List<LocationResponse> response = entities.stream()
                .map(e -> LocationResponse.builder()
                        .userId(e.getUserId())
                        .latitude(e.getLatitude())
                        .longitude(e.getLongitude())
                        .distanceMeter(0.0)
                        .build())
                .collect(Collectors.toList());

        long duration = System.currentTimeMillis() - start;
        log.info(">>> [RDBMS Polling] 주변 {}명 발견 (DB 조회: {}ms)", response.size(), duration);

        return ResponseEntity.ok(response);
    }
}
