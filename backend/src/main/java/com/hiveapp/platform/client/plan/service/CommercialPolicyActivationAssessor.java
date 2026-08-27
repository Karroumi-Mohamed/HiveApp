package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyExecutionBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicyEffect;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.shared.money.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Computes the full deterministic policy activation decision signed by the shared evidence protocol. */
@Service
@RequiredArgsConstructor
public class CommercialPolicyActivationAssessor {

    private static final List<CommercialPolicyExecutionBlocker> EXECUTION_BLOCKERS = List.of(
            CommercialPolicyExecutionBlocker.SUBSCRIPTION_OPERATION_ENGINE_NOT_CONNECTED,
            CommercialPolicyExecutionBlocker.SCHEDULED_EXECUTION_NOT_AVAILABLE);

    private final CommercialPolicyAudienceResolver audienceResolver;
    private final CommercialPolicyRepository policyRepository;
    private final SubscriptionRepository subscriptionRepository;

    public Assessment assess(CommercialPolicy policy, Instant evaluatedAt) {
        List<CommercialPolicyBlocker> blockers = new ArrayList<>();
        if (policy.getStatus() != CommercialPolicyStatus.DRAFT
                && policy.getStatus() != CommercialPolicyStatus.PAUSED) {
            blockers.add(CommercialPolicyBlocker.WRONG_LIFECYCLE_STATE);
        }
        if (policy.getEffectiveUntil() != null && !policy.getEffectiveUntil().isAfter(evaluatedAt)) {
            blockers.add(CommercialPolicyBlocker.EFFECTIVE_WINDOW_EXPIRED);
        }
        if (policy.getEffects().isEmpty()) {
            blockers.add(CommercialPolicyBlocker.NO_EFFECTS);
        }
        policy.getEffects().stream()
                .sorted(Comparator.comparingInt(CommercialPolicyEffect::getEffectOrder))
                .forEach(effect -> validateEffect(effect, blockers));

        CommercialPolicyAudienceResolver.Resolution audience = audienceResolver.resolveExact(policy);
        blockers.addAll(audience.blockers());
        Set<String> currencies = monetaryCurrencies(policy.getEffects());
        if (!audience.accountIds().isEmpty() && !currencies.isEmpty()
                && subscriptionRepository.countAudienceCurrencyMismatches(
                        audience.accountIds(),
                        CommercialPolicyAudienceResolver.CURRENT_SUBSCRIPTION_STATUSES,
                        currencies) > 0) {
            blockers.add(CommercialPolicyBlocker.AUDIENCE_CURRENCY_MISMATCH);
        }

        List<CommercialPolicy> currentLineage = policyRepository.findAllByLineageIdAndStatusIn(
                policy.getLineageId(), List.of(CommercialPolicyStatus.ACTIVE, CommercialPolicyStatus.PAUSED))
                .stream().filter(candidate -> !candidate.getId().equals(policy.getId()))
                .sorted(Comparator.comparing(CommercialPolicy::getId)).toList();
        if (currentLineage.size() > 1) {
            blockers.add(CommercialPolicyBlocker.LINEAGE_HAS_MULTIPLE_CURRENT_REVISIONS);
        }
        CommercialPolicy revisionToEnd = currentLineage.size() == 1 ? currentLineage.getFirst() : null;
        List<CommercialPolicyBlocker> stableBlockers = blockers.stream().distinct().sorted().toList();
        String fingerprint = fingerprint(policy, audience.accountIds(), stableBlockers,
                revisionToEnd);
        return new Assessment(stableBlockers, EXECUTION_BLOCKERS, audience.accountIds(), revisionToEnd,
                fingerprint);
    }

