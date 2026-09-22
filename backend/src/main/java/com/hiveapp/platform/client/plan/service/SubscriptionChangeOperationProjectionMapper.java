package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.admin.dto.AdminSubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.dto.ClientSubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared transaction-scoped projection for subscription operations. */
@Component
@RequiredArgsConstructor
public class SubscriptionChangeOperationProjectionMapper {
  private final SubscriptionCheckoutService checkoutService;

  public SubscriptionChangeOperationDto internal(SubscriptionChangeOperation operation) {
    return new SubscriptionChangeOperationDto(
        operation.getId(),
        operation.getCreatedAt(),
        operation.getUpdatedAt(),
        operation.getTiming(),
        operation.getStatus(),
        operation.getEffectiveAt(),
        operation.getBeforeSnapshot().planCode(),
        operation.getTargetPlan().getCode(),
        operation.getAttentionReason(),
        checkoutService.toDto(operation.getCheckout()),
        operation.getCommercialPolicyEvaluation());
  }

  public ClientSubscriptionChangeOperationDto client(SubscriptionChangeOperation operation) {
    return ClientSubscriptionChangeOperationDto.from(internal(operation));
  }

  public AdminSubscriptionChangeOperationDto admin(SubscriptionChangeOperation operation) {
    return new AdminSubscriptionChangeOperationDto(
        operation.getId(),
        operation.getCreatedAt(),
        operation.getUpdatedAt(),
        operation.getTiming(),
        operation.getStatus(),
        operation.getEffectiveAt(),
        operation.getBeforeSnapshot().planCode(),
        operation.getTargetPlan().getCode(),
        operation.getAttentionReason(),
        checkoutService.toDto(operation.getCheckout()),
        operation.getRequestOrigin(),
        operation.getRequestedByUserId(),
        operation.getRequestReason(),
        operation.getCancellationOrigin(),
        operation.getCancelledByUserId(),
        operation.getCancellationReason(),
        operation.getCancelledAt(),
        operation.getCommercialPolicyEvaluation());
  }
}
