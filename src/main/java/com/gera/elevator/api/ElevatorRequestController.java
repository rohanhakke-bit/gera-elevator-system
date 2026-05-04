package com.gera.elevator.api;

import com.gera.elevator.api.dto.ExternalRequestDto;
import com.gera.elevator.api.dto.InternalRequestDto;
import com.gera.elevator.api.dto.RequestView;
import com.gera.elevator.domain.ElevatorRequest;
import com.gera.elevator.service.ElevatorSystemService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class ElevatorRequestController {
    private final ElevatorSystemService service;

    public ElevatorRequestController(ElevatorSystemService service) {
        this.service = service;
    }

    @PostMapping("/external")
    public RequestView external(@RequestBody ExternalRequestDto request) {
        ElevatorRequest saved = service.addExternalRequest(request.floor(), request.direction());
        return RequestView.from(saved);
    }

    @PostMapping("/internal")
    public RequestView internal(@RequestBody InternalRequestDto request) {
        ElevatorRequest saved = service.addInternalRequest(request.elevatorCode(), request.destinationFloor());
        return RequestView.from(saved);
    }

    @PostMapping("/emergency")
    public RequestView emergency(@RequestBody ExternalRequestDto request) {
        ElevatorRequest saved = service.addEmergencyRequest(request.floor(), request.direction());
        return RequestView.from(saved);
    }
}
