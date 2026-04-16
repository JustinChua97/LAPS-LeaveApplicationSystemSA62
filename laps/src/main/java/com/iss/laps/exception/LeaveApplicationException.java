package com.iss.laps.exception;

public class LeaveApplicationException extends RuntimeException {

    public LeaveApplicationException(String message) {
        super(message);
    }

    public LeaveApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
