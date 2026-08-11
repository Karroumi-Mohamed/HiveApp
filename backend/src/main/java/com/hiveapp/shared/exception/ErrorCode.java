package com.hiveapp.shared.exception;

/**
 * Stable, machine-readable error identifiers.
 *
 * <p>Clients branch on these instead of matching {@code message} text, so messages stay free to be
 * reworded or translated. One HTTP status covers several situations — {@code 409} alone spans a
 * duplicate resource, a database conflict, an invalid state transition and a blocked operation —
 * and the code is what tells them apart.</p>
 *
 * <p><strong>Contract:</strong> once shipped, a constant is never renamed or given a new meaning.
 * Add a code when a caller genuinely needs to distinguish a case; never invent them speculatively.</p>
 */
public enum ErrorCode {

    // 400
    INVALID_REQUEST,
    INVALID_ARGUMENT,
    BUSINESS_RULE_VIOLATED,
    INVALID_PERMISSION_GRANT,

    // 401
    AUTHENTICATION_REQUIRED,
    UNAUTHENTICATED,
    INVALID_CREDENTIALS,

    // 402
    QUOTA_EXCEEDED,

    // 403
    FORBIDDEN,
    PERMISSION_DENIED,
    ACCOUNT_DISABLED,

    // 404
    RESOURCE_NOT_FOUND,

    // 409
    RESOURCE_ALREADY_EXISTS,
    DATA_CONFLICT,
    INVALID_STATE,
    OPERATION_BLOCKED,

    // 422
    VALIDATION_FAILED,

    // 500
    INTERNAL_ERROR
}
