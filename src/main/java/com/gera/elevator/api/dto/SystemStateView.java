package com.gera.elevator.api.dto;

import java.util.List;

public record SystemStateView(
        int totalFloors,
        List<ElevatorView> elevators,
        List<RequestView> activeRequests
) {
}
