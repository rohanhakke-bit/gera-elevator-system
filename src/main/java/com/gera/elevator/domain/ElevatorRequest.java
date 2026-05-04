package com.gera.elevator.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "elevator_requests", indexes = {
        @Index(name = "idx_request_dedup", columnList = "dedup_key"),
        @Index(name = "idx_request_status", columnList = "status")
})
public class ElevatorRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private RequestType type;

    private Integer sourceFloor;
    private Integer destinationFloor;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.WAITING;

    @Column(name = "assigned_elevator_code")
    private String assignedElevatorCode;

    @Column(name = "dedup_key")
    private String dedupKey;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "priority_boosted")
    private boolean priorityBoosted;

    protected ElevatorRequest() {
    }

    public static ElevatorRequest external(int floor, Direction direction) {
        ElevatorRequest request = new ElevatorRequest();
        request.type = RequestType.EXTERNAL;
        request.sourceFloor = floor;
        request.direction = direction;
        request.dedupKey = "EXT:" + floor + ":" + direction;
        return request;
    }

    public static ElevatorRequest internal(String elevatorCode, int destinationFloor) {
        ElevatorRequest request = new ElevatorRequest();
        request.type = RequestType.INTERNAL;
        request.destinationFloor = destinationFloor;
        request.assignedElevatorCode = elevatorCode;
        request.dedupKey = "INT:" + elevatorCode + ":" + destinationFloor;
        return request;
    }

    public static ElevatorRequest emergency(int floor, Direction direction) {
        ElevatorRequest request = external(floor, direction);
        request.type = RequestType.EMERGENCY;
        request.dedupKey = "EMG:" + floor + ":" + direction + ":" + request.createdAt.toEpochMilli();
        return request;
    }

    public void assignTo(String elevatorCode) {
        assignedElevatorCode = elevatorCode;
        assignedAt = Instant.now();
        status = RequestStatus.ASSIGNED;
    }

    public void markCompleted() {
        status = RequestStatus.COMPLETED;
    }

    public void markReassigned() {
        status = RequestStatus.REASSIGNED;
        assignedElevatorCode = null;
        assignedAt = null;
    }

    public Long getId() {
        return id;
    }

    public RequestType getType() {
        return type;
    }

    public Integer getSourceFloor() {
        return sourceFloor;
    }

    public Integer getDestinationFloor() {
        return destinationFloor;
    }

    public Direction getDirection() {
        return direction;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getAssignedElevatorCode() {
        return assignedElevatorCode;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isPriorityBoosted() {
        return priorityBoosted;
    }

    public void boostPriority() {
        priorityBoosted = true;
    }
}
