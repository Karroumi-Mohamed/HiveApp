package com.hiveapp.shared.exception;

public class InvalidPermissionGrantException extends RuntimeException {
    public InvalidPermissionGrantException(String message) {
        super(message);
    }
}
