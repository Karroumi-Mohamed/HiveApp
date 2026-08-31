package com.hiveapp.platform.client.plan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.ProductPriceActivationRequest;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.client.plan.service.ProductPriceActivationAssessor;
import com.hiveapp.platform.registry.domain.entity.RegistrySyncLock;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPriceAdminServiceImplTest {

    @Mock private ProductPriceRepository productPriceRepository;
    @Mock private PlanRepository planRepository;
    @Mock private AddOnRepository addOnRepository;
    @Mock private QuotaPackageRepository quotaPackageRepository;
    @Mock private Clock clock;
    @Mock private ProductPriceActivationAssessor productPriceActivationAssessor;
    @Mock private CommercialCatalogVersionService commercialCatalogVersionService;
    @Mock private RegistryCatalogVersionService registryCatalogVersionService;
    @Mock private CommercialPreviewTokenService previewTokenService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AdminUserRepository adminUserRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private EntityManager entityManager;
    @Mock private AuditTrail auditTrail;
    @Mock private AdminMutationAuthorizer adminMutationAuthorizer;
    @InjectMocks
    private ProductPriceAdminServiceImpl service;

    @Test
    void activationRechecksOwnerAfterWaitingForItsLock() {
        UUID ownerId = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        Plan staleDraftOwner = plan(ownerId, PlanStatus.DRAFT);
        Plan authoritativeArchivedOwner = plan(ownerId, PlanStatus.ARCHIVED);
        ProductPrice staleHint = price(priceId, staleDraftOwner);
        ProductPrice authoritativePrice = price(priceId, authoritativeArchivedOwner);
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        UUID actorId = UUID.randomUUID();

        when(clock.instant()).thenReturn(now);
        when(registryCatalogVersionService.lockForMutation()).thenReturn(new RegistrySyncLock());
        when(registryCatalogVersionService.currentVersion()).thenReturn("registry:1");
        when(commercialCatalogVersionService.currentRevision()).thenReturn(1L);
        when(adminMutationAuthorizer.currentActorUserId()).thenReturn(actorId);
        when(productPriceRepository.findById(priceId)).thenReturn(Optional.of(staleHint));
        when(planRepository.findByIdForUpdate(ownerId))
                .thenReturn(Optional.of(authoritativeArchivedOwner));
        when(productPriceRepository.findByIdForUpdate(priceId))
                .thenReturn(Optional.of(authoritativePrice));
        when(productPriceActivationAssessor.assess(authoritativePrice, now))
                .thenReturn(new ProductPriceActivationAssessor.Assessment(
                        java.util.List.of(
                                com.hiveapp.platform.client.plan.domain.constant.ProductPriceBlocker.OWNER_NOT_ACTIVE),
                        "assessment"));

        assertThatThrownBy(() -> service.activate(priceId, new ProductPriceActivationRequest(
                        0L, "Publish reviewed price", "preview")))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("OWNER_NOT_ACTIVE");

        assertThat(staleHint.getStatus()).isEqualTo(ProductPriceStatus.DRAFT);
        assertThat(authoritativePrice.getStatus()).isEqualTo(ProductPriceStatus.DRAFT);
        verify(productPriceRepository, never()).saveAndFlush(authoritativePrice);
        InOrder order = inOrder(
                registryCatalogVersionService, productPriceRepository, planRepository, entityManager);
        order.verify(registryCatalogVersionService).lockForMutation();
        order.verify(registryCatalogVersionService).currentVersion();
        order.verify(productPriceRepository).findById(priceId);
        order.verify(planRepository).findByIdForUpdate(ownerId);
        order.verify(entityManager).clear();
        order.verify(productPriceRepository).findByIdForUpdate(priceId);
    }

    @Test
    void reviseChecksTheAuthoritativeLockedPriceVersionAfterOwnerLock() {
        UUID ownerId = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        Plan owner = plan(ownerId, PlanStatus.ACTIVE);
        ProductPrice staleHint = publishedPrice(priceId, owner, 0L);
        ProductPrice authoritative = publishedPrice(priceId, owner, 1L);

        when(productPriceRepository.findById(priceId)).thenReturn(Optional.of(staleHint));
        when(planRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(productPriceRepository.findByIdForUpdate(priceId))
                .thenReturn(Optional.of(authoritative));

        assertThatThrownBy(() -> service.revise(priceId, 0L, "Revise reviewed price"))
                .isInstanceOf(StaleResourceVersionException.class);

        verify(productPriceRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        InOrder order = inOrder(productPriceRepository, planRepository, entityManager);
        order.verify(productPriceRepository).findById(priceId);
        order.verify(planRepository).findByIdForUpdate(ownerId);
        order.verify(entityManager).clear();
        order.verify(productPriceRepository).findByIdForUpdate(priceId);
    }

    private Plan plan(UUID id, PlanStatus status) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", id);
        plan.setStatus(status);
        return plan;
    }

    private ProductPrice price(UUID id, Plan owner) {
        ProductPrice price = ProductPrice.draft(
                owner, Money.of(new BigDecimal("9.9900"), "USD"), BillingCycle.MONTHLY,
                Instant.parse("2026-08-26T11:59:00Z"), null);
        ReflectionTestUtils.setField(price, "id", id);
        return price;
    }

    private ProductPrice publishedPrice(UUID id, Plan owner, long version) {
        ProductPrice price = price(id, owner);
        price.activate();
        price.pause();
        ReflectionTestUtils.setField(price, "version", version);
        return price;
    }
}
