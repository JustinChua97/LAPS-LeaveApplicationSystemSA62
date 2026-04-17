package com.iss.laps.exception;

public class MessageNotSentException extends RuntimeException {
    public MessageNotSentException(String message) {
        super(message);

    }
    public MessageNotSentException(String message, Throwable cause) {
        super(message, cause);
    }

}
