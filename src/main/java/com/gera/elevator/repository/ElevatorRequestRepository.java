package com.gera.elevator.repository;

import com.gera.elevator.domain.ElevatorRequest;
import com.gera.elevator.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ElevatorRequestRepository extends JpaRepository<ElevatorRequest, Long> {
    Optional<ElevatorRequest> findFirstByDedupKeyAndStatusIn(String dedupKey, Collection<RequestStatus> statuses);

    List<ElevatorRequest> findByAssignedElevatorCodeAndStatusIn(String elevatorCode, Collection<RequestStatus> statuses);

    List<ElevatorRequest> findByStatusIn(Collection<RequestStatus> statuses);
}
