package com.gera.elevator.api.dto;

public record InternalRequestDto(
        String elevatorCode,
        int destinationFloor
) {
}
