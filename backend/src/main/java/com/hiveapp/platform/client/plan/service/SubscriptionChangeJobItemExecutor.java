package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeJobItem;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeJobItemRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Executes one Account in one transaction so a failure never rolls back another Account. */
@Service
@RequiredArgsConstructor
public class SubscriptionChangeJobItemExecutor {
  private final SubscriptionChangeJobItemRepository items;
  private final SubscriptionService subscriptions;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void execute(UUID itemId) {
    SubscriptionChangeJobItem item = items.findByIdForUpdate(itemId).orElse(null);
    if (item == null
        || item.getStatus() != SubscriptionChangeJobItemStatus.READY
        || item.getJob().getStatus() != SubscriptionChangeJobStatus.RUNNING) {
      return;
    }
    var preview =
        subscriptions.previewChangeAsOperator(
            item.getAccount().getId(),
            item.getJob().getRequestedByUserId(),
            item.getJob().getSelection());
    if (!preview.conflicts().isEmpty()) {
      item.fail(SubscriptionChangeJobItemStatus.CONFLICT, "PREVIEW_CONFLICT", clock.instant());
      items.saveAndFlush(item);
      return;
    }
    SubscriptionChangeApplyResponse response =
        subscriptions.applyChangeAsOperator(
            item.getAccount().getId(),
            item.getJob().getRequestedByUserId(),
            new SubscriptionChangeApplyRequest(item.getJob().getSelection(), preview.previewToken()),
            item.getJob().getReason());
    SubscriptionChangeJobItemStatus outcome = outcome(response.operation().status());
    if (outcome == SubscriptionChangeJobItemStatus.CONFLICT
        || outcome == SubscriptionChangeJobItemStatus.FAILED) {
      item.fail(
          outcome,
          "SUBSCRIPTION_OPERATION_" + response.operation().status().name(),
          clock.instant());
    } else {
      item.succeed(outcome, response.operation().id(), clock.instant());
    }
    items.saveAndFlush(item);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(UUID itemId, RuntimeException failure) {
    SubscriptionChangeJobItem item = items.findByIdForUpdate(itemId).orElse(null);
    if (item == null || item.getStatus() != SubscriptionChangeJobItemStatus.READY) return;
    item.fail(classifyStatus(failure), classifyCode(failure), clock.instant());
    items.saveAndFlush(item);
  }

  private SubscriptionChangeJobItemStatus outcome(SubscriptionChangeStatus status) {
    return switch (status) {
      case APPLIED -> SubscriptionChangeJobItemStatus.APPLIED;
      case PENDING -> SubscriptionChangeJobItemStatus.PENDING_RENEWAL;
      case AWAITING_CONFIRMATION -> SubscriptionChangeJobItemStatus.AWAITING_PAYMENT;
      case NEEDS_ATTENTION -> SubscriptionChangeJobItemStatus.CONFLICT;
      case CANCELLED -> SubscriptionChangeJobItemStatus.FAILED;
    };
  }

  private SubscriptionChangeJobItemStatus classifyStatus(RuntimeException failure) {
    if (failure instanceof StaleResourceVersionException
        || failure instanceof InvalidRequestException
        || failure instanceof InvalidStateException
        || failure instanceof ResourceNotFoundException
        || failure instanceof DataIntegrityViolationException
        || failure instanceof ObjectOptimisticLockingFailureException
        || failure instanceof CannotAcquireLockException) {
      return SubscriptionChangeJobItemStatus.CONFLICT;
    }
    return SubscriptionChangeJobItemStatus.FAILED;
  }

  private String classifyCode(RuntimeException failure) {
    if (failure instanceof StaleResourceVersionException
        || failure instanceof ObjectOptimisticLockingFailureException
        || failure instanceof CannotAcquireLockException) {
      return "STALE_RESOURCE_VERSION";
    }
    if (failure instanceof ResourceNotFoundException) return "SUBSCRIPTION_UNAVAILABLE";
    if (failure instanceof InvalidRequestException) return "SELECTION_INVALID";
    if (failure instanceof InvalidStateException) return "SUBSCRIPTION_STATE_CONFLICT";
    if (failure instanceof DataIntegrityViolationException) return "DATA_CONFLICT";
    return "EXECUTION_FAILED";
  }
}
