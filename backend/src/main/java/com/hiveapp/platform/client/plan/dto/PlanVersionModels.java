package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.shared.money.ExactDecimal;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.hiveapp.shared.api.PageResponse;

public final class PlanVersionModels {
    private PlanVersionModels() {}
    public record Version(UUID id, String code, String name, String description, UUID lineageId,
                          int productVersionNumber, UUID sourcePlanId, PlanStatus status,
                          PlanExtensionPolicy extensionPolicy, ProductSalesVisibility salesVisibility,
                          long rowVersion) {}
    public record Family(UUID lineageId, Version publicVersion, Version draft,
                         long versionCount, Long currentSubscriberCount,
                         List<Price> currentPrices, boolean pricesVisible) {}
    public record Versions(UUID lineageId, UUID publicPlanId, UUID draftPlanId,
                           long catalogRevision, PageResponse<PlanOperationalListItemDto> versions) {}
    public record Price(UUID id, @ExactDecimal BigDecimal amount, String currencyCode,
                        BillingCycle billingCycle, Instant effectiveFrom, Instant effectiveUntil) {}
    public record FeatureChange(String featureCode, PlanFeatureDto before, PlanFeatureDto after,
                                boolean changed) {}
    public record Comparison(UUID lineageId, Version source, Version target,
                             List<FeatureChange> features, boolean extensionPolicyChanged,
                             boolean visibilityChanged, List<Price> sourcePrices,
                             List<Price> targetPrices, boolean pricesVisible) {}
    public record SelectPublic(@NotNull Long expectedCatalogRevision, @NotNull Long expectedVersion,
                               @NotBlank @Size(max = 1000) String reason) {}
    public record Metadata(@NotNull Long expectedVersion, @NotBlank @Size(max = 255) String name,
                           @Size(max = 255) String description,
                           @NotBlank @Size(max = 1000) String reason) {}
}
