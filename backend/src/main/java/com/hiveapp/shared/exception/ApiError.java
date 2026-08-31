package com.hiveapp.shared.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hiveapp.shared.observability.RequestCorrelation;

/**
 * Normalized error response.
 *
 * <p>{@code code} is the stable contract clients branch on. {@code error} and {@code message} are
 * human-readable and may change without notice.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    int status,
    ErrorCode code,
    String error,
    String message,
    Instant timestamp,
    String requestId,
    List<String> details
) {
    public static ApiError of(int status, ErrorCode code, String error, String message) {
        return new ApiError(status, code, error, message, Instant.now(),
                RequestCorrelation.currentId(), null);
    }

    public static ApiError of(int status, ErrorCode code, String error, String message, List<String> details) {
        return new ApiError(status, code, error, message, Instant.now(),
                RequestCorrelation.currentId(), details);
    }
}
