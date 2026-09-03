package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementAttentionStage;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementEndInstruction;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementPricingMode;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementSettlementMode;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.constant.CheckoutConfirmationSource;
import com.hiveapp.shared.money.ExactDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin contract for one-Account negotiated subscription terms. */
public final class SpecialAgreementModels {
    private SpecialAgreementModels() {}

    public record Definition(
            @NotNull @Valid SubscriptionChangeRequest selection,
            @Valid @Size(max = 100) List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull SpecialAgreementPricingMode pricingMode,
            @ExactDecimal BigDecimal customTotal,
            @Size(min = 3, max = 3) String currencyCode,
            @NotNull SpecialAgreementSettlementMode settlementMode,
            @NotNull SpecialAgreementEndInstruction endInstruction,
            SpecialAgreementPricingMode followOnPricingMode,
            @ExactDecimal BigDecimal followOnCustomAmount) {
        public Definition {
            quotaBonuses = quotaBonuses == null ? List.of() : List.copyOf(quotaBonuses);
        }
    }

    public record PreviewRequest(@NotNull @Valid Definition definition) {}

    public record ConfirmRequest(
            @NotNull @Valid Definition definition,
            @NotBlank @Size(max = 4096) String previewToken,
            @NotBlank @Size(max = 2000) String reason) {}

    public record CancelRequest(@NotBlank @Size(max = 2000) String reason) {}

    public record RetryRequest(@NotBlank @Size(max = 2000) String reason) {}

    public record Preview(
            UUID subscriptionId,
            long expectedSubscriptionVersion,
            long catalogRevision,
            String registryVersion,
            Instant evaluatedAt,
            Instant expiresAt,
            String previewToken,
            Instant startsAt,
            Instant endsAt,
            long completeBillingCycles,
            @ExactDecimal BigDecimal catalogueCycleAmount,
            @ExactDecimal BigDecimal catalogueTermAmount,
            @ExactDecimal BigDecimal agreedTermAmount,
            @ExactDecimal BigDecimal varianceAmount,
            @ExactDecimal BigDecimal followOnAmount,
            String currencyCode,
            SpecialAgreementPricingMode pricingMode,
            SpecialAgreementSettlementMode settlementMode,
            SpecialAgreementEndInstruction endInstruction,
            ClientSubscriptionEntitlementState currentEntitlements,
            ClientSubscriptionEntitlementState termEntitlements,
            List<SubscriptionChangeConflict> conflicts,
            boolean confirmable) {}

    public record AvailableActions(
            boolean cancel,
            boolean settleManually,
            boolean retryStart,
            boolean retryEnd,
            boolean resolveManualReview) {}

    public record Summary(
            UUID id,
            long version,
            UUID accountId,
            String accountName,
            String planCode,
            String planName,
            SpecialAgreementStatus status,
            SpecialAgreementPricingMode pricingMode,
            SpecialAgreementSettlementMode settlementMode,
            SpecialAgreementEndInstruction endInstruction,
            Instant startsAt,
            Instant endsAt,
            @ExactDecimal BigDecimal agreedTermAmount,
            String currencyCode,
            SpecialAgreementAttentionStage attentionStage,
            AvailableActions availableActions,
            Instant createdAt) {}

    public record Detail(
            Summary summary,
            SubscriptionOverrides selection,
            List<SelectedAddOn> addOns,
            List<SelectedQuotaPackage> quotaPackages,
            List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses,
            @ExactDecimal BigDecimal catalogueCycleAmount,
            @ExactDecimal BigDecimal catalogueTermAmount,
            @ExactDecimal BigDecimal followOnAmount,
            @ExactDecimal BigDecimal previousRecurringAmount,
            UUID sourceSubscriptionId,
            UUID resultSubscriptionId,
            UUID changeOperationId,
            Checkout checkout,
            UUID createdByUserId,
            String reason,
            String attentionReason,
            Instant activatedAt,
            Instant completedAt,
            Instant cancelledAt,
            UUID cancelledByUserId,
            String cancellationReason) {}

    public record Created(Detail agreement, Checkout checkout) {}

    /**
     * Operational checkout state without payment-provider or manual-settlement references. Those
     * identifiers remain on the independently protected billing evidence surfaces.
     */
    public record Checkout(
            UUID id,
            SubscriptionCheckoutStatus status,
            @ExactDecimal BigDecimal amount,
            String currencyCode,
            CheckoutConfirmationSource confirmationSource,
            Instant confirmedAt) {}

    public record SelectedAddOn(String code, String name) {}

    public record SelectedQuotaPackage(
            String code,
            String name,
            String resource,
            long capacityPerUnit,
            int quantity) {}

    /**
     * Client-safe agreement terms. Operator identity, reason, settlement route, manual references,
     * internal Feature identities, and retry controls deliberately remain on the admin surface.
     */
    public record ClientView(
            UUID id,
            SpecialAgreementStatus status,
            String planCode,
            String planName,
            SubscriptionOverrides selection,
            List<SelectedAddOn> addOns,
            List<SelectedQuotaPackage> quotaPackages,
            ClientSubscriptionEntitlementState termEntitlements,
            Instant startsAt,
            Instant endsAt,
            @ExactDecimal BigDecimal agreedTermAmount,
            String currencyCode,
            SpecialAgreementPricingMode pricingMode,
            SpecialAgreementEndInstruction endInstruction,
            SpecialAgreementAttentionStage attentionStage,
            Instant activatedAt,
            Instant completedAt) {}

    public record CurrencyAnalytics(
            String currencyCode,
            @ExactDecimal BigDecimal catalogueValue,
            @ExactDecimal BigDecimal agreedValue,
            @ExactDecimal BigDecimal complimentaryValue,
            @ExactDecimal BigDecimal invoicedValue,
            @ExactDecimal BigDecimal collectedValue) {}

    public record Analytics(
            long total,
            long scheduled,
            long awaitingSettlement,
            long active,
            long completed,
            long cancelled,
            long needsAttention,
            long complimentary,
            long providerSettlement,
            long manualSettlement,
            List<CurrencyAnalytics> currencies) {}
}
