package com.gera.elevator.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.OptionalInt;
import java.util.SortedSet;
import java.util.TreeSet;

@Entity
@Table(name = "elevators")
public class Elevator {

    @Id
    private String code;

    private int currentFloor;

    @Enumerated(EnumType.STRING)
    private Direction direction = Direction.IDLE;

    @Enumerated(EnumType.STRING)
    private DoorState doorState = DoorState.CLOSED;

    @Enumerated(EnumType.STRING)
    private ElevatorStatus status = ElevatorStatus.ACTIVE;

    private int capacity;
    private int currentLoad;
    private int moveSecondsRemaining;
    private int doorSecondsRemaining;
    private int completedTrips;
    private int assignedHallCalls;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "elevator_up_stops", joinColumns = @JoinColumn(name = "elevator_code"))
    @Column(name = "floor")
    private SortedSet<Integer> upStops = new TreeSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "elevator_down_stops", joinColumns = @JoinColumn(name = "elevator_code"))
    @Column(name = "floor")
    private SortedSet<Integer> downStops = new TreeSet<>();

    @Version
    private long version;

    protected Elevator() {
    }

    public Elevator(String code, int currentFloor, int capacity) {
        this.code = code;
        this.currentFloor = currentFloor;
        this.capacity = capacity;
    }

    public void addStop(int floor) {
        if (floor > currentFloor) {
            upStops.add(floor);
        } else if (floor < currentFloor) {
            downStops.add(floor);
        } else {
            openOrReopenDoors(10);
        }
        chooseDirectionSafely();
    }


    public void addStopForDirection(int floor, Direction requestedDirection) {
        if (floor == currentFloor) {
            openOrReopenDoors(10);
            return;
        }
        if (floor > currentFloor) {
            upStops.add(floor);
        } else {
            downStops.add(floor);
        }

        chooseDirectionSafely();
    }
    public boolean hasStop(int floor) {
        return upStops.contains(floor) || downStops.contains(floor);
    }

    public boolean isAvailable() {
        return status == ElevatorStatus.ACTIVE && currentLoad < capacity;
    }

    public boolean isIdle() {
        return direction == Direction.IDLE
                && upStops.isEmpty()
                && downStops.isEmpty()
                && doorState == DoorState.CLOSED
                && moveSecondsRemaining == 0;
    }

    public boolean isOnTheWay(int floor, Direction requestedDirection) {
        if (!isAvailable() || direction != requestedDirection) {
            return false;
        }

        if (direction == Direction.UP) {
            return floor >= currentFloor;
        }

        if (direction == Direction.DOWN) {
            return floor <= currentFloor;
        }

        return false;
    }

    public void openOrReopenDoors(int openSeconds) {
        if (status != ElevatorStatus.ACTIVE) {
            return;
        }

        doorState = DoorState.OPENING;
        doorSecondsRemaining = openSeconds;
        moveSecondsRemaining = 0;
    }

    public void advanceOneSecond(int moveSecondsPerFloor, int doorOpenSeconds, int doorCloseSeconds) {
        if (status != ElevatorStatus.ACTIVE) {
            return;
        }

        if (doorState != DoorState.CLOSED) {
            advanceDoor(doorOpenSeconds, doorCloseSeconds);
            return;
        }

        if (moveSecondsRemaining > 0) {
            moveSecondsRemaining--;

            if (moveSecondsRemaining == 0) {
                moveOneFloorSafely();

                if (shouldStopAtCurrentFloor()) {
                    removeCurrentStop();
                    completedTrips++;
                    openOrReopenDoors(doorOpenSeconds);
                } else {
                    chooseDirectionSafely();
                }
            }

            return;
        }

        chooseDirectionSafely();

        if (direction == Direction.IDLE) {
            return;
        }

        if (!hasStopsInCurrentDirection()) {
            chooseDirectionSafely();
            if (direction == Direction.IDLE) {
                return;
            }
        }

        moveSecondsRemaining = moveSecondsPerFloor;
    }

    private void moveOneFloorSafely() {
        if (direction == Direction.UP) {
            currentFloor++;
        } else if (direction == Direction.DOWN) {
            currentFloor--;
        }

        if (currentFloor < 1) {
            currentFloor = 1;
            direction = Direction.IDLE;
            downStops.clear();
            moveSecondsRemaining = 0;
        }
    }

    private void advanceDoor(int doorOpenSeconds, int doorCloseSeconds) {
        if (doorSecondsRemaining > 0) {
            doorSecondsRemaining--;
            return;
        }

        if (doorState == DoorState.OPENING) {
            doorState = DoorState.OPEN;
            doorSecondsRemaining = doorOpenSeconds;
            return;
        }

        if (doorState == DoorState.OPEN) {
            doorState = DoorState.CLOSING;
            doorSecondsRemaining = doorCloseSeconds;
            return;
        }

        if (doorState == DoorState.CLOSING) {
            doorState = DoorState.CLOSED;
            chooseDirectionSafely();
        }
    }

    private boolean shouldStopAtCurrentFloor() {
        return direction == Direction.UP && upStops.contains(currentFloor)
                || direction == Direction.DOWN && downStops.contains(currentFloor);
    }

    private void removeCurrentStop() {
        upStops.remove(currentFloor);
        downStops.remove(currentFloor);
    }

    private boolean hasStopsInCurrentDirection() {
        if (direction == Direction.UP) {
            return upStops.stream().anyMatch(stop -> stop > currentFloor);
        }

        if (direction == Direction.DOWN) {
            return downStops.stream().anyMatch(stop -> stop < currentFloor);
        }

        return false;
    }

    private void chooseDirectionSafely() {
        if (direction == Direction.UP && upStops.stream().anyMatch(stop -> stop > currentFloor)) {
            return;
        }

        if (direction == Direction.DOWN && downStops.stream().anyMatch(stop -> stop < currentFloor)) {
            return;
        }

        if (upStops.stream().anyMatch(stop -> stop > currentFloor)) {
            direction = Direction.UP;
            return;
        }

        if (downStops.stream().anyMatch(stop -> stop < currentFloor)) {
            direction = Direction.DOWN;
            return;
        }

        if (upStops.contains(currentFloor) || downStops.contains(currentFloor)) {
            removeCurrentStop();
            openOrReopenDoors(10);
            return;
        }

        direction = Direction.IDLE;
        moveSecondsRemaining = 0;
    }

    public void markBroken() {
        status = ElevatorStatus.BROKEN;
        direction = Direction.IDLE;
        moveSecondsRemaining = 0;
        doorSecondsRemaining = 0;
    }

    public void markStuck() {
        status = ElevatorStatus.STUCK;
        direction = Direction.IDLE;
        moveSecondsRemaining = 0;
        doorSecondsRemaining = 0;
    }

    public void restore() {
        status = ElevatorStatus.ACTIVE;
        doorState = DoorState.CLOSED;
        chooseDirectionSafely();
    }

    public void clearQueues() {
        upStops.clear();
        downStops.clear();
        direction = Direction.IDLE;
        moveSecondsRemaining = 0;
    }

    public OptionalInt nextStopInCurrentDirection() {
        if (direction == Direction.UP && !upStops.isEmpty()) {
            return OptionalInt.of(upStops.first());
        }

        if (direction == Direction.DOWN && !downStops.isEmpty()) {
            return OptionalInt.of(downStops.last());
        }

        return OptionalInt.empty();
    }

    public String getCode() {
        return code;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public void setCurrentFloor(int currentFloor) {
        this.currentFloor = Math.max(1, currentFloor);
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public DoorState getDoorState() {
        return doorState;
    }

    public void setDoorState(DoorState doorState) {
        this.doorState = doorState;
    }

    public ElevatorStatus getStatus() {
        return status;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(int currentLoad) {
        this.currentLoad = currentLoad;
    }

    public int getMoveSecondsRemaining() {
        return moveSecondsRemaining;
    }

    public int getDoorSecondsRemaining() {
        return doorSecondsRemaining;
    }

    public int getCompletedTrips() {
        return completedTrips;
    }

    public int getAssignedHallCalls() {
        return assignedHallCalls;
    }

    public void incrementAssignedHallCalls() {
        assignedHallCalls++;
    }

    public SortedSet<Integer> getUpStops() {
        return upStops;
    }

    public SortedSet<Integer> getDownStops() {
        return downStops;
    }
}