    private void validateEffect(CommercialPolicyEffect effect, List<CommercialPolicyBlocker> blockers) {
        boolean valid = switch (effect.getType()) {
            case FIXED_SUBSCRIPTION_PRICE -> effect.money() != null
                    && !effect.money().isNegative() && effect.getBillingCycle() != null
                    && noProductOrFeature(effect);
            case FIXED_DISCOUNT -> positive(effect.money()) && effect.getBillingCycle() == null
                    && noProductOrFeature(effect);
            case PERCENTAGE_DISCOUNT -> effect.getPercentage() != null
                    && effect.getPercentage().compareTo(BigDecimal.ZERO) > 0
                    && effect.getPercentage().compareTo(new BigDecimal("100")) <= 0
                    && positive(effect.maximumMoney()) && effect.getBillingCycle() == null
                    && noProductOrFeature(effect);
            case ALLOW_PRODUCT_SELECTION, BLOCK_PRODUCT_SELECTION -> effect.getProductType() != null
                    && effect.productId() != null && effect.getFeature() == null
                    && effect.getQuotaResource() == null && effect.getQuantityDelta() == null
                    && effect.money() == null && effect.getPercentage() == null;
            case ADDITIVE_QUOTA_BONUS -> effect.getFeature() != null
                    && effect.getQuotaResource() != null && effect.getQuantityDelta() != null
                    && effect.getQuantityDelta() > 0 && effect.getProductType() == null
                    && effect.money() == null && effect.getPercentage() == null;
            case GRANT_ADD_ON -> effect.getProductType() == CommercialPolicyProductType.ADD_ON
                    && effect.getAddOn() != null && effect.getPolicy().getEffectiveUntil() != null;
            case GRANT_QUOTA_PACKAGE -> effect.getProductType() == CommercialPolicyProductType.QUOTA_PACKAGE
                    && effect.getQuotaPackage() != null && effect.getPolicy().getEffectiveUntil() != null;
            case BLOCK_FEATURE -> effect.getFeature() != null && effect.getProductType() == null
                    && effect.getQuotaResource() == null && effect.getQuantityDelta() == null;
        };
        if (!valid) blockers.add(CommercialPolicyBlocker.INVALID_EFFECT);

        if (effect.getProductType() != null && effect.productId() == null) {
            blockers.add(CommercialPolicyBlocker.PRODUCT_REVISION_MISSING);
        } else if (effect.getType() != CommercialPolicyEffectType.BLOCK_PRODUCT_SELECTION
                && effect.getProductType() != null && !productActive(effect)) {
            blockers.add(CommercialPolicyBlocker.PRODUCT_REVISION_NOT_ACTIVE);
        }
        if (effect.getFeature() != null) {
            if (effect.getFeature().getId() == null) {
                blockers.add(CommercialPolicyBlocker.FEATURE_MISSING);
            } else if (effect.getType() != CommercialPolicyEffectType.BLOCK_FEATURE
                    && !commerciallyGrantable(effect)) {
                blockers.add(CommercialPolicyBlocker.FEATURE_NOT_COMMERCIALLY_GRANTABLE);
            }
        }
        if (effect.getType() == CommercialPolicyEffectType.ADDITIVE_QUOTA_BONUS
                && effect.getFeature() != null
                && effect.getFeature().getQuotaSchema().stream()
                        .noneMatch(slot -> slot.resource().equals(effect.getQuotaResource()))) {
            blockers.add(CommercialPolicyBlocker.QUOTA_RESOURCE_NOT_DECLARED);
        }
    }

    private boolean productActive(CommercialPolicyEffect effect) {
        return switch (effect.getProductType()) {
            case PLAN -> effect.getPlan().getStatus() == PlanStatus.ACTIVE;
            case ADD_ON -> effect.getAddOn().getStatus() == AddOnStatus.ACTIVE;
            case QUOTA_PACKAGE -> effect.getQuotaPackage().getStatus() == QuotaPackageStatus.ACTIVE;
        };
    }

    private boolean commerciallyGrantable(CommercialPolicyEffect effect) {
        var feature = effect.getFeature();
        return (feature.getStatus() == FeatureStatus.PUBLIC || feature.getStatus() == FeatureStatus.BETA)
                && feature.isPublicVisible()
                && feature.isNewGrantsEnabled()
                && feature.isRuntimeEnabled();
    }

    private boolean noProductOrFeature(CommercialPolicyEffect effect) {
        return effect.getProductType() == null && effect.getFeature() == null
                && effect.getQuotaResource() == null && effect.getQuantityDelta() == null;
    }

    private boolean positive(Money money) {
        return money != null && money.amount().signum() > 0;
    }

    private Set<String> monetaryCurrencies(java.util.Collection<CommercialPolicyEffect> effects) {
        Set<String> currencies = new LinkedHashSet<>();
        for (CommercialPolicyEffect effect : effects) {
            if (effect.money() != null) currencies.add(effect.money().currencyCode());
            if (effect.maximumMoney() != null) currencies.add(effect.maximumMoney().currencyCode());
        }
        return Set.copyOf(currencies);
    }

