package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.RepricingModels.*;
import com.hiveapp.shared.audit.AuditedMutation;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class SubscriptionRepricingExecutor {
  private final SubscriptionRepricingItemRepository items;
  private final AccountRepository accounts;
  private final SubscriptionRepository subscriptions;
  private final SubscriptionRepricingService repricing;
  private final SubscriptionBillingRenewalService billing;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @AuditedMutation(
      action = "platform.subscriptions.repricing.execute",
      resourceType = "SUBSCRIPTION_REPRICING_ITEM")
  public void execute(UUID id, Instant cutoff) {
    UUID accountId = items.accountId(id).orElse(null);
    if (accountId == null) return;
    accounts.findByIdForSubscriptionUpdate(accountId);
    var item = items.lock(id).orElseThrow();
    if (item.getStatus() != State.PENDING || item.getEffectiveAt().isAfter(cutoff)) return;
    var current = subscriptions.findCurrentByAccountId(accountId).orElse(null);
    String blocker =
        current == null
            ? "NO_CURRENT_SUBSCRIPTION"
            : !current.termsIdentity().equals(item.getTermsIdentity())
                    || !repricing.termsFingerprint(current).equals(item.getTermsFingerprint())
                ? "SUBSCRIPTION_CHANGED"
                : repricing.blocker(current, item.getJob(), item.getEffectiveAt(), false);
    if (blocker == null && !current.getCurrentPeriodEnd().equals(item.getEffectiveAt()))
      blocker = "RENEWAL_DATE_CHANGED";
    if (blocker == null
        && item.getJob().getTargetPrice().getVersion() != item.getJob().getTargetPriceVersion())
      blocker = "TARGET_TARIFF_CHANGED";
    if (blocker != null) {
      item.setStatus(State.CONFLICT);
      item.setBlocker(blocker);
      items.saveAndFlush(item);
      return;
    }
    item.setOperation(billing.prepareRepricingCharge(current, item));
    item.setStatus(
        State.AWAITING_PAYMENT); // Zero-price operations are applied by the next renewal-processing
    // step.
    items.saveAndFlush(item);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(UUID id) {
    UUID accountId = items.accountId(id).orElse(null);
    if (accountId == null) return;
    accounts.findByIdForSubscriptionUpdate(accountId);
    var item = items.lock(id).orElseThrow();
    if (item.getStatus() == State.PENDING) {
      item.setStatus(State.CONFLICT);
      item.setBlocker("EXECUTION_FAILED");
      items.saveAndFlush(item);
    }
  }
}
