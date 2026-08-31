package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferRedemptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.repository.CommercialOfferRedemptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Reconciles durable Offer capacity with asynchronous checkout and renewal operations. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommercialOfferRedemptionLifecycleService {
  private final CommercialOfferRedemptionRepository redemptions;
  private final SubscriptionChangeOperationRepository operations;
  private final AccountRepository accounts;
  private final CommercialOfferRedemptionTransitionService transitions;
  private final PlatformTransactionManager transactionManager;
  private final Clock clock;

  @Value("${hiveapp.offers.application-abandonment-grace:PT5M}")
  private Duration applicationAbandonmentGrace = Duration.ofMinutes(5);

  @Scheduled(fixedDelayString = "${hiveapp.offers.redemption-delay-ms:30000}")
  public void reconcile() {
    var ids =
        redemptions
            .findActionableIds(
                clock.instant().minus(applicationAbandonmentGrace),
                java.util.List.of(
                    SubscriptionChangeStatus.APPLIED,
                    SubscriptionChangeStatus.CANCELLED,
                    SubscriptionChangeStatus.NEEDS_ATTENTION),
                PageRequest.of(0, 100))
            .getContent();
    for (UUID id : ids) {
      try {
        inNewTransaction(() -> reconcileOne(id));
      } catch (RuntimeException failure) {
        log.error("Failed to reconcile commercial Offer redemption {}", id, failure);
      }
    }
  }

  void reconcileOne(UUID id) {
    UUID accountId = redemptions.findAccountIdById(id).orElse(null);
    if (accountId == null || accounts.findByIdForSubscriptionUpdate(accountId).isEmpty()) return;
    var redemption = redemptions.lockById(id).orElse(null);
    if (redemption == null || redemption.getStatus() != CommercialOfferRedemptionStatus.RESERVED) {
      return;
    }
    var operation =
        redemption.getSubscriptionOperationId() == null
            ? operations.findByOfferRedemptionIdAndAccountId(id, accountId).orElse(null)
            : operations
                .findByIdAndAccountId(redemption.getSubscriptionOperationId(), accountId)
                .orElse(null);
    if (operation == null) {
      if (redemption.applicationLeaseExpired(clock.instant().minus(applicationAbandonmentGrace))) {
        transitions.failReservation(redemption, "RESERVATION_ABANDONED");
        redemptions.saveAndFlush(redemption);
      }
      return;
    }
    transitions.applyOperation(redemption, operation);
    redemptions.saveAndFlush(redemption);
  }

  private void inNewTransaction(Runnable work) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.executeWithoutResult(status -> work.run());
  }
}
