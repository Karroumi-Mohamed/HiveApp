package com.hiveapp.shared.exception;

import java.util.List;

public class OperationBlockedException extends RuntimeException {
    private final List<String> details;

    public OperationBlockedException(String message, List<String> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    public List<String> getDetails() {
        return details;
    }
}
