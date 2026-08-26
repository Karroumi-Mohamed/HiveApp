package com.hiveapp.shared.exception;

public class StaleResourceVersionException extends RuntimeException {
    public StaleResourceVersionException(String message) {
        super(message);
    }
}
