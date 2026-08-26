package com.hiveapp.shared.exception;

public class DraftSuccessorExistsException extends RuntimeException {
    public DraftSuccessorExistsException(String message) {
        super(message);
    }
}
