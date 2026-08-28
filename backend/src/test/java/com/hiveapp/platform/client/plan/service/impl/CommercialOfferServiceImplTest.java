package com.hiveapp.platform.client.plan.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.CommercialOfferRequests;
import com.hiveapp.platform.client.plan.service.*;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
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
}
