package com.gera.elevator.service;

import com.gera.elevator.api.dto.ElevatorView;
import com.gera.elevator.api.dto.RequestView;
import com.gera.elevator.api.dto.SystemStateView;
import com.gera.elevator.config.ElevatorProperties;
import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.Elevator;
import com.gera.elevator.domain.ElevatorRequest;
import com.gera.elevator.domain.RequestStatus;
import com.gera.elevator.domain.RequestType;
import com.gera.elevator.repository.ElevatorRepository;
import com.gera.elevator.repository.ElevatorRequestRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class ElevatorSystemService {
    private static final List<RequestStatus> ACTIVE_REQUEST_STATUSES = List.of(RequestStatus.WAITING, RequestStatus.ASSIGNED, RequestStatus.REASSIGNED);

    private final ElevatorProperties properties;
    private final ElevatorRepository elevatorRepository;
    private final ElevatorRequestRepository requestRepository;
    private final EtaCalculator etaCalculator;
    private final ReentrantLock lock = new ReentrantLock(true);
    private Instant simulationNow = Instant.now();

    public ElevatorSystemService(ElevatorProperties properties,
                                 ElevatorRepository elevatorRepository,
                                 ElevatorRequestRepository requestRepository,
                                 EtaCalculator etaCalculator) {
        this.properties = properties;
        this.elevatorRepository = elevatorRepository;
        this.requestRepository = requestRepository;
        this.etaCalculator = etaCalculator;
    }

    @PostConstruct
    @Transactional
    public void initialize() {
        if (elevatorRepository.count() == 0) {
            resetSystem();
        }
    }

    @Transactional
    public ElevatorRequest addExternalRequest(int floor, Direction direction) {
        lock.lock();
        try {
            validateFloor(floor);
            Direction normalizedDirection = normalizeHallDirection(floor, direction);
            ElevatorRequest candidate = ElevatorRequest.external(floor, normalizedDirection);
            Optional<ElevatorRequest> existing = requestRepository
                    .findFirstByDedupKeyAndStatusIn(candidate.getDedupKey(), ACTIVE_REQUEST_STATUSES);
            if (existing.isPresent()) {
                return existing.get();
            }
            ElevatorRequest request = requestRepository.save(candidate);
            request.setCreatedAt(simulationNow);
            assignExternalRequest(request);
            return requestRepository.save(request);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public ElevatorRequest addEmergencyRequest(int floor, Direction direction) {
        lock.lock();
        try {
            validateFloor(floor);
            ElevatorRequest request = requestRepository.save(ElevatorRequest.emergency(floor, normalizeHallDirection(floor, direction)));
            request.setCreatedAt(simulationNow);
            Elevator elevator = bestElevatorByEta(request, elevatorRepository.findAll(), elevatorCandidate -> true)
                    .orElseThrow(() -> new IllegalStateException("No active elevator available"));
            elevator.addStopForDirection(floor, request.getDirection());
            request.assignTo(elevator.getCode());
            elevator.incrementAssignedHallCalls();
            elevatorRepository.save(elevator);
            return requestRepository.save(request);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public ElevatorRequest addInternalRequest(String elevatorCode, int destinationFloor) {
        lock.lock();
        try {
            validateFloor(destinationFloor);
            Elevator elevator = elevatorRepository.findById(elevatorCode)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown elevator " + elevatorCode));
            if (!elevator.isAvailable()) {
                throw new IllegalStateException("Elevator " + elevatorCode + " is not available");
            }
            ElevatorRequest request = ElevatorRequest.internal(elevatorCode, destinationFloor);
            request.setCreatedAt(simulationNow);
            Optional<ElevatorRequest> existing = requestRepository
                    .findFirstByDedupKeyAndStatusIn(request.getDedupKey(), ACTIVE_REQUEST_STATUSES);
            if (existing.isPresent()) {
                return existing.get();
            }
            elevator.addStop(destinationFloor);
            request.assignTo(elevator.getCode());
            elevatorRepository.save(elevator);
            return requestRepository.save(request);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void tick(int seconds) {
        lock.lock();
        try {
            for (int i = 0; i < seconds; i++) {
                simulationNow = simulationNow.plusSeconds(1);
                preventStarvation();
                for (Elevator elevator : elevatorRepository.findAll()) {
                    elevator.advanceOneSecond(
                            properties.moveSecondsPerFloor(),
                            properties.doorOpenSeconds(),
                            properties.doorCloseSeconds()
                    );
                    completeRequestsServedAt(elevator);
                    elevatorRepository.save(elevator);
                }
                assignWaitingRequests();
            }
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void markBroken(String code, boolean stuck) {
        lock.lock();
        try {
            Elevator elevator = elevatorRepository.findById(code)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown elevator " + code));
            if (stuck) {
                elevator.markStuck();
            } else {
                elevator.markBroken();
            }
            for (ElevatorRequest request : requestRepository.findByAssignedElevatorCodeAndStatusIn(code, ACTIVE_REQUEST_STATUSES)) {
                request.markReassigned();
                requestRepository.save(request);
            }
            elevator.clearQueues();
            elevatorRepository.save(elevator);
            assignWaitingRequests();
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void restoreElevator(String code) {
        lock.lock();
        try {
            Elevator elevator = elevatorRepository.findById(code)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown elevator " + code));
            elevator.restore();
            elevatorRepository.save(elevator);
            assignWaitingRequests();
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void rebalanceIdleElevators() {
        lock.lock();
        try {
            List<Elevator> idle = elevatorRepository.findAll().stream().filter(Elevator::isIdle).toList();
            if (idle.isEmpty()) {
                return;
            }
            int spacing = Math.max(1, properties.totalFloors() / (idle.size() + 1));
            for (int i = 0; i < idle.size(); i++) {
                int target = Math.min(properties.totalFloors(), Math.max(1, spacing * (i + 1)));
                idle.get(i).addStop(target);
                elevatorRepository.save(idle.get(i));
            }
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void predictivePositionForRush(Direction rushDirection) {
        lock.lock();
        try {
            int target = rushDirection == Direction.UP ? 1 : properties.totalFloors();
            for (Elevator elevator : elevatorRepository.findAll().stream().filter(Elevator::isIdle).toList()) {
                elevator.addStop(target);
                elevatorRepository.save(elevator);
            }
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void resetSystem() {
        lock.lock();
        try {
            requestRepository.deleteAll();
            elevatorRepository.deleteAll();
            simulationNow = Instant.now();
            int[] defaultFloors = defaultFloors();
            for (int i = 0; i < properties.elevatorCodes().size(); i++) {
                elevatorRepository.save(new Elevator(properties.elevatorCodes().get(i), defaultFloors[i], properties.defaultCapacity()));
            }
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void setElevatorForTest(String code, int floor, Direction direction, int currentLoad) {
        Elevator elevator = elevatorRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown elevator " + code));
        elevator.clearQueues();
        elevator.setCurrentFloor(floor);
        elevator.setDirection(direction);
        elevator.setCurrentLoad(currentLoad);
        elevatorRepository.save(elevator);
    }

    @Transactional
    public SystemStateView state() {
        return new SystemStateView(
                properties.totalFloors(),
                elevatorRepository.findAll().stream()
                        .sorted(Comparator.comparing(Elevator::getCode))
                        .map(ElevatorView::from)
                        .toList(),
                requestRepository.findByStatusIn(ACTIVE_REQUEST_STATUSES).stream()
                        .map(RequestView::from)
                        .toList()
        );
    }

    private void assignWaitingRequests() {
        for (ElevatorRequest request : requestRepository.findByStatusIn(List.of(RequestStatus.WAITING, RequestStatus.REASSIGNED))) {
            if (request.getType() == RequestType.EXTERNAL || request.getType() == RequestType.EMERGENCY) {
                assignExternalRequest(request);
                requestRepository.save(request);
            }
        }
    }

//    private void assignExternalRequest(ElevatorRequest request) {
//        List<Elevator> elevators = elevatorRepository.findAll().stream().filter(Elevator::isAvailable).toList();
//        if (elevators.isEmpty()) {
//            return;
//        }
//
//        Optional<Elevator> sameDirection = bestElevatorByEta(request, elevators,
//                elevator -> elevator.isOnTheWay(request.getSourceFloor(), request.getDirection())
//                        && lateInsertDelay(elevator, request.getSourceFloor(), request.getDirection()) <= properties.lateInsertMaxDelaySeconds());
//        Elevator selected = sameDirection
//                .or(() -> nearestIdle(elevators, request.getSourceFloor()))
//                .or(() -> bestElevatorByEta(request, elevators, elevator -> true))
//                .orElseThrow();
//
//        selected.addStopForDirection(request.getSourceFloor(), request.getDirection());
//        selected.incrementAssignedHallCalls();
//        request.assignTo(selected.getCode());
//        elevatorRepository.save(selected);
//    }

    private void assignExternalRequest(ElevatorRequest request) {
        List<Elevator> elevators = elevatorRepository.findAll()
                .stream()
                .filter(Elevator::isAvailable)
                .toList();

        if (elevators.isEmpty()) {
            return;
        }

        Optional<Elevator> sameDirection = bestElevatorByEta(
                request,
                elevators,
                elevator -> elevator.isOnTheWay(request.getSourceFloor(), request.getDirection())
                        && lateInsertDelay(
                        elevator,
                        request.getSourceFloor(),
                        request.getDirection()
                ) <= properties.lateInsertMaxDelaySeconds()
        );

        Optional<Elevator> idle = nearestIdleByEta(elevators, request);

        Elevator selected;

        if (sameDirection.isPresent() && idle.isPresent()) {
            int sameDirectionEta = weightedEta(sameDirection.get(), request);
            int idleEta = weightedEta(idle.get(), request);

            if (idleEta < sameDirectionEta) {
                selected = idle.get();
            } else {
                selected = sameDirection.get();
            }
        } else {
            selected = idle
                    .or(() -> sameDirection)
                    .or(() -> bestElevatorByEta(request, elevators, elevator -> true))
                    .orElseThrow();
        }

        selected.addStopForDirection(request.getSourceFloor(), request.getDirection());
        selected.incrementAssignedHallCalls();
        request.assignTo(selected.getCode());
        elevatorRepository.save(selected);
    }
    
    private Optional<Elevator> nearestIdleByEta(List<Elevator> elevators, ElevatorRequest request) {
        return elevators.stream()
                .filter(Elevator::isIdle)
                .min(Comparator.comparingInt((Elevator elevator) -> weightedEta(elevator, request))
                        .thenComparingInt(Elevator::getAssignedHallCalls)
                        .thenComparing(Elevator::getCode));
    }
    private Optional<Elevator> nearestIdle(List<Elevator> elevators, int floor) {
        return elevators.stream()
                .filter(Elevator::isIdle)
                .min(Comparator.comparingInt((Elevator elevator) -> Math.abs(elevator.getCurrentFloor() - floor))
                        .thenComparingInt(Elevator::getAssignedHallCalls)
                        .thenComparing(Elevator::getCode));
    }

    private Optional<Elevator> bestElevatorByEta(ElevatorRequest request, List<Elevator> elevators, Predicate<Elevator> filter) {
        return elevators.stream()
                .filter(Elevator::isAvailable)
                .filter(filter)
                .min(Comparator.comparingInt((Elevator elevator) -> weightedEta(elevator, request))
                        .thenComparingInt(Elevator::getAssignedHallCalls)
                        .thenComparing(Elevator::getCode));
    }

    private int weightedEta(Elevator elevator, ElevatorRequest request) {
        int floor = request.getSourceFloor() != null ? request.getSourceFloor() : request.getDestinationFloor();
        int eta = etaCalculator.etaSeconds(elevator, floor, request.getDirection());
        int usagePenalty = elevator.getAssignedHallCalls() * 2;
        int priorityBonus = request.getType() == RequestType.EMERGENCY || request.isPriorityBoosted() ? -properties.maxWaitSeconds() : 0;
        return eta + usagePenalty + priorityBonus;
    }

    private int lateInsertDelay(Elevator elevator, int floor, Direction direction) {
        if (!elevator.isOnTheWay(floor, direction)) {
            return Integer.MAX_VALUE / 4;
        }
        return (elevator.hasStop(floor) ? 0 : properties.doorOpenSeconds() + properties.doorCloseSeconds());
    }

    private void preventStarvation() {
        Instant limit = simulationNow.minus(Duration.ofSeconds(properties.maxWaitSeconds()));
        for (ElevatorRequest request : requestRepository.findByStatusIn(ACTIVE_REQUEST_STATUSES)) {
            if (request.getCreatedAt().isBefore(limit) && !request.isPriorityBoosted()) {
                request.boostPriority();
                requestRepository.save(request);
            }
        }
    }

    private void completeRequestsServedAt(Elevator elevator) {
        if (elevator.getDoorState() != com.gera.elevator.domain.DoorState.OPENING
                && elevator.getDoorState() != com.gera.elevator.domain.DoorState.OPEN) {
            return;
        }
        for (ElevatorRequest request : requestRepository.findByAssignedElevatorCodeAndStatusIn(elevator.getCode(), ACTIVE_REQUEST_STATUSES)) {
            boolean servedExternal = request.getSourceFloor() != null && request.getSourceFloor() == elevator.getCurrentFloor();
            boolean servedInternal = request.getDestinationFloor() != null && request.getDestinationFloor() == elevator.getCurrentFloor();
            if (servedExternal || servedInternal) {
                request.markCompleted();
                requestRepository.save(request);
            }
        }
    }

    private Direction normalizeHallDirection(int floor, Direction direction) {
        if (floor == 1) {
            return Direction.UP;
        }
        if (floor == properties.totalFloors()) {
            return Direction.DOWN;
        }
        if (!direction.isTravelDirection()) {
            throw new IllegalArgumentException("Hall call direction must be UP or DOWN");
        }
        return direction;
    }

    private void validateFloor(int floor) {
        if (floor < 1 || floor > properties.totalFloors()) {
            throw new IllegalArgumentException("Floor must be between 1 and " + properties.totalFloors());
        }
    }


    private int[] defaultFloors() {
        int count = properties.elevatorCodes().size();
        int[] floors = new int[count];

        for (int i = 0; i < count; i++) {
            floors[i] = 1;
        }

        return floors;
    }
    
    public Map<String, ElevatorView> elevatorsByCode() {
        return state().elevators().stream().collect(Collectors.toMap(ElevatorView::code, view -> view));
    }
}
