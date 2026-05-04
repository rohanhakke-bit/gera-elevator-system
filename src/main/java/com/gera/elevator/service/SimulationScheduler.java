package com.gera.elevator.service;

import com.gera.elevator.config.ElevatorProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SimulationScheduler {
    private final ElevatorProperties properties;
    private final ElevatorSystemService service;

    public SimulationScheduler(ElevatorProperties properties, ElevatorSystemService service) {
        this.properties = properties;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${gera.elevator.scheduler-tick-seconds:1}000")
    public void tick() {
        if (properties.schedulerEnabled()) {
            service.tick(properties.schedulerTickSeconds());
        }
    }
}
