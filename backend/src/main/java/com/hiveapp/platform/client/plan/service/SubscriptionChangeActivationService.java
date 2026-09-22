package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.shared.audit.AuditedMutation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionChangeActivationService {

    private final SubscriptionChangeOperationRepository operationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AccountRepository accountRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final SubscriptionImpactAnalyzer impactAnalyzer;
    private final SubscriptionLifecycleManager lifecycleManager;
    private final SubscriptionPeriodCalculator periodCalculator;
    private final BillingCalculator billingCalculator;
    private final SpecialAgreementTransitionService specialAgreements;
    private final com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepricingItemRepository repricingItems;

    @Transactional
    @AuditedMutation(
            action = "platform.client.subscription.change.activate",
            resourceType = "SUBSCRIPTION_CHANGE_OPERATION")
    public SubscriptionChangeOperation activate(SubscriptionChangeOperation operation, Instant startsAt) {
        if (operation.getContentCommandId() != null) {
            throw new IllegalStateException("Content-only changes cannot enter the billing activation flow.");
        }
        UUID accountId = operation.getAccount().getId();
        if (accountRepository.findByIdForSubscriptionUpdate(accountId).isEmpty()) {
            return markNeedsAttention(operation, "The Account no longer exists.");
        }
        Subscription current = subscriptionRepository.findCurrentByAccountId(accountId).orElse(null);
        if (current == null || !current.getId().equals(operation.getSourceSubscription().getId())) {
            return markNeedsAttention(
                    operation, "The Account subscription changed after this operation was requested.");
        }
        if (operation.getRepricingItemId() != null) {
            var instruction = repricingItems.lock(operation.getRepricingItemId()).orElse(null);
            if (instruction == null || instruction.getOperation() == null
                    || !instruction.getOperation().getId().equals(operation.getId())
                    || !current.termsIdentity().equals(instruction.getTermsIdentity())
                    || !current.getEntitlementSnapshot().withEffectivePeriod(null, null).equals(instruction.getBeforeSnapshot().withEffectivePeriod(null, null))
                    || !java.util.Objects.equals(current.getCustomOverrides(), operation.getRequestedSelection())
                    || current.getCurrentPrice().compareTo(instruction.getOldTotal()) != 0
                    || !operation.getTargetSnapshot().withEffectivePeriod(null, null).equals(instruction.getTargetSnapshot().withEffectivePeriod(null, null))
                    || !current.getAccount().isActive() || current.isCancelAtPeriodEnd()
                    || (current.getStatus() == SubscriptionStatus.SUSPENDED
                        && current.getSuspensionCause() != com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause.COLLECTION)) {
                return markNeedsAttention(operation, "Reviewed price-only terms changed before activation.");
            }
        }
        boolean sameTermsRenewal = operation.getRequestOrigin() == com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeOrigin.SYSTEM
                && operation.getBeforeSnapshot().withEffectivePeriod(null, null).equals(operation.getTargetSnapshot().withEffectivePeriod(null, null));
        boolean retainedRenewal = sameTermsRenewal || operation.getRepricingItemId() != null;
        if (!retainedRenewal && !operation.getTargetPlan().isActive()) {
            return markNeedsAttention(operation, "The target Plan is no longer active.");
        }
        String unavailableItem = retainedRenewal ? null : unavailableCommercialItem(operation);
        if (unavailableItem != null) {
            return markNeedsAttention(operation, unavailableItem);
        }
        var conflicts = impactAnalyzer.analyze(accountId, current, operation.getTargetSnapshot());
        if (!conflicts.isEmpty()) {
            return markNeedsAttention(operation, conflicts.stream()
                    .map(conflict -> conflict.message())
                    .collect(Collectors.joining(" ")));
        }

        var specialAgreement = specialAgreements.find(operation.getId());
        var period = specialAgreement
                .map(agreement -> periodCalculator.exact(
                        agreement.getStartsAt(), agreement.getEndsAt()))
                .orElseGet(() -> periodCalculator.recurring(
                        operation.getTargetSnapshot().billingCycle(), startsAt));
        operation.setEffectiveAt(period.startsAt());
        operation.setTargetSnapshot(operation.getTargetSnapshot()
                .withEffectivePeriod(period.startsAt(), period.endsAt()));

        var usable = subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING,
                        SubscriptionStatus.PAST_DUE, SubscriptionStatus.SUSPENDED));
        usable.forEach(lifecycleManager::closeForReplacement);
        subscriptionRepository.saveAllAndFlush(usable);

        Subscription replacement = new Subscription();
        replacement.setAccount(current.getAccount());
        if (sameTermsRenewal && operation.getRepricingItemId() == null) {
            replacement.setCommercialTermsId(current.termsIdentity());
        }
        if (retainedRenewal) replacement.setContentEvidenceId(current.getContentEvidenceId());
        replacement.setPlan(operation.getTargetPlan());
        replacement.setCustomOverrides(operation.getRequestedSelection());
        replacement.setEntitlementSnapshot(operation.getTargetSnapshot());
        // A fixed-term agreement's charge is the reviewed contractual total, not the catalogue's
        // recurring-cycle price. The completion instruction installs the reviewed follow-on or
        // restores the previous recurring amount before ordinary renewal processing runs.
        replacement.setCurrentMoney(specialAgreement.isPresent()
                ? specialAgreement.get().agreedMoney()
                : billingCalculator.calculateMoney(replacement));
        lifecycleManager.initialize(replacement, SubscriptionStatus.ACTIVE, period);
        replacement = subscriptionRepository.saveAndFlush(replacement);
        lifecycleManager.recordOpenPeriod(replacement);

        operation.setResultSubscription(replacement);
        operation.setStatus(SubscriptionChangeStatus.APPLIED);
        operation.setAttentionReason(null);
        SubscriptionChangeOperation saved = operationRepository.save(operation);
        specialAgreements.operationApplied(saved, replacement);
        updateRepricing(saved, com.hiveapp.platform.client.plan.dto.RepricingModels.State.APPLIED, null);
        return saved;
    }

    private String unavailableCommercialItem(SubscriptionChangeOperation operation) {
        for (var snapshot : operation.getTargetSnapshot().addOns()) {
            // Existing subscribers retain the exact commercial snapshot they already bought.
            // Requiring the catalogue entry to remain active here would turn an intentional
            // "no new sales" lifecycle transition into an involuntary removal on any later
            // subscription change. Only newly selected or changed items are revalidated.
            if (operation.getBeforeSnapshot().addOns().contains(snapshot)) {
                continue;
            }
            var current = addOnRepository.findByCode(snapshot.code()).orElse(null);
            if (current == null || current.getStatus() != AddOnStatus.ACTIVE
                    || current.getDefinitionVersion() != snapshot.definitionVersion()) {
                return "AddOn " + snapshot.code() + " is unavailable or changed after the request.";
            }
        }
        for (var snapshot : operation.getTargetSnapshot().quotaPackages()) {
            // Equality includes quantity, capacity, price and definition version. Increasing
            // an inactive package therefore remains a new commercial selection and is denied;
            // only the exact already-held purchase may pass through unchanged.
            if (operation.getBeforeSnapshot().quotaPackages().contains(snapshot)) {
                continue;
            }
            var current = quotaPackageRepository.findByCode(snapshot.code()).orElse(null);
            if (current == null || current.getStatus() != QuotaPackageStatus.ACTIVE
                    || current.getDefinitionVersion() != snapshot.definitionVersion()) {
                return "Quota package " + snapshot.code() + " is unavailable or changed after the request.";
            }
        }
        return null;
    }

    private SubscriptionChangeOperation markNeedsAttention(
            SubscriptionChangeOperation operation,
            String reason
    ) {
        operation.setStatus(SubscriptionChangeStatus.NEEDS_ATTENTION);
        operation.setAttentionReason(reason);
        SubscriptionChangeOperation saved = operationRepository.save(operation);
        specialAgreements.operationNeedsAttention(saved, reason);
        updateRepricing(saved, com.hiveapp.platform.client.plan.dto.RepricingModels.State.CONFLICT, "ACTIVATION_CONFLICT");
        return saved;
    }

    private void updateRepricing(SubscriptionChangeOperation operation,
            com.hiveapp.platform.client.plan.dto.RepricingModels.State status, String blocker) {
        if (operation.getRepricingItemId() == null) return;
        repricingItems.findById(operation.getRepricingItemId()).ifPresent(item -> {
            item.setStatus(status); item.setBlocker(blocker); repricingItems.save(item);
        });
    }
}
