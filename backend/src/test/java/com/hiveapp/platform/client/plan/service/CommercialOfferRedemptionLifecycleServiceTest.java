package com.hiveapp.platform.client.plan.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferRedemptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferCapacity;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.repository.CommercialOfferCapacityRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialOfferRedemptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.shared.exception.OfferRedemptionBlockedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class CommercialOfferRedemptionLifecycleServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

  @Mock private CommercialOfferRedemptionRepository redemptions;
  @Mock private CommercialOfferCapacityRepository capacities;
  @Mock private SubscriptionChangeOperationRepository operations;
  @Mock private AccountRepository accounts;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private CommercialOfferRedemption redemption;
  @Mock private CommercialOfferCapacity capacity;
  @Mock private SubscriptionChangeOperation operation;
  @Mock private Account account;

  private CommercialOfferRedemptionLifecycleService service;
  private UUID redemptionId;
  private UUID lineageId;
  private UUID accountId;
  private UUID applicationClaimId;

  @BeforeEach
  void setUp() {
    service =
        new CommercialOfferRedemptionLifecycleService(
            redemptions,
            operations,
            accounts,
            new CommercialOfferRedemptionTransitionService(
                capacities, Clock.fixed(NOW, ZoneOffset.UTC)),
            transactionManager,
            Clock.fixed(NOW, ZoneOffset.UTC));
    redemptionId = UUID.randomUUID();
    lineageId = UUID.randomUUID();
    accountId = UUID.randomUUID();
    applicationClaimId = UUID.randomUUID();
  }

  @Test
  void appliedSubscriptionOperationConsumesReservedCapacity() {
    stubReservedRedemption();
    UUID operationId = UUID.randomUUID();
    when(redemption.getSubscriptionOperationId()).thenReturn(operationId);
    when(operations.findByIdAndAccountId(operationId, accountId))
        .thenReturn(Optional.of(operation));
    stubOperation(operationId, SubscriptionChangeStatus.APPLIED);
    when(redemption.getOfferLineageId()).thenReturn(lineageId);
    when(capacities.lockByLineage(lineageId)).thenReturn(Optional.of(capacity));

    service.reconcileOne(redemptionId);

    var lockOrder = inOrder(accounts, redemptions, capacities);
    lockOrder.verify(accounts).findByIdForSubscriptionUpdate(accountId);
    lockOrder.verify(redemptions).lockById(redemptionId);
    lockOrder.verify(capacities).lockByLineage(lineageId);
    verify(redemption).linkOperation(operationId, applicationClaimId);
    verify(capacity).apply();
    verify(redemption).apply(NOW);
  }

  @Test
  void cancelledSubscriptionOperationReleasesReservedCapacity() {
    stubReservedRedemption();
    UUID operationId = UUID.randomUUID();
    when(redemption.getSubscriptionOperationId()).thenReturn(operationId);
    when(operations.findByIdAndAccountId(operationId, accountId))
        .thenReturn(Optional.of(operation));
    stubOperation(operationId, SubscriptionChangeStatus.CANCELLED);
    when(redemption.getOfferLineageId()).thenReturn(lineageId);
    when(capacities.lockByLineage(lineageId)).thenReturn(Optional.of(capacity));

    service.reconcileOne(redemptionId);

    verify(redemption).linkOperation(operationId, applicationClaimId);
    verify(capacity).release();
    verify(redemption).cancel("SUBSCRIPTION_OPERATION_CANCELLED", NOW);
  }

  @Test
  void operationNeedingAttentionFailsTheRedemptionAndReleasesCapacity() {
    stubReservedRedemption();
    UUID operationId = UUID.randomUUID();
    when(redemption.getSubscriptionOperationId()).thenReturn(operationId);
    when(operations.findByIdAndAccountId(operationId, accountId))
        .thenReturn(Optional.of(operation));
    stubOperation(operationId, SubscriptionChangeStatus.NEEDS_ATTENTION);
    when(redemption.getOfferLineageId()).thenReturn(lineageId);
    when(capacities.lockByLineage(lineageId)).thenReturn(Optional.of(capacity));

    service.reconcileOne(redemptionId);

    verify(redemption).linkOperation(operationId, applicationClaimId);
    verify(capacity).release();
    verify(redemption).fail("SUBSCRIPTION_OPERATION_NEEDS_ATTENTION", NOW);
  }

  @Test
  void abandonedReservationIsReleasedAfterTheRecoveryDeadline() {
    stubReservedRedemption();
    when(redemption.getSubscriptionOperationId()).thenReturn(null);
    when(redemption.applicationLeaseExpired(NOW.minusSeconds(300))).thenReturn(true);
    when(operations.findByOfferRedemptionIdAndAccountId(redemptionId, accountId))
        .thenReturn(Optional.empty());
    when(redemption.getOfferLineageId()).thenReturn(lineageId);
    when(capacities.lockByLineage(lineageId)).thenReturn(Optional.of(capacity));

    service.reconcileOne(redemptionId);

    verify(capacity).release();
    verify(redemption).fail("RESERVATION_ABANDONED", NOW);
  }

  @Test
  void recentReservationWithoutAnOperationRemainsRecoverable() {
    stubReservedRedemption();
    when(redemption.getSubscriptionOperationId()).thenReturn(null);
    when(redemption.applicationLeaseExpired(NOW.minusSeconds(300))).thenReturn(false);
    when(operations.findByOfferRedemptionIdAndAccountId(redemptionId, accountId))
        .thenReturn(Optional.empty());

    service.reconcileOne(redemptionId);

    verify(capacities, never()).lockByLineage(lineageId);
    verify(redemption, never()).fail("RESERVATION_ABANDONED", NOW);
  }

  @Test
  void schedulerSelectsOnlyTerminalOrAbandonedReservations() {
    var terminal =
        List.of(
            SubscriptionChangeStatus.APPLIED,
            SubscriptionChangeStatus.CANCELLED,
            SubscriptionChangeStatus.NEEDS_ATTENTION);
    when(redemptions.findActionableIds(NOW.minusSeconds(300), terminal, PageRequest.of(0, 100)))
        .thenReturn(Page.empty());

    service.reconcile();

    verify(redemptions)
        .findActionableIds(NOW.minusSeconds(300), terminal, PageRequest.of(0, 100));
  }

  @Test
  void staleWorkerClaimCannotLinkOrConsumeCapacity() {
    UUID operationId = UUID.randomUUID();
    UUID staleClaimId = UUID.randomUUID();
    when(redemption.getId()).thenReturn(redemptionId);
    when(redemption.getAccount()).thenReturn(account);
    when(account.getId()).thenReturn(accountId);
    when(operation.getOfferRedemptionId()).thenReturn(redemptionId);
    when(operation.getOfferApplicationClaimId()).thenReturn(staleClaimId);
    when(operation.getAccount()).thenReturn(account);
    when(redemption.hasApplicationClaim(staleClaimId)).thenReturn(false);

    var transitions =
        new CommercialOfferRedemptionTransitionService(
            capacities, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> transitions.applyOperation(redemption, operation))
        .isInstanceOf(OfferRedemptionBlockedException.class);
    verify(redemption, never()).linkOperation(operationId, staleClaimId);
    verify(capacities, never()).lockByLineage(lineageId);
  }

  private void stubReservedRedemption() {
    when(redemptions.findAccountIdById(redemptionId)).thenReturn(Optional.of(accountId));
    when(accounts.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
    when(redemptions.lockById(redemptionId)).thenReturn(Optional.of(redemption));
    lenient().when(redemption.getAccount()).thenReturn(account);
    lenient().when(account.getId()).thenReturn(accountId);
    when(redemption.getStatus()).thenReturn(CommercialOfferRedemptionStatus.RESERVED);
  }

  private void stubOperation(UUID operationId, SubscriptionChangeStatus status) {
    when(redemption.getId()).thenReturn(redemptionId);
    when(operation.getId()).thenReturn(operationId);
    when(operation.getStatus()).thenReturn(status);
    when(operation.getOfferRedemptionId()).thenReturn(redemptionId);
    when(operation.getOfferApplicationClaimId()).thenReturn(applicationClaimId);
    when(operation.getAccount()).thenReturn(account);
    when(redemption.hasApplicationClaim(applicationClaimId)).thenReturn(true);
  }
}
