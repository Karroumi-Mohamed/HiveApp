package com.hiveapp.shared.exception;

import dev.karroumi.permissionizer.PermissionDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

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
            case PriceEntryOverlapException ignored -> ErrorCode.PRICE_ENTRY_OVERLAP;
            case DraftSuccessorExistsException ignored -> ErrorCode.DRAFT_SUCCESSOR_EXISTS;
            case StaleActivationPreviewException ignored -> ErrorCode.STALE_ACTIVATION_PREVIEW;
            case StaleSchedulePreviewException ignored -> ErrorCode.STALE_SCHEDULE_PREVIEW;
            case StaleOfferPreviewException ignored -> ErrorCode.STALE_OFFER_PREVIEW;
            case OfferCodeConflictException ignored -> ErrorCode.OFFER_CODE_CONFLICT;
            case OfferNotAvailableException ignored -> ErrorCode.OFFER_NOT_AVAILABLE;
            case OfferRedemptionBlockedException ignored -> ErrorCode.OFFER_REDEMPTION_BLOCKED;
            case IdempotencyConflictException ignored -> ErrorCode.IDEMPOTENCY_CONFLICT;
            case StaleResourceVersionException ignored -> ErrorCode.STALE_RESOURCE_VERSION;
            case ObjectOptimisticLockingFailureException ignored -> ErrorCode.STALE_RESOURCE_VERSION;
            case DataIntegrityViolationException ignored -> ErrorCode.DATA_CONFLICT;
            case InvalidPermissionGrantException ignored -> ErrorCode.INVALID_PERMISSION_GRANT;
            case PermissionDeniedException ignored -> ErrorCode.PERMISSION_DENIED;
            case AccessDeniedException ignored -> ErrorCode.PERMISSION_DENIED;
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
