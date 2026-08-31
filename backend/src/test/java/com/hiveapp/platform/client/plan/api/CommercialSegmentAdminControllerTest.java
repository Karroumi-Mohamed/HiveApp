package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.service.CommercialSegmentAdminService;
import com.hiveapp.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommercialSegmentAdminControllerTest {

    @Test
    void accountChoicesUseStableNameAndIdOrdering() {
        var service = mock(CommercialSegmentAdminService.class);
        when(service.chooseAccounts(isNull(), isNull(), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(2)));
        var controller = new CommercialSegmentAdminController(service);

        controller.chooseAccounts(null, null, 2, 25);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).chooseAccounts(isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        assertThat(pageable.getValue().getSort().getOrderFor("name"))
                .satisfies(order -> {
                    assertThat(order).isNotNull();
                    assertThat(order.isAscending()).isTrue();
                });
        assertThat(pageable.getValue().getSort().getOrderFor("id"))
                .satisfies(order -> {
                    assertThat(order).isNotNull();
                    assertThat(order.isAscending()).isTrue();
                });
    }

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
