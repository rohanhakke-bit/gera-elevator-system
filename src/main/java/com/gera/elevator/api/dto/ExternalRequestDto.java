package com.gera.elevator.api.dto;

import com.gera.elevator.domain.Direction;

public record ExternalRequestDto(
        int floor,
        Direction direction
){
}
