package com.gera.elevator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "gera.elevator")
public class ElevatorProperties {

    private int totalFloors;
    private List<String> elevatorCodes;
    private int moveSecondsPerFloor;
    private int doorOpenSeconds;
    private int doorCloseSeconds;
    private int maxWaitSeconds;
    private int lateInsertMaxDelaySeconds;
    private int defaultCapacity;
    private boolean schedulerEnabled;
    private int schedulerTickSeconds;

    public int totalFloors() {
        return totalFloors;
    }

    public void setTotalFloors(int totalFloors) {
        this.totalFloors = totalFloors;
    }

    public List<String> elevatorCodes() {
        return elevatorCodes;
    }

    public void setElevatorCodes(List<String> elevatorCodes) {
        this.elevatorCodes = elevatorCodes;
    }

    public int moveSecondsPerFloor() {
        return moveSecondsPerFloor;
    }

    public void setMoveSecondsPerFloor(int moveSecondsPerFloor) {
        this.moveSecondsPerFloor = moveSecondsPerFloor;
    }

    public int doorOpenSeconds() {
        return doorOpenSeconds;
    }

    public void setDoorOpenSeconds(int doorOpenSeconds) {
        this.doorOpenSeconds = doorOpenSeconds;
    }

    public int doorCloseSeconds() {
        return doorCloseSeconds;
    }

    public void setDoorCloseSeconds(int doorCloseSeconds) {
        this.doorCloseSeconds = doorCloseSeconds;
    }

    public int maxWaitSeconds() {
        return maxWaitSeconds;
    }

    public void setMaxWaitSeconds(int maxWaitSeconds) {
        this.maxWaitSeconds = maxWaitSeconds;
    }

    public int lateInsertMaxDelaySeconds() {
        return lateInsertMaxDelaySeconds;
    }

    public void setLateInsertMaxDelaySeconds(int lateInsertMaxDelaySeconds) {
        this.lateInsertMaxDelaySeconds = lateInsertMaxDelaySeconds;
    }

    public int defaultCapacity() {
        return defaultCapacity;
    }

    public void setDefaultCapacity(int defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    public boolean schedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public int schedulerTickSeconds() {
        return schedulerTickSeconds;
    }

    public void setSchedulerTickSeconds(int schedulerTickSeconds) {
        this.schedulerTickSeconds = schedulerTickSeconds;
    }
}
