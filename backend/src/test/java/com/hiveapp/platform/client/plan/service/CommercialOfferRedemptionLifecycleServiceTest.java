package com.hiveapp.platform.client.plan.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferRedemptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferCapacity;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.repository.CommercialOfferCapacityRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialOfferRedemptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import java.time.Clock;
import java.time.Duration;
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
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private CommercialOfferRedemption redemption;
  @Mock private CommercialOfferCapacity capacity;
  @Mock private SubscriptionChangeOperation operation;

  private CommercialOfferRedemptionLifecycleService service;
  private UUID redemptionId;
  private UUID lineageId;

  @BeforeEach
  void setUp() {
    service =
        new CommercialOfferRedemptionLifecycleService(
            redemptions,
            operations,
            new CommercialOfferRedemptionTransitionService(
                capacities, Clock.fixed(NOW, ZoneOffset.UTC)),
            transactionManager,
            Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(service, "reservationTimeout", Duration.ofMinutes(15));
    redemptionId = UUID.randomUUID();
    lineageId = UUID.randomUUID();
  }

  @Test
  void appliedSubscriptionOperationConsumesReservedCapacity() {
    stubReservedRedemption();
    UUID operationId = UUID.randomUUID();
    when(redemption.getSubscriptionOperationId()).thenReturn(operationId);
    when(operations.findById(operationId)).thenReturn(Optional.of(operation));
    when(operation.getId()).thenReturn(operationId);
    when(operation.getStatus()).thenReturn(SubscriptionChangeStatus.APPLIED);
    when(redemption.getOfferLineageId()).thenReturn(lineageId);
    when(capacities.lockByLineage(lineageId)).thenReturn(Optional.of(capacity));

    service.reconcileOne(redemptionId);

    verify(redemption).linkOperation(operationId);
    verify(capacity).apply();
    verify(redemption).apply(NOW);
  }

  @Test
  void cancelledSubscriptionOperationReleasesReservedCapacity() {
    stubReservedRedemption();
    UUID operationId = UUID.randomUUID();
    when(redemption.getSubscriptionOperationId()).thenReturn(operationId);
    when(operations.findById(operationId)).thenReturn(Optional.of(operation));
    when(operation.getId()).thenReturn(operationId);
    when(operation.getStatus()).thenReturn(SubscriptionChangeStatus.CANCELLED);
    when(redemption.getOfferLineageId()).thenReturn(lineageId);
    when(capacities.lockByLineage(lineageId)).thenReturn(Optional.of(capacity));

    service.reconcileOne(redemptionId);

    verify(redemption).linkOperation(operationId);
    verify(capacity).release();
    verify(redemption).cancel("SUBSCRIPTION_OPERATION_CANCELLED", NOW);
  }

  @Test
  void operationNeedingAttentionFailsTheRedemptionAndReleasesCapacity() {
    stubReservedRedemption();
    UUID operationId = UUID.randomUUID();
    when(redemption.getSubscriptionOperationId()).thenReturn(operationId);
    when(operations.findById(operationId)).thenReturn(Optional.of(operation));
    when(operation.getId()).thenReturn(operationId);
    when(operation.getStatus()).thenReturn(SubscriptionChangeStatus.NEEDS_ATTENTION);
    when(redemption.getOfferLineageId()).thenReturn(lineageId);
    when(capacities.lockByLineage(lineageId)).thenReturn(Optional.of(capacity));

    service.reconcileOne(redemptionId);

    verify(redemption).linkOperation(operationId);
    verify(capacity).release();
    verify(redemption).fail("SUBSCRIPTION_OPERATION_NEEDS_ATTENTION", NOW);
  }

  @Test
  void abandonedReservationIsReleasedAfterTheRecoveryDeadline() {
    stubReservedRedemption();
    when(redemption.getSubscriptionOperationId()).thenReturn(null);
    when(redemption.getReservedAt()).thenReturn(NOW.minus(Duration.ofMinutes(15)));
    when(operations.findByOfferRedemptionId(redemptionId)).thenReturn(Optional.empty());
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
    when(redemption.getReservedAt()).thenReturn(NOW.minus(Duration.ofMinutes(14)));
    when(operations.findByOfferRedemptionId(redemptionId)).thenReturn(Optional.empty());

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
    when(redemptions.findActionableIds(
            NOW.minus(Duration.ofMinutes(15)), terminal, PageRequest.of(0, 100)))
        .thenReturn(Page.empty());

    service.reconcile();

    verify(redemptions)
        .findActionableIds(NOW.minus(Duration.ofMinutes(15)), terminal, PageRequest.of(0, 100));
  }

  private void stubReservedRedemption() {
    when(redemptions.lockById(redemptionId)).thenReturn(Optional.of(redemption));
    when(redemption.getStatus()).thenReturn(CommercialOfferRedemptionStatus.RESERVED);
  }
}
