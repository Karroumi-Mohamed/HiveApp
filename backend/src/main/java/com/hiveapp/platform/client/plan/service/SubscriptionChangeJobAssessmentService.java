package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolates each preview so one invalid Account cannot mark the population transaction rollback-only. */
@Service
@RequiredArgsConstructor
public class SubscriptionChangeJobAssessmentService {
  private final SubscriptionService subscriptions;

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  public SubscriptionChangePreviewResponse preview(
      UUID accountId, UUID actorUserId, SubscriptionChangeRequest selection) {
    return subscriptions.previewChangeAsOperator(accountId, actorUserId, selection);
  }
}
