package com.hiveapp.shared.exception;

public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resource, String field, Object value) {
        super(String.format("%s already exists with %s: '%s'", resource, field, value));
    }

    public DuplicateResourceException(String message) {
        super(message);
    }
}
