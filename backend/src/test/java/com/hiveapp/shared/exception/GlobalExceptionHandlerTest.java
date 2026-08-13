package com.hiveapp.shared.exception;

import com.hiveapp.shared.quota.QuotaExceededException;
import dev.karroumi.permissionizer.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidRequestReturnsStableBadRequestBody() {
        var response = handler.handleInvalidRequest(new InvalidRequestException("Invalid feature code"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("Invalid feature code");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void invalidStateReturnsStableConflictBody() {
        var response = handler.handleInvalidState(new InvalidStateException("Credential link is expired"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).isEqualTo("Credential link is expired");
    }

    @Test
    void permissionDeniedDoesNotLeakPolicyDetails() {
        var response = handler.handlePermissionDenied(new PermissionDeniedException("platform.company.delete"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(403);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.PERMISSION_DENIED);
        assertThat(response.getBody().error()).isEqualTo("Forbidden");
        assertThat(response.getBody().message()).isEqualTo("You do not have permission to access this resource");
    }

    @Test
    void quotaExceededReturnsPaymentRequiredWithDetails() {
        var response = handler.handleQuotaExceeded(
                new QuotaExceededException("platform.company", 1L, 1L, "companies"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(402);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.QUOTA_EXCEEDED);
        assertThat(response.getBody().error()).isEqualTo("Quota Exceeded");
        assertThat(response.getBody().details())
                .containsExactly(
                        "resource: platform.company",
                        "limit: 1",
                        "current: 1",
                        "unit: companies"
                );
    }

    /**
     * The point of the code is that one HTTP status distinguishes several situations. If these
     * ever collapse to the same code, clients lose the ability to tell them apart.
     */
    @Test
    void conflictStatusIsSplitByCodeSoClientsCanTellCasesApart() {
        var duplicate = handler.handleDuplicate(
                new DuplicateResourceException("Collaboration", "tuple", "x"));
        var invalidState = handler.handleInvalidState(new InvalidStateException("Already pending"));
        var blocked = handler.handleOperationBlocked(
                new OperationBlockedException("Blocked", java.util.List.of("reason")));

        assertThat(duplicate.getBody().status()).isEqualTo(409);
        assertThat(invalidState.getBody().status()).isEqualTo(409);
        assertThat(blocked.getBody().status()).isEqualTo(409);

        assertThat(java.util.Set.of(
                duplicate.getBody().code(),
                invalidState.getBody().code(),
                blocked.getBody().code()))
                .containsExactlyInAnyOrder(
                        ErrorCode.RESOURCE_ALREADY_EXISTS,
                        ErrorCode.INVALID_STATE,
                        ErrorCode.OPERATION_BLOCKED);
    }

    /**
     * These two used to fall through to the catch-all and answer 500. A mistyped URL and a
     * malformed id are both caller mistakes; reporting them as server faults hides real ones.
     */
    @Test
    void anUnmatchedUrlIsNotFoundRatherThanAServerFault() {
        var response = handler.handleNoResource(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "api/admin/roles/x/operators"));

        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void aMalformedPathValueIsABadRequestAndDoesNotEchoTheValueBack() {
        var response = handler.handleTypeMismatch(
                new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                        "<script>not-a-uuid</script>", java.util.UUID.class, "id", null, null));

        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThat(response.getBody().message()).contains("id").doesNotContain("script");
    }
}
