package com.gera.elevator.domain;

public enum Direction {
    UP,
    DOWN,
    IDLE;

    public boolean isTravelDirection() {
        return this == UP || this == DOWN;
    }

    public static Direction between(int from, int to) {
        if (to > from) {
            return UP;
        }
        if (to < from) {
            return DOWN;
        }
        return IDLE;
    }
}
