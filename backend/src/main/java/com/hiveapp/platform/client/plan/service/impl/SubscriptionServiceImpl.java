package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeConflict;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.client.plan.service.BillingConfigurationValidator;
import com.hiveapp.platform.client.plan.service.BillingCalculator;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotFactory;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.client.plan.service.SubscriptionUsageService;
import com.hiveapp.platform.registry.definition.ClientSubscriptionFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaOverride;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = ClientSubscriptionFeature.KEY, description = "Subscription Management")
public class SubscriptionServiceImpl extends ClientWorkspaceFeatureService implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final AccountRepository accountRepository;
    private final BillingCalculator billingCalculator;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotFactory subscriptionSnapshotFactory;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;
    private final BillingConfigurationValidator billingConfigurationValidator;
    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;
    private final SubscriptionUsageService subscriptionUsageService;

    @Override
    protected FeatureDefinition featureDefinition() {
        return ClientSubscriptionFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "View my subscription")
    public Subscription getSubscription(UUID accountId) {
        return subscriptionRepository.findActiveByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "catalog", description = "View subscription plan catalog")
    public ClientPlanCatalogResponse catalog(UUID accountId) {
        Subscription current = getSubscription(accountId);
        SubscriptionOverrides currentOverrides = subscriptionOverrideReader.read(current.getCustomOverrides());
        Map<String, FeatureDefinition> definitions = featureDefinitionCollectorProvider.getObject().collectByCode();
        Map<String, Long> usage = usageByQuotaSlot(accountId, definitions);

        var plans = planRepository.findAll().stream()
                .filter(Plan::isActive)
                .sorted(Comparator.comparing(Plan::getPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Plan::getCode))
                .map(plan -> toCatalogPlan(plan, current, definitions, usage))
                .toList();

        return new ClientPlanCatalogResponse(
                new ClientPlanCatalogResponse.CurrentSubscription(
                        current.getId(),
                        current.getPlan().getCode(),
                        current.getStatus(),
                        current.getCurrentPrice(),
                        current.getCurrentPeriodEnd(),
                        currentOverrides.addedFeatures(),
                        currentOverrides.quotaOverrides()),
                plans);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview", description = "Preview a subscription change")
    public SubscriptionChangePreviewResponse previewChange(UUID accountId, SubscriptionChangeRequest request) {
        Subscription current = getSubscription(accountId);
        Plan targetPlan = requireActivePlan(request.targetPlanCode());
        ChangeSelection selection = validateSelection(targetPlan, request);
        SubscriptionEntitlementSnapshot targetSnapshot = subscriptionSnapshotFactory.fromPlan(
                targetPlan, selection.addOnFeatureCodes());
        List<SubscriptionChangeConflict> conflicts = findConflicts(accountId, current, targetSnapshot, selection);
        BigDecimal previewPrice = previewPrice(current, targetPlan, targetSnapshot, selection);

        return new SubscriptionChangePreviewResponse(
                current.getPlan().getCode(),
                targetPlan.getCode(),
                current.getCurrentPrice(),
                previewPrice,
                conflicts.isEmpty(),
                targetSnapshot.features().stream()
                        .map(feature -> feature.featureCode())
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                effectiveQuotaLimits(targetSnapshot, selection.quotaOverrides()),
                selection.addOnFeatureCodes(),
                selection.quotaOverrides(),
                conflicts);
    }

    @Override
    @Transactional
    @PermissionNode(key = "apply", description = "Apply a subscription change")
    public SubscriptionChangeApplyResponse applyChange(UUID accountId, SubscriptionChangeRequest request) {
        accountRepository.findByIdForSubscriptionUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        SubscriptionChangePreviewResponse preview = previewChange(accountId, request);
        if (!preview.immediateAllowed()) {
            throw new InvalidStateException("Subscription change cannot be applied until conflicts are resolved.");
        }

        Subscription current = getSubscription(accountId);
        ChangeSelection selection = validateSelection(requireActivePlan(request.targetPlanCode()), request);
        if (isNoOp(current, preview, selection)) {
            throw new InvalidStateException("Requested subscription change does not modify the current subscription.");
        }

        var targetPlan = planRepository.findByCode(request.targetPlanCode()).orElseThrow();
        var targetSnapshot = subscriptionSnapshotFactory.fromPlan(targetPlan, selection.addOnFeatureCodes());
        var account = current.getAccount();

        var usableSubscriptions = subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING));
        usableSubscriptions.forEach(subscription -> subscription.setStatus(SubscriptionStatus.CANCELLED));
        subscriptionRepository.saveAllAndFlush(usableSubscriptions);

        Subscription replacement = new Subscription();
        replacement.setAccount(account);
        replacement.setPlan(targetPlan);
        replacement.setStatus(SubscriptionStatus.ACTIVE);
        replacement.setCustomOverrides(subscriptionOverrideReader.write(
                new SubscriptionOverrides(selection.addOnFeatureCodes(), selection.quotaOverrides())));
        replacement.setEntitlementSnapshot(subscriptionSnapshotReader.write(targetSnapshot));
        replacement.setCurrentPrice(billingCalculator.calculate(replacement));
        Subscription saved = subscriptionRepository.saveAndFlush(replacement);

        return new SubscriptionChangeApplyResponse(toDto(saved), preview);
    }

    @Override
    @Transactional
    public Subscription createSubscription(UUID accountId, String planCode) {
        var account = accountRepository.findByIdForSubscriptionUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "code", planCode));
        if (!plan.isActive()) {
            throw new InvalidStateException("Inactive plans cannot be assigned to an account.");
        }

        var usableSubscriptions = subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)
        );
        boolean alreadyActiveOnPlan = usableSubscriptions.stream()
                .anyMatch(subscription -> subscription.getStatus() == SubscriptionStatus.ACTIVE
                        && subscription.getPlan().getCode().equals(plan.getCode()));
        if (alreadyActiveOnPlan) {
            throw new InvalidStateException("Account is already subscribed to plan " + plan.getCode() + ".");
        }
        usableSubscriptions.forEach(subscription -> subscription.setStatus(SubscriptionStatus.CANCELLED));
        subscriptionRepository.saveAllAndFlush(usableSubscriptions);

        Subscription sub = new Subscription();
        sub.setAccount(account);
        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCustomOverrides(subscriptionOverrideReader.write(SubscriptionOverrides.empty()));
        sub.setEntitlementSnapshot(subscriptionSnapshotReader.write(subscriptionSnapshotFactory.fromPlan(plan)));
        sub.setCurrentPrice(billingCalculator.calculate(sub));
        return subscriptionRepository.saveAndFlush(sub);
    }

    @Override
    @Transactional
    public Subscription updateOverrides(UUID accountId,
                                        Set<String> featureCodes,
                                        List<QuotaOverride> quotaOverrides) {
        var sub = getSubscription(accountId);
        billingConfigurationValidator.validateSubscriptionOverrides(featureCodes, quotaOverrides);

        var overrides = new SubscriptionOverrides(
                featureCodes != null ? featureCodes : Set.of(),
                quotaOverrides != null ? quotaOverrides : List.of()
        );
        sub.setCustomOverrides(subscriptionOverrideReader.write(overrides));
        sub.setCurrentPrice(billingCalculator.calculate(sub));
        return subscriptionRepository.save(sub);
    }

    private ClientPlanCatalogResponse.CatalogPlan toCatalogPlan(
            Plan plan,
            Subscription current,
            Map<String, FeatureDefinition> definitions,
            Map<String, Long> usage
    ) {
        var features = planFeatureRepository.findAllByPlanId(plan.getId()).stream()
                .filter(planFeature -> isSelfServiceAvailable(planFeature, definitions))
                .sorted(Comparator.comparing(planFeature -> definitions.get(planFeature.getFeature().getCode()).sortOrder()))
                .map(planFeature -> toCatalogFeature(planFeature, definitions.get(planFeature.getFeature().getCode()), usage))
                .toList();
        return new ClientPlanCatalogResponse.CatalogPlan(
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getBillingCycle(),
                current.getPlan().getCode().equals(plan.getCode()),
                features);
    }

    private ClientPlanCatalogResponse.CatalogFeature toCatalogFeature(
            PlanFeature planFeature,
            FeatureDefinition definition,
            Map<String, Long> usage
    ) {
        Map<String, QuotaLimitEntry> quotasByResource = safeQuotaConfigs(planFeature).stream()
                .collect(Collectors.toMap(QuotaLimitEntry::resource, Function.identity(), (left, right) -> left));
        var quotas = definition.quotaSlots().stream()
                .map(slot -> {
                    QuotaLimitEntry limit = quotasByResource.get(slot.resource());
                    Long value = limit != null ? limit.limit() : null;
                    BigDecimal pricePerUnit = limit != null ? limit.pricePerUnit() : null;
                    return new ClientPlanCatalogResponse.CatalogQuota(
                            slot.resource(),
                            slot.unit(),
                            slot,
                            value,
                            value == null,
                            pricePerUnit,
                            usage.getOrDefault(definition.code() + ":" + slot.resource(), 0L));
                })
                .toList();
        return new ClientPlanCatalogResponse.CatalogFeature(
                definition.code(),
                definition.displayName(),
                definition.description(),
                planFeature.getAddOnPrice() == null,
                planFeature.getAddOnPrice() != null,
                planFeature.getAddOnPrice(),
                quotas);
    }

    private ChangeSelection validateSelection(Plan targetPlan, SubscriptionChangeRequest request) {
        Set<String> addOns = request.addOnFeatureCodes() == null ? Set.of() : new LinkedHashSet<>(request.addOnFeatureCodes());
        List<QuotaOverride> quotas = request.quotaOverrides() == null ? List.of() : List.copyOf(request.quotaOverrides());
        billingConfigurationValidator.validateSubscriptionOverrides(addOns, quotas);

        Map<String, FeatureDefinition> definitions = featureDefinitionCollectorProvider.getObject().collectByCode();
        Map<String, PlanFeature> planFeatures = planFeatureRepository.findAllByPlanId(targetPlan.getId()).stream()
                .filter(planFeature -> isSelfServiceAvailable(planFeature, definitions))
                .collect(Collectors.toMap(planFeature -> planFeature.getFeature().getCode(), Function.identity()));

        for (String featureCode : addOns) {
            PlanFeature planFeature = planFeatures.get(featureCode);
            if (planFeature == null) {
                throw new InvalidRequestException("Feature " + featureCode + " is not available for plan " + targetPlan.getCode() + ".");
            }
            if (planFeature.getAddOnPrice() == null) {
                throw new InvalidRequestException("Feature " + featureCode + " is already included in plan " + targetPlan.getCode() + ".");
            }
        }

        Set<String> entitledFeatureCodes = planFeatures.values().stream()
                .filter(planFeature -> planFeature.getAddOnPrice() == null
                        || addOns.contains(planFeature.getFeature().getCode()))
                .map(planFeature -> planFeature.getFeature().getCode())
                .collect(Collectors.toSet());

        for (QuotaOverride quota : quotas) {
            if (!entitledFeatureCodes.contains(quota.featureCode())) {
                throw new InvalidRequestException(
                        "Quota " + quota.featureCode() + "." + quota.resource()
                                + " is not available in the selected plan entitlement.");
            }
            PlanFeature planFeature = planFeatures.get(quota.featureCode());
            boolean knownQuota = safeQuotaConfigs(planFeature).stream()
                    .anyMatch(limit -> limit.resource().equals(quota.resource()));
            if (!knownQuota) {
                throw new InvalidRequestException(
                        "Quota " + quota.featureCode() + "." + quota.resource()
                                + " is not configurable for plan " + targetPlan.getCode() + ".");
            }
        }

        return new ChangeSelection(addOns, quotas);
    }

    private List<SubscriptionChangeConflict> findConflicts(
            UUID accountId,
            Subscription current,
            SubscriptionEntitlementSnapshot targetSnapshot,
            ChangeSelection selection
    ) {
        Set<String> currentFeatures = activeFeatureCodes(current);
        Set<String> targetFeatures = targetSnapshot.features().stream()
                .map(feature -> feature.featureCode())
                .collect(Collectors.toSet());
        List<SubscriptionChangeConflict> conflicts = new ArrayList<>();

        currentFeatures.stream()
                .filter(featureCode -> !targetFeatures.contains(featureCode))
                .sorted()
                .forEach(featureCode -> {
                    long usage = subscriptionUsageService.featureUsage(accountId, featureCode);
                    if (usage > 0) {
                        conflicts.add(new SubscriptionChangeConflict(
                                "FEATURE_IN_USE",
                                featureCode,
                                null,
                                usage,
                                null,
                                "Feature " + featureCode + " is still in use by this account."));
                    }
                });

        for (QuotaLimitEntry limit : effectiveQuotaLimits(targetSnapshot, selection.quotaOverrides())) {
            if (limit.limit() == null) {
                continue;
            }
            String featureCode = quotaOwner(targetSnapshot, limit.resource()).orElse(null);
            if (featureCode == null) {
                continue;
            }
            long usage = subscriptionUsageService.currentUsage(accountId, featureCode, limit.resource());
            if (usage > limit.limit()) {
                conflicts.add(new SubscriptionChangeConflict(
                        "QUOTA_BELOW_USAGE",
                        featureCode,
                        limit.resource(),
                        usage,
                        limit.limit(),
                        "Current usage for " + featureCode + "." + limit.resource()
                                + " is " + usage + ", above requested limit " + limit.limit() + "."));
            }
        }

        return conflicts;
    }

    private BigDecimal previewPrice(
            Subscription current,
            Plan targetPlan,
            SubscriptionEntitlementSnapshot targetSnapshot,
            ChangeSelection selection
    ) {
        Subscription preview = new Subscription();
        preview.setAccount(current.getAccount());
        preview.setPlan(targetPlan);
        preview.setStatus(SubscriptionStatus.ACTIVE);
        preview.setEntitlementSnapshot(subscriptionSnapshotReader.write(targetSnapshot));
        preview.setCustomOverrides(subscriptionOverrideReader.write(
                new SubscriptionOverrides(selection.addOnFeatureCodes(), selection.quotaOverrides())));
        return billingCalculator.calculate(preview);
    }

    private boolean isNoOp(Subscription current, SubscriptionChangePreviewResponse preview, ChangeSelection selection) {
        SubscriptionOverrides currentOverrides = subscriptionOverrideReader.read(current.getCustomOverrides());
        return current.getPlan().getCode().equals(preview.targetPlanCode())
                && Objects.equals(currentOverrides.addedFeatures(), selection.addOnFeatureCodes())
                && Objects.equals(currentOverrides.quotaOverrides(), selection.quotaOverrides());
    }

    private List<QuotaLimitEntry> effectiveQuotaLimits(
            SubscriptionEntitlementSnapshot snapshot,
            List<QuotaOverride> quotaOverrides
    ) {
        Map<String, QuotaOverride> overridesByKey = quotaOverrides.stream()
                .collect(Collectors.toMap(
                        override -> override.featureCode() + ":" + override.resource(),
                        Function.identity(),
                        (left, right) -> right));
        List<QuotaLimitEntry> limits = new ArrayList<>();
        for (var feature : snapshot.features()) {
            for (QuotaLimitEntry base : feature.quotaConfigs()) {
                QuotaOverride override = overridesByKey.get(feature.featureCode() + ":" + base.resource());
                limits.add(override == null
                        ? base
                        : new QuotaLimitEntry(base.resource(), override.limit(), base.pricePerUnit()));
            }
        }
        return limits;
    }

    private Optional<String> quotaOwner(SubscriptionEntitlementSnapshot snapshot, String resource) {
        return snapshot.features().stream()
                .filter(feature -> feature.quotaConfigs().stream()
                        .anyMatch(limit -> limit.resource().equals(resource)))
                .map(feature -> feature.featureCode())
                .findFirst();
    }

    private Set<String> activeFeatureCodes(Subscription subscription) {
        return subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot())
                .map(snapshot -> snapshot.features().stream()
                        .map(feature -> feature.featureCode())
                        .collect(Collectors.toSet()))
                .orElseGet(() -> planFeatureRepository.findAllByPlanId(subscription.getPlan().getId()).stream()
                        .filter(planFeature -> planFeature.getAddOnPrice() == null)
                        .map(planFeature -> planFeature.getFeature().getCode())
                        .collect(Collectors.toSet()));
    }

    private Plan requireActivePlan(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            throw new InvalidRequestException("Target plan code is required.");
        }
        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "code", planCode));
        if (!plan.isActive()) {
            throw new InvalidStateException("Inactive plans cannot be selected.");
        }
        return plan;
    }

    private Map<String, Long> usageByQuotaSlot(UUID accountId, Map<String, FeatureDefinition> definitions) {
        return definitions.values().stream()
                .flatMap(definition -> definition.quotaSlots().stream()
                        .map(slot -> Map.entry(
                                definition.code() + ":" + slot.resource(),
                                subscriptionUsageService.currentUsage(accountId, definition.code(), slot.resource()))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<QuotaLimitEntry> safeQuotaConfigs(PlanFeature planFeature) {
        return planFeature.getQuotaConfigs() != null ? planFeature.getQuotaConfigs() : List.of();
    }

    private boolean isSelfServiceAvailable(PlanFeature planFeature, Map<String, FeatureDefinition> definitions) {
        FeatureDefinition definition = definitions.get(planFeature.getFeature().getCode());
        if (definition == null || !definition.planAssignable()) {
            return false;
        }
        return planFeature.getFeature().isActive()
                && (planFeature.getFeature().getStatus() == FeatureStatus.PUBLIC
                || planFeature.getFeature().getStatus() == FeatureStatus.BETA);
    }

    private SubscriptionDto toDto(Subscription subscription) {
        return new SubscriptionDto(
                subscription.getId(),
                new SubscriptionDto.PlanSummaryDto(
                        subscription.getPlan().getCode(),
                        subscription.getPlan().getName(),
                        subscription.getPlan().getPrice()),
                subscription.getStatus(),
                subscription.getCurrentPrice(),
                subscription.getCurrentPeriodEnd());
    }

    private record ChangeSelection(
            Set<String> addOnFeatureCodes,
            List<QuotaOverride> quotaOverrides
    ) {}
}
