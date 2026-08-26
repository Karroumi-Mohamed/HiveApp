package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.money.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Single source of truth for all prices selectable for new subscription snapshots. */
@Component
@RequiredArgsConstructor
public class ProductPriceResolver {

    private final ProductPriceRepository productPriceRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProductPrice resolvePlan(Plan plan, ProductPriceSelectionRequest requested) {
        if (requested == null) {
            return resolve(ProductPriceOwnerType.PLAN, plan.getId(), plan.money(), plan.getBillingCycle());
        }
        return resolveRequested(ProductPriceOwnerType.PLAN, plan.getId(), requested);
    }

    @Transactional(readOnly = true)
    public ProductPrice resolveAddOn(AddOn addOn, String currencyCode, BillingCycle billingCycle) {
        return resolve(ProductPriceOwnerType.ADD_ON, addOn.getId(), Money.zero(currencyCode), billingCycle);
    }

    @Transactional(readOnly = true)
    public ProductPrice resolveQuotaPackage(QuotaPackage item, String currencyCode, BillingCycle billingCycle) {
        return resolve(ProductPriceOwnerType.QUOTA_PACKAGE, item.getId(), Money.zero(currencyCode), billingCycle);
    }

    @Transactional(readOnly = true)
    public List<ProductPrice> availablePrices(ProductPriceOwnerType ownerType, UUID ownerId) {
        return productPriceRepository.findAllApplicable(ownerType, ownerId, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ProductPrice> availableCatalogPrices() {
        return productPriceRepository.findAllApplicable(clock.instant());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveOverlap(ProductPrice candidate) {
        if (candidate.getId() == null) {
            throw new InvalidRequestException("Price entry must be persisted before activation preview.");
        }
        return productPriceRepository.countActiveOverlaps(
                candidate.getOwnerType(), candidate.ownerId(), candidate.getCurrencyCode(),
                candidate.getBillingCycle(), candidate.getEffectiveFrom(), candidate.getEffectiveUntil(),
                candidate.getId()) > 0;
    }

    private ProductPrice resolveRequested(ProductPriceOwnerType ownerType, UUID ownerId,
                                          ProductPriceSelectionRequest requested) {
        validateCycle(requested.billingCycle());
        if (requested.priceEntryId() != null) {
            ProductPrice exact = productPriceRepository.findOwned(ownerType, ownerId, requested.priceEntryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "ProductPrice", "id", requested.priceEntryId()));
            requireSelectable(exact, clock.instant());
            if (requested.currencyCode() != null
                    && !Money.normalizeCurrencyCode(requested.currencyCode()).equals(exact.getCurrencyCode())) {
                throw new InvalidRequestException("Selected price entry does not use the requested currency.");
            }
            if (requested.billingCycle() != null && requested.billingCycle() != exact.getBillingCycle()) {
                throw new InvalidRequestException("Selected price entry does not use the requested billing cycle.");
            }
            return exact;
        }
        if (requested.currencyCode() == null || requested.currencyCode().isBlank()
                || requested.billingCycle() == null) {
            throw new InvalidRequestException(
                    "A price selection requires an exact priceEntryId or both currencyCode and billingCycle.");
        }
        return resolve(ownerType, ownerId, Money.zero(requested.currencyCode()), requested.billingCycle());
    }

    private ProductPrice resolve(ProductPriceOwnerType ownerType, UUID ownerId, Money currency,
                                 BillingCycle billingCycle) {
        validateCycle(billingCycle);
        List<ProductPrice> matches = productPriceRepository.findApplicable(
                ownerType, ownerId, currency.currencyCode(), billingCycle, clock.instant());
        if (matches.isEmpty()) {
            throw new InvalidStateException("No active applicable " + billingCycle
                    + " price exists for this product revision in " + currency.currencyCode() + ".");
        }
        if (matches.size() > 1) {
            throw new InvalidStateException("Multiple active applicable prices exist for the same product tuple.");
        }
        return matches.getFirst();
    }

    private void requireSelectable(ProductPrice price, Instant at) {
        if (!price.isApplicableAt(at)) {
            throw new InvalidStateException("The selected price entry is not active and applicable.");
        }
    }

    private void validateCycle(BillingCycle billingCycle) {
        if (billingCycle != null && billingCycle != BillingCycle.MONTHLY && billingCycle != BillingCycle.YEARLY) {
            throw new InvalidRequestException("Product prices support MONTHLY and YEARLY billing cycles only.");
        }
    }
}
