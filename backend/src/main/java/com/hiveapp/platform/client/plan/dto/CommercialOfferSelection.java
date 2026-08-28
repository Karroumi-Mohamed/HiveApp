package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable exact product/price selection accepted by an Offer revision. */
public record CommercialOfferSelection(
        @NotNull UUID planId,
        @NotNull UUID planPriceId,
        @Valid List<AddOnSelection> addOns,
        @Valid List<PackageSelection> quotaPackages,
        @NotNull SubscriptionChangeTiming timing) {
    public CommercialOfferSelection {
        addOns = addOns == null ? List.of() : List.copyOf(addOns);
        quotaPackages = quotaPackages == null ? List.of() : List.copyOf(quotaPackages);
    }
    public enum PricingMode { PAID, FREE }
    public record AddOnSelection(@NotNull UUID addOnId, @NotNull UUID priceId,
                                 @NotNull PricingMode pricingMode) {}
    public record PackageSelection(@NotNull UUID quotaPackageId, @NotNull UUID priceId,
                                   @Positive int quantity, @NotNull PricingMode pricingMode) {}
}
