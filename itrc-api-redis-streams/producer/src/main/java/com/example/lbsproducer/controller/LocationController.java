package com.example.lbsproducer.controller;

import com.example.lbsproducer.dto.LocationRequest;
import com.example.lbsproducer.service.LocationEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationEventProducer locationEventProducer;

    @PostMapping
    public ResponseEntity<Void> receiveLocation(@RequestBody LocationRequest request) {
        locationEventProducer.sendLocationEvent(request);
        return ResponseEntity.ok().build();
    }
}