    private String fingerprint(
            CommercialPolicy policy,
            List<UUID> accountIds,
            List<CommercialPolicyBlocker> blockers,
            CommercialPolicy revisionToEnd
    ) {
        StringBuilder state = new StringBuilder();
        ActivationAssessmentFingerprint.append(state, "commercial-policy-activation");
        ActivationAssessmentFingerprint.append(state, policy.getId());
        ActivationAssessmentFingerprint.append(state, policy.getVersion());
        ActivationAssessmentFingerprint.append(state, policy.getCode());
        ActivationAssessmentFingerprint.append(state, policy.getStatus());
        ActivationAssessmentFingerprint.append(state, policy.getTargetKind());
        ActivationAssessmentFingerprint.append(state,
                policy.getTargetAccount() == null ? null : policy.getTargetAccount().getId());
        ActivationAssessmentFingerprint.appendValues(state, "explicit-target-accounts",
                policy.getTargetKind() == CommercialPolicyTargetKind.ACCOUNT_SET
                        ? accountIds.stream().sorted().toList()
                        : List.of());
        ActivationAssessmentFingerprint.append(state,
                policy.getTargetPlan() == null ? null : policy.getTargetPlan().getId());
        ActivationAssessmentFingerprint.append(state,
                policy.getTargetPlan() == null ? null : policy.getTargetPlan().getVersion());
        ActivationAssessmentFingerprint.append(state,
                policy.getTargetPlan() == null ? null : policy.getTargetPlan().getStatus());
        ActivationAssessmentFingerprint.append(state, policy.getSegmentReference());
        ActivationAssessmentFingerprint.append(state, policy.getEffectiveFrom());
        ActivationAssessmentFingerprint.append(state, policy.getEffectiveUntil());
        ActivationAssessmentFingerprint.append(state, policy.getSource());
        ActivationAssessmentFingerprint.append(state, policy.getPriority());
        ActivationAssessmentFingerprint.append(state, policy.getReason());
        ActivationAssessmentFingerprint.append(state, policy.getApprovalReference());
        ActivationAssessmentFingerprint.append(state, policy.getContractReference());
        ActivationAssessmentFingerprint.append(state, policy.getLineageId());
        ActivationAssessmentFingerprint.append(state, policy.getRevisionNumber());
        ActivationAssessmentFingerprint.append(state, policy.getOwner().getId());
        for (CommercialPolicyEffect effect : policy.getEffects().stream()
                .sorted(Comparator.comparingInt(CommercialPolicyEffect::getEffectOrder)).toList()) {
            ActivationAssessmentFingerprint.append(state, effect.getEffectOrder());
            ActivationAssessmentFingerprint.append(state, effect.getType());
            ActivationAssessmentFingerprint.append(state, effect.getProductType());
            ActivationAssessmentFingerprint.append(state, effect.productId());
            appendProductVersion(state, effect);
            ActivationAssessmentFingerprint.append(state,
                    effect.getFeature() == null ? null : effect.getFeature().getId());
            ActivationAssessmentFingerprint.append(state,
                    effect.getFeature() == null ? null : effect.getFeature().getUpdatedAt());
            ActivationAssessmentFingerprint.append(state, effect.getQuotaResource());
            ActivationAssessmentFingerprint.append(state, effect.getQuantityDelta());
            ActivationAssessmentFingerprint.append(state,
                    effect.money() == null ? null : effect.money().toString());
            ActivationAssessmentFingerprint.append(state, effect.getBillingCycle());
            ActivationAssessmentFingerprint.append(state, effect.getPercentage());
            ActivationAssessmentFingerprint.append(state,
                    effect.maximumMoney() == null ? null : effect.maximumMoney().toString());
        }
        ActivationAssessmentFingerprint.appendValues(state, "audience",
                accountIds.stream().sorted().toList());
        ActivationAssessmentFingerprint.appendValues(state, "blockers",
                blockers.stream().map(Enum::name).toList());
        ActivationAssessmentFingerprint.append(state,
                revisionToEnd == null ? null : revisionToEnd.getId());
        ActivationAssessmentFingerprint.append(state,
                revisionToEnd == null ? null : revisionToEnd.getVersion());
        ActivationAssessmentFingerprint.append(state,
                revisionToEnd == null ? null : revisionToEnd.getStatus());
        return ActivationAssessmentFingerprint.digest(state.toString());
    }

    private void appendProductVersion(StringBuilder state, CommercialPolicyEffect effect) {
        if (effect.getPlan() != null) {
            ActivationAssessmentFingerprint.append(state, effect.getPlan().getVersion());
            ActivationAssessmentFingerprint.append(state, effect.getPlan().getStatus());
        } else if (effect.getAddOn() != null) {
            ActivationAssessmentFingerprint.append(state, effect.getAddOn().getRowVersion());
            ActivationAssessmentFingerprint.append(state, effect.getAddOn().getDefinitionVersion());
            ActivationAssessmentFingerprint.append(state, effect.getAddOn().getStatus());
        } else if (effect.getQuotaPackage() != null) {
            ActivationAssessmentFingerprint.append(state, effect.getQuotaPackage().getRowVersion());
            ActivationAssessmentFingerprint.append(state, effect.getQuotaPackage().getDefinitionVersion());
            ActivationAssessmentFingerprint.append(state, effect.getQuotaPackage().getStatus());
        }
    }

    public record Assessment(
            List<CommercialPolicyBlocker> blockers,
            List<CommercialPolicyExecutionBlocker> executionBlockers,
            List<UUID> accountIds,
            CommercialPolicy revisionToEnd,
            String fingerprint
    ) {
        public Assessment {
            blockers = List.copyOf(blockers);
            executionBlockers = List.copyOf(executionBlockers);
            accountIds = List.copyOf(accountIds);
        }

        public boolean activatable() {
            return blockers.isEmpty();
        }
    }
}
