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
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).isEqualTo("Credential link is expired");
    }

    @Test
    void permissionDeniedDoesNotLeakPolicyDetails() {
        var response = handler.handlePermissionDenied(new PermissionDeniedException("platform.company.delete"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(403);
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
        assertThat(response.getBody().error()).isEqualTo("Quota Exceeded");
        assertThat(response.getBody().details())
                .containsExactly(
                        "resource: platform.company",
                        "limit: 1",
                        "current: 1",
                        "unit: companies"
                );
    }
}
