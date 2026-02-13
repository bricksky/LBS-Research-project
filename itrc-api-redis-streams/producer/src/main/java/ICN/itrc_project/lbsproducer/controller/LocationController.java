package ICN.itrc_project.lbsproducer.controller;

import ICN.itrc_project.lbsproducer.dto.LocationRequest;
import ICN.itrc_project.lbsproducer.service.LocationEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/redis")
@RequiredArgsConstructor
public class LocationController {

    private final LocationEventProducer locationEventProducer;

    @PostMapping("/update")
    public ResponseEntity<Void> receiveLocation(@RequestBody LocationRequest request) {
        locationEventProducer.sendLocationEvent(request);
        return ResponseEntity.ok().build();
    }
}