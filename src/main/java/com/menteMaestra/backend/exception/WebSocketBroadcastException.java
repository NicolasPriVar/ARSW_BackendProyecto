package com.menteMaestra.backend.exception;

public class WebSocketBroadcastException extends RuntimeException {
    public WebSocketBroadcastException(String message) {
        super(message);
    }

    public WebSocketBroadcastException(String message, Throwable cause) {
        super(message, cause);
    }
}