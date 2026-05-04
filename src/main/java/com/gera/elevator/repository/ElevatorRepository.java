package com.gera.elevator.repository;

import com.gera.elevator.domain.Elevator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElevatorRepository extends JpaRepository<Elevator, String> {
}
