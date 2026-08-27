package com.hiveapp.platform.client.plan.api;

import com.hiveapp.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CommercialSegmentAdminControllerTest {

    @Test
    void concurrentLockFailureUsesTheStableSegmentConflictContract() {
        var controller = new CommercialSegmentAdminController(null);

        var response = controller.handleLockConflict(
                new CannotAcquireLockException("concurrent Segment mutation"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.STALE_RESOURCE_VERSION);
    }
}
