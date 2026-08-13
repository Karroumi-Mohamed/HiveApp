package com.hiveapp.shared.exception;

import dev.karroumi.permissionizer.PermissionDeniedException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Maps an exception to its stable {@link ErrorCode}.
 *
 * <p>{@code GlobalExceptionHandler} performs the same mapping on its way out of the request, but
 * only for exceptions that actually escape. A bulk operation catches its per-item failures and
 * reports them in the body instead, so it needs the mapping without unwinding — and both must
 * agree, or the same rejection would carry one code alone and a different one inside a batch.
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    public static ErrorCode of(Throwable failure) {
        return switch (failure) {
            case ResourceNotFoundException ignored -> ErrorCode.RESOURCE_NOT_FOUND;
            case DuplicateResourceException ignored -> ErrorCode.RESOURCE_ALREADY_EXISTS;
            case DataIntegrityViolationException ignored -> ErrorCode.DATA_CONFLICT;
            case InvalidPermissionGrantException ignored -> ErrorCode.INVALID_PERMISSION_GRANT;
            case PermissionDeniedException ignored -> ErrorCode.PERMISSION_DENIED;
            case ForbiddenException ignored -> ErrorCode.FORBIDDEN;
            case UnauthorizedException ignored -> ErrorCode.UNAUTHENTICATED;
            case OperationBlockedException ignored -> ErrorCode.OPERATION_BLOCKED;
            case InvalidStateException ignored -> ErrorCode.INVALID_STATE;
            case InvalidRequestException ignored -> ErrorCode.INVALID_REQUEST;
            case BusinessException ignored -> ErrorCode.BUSINESS_RULE_VIOLATED;
            case IllegalArgumentException ignored -> ErrorCode.INVALID_ARGUMENT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
