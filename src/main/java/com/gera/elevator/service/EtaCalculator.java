package com.gera.elevator.service;

import java.util.TreeSet;

import com.gera.elevator.config.ElevatorProperties;
import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.Elevator;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import java.util.TreeSet;
import java.util.TreeSet;

import java.util.TreeSet;

@Component
public class EtaCalculator {
    private final ElevatorProperties properties;

    public EtaCalculator(ElevatorProperties properties) {
        this.properties = properties;
    }

    public int etaSeconds(Elevator elevator, int floor, Direction requestDirection) {
        if (!elevator.isAvailable()) {
            return Integer.MAX_VALUE / 4;
        }
        if (elevator.getCurrentFloor() == floor) {
            return elevator.getDoorState().name().startsWith("CLOS") ? properties.doorOpenSeconds() : 0;
        }

        int travel = Math.abs(elevator.getCurrentFloor() - floor) * properties.moveSecondsPerFloor();
        int stops = stopsBefore(elevator, floor, requestDirection);
        int door = stops * (properties.doorOpenSeconds() + properties.doorCloseSeconds());
        return travel + door + elevator.getMoveSecondsRemaining() + elevator.getDoorSecondsRemaining();
    }

    private int stopsBefore(Elevator elevator, int floor, Direction requestDirection) {
        List<Integer> route = plannedRoute(elevator);
        int stops = 0;
        for (int stop : route) {
            if (stop == floor) {
                return stops;
            }
            stops++;
        }
        if (elevator.getDirection() == Direction.IDLE) {
            return 0;
        }
        if (elevator.isOnTheWay(floor, requestDirection)) {
            return (int) route.stream()
                    .filter(stop -> elevator.getDirection() == Direction.UP
                            ? stop < floor && stop >= elevator.getCurrentFloor()
                            : stop > floor && stop <= elevator.getCurrentFloor())
                    .count();
        }
        return route.size();
    }

    public List<Integer> plannedRoute(Elevator elevator) {
        List<Integer> route = new ArrayList<>();
        if (elevator.getDirection() == Direction.DOWN) {
        	route.addAll(new TreeSet<>(elevator.getDownStops()).descendingSet());

            route.addAll(elevator.getUpStops());
        } else {
            route.addAll(elevator.getUpStops());
            route.addAll(new TreeSet<>(elevator.getDownStops()).descendingSet());

        }
        return route;
    }
}
