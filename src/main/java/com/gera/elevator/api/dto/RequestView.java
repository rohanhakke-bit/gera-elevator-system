package com.gera.elevator.api.dto;

import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.ElevatorRequest;
import com.gera.elevator.domain.RequestStatus;
import com.gera.elevator.domain.RequestType;

import java.time.Instant;

public record RequestView(
        Long id,
        RequestType type,
        Integer sourceFloor,
        Integer destinationFloor,
        Direction direction,
        RequestStatus status,
        String assignedElevatorCode,
        Instant createdAt,
        boolean priorityBoosted
) {
    public static RequestView from(ElevatorRequest request) {
        return new RequestView(
                request.getId(),
                request.getType(),
                request.getSourceFloor(),
                request.getDestinationFloor(),
                request.getDirection(),
                request.getStatus(),
                request.getAssignedElevatorCode(),
                request.getCreatedAt(),
                request.isPriorityBoosted()
        );
    }
}
