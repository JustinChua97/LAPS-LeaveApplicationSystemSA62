package com.iss.laps.exception;

public class PublicHolidaySyncException extends RuntimeException {

    public PublicHolidaySyncException(String message) {
        super(message);
    }

    public PublicHolidaySyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
