package com.hiveapp.shared.exception;

/** One idempotency key was reused for a materially different request. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency key was already used for a different request.");
    }
}
