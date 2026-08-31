package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutation;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Disposable-H2 compatibility bridge. It never rewrites or reactivates an existing price row. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductPriceBackfill {

    private final PlanRepository planRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final ProductPriceRepository productPriceRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Order(5)
    @Transactional
    @CommercialCatalogMutation
    public void backfill() {
        int created = 0;
        for (Plan plan : planRepository.findAll()) {
            created += ensure(plan);
        }
        for (AddOn addOn : addOnRepository.findAll()) {
            created += ensure(addOn);
        }
        for (QuotaPackage item : quotaPackageRepository.findAll()) {
            created += ensure(item);
        }
        log.info("Product price compatibility backfill complete — created {} authoritative price(s).", created);
    }

    private int ensure(Plan plan) {
        if ((plan.getStatus() != PlanStatus.ACTIVE && plan.getStatus() != PlanStatus.INACTIVE)
                || !recurring(plan.getBillingCycle())
                || exists(ProductPriceOwnerType.PLAN, plan.getId(),
                plan.getCurrencyCode(), plan.getBillingCycle())) {
            return 0;
        }
        ProductPrice price = ProductPrice.draft(
                plan, plan.money(), plan.getBillingCycle(), Instant.EPOCH, null);
        publishCompatibility(price);
        return 1;
    }

    private int ensure(AddOn addOn) {
        if ((addOn.getStatus() != AddOnStatus.ACTIVE && addOn.getStatus() != AddOnStatus.INACTIVE)
                || !recurring(addOn.getBillingCycle())
                || exists(ProductPriceOwnerType.ADD_ON, addOn.getId(),
                addOn.getCurrencyCode(), addOn.getBillingCycle())) {
            return 0;
        }
        ProductPrice price = ProductPrice.draft(
                addOn, addOn.money(), addOn.getBillingCycle(), Instant.EPOCH, null);
        publishCompatibility(price);
        return 1;
    }

    private int ensure(QuotaPackage item) {
        if ((item.getStatus() != QuotaPackageStatus.ACTIVE
                && item.getStatus() != QuotaPackageStatus.INACTIVE)
                || !recurring(item.getBillingCycle())
                || exists(ProductPriceOwnerType.QUOTA_PACKAGE, item.getId(),
                item.getCurrencyCode(), item.getBillingCycle())) {
            return 0;
        }
        ProductPrice price = ProductPrice.draft(
                item, item.money(), item.getBillingCycle(), Instant.EPOCH, null);
        publishCompatibility(price);
        return 1;
    }

    private boolean exists(ProductPriceOwnerType type, java.util.UUID ownerId,
                           String currencyCode, BillingCycle cycle) {
        return productPriceRepository.countCompatibilityPrice(type, ownerId, currencyCode, cycle) > 0;
    }

    private void publishCompatibility(ProductPrice price) {
        price.markCompatibilityDefault();
        price.activate();
        productPriceRepository.save(price);
    }

    private boolean recurring(BillingCycle cycle) {
        return cycle == BillingCycle.MONTHLY || cycle == BillingCycle.YEARLY;
    }
}
