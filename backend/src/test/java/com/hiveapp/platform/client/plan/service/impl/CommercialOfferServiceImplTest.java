package com.hiveapp.platform.client.plan.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.CommercialOfferRequests;
import com.hiveapp.platform.client.plan.service.*;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.OfferNotAvailableException;
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
  @Mock private CommercialOfferEligibilityService eligibility;
  @Mock private CommercialOfferRedemptionTransitionService transitions;
  @Mock private CommercialCatalogResolver catalogResolver;
  @Mock private CommercialPolicyEvaluator policyEvaluator;

  @InjectMocks private CommercialOfferServiceImpl service;

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
