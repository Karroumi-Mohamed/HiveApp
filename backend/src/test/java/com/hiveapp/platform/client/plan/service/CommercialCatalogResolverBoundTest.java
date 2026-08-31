package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.shared.exception.ErrorCode;
import com.hiveapp.shared.exception.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialCatalogResolverBoundTest {

    @Mock private PlanRepository planRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private AddOnRepository addOnRepository;
    @Mock private QuotaPackageRepository quotaPackageRepository;
    @Mock private ProductPriceRepository productPriceRepository;
    @Mock private ObjectProvider<FeatureDefinitionCollector> collectorProvider;

    private CommercialCatalogResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CommercialCatalogResolver(
                planRepository, planFeatureRepository, addOnRepository, quotaPackageRepository,
                productPriceRepository, collectorProvider,
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void fullCatalogueFailsClosedInsteadOfTruncatingBeyondProductBound() {
        when(planRepository.findAllByOrderByIdAsc(any(Pageable.class)))
                .thenReturn(Collections.nCopies(301, new Plan()));
        when(addOnRepository.findAllByOrderByIdAsc(any(Pageable.class))).thenReturn(List.of());
        when(quotaPackageRepository.findAllByOrderByIdAsc(any(Pageable.class))).thenReturn(List.of());

        Throwable failure = catchThrowable(() -> resolver.resolveCatalog(
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR));

        assertThat(ErrorCodes.of(failure)).isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(failure).hasMessage(
                "The commercial catalogue exceeds the supported 300-product operation bound; "
                        + "use a scoped operation or add catalogue pagination before proceeding.");
    }

    @Test
    void fullCatalogueFailsClosedInsteadOfTruncatingBeyondActivePriceBound() {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        when(planRepository.findAllByOrderByIdAsc(any(Pageable.class))).thenReturn(List.of(plan));
        when(addOnRepository.findAllByOrderByIdAsc(any(Pageable.class))).thenReturn(List.of());
        when(quotaPackageRepository.findAllByOrderByIdAsc(any(Pageable.class))).thenReturn(List.of());
        when(planFeatureRepository.findAllByPlanIds(anyCollection())).thenReturn(List.of());
        when(addOnRepository.findAllDetailedByIdIn(anyCollection())).thenReturn(List.<AddOn>of());
        when(quotaPackageRepository.findAllDetailedByIdIn(anyCollection()))
                .thenReturn(List.<QuotaPackage>of());
        when(productPriceRepository.findAllApplicableForOwnersBounded(
                anyCollection(), anyCollection(), anyCollection(), any(Instant.class),
                any(Pageable.class)))
                .thenReturn(Collections.nCopies(
                        1_201, org.mockito.Mockito.mock(ProductPrice.class)));

        Throwable failure = catchThrowable(() -> resolver.resolveCatalog(
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR));

        assertThat(ErrorCodes.of(failure)).isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(failure).hasMessage(
                "The commercial catalogue exceeds the supported 1200-active-price operation bound; "
                        + "narrow the operation or add catalogue pagination before proceeding.");
    }
}
