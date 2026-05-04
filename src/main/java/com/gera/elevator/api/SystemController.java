package com.gera.elevator.api;

import com.gera.elevator.api.dto.SystemStateView;
import com.gera.elevator.domain.Direction;
import com.gera.elevator.service.ElevatorSystemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {
    private final ElevatorSystemService service;

    public SystemController(ElevatorSystemService service) {
        this.service = service;
    }

    @GetMapping("/system/state")
    public SystemStateView state() {
        return service.state();
    }

    @PostMapping("/system/reset")
    public SystemStateView reset() {
        service.resetSystem();
        return service.state();
    }

    @PostMapping("/simulation/tick")
    public SystemStateView tick(@RequestParam(defaultValue = "20") int seconds) {
        service.tick(seconds);
        return service.state();
    }

    @PostMapping("/elevators/{code}/breakdown")
    public SystemStateView breakdown(@PathVariable String code, @RequestParam(defaultValue = "false") boolean stuck) {
        service.markBroken(code, stuck);
        return service.state();
    }

    @PostMapping("/elevators/{code}/restore")
    public SystemStateView restore(@PathVariable String code) {
        service.restoreElevator(code);
        return service.state();
    }

    @PostMapping("/elevators/rebalance")
    public SystemStateView rebalance() {
        service.rebalanceIdleElevators();
        return service.state();
    }

    @PostMapping("/elevators/predictive-position")
    public SystemStateView predictivePosition(@RequestParam Direction rushDirection) {
        service.predictivePositionForRush(rushDirection);
        return service.state();
    }
}
