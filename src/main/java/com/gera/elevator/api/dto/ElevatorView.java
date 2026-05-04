package com.gera.elevator.api.dto;

import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.DoorState;
import com.gera.elevator.domain.Elevator;
import com.gera.elevator.domain.ElevatorStatus;

import java.util.List;
import java.util.TreeSet;

public record ElevatorView(
        String code,
        int currentFloor,
        Direction direction,
        DoorState doorState,
        ElevatorStatus status,
        int capacity,
        int currentLoad,
        int moveSecondsRemaining,
        int doorSecondsRemaining,
        List<Integer> upStops,
        List<Integer> downStops
) {
    public static ElevatorView from(Elevator elevator) {
        return new ElevatorView(
                elevator.getCode(),
                elevator.getCurrentFloor(),
                elevator.getDirection(),
                elevator.getDoorState(),
                elevator.getStatus(),
                elevator.getCapacity(),
                elevator.getCurrentLoad(),
                elevator.getMoveSecondsRemaining(),
                elevator.getDoorSecondsRemaining(),
                List.copyOf(elevator.getUpStops()),
                new TreeSet<>(elevator.getDownStops()).descendingSet().stream().toList()
        );
    }
}
