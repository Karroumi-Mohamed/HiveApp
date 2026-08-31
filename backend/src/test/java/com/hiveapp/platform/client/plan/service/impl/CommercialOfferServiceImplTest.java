package com.hiveapp.platform.client.plan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferEligibilityBlocker;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.dto.CommercialOfferRequests;
import com.hiveapp.platform.client.plan.service.*;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.OfferNotAvailableException;
import com.hiveapp.shared.exception.IdempotencyConflictException;
import com.hiveapp.shared.exception.OfferRedemptionBlockedException;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.LongFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class CommercialOfferServiceImplTest {
  @Mock private CommercialOfferRepository offers;
  @Mock private CommercialOfferRedemptionRepository redemptions;
  @Mock private CommercialOfferCapacityRepository capacities;
  @Mock private CommercialCampaignRepository campaigns;
  @Mock private CommercialCampaignAudienceSnapshotRepository audiences;
  @Mock private AccountRepository accounts;
  @Mock private SubscriptionRepository subscriptions;
  @Mock private SubscriptionChangeOperationRepository subscriptionOperations;
  @Mock private PlanRepository plans;
  @Mock private AddOnRepository addOns;
  @Mock private QuotaPackageRepository packages;
  @Mock private ProductPriceRepository prices;
  @Mock private SubscriptionService subscriptionsService;
  @Mock private CommercialCatalogVersionService catalogVersions;
  @Mock private RegistryCatalogVersionService registryVersions;
  @Mock private CommercialPreviewTokenService previewTokens;
  @Mock private Clock clock;
  @Mock private CommercialOfferCodeHasher codeHasher;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private CommercialOfferClientProjectionMapper projections;
  @Mock private SubscriptionChangeOperationProjectionMapper operationProjections;
  @Mock private CommercialOfferEligibilityService eligibility;
  @Mock private CommercialOfferRedemptionTransitionService transitions;
  @Mock private CommercialCatalogResolver catalogResolver;
  @Mock private CommercialPolicyEvaluator policyEvaluator;
  @Mock private CommercialOffer offer;
  @Mock private Account account;
  @Mock private CommercialOfferRedemption redemption;
  @Mock private SubscriptionChangeOperation operation;

  @InjectMocks private CommercialOfferServiceImpl service;

  @Test
  void expectedOperatorIneligibilityReturnsTypedAssessmentWithoutIssuingEvidence() {
    UUID offerId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    Instant now = Instant.parse("2030-01-01T00:00:00Z");
    when(offers.findDetailById(offerId)).thenReturn(java.util.Optional.of(offer));
    when(eligibility.blockers(offer, accountId, true, true))
        .thenReturn(List.of(CommercialOfferEligibilityBlocker.ACCOUNT_INACTIVE));
    when(clock.instant()).thenReturn(now);

    var assessment = service.assessForOperator(accountId, actor, offerId);

    assertThat(assessment.eligible()).isFalse();
    assertThat(assessment.blockers())
        .containsExactly(CommercialOfferEligibilityBlocker.ACCOUNT_INACTIVE);
    assertThat(assessment.preview()).isNull();
    assertThat(assessment.evaluatedAt()).isEqualTo(now);
    verifyNoInteractions(previewTokens, subscriptionsService);
  }

  @Test
  void operatorAssessmentMapsBothOfferSelectionFailuresToTypedUnavailableState() {
    UUID offerId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    Instant now = Instant.parse("2030-01-01T00:00:00Z");
    when(offers.findDetailById(offerId)).thenReturn(java.util.Optional.of(offer));
    when(eligibility.blockers(offer, accountId, true, true)).thenReturn(List.of());
    when(subscriptionOperations.findTopByAccountIdAndStatusIn(any(), any()))
        .thenReturn(java.util.Optional.empty());
    when(projections.exactPrices(
            org.mockito.ArgumentMatchers.<CommercialOffer>anyCollection()))
        .thenThrow(new OfferRedemptionBlockedException(), new OfferNotAvailableException());
    when(clock.instant()).thenReturn(now);

    var blocked = service.assessForOperator(accountId, actor, offerId);
    var unavailable = service.assessForOperator(accountId, actor, offerId);

    for (var assessment : List.of(blocked, unavailable)) {
      assertThat(assessment.eligible()).isFalse();
      assertThat(assessment.blockers())
          .containsExactly(CommercialOfferEligibilityBlocker.SELECTION_UNAVAILABLE);
      assertThat(assessment.preview()).isNull();
    }
  }

  @Test
  void operatorIdempotencyFingerprintIncludesNormalizedDurableReason() {
    UUID offerId = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    String normalized =
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service, "requiredOperatorReason", "  Customer requested upgrade  ");
    String first =
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "acceptanceRequestFingerprint",
            offerId,
            actor,
            true,
            "preview-token",
            normalized);
    String sameAfterTrimming =
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "acceptanceRequestFingerprint",
            offerId,
            actor,
            true,
            "preview-token",
            "Customer requested upgrade");
    String changedReason =
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "acceptanceRequestFingerprint",
            offerId,
            actor,
            true,
            "preview-token",
            "Compliance exception");

    assertThat(first).isEqualTo(sameAfterTrimming).isNotEqualTo(changedReason);
  }

  @Test
  void replayByAnotherActorConflictsBeforeOperationRecovery() {
    UUID offerId = UUID.randomUUID();
    UUID firstActor = UUID.randomUUID();
    UUID secondActor = UUID.randomUUID();
    String stored =
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "acceptanceRequestFingerprint",
            offerId,
            firstActor,
            true,
            "preview-token",
            "Customer requested upgrade");
    String replayed =
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,
            "acceptanceRequestFingerprint",
            offerId,
            secondActor,
            true,
            "preview-token",
            "Customer requested upgrade");
    when(redemption.getRequestFingerprint()).thenReturn(stored);
    Class<?> projectorType =
        java.util.Arrays.stream(CommercialOfferServiceImpl.class.getDeclaredClasses())
            .filter(type -> type.getSimpleName().equals("AcceptanceProjector"))
            .findFirst()
            .orElseThrow();
    Object projector =
        java.lang.reflect.Proxy.newProxyInstance(
            projectorType.getClassLoader(),
            new Class<?>[] {projectorType},
            (proxy, method, args) -> null);

    assertThatThrownBy(
            () ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                    service, "replay", redemption, replayed, projector))
        .isInstanceOf(IdempotencyConflictException.class);
    verifyNoInteractions(subscriptionOperations, transitions);
  }

  @Test
  void replayRecoveryRehydratesAndReconcilesTheLatestOperation() {
    UUID redemptionId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    when(redemption.getId()).thenReturn(redemptionId);
    when(redemption.getAccount()).thenReturn(account);
    when(account.getId()).thenReturn(accountId);
    when(subscriptionOperations.findByOfferRedemptionIdAndAccountId(redemptionId, accountId))
        .thenReturn(java.util.Optional.of(operation));
    SubscriptionChangeOperation recovered =
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service, "recoverOperation", redemption);

    assertThat(recovered).isSameAs(operation);
    verify(transitions)
        .applyOperation(redemption, operation);
  }

  @Test
  void codeResolutionDoesNotDisguiseRepositoryOutagesAsAnUnknownCode() {
    var outage = new DataAccessResourceFailureException("database unavailable");
    when(codeHasher.hash("CUSTOMER-CODE")).thenReturn("a".repeat(64));
    when(offers.findPublishedByReservedCodeHash(any(), any(Pageable.class))).thenThrow(outage);

    assertThatThrownBy(
            () ->
                service.resolveCode(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new CommercialOfferRequests.ResolveCode("CUSTOMER-CODE")))
        .isSameAs(outage);
  }

  @Test
  @SuppressWarnings("unchecked")
  void clientCatalogueFailsClosedAboveItsBoundInsteadOfReturningAFalsePartialTotal() {
    UUID accountId = UUID.randomUUID();
    Instant now = Instant.parse("2030-01-01T00:00:00Z");
    when(clock.instant()).thenReturn(now);
    when(catalogVersions.readConsistently(any(LongFunction.class)))
        .thenAnswer(invocation -> ((LongFunction<Object>) invocation.getArgument(0)).apply(17L));
    when(offers.findEligibleClientCatalogue(any(), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 201));

    assertThatThrownBy(() -> service.catalogue(accountId, PageRequest.of(0, 20)))
        .isInstanceOf(OfferNotAvailableException.class)
        .hasMessage("Offer is not available for this Account.");
    verifyNoInteractions(projections, catalogResolver, policyEvaluator);
  }
}
