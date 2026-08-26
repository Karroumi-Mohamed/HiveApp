package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Creates the temporary legacy-projection row when a newly-authored product is first published. */
@Component
@RequiredArgsConstructor
public class ProductPriceCompatibilityService {

    private final ProductPriceRepository productPriceRepository;

    public void ensurePublishedDefault(Plan plan) {
        if (missing(ProductPriceOwnerType.PLAN, plan.getId(), plan.getCurrencyCode(), plan.getBillingCycle())) {
            publish(ProductPrice.draft(plan, plan.money(), plan.getBillingCycle(), Instant.EPOCH, null));
        }
    }

    public void ensurePublishedDefault(AddOn addOn) {
        if (missing(ProductPriceOwnerType.ADD_ON, addOn.getId(), addOn.getCurrencyCode(), addOn.getBillingCycle())) {
            publish(ProductPrice.draft(addOn, addOn.money(), addOn.getBillingCycle(), Instant.EPOCH, null));
        }
    }

    public void ensurePublishedDefault(QuotaPackage item) {
        if (missing(ProductPriceOwnerType.QUOTA_PACKAGE, item.getId(), item.getCurrencyCode(), item.getBillingCycle())) {
            publish(ProductPrice.draft(item, item.money(), item.getBillingCycle(), Instant.EPOCH, null));
        }
    }

    private boolean missing(ProductPriceOwnerType type, java.util.UUID id, String currency, BillingCycle cycle) {
        if (cycle != BillingCycle.MONTHLY && cycle != BillingCycle.YEARLY) {
            return false;
        }
        return productPriceRepository.countCompatibilityPrice(type, id, currency, cycle) == 0;
    }

    private void publish(ProductPrice price) {
        price.markCompatibilityDefault();
        price.activate();
        productPriceRepository.saveAndFlush(price);
    }
}
