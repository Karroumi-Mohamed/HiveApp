package com.hiveapp.shared.exception;

import com.hiveapp.shared.quota.QuotaExceededException;
import dev.karroumi.permissionizer.PermissionDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Every response carries a stable {@link ErrorCode}. HTTP status alone is ambiguous — 409 covers a
 * duplicate resource, a database conflict, an invalid state transition and a blocked operation —
 * so the code is what a client branches on.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, ErrorCode.RESOURCE_NOT_FOUND, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.RESOURCE_ALREADY_EXISTS, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(PriceEntryOverlapException.class)
    public ResponseEntity<ApiError> handlePriceEntryOverlap(PriceEntryOverlapException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.PRICE_ENTRY_OVERLAP, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(DraftSuccessorExistsException.class)
    public ResponseEntity<ApiError> handleDraftSuccessorExists(DraftSuccessorExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.DRAFT_SUCCESSOR_EXISTS, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(StaleResourceVersionException.class)
    public ResponseEntity<ApiError> handleStaleResourceVersion(StaleResourceVersionException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.STALE_RESOURCE_VERSION, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(StaleActivationPreviewException.class)
    public ResponseEntity<ApiError> handleStaleActivationPreview(StaleActivationPreviewException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.STALE_ACTIVATION_PREVIEW,
                        "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.STALE_RESOURCE_VERSION, "Conflict",
                        "The resource changed since it was read. Reload it and retry."));
    }

    @ExceptionHandler(com.hiveapp.platform.admin.service.AdminRoleNameConflictException.class)
    public ResponseEntity<ApiError> handleAdminRoleNameConflict(
            com.hiveapp.platform.admin.service.AdminRoleNameConflictException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.ROLE_NAME_CONFLICT, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(com.hiveapp.platform.admin.service.StaleAdminRoleImpactException.class)
    public ResponseEntity<ApiError> handleStaleAdminRoleImpact(
            com.hiveapp.platform.admin.service.StaleAdminRoleImpactException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.STALE_IMPACT_PREVIEW, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.DATA_CONFLICT, "Conflict",
                        "The requested change conflicts with existing data."));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, ErrorCode.BUSINESS_RULE_VIOLATED, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, ErrorCode.INVALID_ARGUMENT, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, ErrorCode.INVALID_REQUEST, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, ErrorCode.UNAUTHENTICATED, "Unauthorized", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, ErrorCode.FORBIDDEN, "Forbidden", ex.getMessage()));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ApiError> handlePermissionDenied(PermissionDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, ErrorCode.PERMISSION_DENIED, "Forbidden",
                        "You do not have permission to access this resource"));
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(InvalidStateException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.INVALID_STATE, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(OperationBlockedException.class)
    public ResponseEntity<ApiError> handleOperationBlocked(OperationBlockedException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.OPERATION_BLOCKED, "Conflict",
                        ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(InvalidPermissionGrantException.class)
    public ResponseEntity<ApiError> handleInvalidGrant(InvalidPermissionGrantException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, ErrorCode.INVALID_PERMISSION_GRANT, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ApiError> handleQuotaExceeded(QuotaExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiError.of(402, ErrorCode.QUOTA_EXCEEDED, "Quota Exceeded", ex.getMessage(),
                        List.of(
                                "resource: " + ex.getResource(),
                                "limit: " + ex.getLimit(),
                                "current: " + ex.getCurrent(),
                                "unit: " + ex.getUnit()
                        )));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, ErrorCode.INVALID_CREDENTIALS, "Unauthorized",
                        "Invalid email or password"));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, ErrorCode.ACCOUNT_DISABLED, "Forbidden", "Account is disabled"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, ErrorCode.VALIDATION_FAILED, "Validation Failed",
                        "Request validation failed", details));
    }

    /**
     * A URL that matches no route. Without this it reaches {@link #handleGeneric} and every
     * mistyped path answers 500 — which reads as "the server broke" when the truth is "no such
     * thing here", and buries genuine faults in the same log line.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, ErrorCode.RESOURCE_NOT_FOUND, "Not Found",
                        "No endpoint matches " + ex.getResourcePath()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of(405, ErrorCode.METHOD_NOT_ALLOWED, "Method Not Allowed",
                        ex.getMethod() + " is not supported for this endpoint"));
    }

    /**
     * A path or query value that cannot be converted — most often a malformed UUID. The request
     * is bad, not the server, and the message deliberately names only the parameter: echoing the
     * rejected value back would reflect caller-supplied text into the response.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, ErrorCode.INVALID_ARGUMENT, "Bad Request",
                        "Parameter '" + ex.getName() + "' has an invalid format"));
    }

    /**
     * A required header the caller omitted — same class of gap as the two above: a client mistake
     * that reached the catch-all and answered 500.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, ErrorCode.INVALID_REQUEST, "Bad Request",
                        "Required header '" + ex.getHeaderName() + "' is missing"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, ErrorCode.INVALID_REQUEST, "Bad Request",
                        "Required parameter '" + ex.getParameterName() + "' is missing"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, ErrorCode.INTERNAL_ERROR, "Internal Server Error",
                        "An unexpected error occurred"));
    }
}
