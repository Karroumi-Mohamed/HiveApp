package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeConflict;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.EffectiveQuotaLimit;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.service.BillingCalculator;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotFactory;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.client.plan.service.SubscriptionImpactAnalyzer;
import com.hiveapp.platform.client.plan.service.SubscriptionLifecycleManager;
import com.hiveapp.platform.client.plan.service.SubscriptionPeriodCalculator;
import com.hiveapp.platform.client.plan.service.SubscriptionCheckoutService;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeActivationService;
import com.hiveapp.platform.client.plan.service.ProductPriceResolver;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.registry.definition.ClientSubscriptionFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaLimitMode;
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
@PermissionNode(key = ClientSubscriptionFeature.KEY, description = "Subscription Management", guard = PermissionNode.Guard.ON)
public class SubscriptionServiceImpl extends ClientWorkspaceFeatureService implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final AddOnRepository addOnRepository;
    private final AddOnFeatureRepository addOnFeatureRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final AccountRepository accountRepository;
    private final BillingCalculator billingCalculator;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotFactory subscriptionSnapshotFactory;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;
    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;
    private final SubscriptionImpactAnalyzer subscriptionImpactAnalyzer;
    private final SubscriptionLifecycleManager subscriptionLifecycleManager;
    private final SubscriptionPeriodCalculator subscriptionPeriodCalculator;
    private final SubscriptionChangeOperationRepository subscriptionChangeOperationRepository;
    private final SubscriptionCheckoutService subscriptionCheckoutService;
    private final SubscriptionChangeActivationService subscriptionChangeActivationService;
    private final ProductPriceResolver productPriceResolver;

    @Override
    protected FeatureDefinition featureDefinition() {
        return ClientSubscriptionFeature.definition();
    }

    /**
     * Internal cross-service lookup returning the entity. Unguarded on purpose: its only caller
     * is the admin subscription surface, which carries its own guard. The client-facing guard
     * lives on {@link #getMySubscription(UUID)}.
     */
    @Override
    @Transactional(readOnly = true)
    public Subscription getSubscription(UUID accountId) {
        return requireUsableSubscription(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "View my subscription")
    public SubscriptionDto getMySubscription(UUID accountId) {
        return subscriptionMapper.toDto(requireUsableSubscription(accountId));
    }

    /**
     * The single definition of "the Account's usable subscription" — ACTIVE, else TRIALING.
     * Shared so the entity and read-model surfaces cannot drift apart.
     */
    private Subscription requireUsableSubscription(UUID accountId) {
        return subscriptionRepository.findActiveByAccountId(accountId)
                .or(() -> subscriptionRepository.findByAccountIdAndStatus(
                        accountId, SubscriptionStatus.TRIALING))
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
        Map<PriceOwnerKey, List<ProductPrice>> catalogPrices = productPriceResolver.availableCatalogPrices().stream()
                .collect(Collectors.groupingBy(
                        price -> new PriceOwnerKey(price.getOwnerType(), price.ownerId()),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));

        var plans = planRepository.findAll().stream()
                .filter(Plan::isActive)
                .sorted(Comparator.comparing(Plan::getPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Plan::getCode))
                .map(plan -> toCatalogPlan(plan, current, definitions, usage, catalogPrices))
                .toList();

        return new ClientPlanCatalogResponse(
                new ClientPlanCatalogResponse.CurrentSubscription(
                        current.getId(),
                        current.getPlan().getCode(),
                        current.getStatus(),
                        current.getCurrentPrice(),
                        current.getCurrentPriceCurrencyCode(),
                        current.getCurrentPeriodStart(),
                        current.getCurrentPeriodEnd(),
                        current.isCancelAtPeriodEnd(),
                        currentOverrides.addOnCodes(),
                        currentOverrides.quotaPackages()),
                plans);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview", description = "Preview a subscription change")
    public SubscriptionChangePreviewResponse previewChange(UUID accountId, SubscriptionChangeRequest request) {
        Subscription current = getSubscription(accountId);
        Plan targetPlan = requireActivePlan(request.targetPlanCode());
        ProductPrice planPrice = productPriceResolver.resolvePlan(targetPlan, request.planPriceSelection());
        requireSameSubscriptionCurrency(current, planPrice);
        ChangeSelection selection = validateSelection(targetPlan, request, planPrice, null);
        SubscriptionEntitlementSnapshot targetSnapshot = subscriptionSnapshotFactory.fromPlan(
                targetPlan, selection.addOnCodes(), selection.quotaPackages(), planPrice);
        List<SubscriptionChangeConflict> conflicts = subscriptionImpactAnalyzer.analyze(
                accountId, current, targetSnapshot);
        Money previewPrice = previewPrice(current, targetPlan, targetSnapshot, selection);

        return new SubscriptionChangePreviewResponse(
                current.getPlan().getCode(),
                targetPlan.getCode(),
                current.getCurrentPrice(),
                previewPrice.amount(),
                previewPrice.currencyCode(),
                conflicts.isEmpty(),
                targetSnapshot.features().stream()
                        .map(feature -> feature.featureCode())
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                subscriptionImpactAnalyzer.effectiveQuotaLimits(targetSnapshot),
                selection.addOnCodes(),
                selection.quotaPackages(),
                conflicts);
    }

    @Override
    @Transactional
    @PermissionNode(key = "apply", description = "Apply a subscription change")
    public SubscriptionChangeApplyResponse applyChange(
            UUID accountId,
            UUID actorUserId,
            SubscriptionChangeRequest request
    ) {
        accountRepository.findByIdForSubscriptionUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        SubscriptionChangePreviewResponse preview = previewChange(accountId, request);
        if (request.effectiveTiming() == SubscriptionChangeTiming.IMMEDIATE && !preview.immediateAllowed()) {
            throw new InvalidStateException("Subscription change cannot be applied until conflicts are resolved.");
        }

        Subscription current = getSubscription(accountId);
        Plan selectedPlan = requireActivePlan(request.targetPlanCode());
        ProductPrice selectedPlanPrice = productPriceResolver.resolvePlan(
                selectedPlan, request.planPriceSelection());
        ChangeSelection selection = validateSelection(selectedPlan, request, selectedPlanPrice, null);
        if (isNoOp(current, preview, selection)) {
            throw new InvalidStateException("Requested subscription change does not modify the current subscription.");
        }

        var targetPlan = planRepository.findByCode(request.targetPlanCode()).orElseThrow();
        var targetSnapshot = subscriptionSnapshotFactory.fromPlan(
                targetPlan, selection.addOnCodes(), selection.quotaPackages(), selectedPlanPrice);
        SubscriptionOverrides requestedSelection = new SubscriptionOverrides(
                selection.addOnCodes(), selection.quotaPackages());
        SubscriptionChangeOperation operation = newChangeOperation(
                current, targetPlan, requestedSelection, targetSnapshot, request.effectiveTiming());

        subscriptionChangeOperationRepository.findTopByAccountIdAndStatusIn(
                        accountId,
                        List.of(SubscriptionChangeStatus.PENDING,
                                SubscriptionChangeStatus.AWAITING_CONFIRMATION))
                .ifPresent(existing -> {
                    throw new InvalidStateException(
                            "Account already has an outstanding subscription change. Cancel it before creating another.");
                });

        Money targetPrice = Money.of(preview.previewPrice(), preview.currencyCode());
        if (targetPrice.amount().signum() > 0) {
            operation.setStatus(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
            SubscriptionChangeOperation savedOperation = subscriptionChangeOperationRepository.saveAndFlush(operation);
            subscriptionCheckoutService.initiate(savedOperation, targetPrice, actorUserId);
            return new SubscriptionChangeApplyResponse(
                    toDto(current), preview, toOperationDto(savedOperation));
        }

        if (request.effectiveTiming() == SubscriptionChangeTiming.AT_RENEWAL) {
            SubscriptionChangeOperation savedOperation = subscriptionChangeOperationRepository.saveAndFlush(operation);
            return new SubscriptionChangeApplyResponse(toDto(current), preview, toOperationDto(savedOperation));
        }

        SubscriptionChangeOperation savedOperation = subscriptionChangeOperationRepository.saveAndFlush(operation);
        savedOperation = subscriptionChangeActivationService.activate(
                savedOperation, operation.getTargetSnapshot().effectiveFrom());
        return new SubscriptionChangeApplyResponse(
                toDto(savedOperation.getResultSubscription()), preview, toOperationDto(savedOperation));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_changes", description = "View subscription change operations")
    public List<SubscriptionChangeOperationDto> listChangeOperations(UUID accountId) {
        return subscriptionChangeOperationRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::toOperationDto)
                .toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = "cancel_change", description = "Cancel a pending renewal subscription change")
    public SubscriptionChangeOperationDto cancelPendingChange(UUID accountId, UUID operationId) {
        SubscriptionChangeOperation operation = subscriptionChangeOperationRepository
                .findByIdAndAccountId(operationId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionChangeOperation", "id", operationId));
        if (operation.getStatus() != SubscriptionChangeStatus.PENDING
                && operation.getStatus() != SubscriptionChangeStatus.AWAITING_CONFIRMATION) {
            throw new InvalidStateException("Only pending or awaiting-confirmation changes can be cancelled.");
        }
        subscriptionCheckoutService.cancelFor(operation);
        operation.setStatus(SubscriptionChangeStatus.CANCELLED);
        return toOperationDto(subscriptionChangeOperationRepository.save(operation));
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
        usableSubscriptions.forEach(subscriptionLifecycleManager::closeForReplacement);
        subscriptionRepository.saveAllAndFlush(usableSubscriptions);

        Subscription sub = new Subscription();
        sub.setAccount(account);
        sub.setPlan(plan);
        sub.setCustomOverrides(subscriptionOverrideReader.write(SubscriptionOverrides.empty()));
        ProductPrice selectedPrice = productPriceResolver.resolvePlan(plan, null);
        sub.setEntitlementSnapshot(subscriptionSnapshotReader.write(
                subscriptionSnapshotFactory.fromPlan(plan, Set.of(), List.of(), selectedPrice)));
        sub.setCurrentMoney(billingCalculator.calculateMoney(sub));
        subscriptionLifecycleManager.initialize(
                sub, SubscriptionStatus.ACTIVE,
                subscriptionPeriodCalculator.recurring(selectedPrice.getBillingCycle()));
        Subscription saved = subscriptionRepository.saveAndFlush(sub);
        subscriptionLifecycleManager.recordOpenPeriod(saved);
        return saved;
    }

    @Override
    @Transactional
    public Subscription createTrial(UUID accountId, String planCode, int trialDays) {
        var account = accountRepository.findByIdForSubscriptionUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        Plan plan = requireActivePlan(planCode);
        var usableSubscriptions = subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING));
        usableSubscriptions.forEach(subscriptionLifecycleManager::closeForReplacement);
        subscriptionRepository.saveAllAndFlush(usableSubscriptions);

        Subscription trial = new Subscription();
        trial.setAccount(account);
        trial.setPlan(plan);
        trial.setCustomOverrides(SubscriptionOverrides.empty());
        ProductPrice selectedPrice = productPriceResolver.resolvePlan(plan, null);
        trial.setEntitlementSnapshot(subscriptionSnapshotFactory.fromPlan(
                plan, Set.of(), List.of(), selectedPrice));
        trial.setCurrentMoney(Money.zero(selectedPrice.getCurrencyCode()));
        subscriptionLifecycleManager.initialize(
                trial, SubscriptionStatus.TRIALING, subscriptionPeriodCalculator.trial(trialDays));
        Subscription saved = subscriptionRepository.saveAndFlush(trial);
        subscriptionLifecycleManager.recordOpenPeriod(saved);
        return saved;
    }

    @Override
    @Transactional
    public Subscription updateOverrides(UUID accountId,
                                        Set<String> addOnCodes,
                                        List<QuotaPackageSelection> quotaPackages) {
        var sub = getSubscription(accountId);
        SubscriptionEntitlementSnapshot currentSnapshot = sub.getEntitlementSnapshot();
        ChangeSelection selection = validateSelection(sub.getPlan(),
                new SubscriptionChangeRequest(sub.getPlan().getCode(), addOnCodes, quotaPackages),
                null, currentSnapshot);
        var overrides = new SubscriptionOverrides(selection.addOnCodes(), selection.quotaPackages());
        sub.setCustomOverrides(subscriptionOverrideReader.write(overrides));
        sub.setEntitlementSnapshot(subscriptionSnapshotFactory.fromPlanPreservingPrices(
                        sub.getPlan(), selection.addOnCodes(), selection.quotaPackages(), currentSnapshot)
                .withEffectivePeriod(sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd()));
        sub.setCurrentMoney(billingCalculator.calculateMoney(sub));
        return subscriptionRepository.save(sub);
    }

    private ClientPlanCatalogResponse.CatalogPlan toCatalogPlan(
            Plan plan,
            Subscription current,
            Map<String, FeatureDefinition> definitions,
            Map<String, Long> usage,
            Map<PriceOwnerKey, List<ProductPrice>> catalogPrices
    ) {
        var features = planFeatureRepository.findAllByPlanId(plan.getId()).stream()
                .filter(planFeature -> isSelfServiceAvailable(planFeature, definitions))
                .sorted(Comparator.comparing(planFeature -> definitions.get(planFeature.getFeature().getCode()).sortOrder()))
                .map(planFeature -> toCatalogFeature(planFeature, definitions.get(planFeature.getFeature().getCode()), usage))
                .toList();
        var compatibleAddOns = addOnRepository.findAll().stream()
                .filter(AddOn::isActive)
                .filter(addOn -> isAddOnCompatibleWithPlan(addOn, plan, definitions))
                .sorted(Comparator.comparing(AddOn::getCode))
                .toList();
        var addOns = compatibleAddOns.stream()
                .map(addOn -> new ClientPlanCatalogResponse.CatalogAddOn(
                        addOn.getCode(), addOn.getName(), addOn.getDescription(), addOn.getPrice(),
                        addOn.getCurrencyCode(), addOn.getBillingCycle(), addOn.getDefinitionVersion(),
                        addOn.getDependencyCodes(), addOn.getExclusionCodes(),
                        addOnFeatureRepository.findAllByAddOnId(addOn.getId()).stream()
                                .sorted(Comparator.comparing(feature -> feature.getFeature().getCode()))
                                .map(feature -> toCatalogAddOnFeature(feature, definitions, usage))
                                .toList(),
                        toCatalogPrices(catalogPrices.get(new PriceOwnerKey(
                                ProductPriceOwnerType.ADD_ON, addOn.getId())))))
                .toList();
        var quotaPackages = quotaPackageRepository.findAllByOrderByCodeAsc().stream()
                .filter(QuotaPackage::isActive)
                .filter(item -> isQuotaPackageCatalogAvailable(item, plan, compatibleAddOns, definitions))
                .map(item -> new ClientPlanCatalogResponse.CatalogQuotaPackage(
                        item.getCode(), item.getName(), item.getDescription(), item.getDefinitionVersion(),
                        item.getFeature().getCode(), item.getResource(), item.getCapacityPerUnit(),
                        item.getPrice(), item.getCurrencyCode(), item.getBillingCycle(), item.isRepeatable(),
                        item.getMaximumQuantity(), item.getAllowedPlanCodes(), item.getAllowedAddOnCodes(),
                        toCatalogPrices(catalogPrices.get(new PriceOwnerKey(
                                ProductPriceOwnerType.QUOTA_PACKAGE, item.getId())))))
                .toList();
        return new ClientPlanCatalogResponse.CatalogPlan(
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getCurrencyCode(),
                plan.getBillingCycle(),
                current.getPlan().getCode().equals(plan.getCode()),
                features,
                addOns,
                quotaPackages,
                toCatalogPrices(catalogPrices.get(new PriceOwnerKey(
                        ProductPriceOwnerType.PLAN, plan.getId()))));
    }

    private ClientPlanCatalogResponse.CatalogFeature toCatalogFeature(
            PlanFeature planFeature,
            FeatureDefinition definition,
            Map<String, Long> usage
    ) {
        Map<String, QuotaLimitEntry> quotasByResource = safeQuotaConfigs(planFeature).stream()
                .collect(Collectors.toMap(QuotaLimitEntry::resource, Function.identity(), (left, right) -> left));
        var quotas = definition.quotaSlots().stream()
                .filter(slot -> quotasByResource.containsKey(slot.resource()))
                .map(slot -> {
                    QuotaLimitEntry limit = quotasByResource.get(slot.resource());
                    return new ClientPlanCatalogResponse.CatalogQuota(
                            definition.code(),
                            slot,
                            limit.mode(),
                            limit.limit(),
                            limit.mode() == QuotaLimitMode.UNLIMITED,
                            usage.get(definition.code() + ":" + slot.resource()));
                })
                .toList();
        return new ClientPlanCatalogResponse.CatalogFeature(
                definition.code(),
                definition.displayName(),
                definition.description(),
                planFeature.getMode(),
                quotas);
    }

    private ClientPlanCatalogResponse.CatalogAddOnFeature toCatalogAddOnFeature(
            AddOnFeature addOnFeature,
            Map<String, FeatureDefinition> definitions,
            Map<String, Long> usage
    ) {
        FeatureDefinition definition = definitions.get(addOnFeature.getFeature().getCode());
        Map<String, QuotaLimitEntry> quotasByResource = addOnFeature.getQuotaConfigs().stream()
                .collect(Collectors.toMap(QuotaLimitEntry::resource, Function.identity(), (left, right) -> left));
        var quotas = definition.quotaSlots().stream()
                .filter(slot -> quotasByResource.containsKey(slot.resource()))
                .map(slot -> {
                    QuotaLimitEntry limit = quotasByResource.get(slot.resource());
                    return new ClientPlanCatalogResponse.CatalogQuota(
                            definition.code(), slot,
                            limit.mode(), limit.limit(), limit.mode() == QuotaLimitMode.UNLIMITED,
                            usage.get(definition.code() + ":" + slot.resource()));
                })
                .toList();
        return new ClientPlanCatalogResponse.CatalogAddOnFeature(
                definition.code(), definition.displayName(), definition.description(), quotas);
    }

    private ChangeSelection validateSelection(Plan targetPlan, SubscriptionChangeRequest request,
                                              ProductPrice planPrice,
                                              SubscriptionEntitlementSnapshot preservedPrices) {
        Set<String> addOnCodes = request.addOnCodes() == null ? Set.of() : new LinkedHashSet<>(request.addOnCodes());
        List<QuotaPackageSelection> packageSelections = request.quotaPackages() == null
                ? List.of()
                : List.copyOf(request.quotaPackages());
        if (packageSelections.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRequestException("Quota package selection cannot be null.");
        }
        Set<String> packageCodes = new LinkedHashSet<>();
        for (QuotaPackageSelection selection : packageSelections) {
            if (selection.packageCode() == null || selection.packageCode().isBlank()) {
                throw new InvalidRequestException("Quota package code is required.");
            }
            if (!packageCodes.add(selection.packageCode())) {
                throw new InvalidRequestException("Duplicate quota package selection: " + selection.packageCode());
            }
        }

        Map<String, FeatureDefinition> definitions = featureDefinitionCollectorProvider.getObject().collectByCode();
        Map<String, PlanFeature> planFeatures = planFeatureRepository.findAllByPlanId(targetPlan.getId()).stream()
                .filter(planFeature -> isSelfServiceAvailable(planFeature, definitions))
                .collect(Collectors.toMap(planFeature -> planFeature.getFeature().getCode(), Function.identity()));
        List<AddOn> selectedAddOns = addOnRepository.findAllByCodeIn(addOnCodes);
        if (selectedAddOns.size() != addOnCodes.size()) {
            throw new InvalidRequestException("One or more selected AddOns do not exist.");
        }
        Set<String> entitledFeatureCodes = planFeatures.values().stream()
                .filter(planFeature -> planFeature.getMode() == PlanFeatureMode.INCLUDED)
                .map(planFeature -> planFeature.getFeature().getCode())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (AddOn addOn : selectedAddOns) {
            validateSelectedAddOn(addOn, targetPlan, addOnCodes, planFeatures, entitledFeatureCodes, definitions);
        }

        SubscriptionEntitlementSnapshot baseSnapshot = preservedPrices == null
                ? subscriptionSnapshotFactory.fromPlan(targetPlan, addOnCodes, List.of(), planPrice)
                : subscriptionSnapshotFactory.fromPlanPreservingPrices(
                        targetPlan, addOnCodes, List.of(), preservedPrices);
        Map<String, List<QuotaLimitEntry>> quotaConfigs = baseSnapshot.features().stream()
                .collect(Collectors.toMap(
                        feature -> feature.featureCode(),
                        feature -> feature.quotaConfigs() != null ? feature.quotaConfigs() : List.of()));

        Map<String, QuotaPackage> packagesByCode = quotaPackageRepository.findAllByCodeIn(packageCodes).stream()
                .collect(Collectors.toMap(QuotaPackage::getCode, Function.identity()));
        if (packagesByCode.size() != packageCodes.size()) {
            throw new InvalidRequestException("One or more selected quota packages do not exist.");
        }
        for (QuotaPackageSelection selection : packageSelections) {
            validateQuotaPackageSelection(
                    packagesByCode.get(selection.packageCode()), selection, targetPlan, addOnCodes, quotaConfigs);
        }

        return new ChangeSelection(addOnCodes, packageSelections);
    }

    private Money previewPrice(
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
                new SubscriptionOverrides(selection.addOnCodes(), selection.quotaPackages())));
        return billingCalculator.calculateMoney(preview);
    }

    private boolean isNoOp(Subscription current, SubscriptionChangePreviewResponse preview, ChangeSelection selection) {
        SubscriptionOverrides currentOverrides = subscriptionOverrideReader.read(current.getCustomOverrides());
        return current.getPlan().getCode().equals(preview.targetPlanCode())
                && Objects.equals(currentOverrides.addOnCodes(), selection.addOnCodes())
                && Objects.equals(currentOverrides.quotaPackages(), selection.quotaPackages());
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

    private void requireSameSubscriptionCurrency(Subscription current, ProductPrice targetPrice) {
        String currentCurrency = current.getCurrentPriceCurrencyCode() != null
                ? current.getCurrentPriceCurrencyCode()
                : current.getPlan().getCurrencyCode();
        if (!currentCurrency.equals(targetPrice.getCurrencyCode())) {
            throw new InvalidRequestException(
                    "Subscription changes cannot convert " + currentCurrency + " to "
                            + targetPrice.getCurrencyCode() + ". Choose a plan in the current currency.");
        }
    }

    private Map<String, Long> usageByQuotaSlot(UUID accountId, Map<String, FeatureDefinition> definitions) {
        Map<String, Long> usage = new java.util.LinkedHashMap<>();
        definitions.values().forEach(definition -> definition.quotaSlots().forEach(slot -> {
            var measurement = subscriptionImpactAnalyzer.quotaUsage(
                    accountId, definition.code(), slot.resource());
            if (measurement.isPresent()) {
                usage.put(definition.code() + ":" + slot.resource(), measurement.getAsLong());
            }
        }));
        return Map.copyOf(usage);
    }

    private List<QuotaLimitEntry> safeQuotaConfigs(PlanFeature planFeature) {
        return planFeature.getQuotaConfigs() != null ? planFeature.getQuotaConfigs() : List.of();
    }

    private void validateQuotaPackageSelection(
            QuotaPackage item,
            QuotaPackageSelection selection,
            Plan plan,
            Set<String> selectedAddOnCodes,
            Map<String, List<QuotaLimitEntry>> quotaConfigs
    ) {
        if (!item.isActive()) {
            throw new InvalidRequestException("Quota package " + item.getCode() + " is not active.");
        }
        if (selection.quantity() <= 0
                || selection.quantity() > item.getMaximumQuantity()
                || (!item.isRepeatable() && selection.quantity() != 1)) {
            throw new InvalidRequestException(
                    "Quota package " + item.getCode() + " quantity must be between 1 and "
                            + item.getMaximumQuantity() + ".");
        }
        boolean ownedByPlan = item.getAllowedPlanCodes().contains(plan.getCode());
        boolean ownedBySelectedAddOn = item.getAllowedAddOnCodes().stream().anyMatch(selectedAddOnCodes::contains);
        if (!ownedByPlan && !ownedBySelectedAddOn) {
            throw new InvalidRequestException(
                    "Quota package " + item.getCode() + " is not available for the selected Plan and AddOns.");
        }
        QuotaLimitEntry included = quotaConfigs
                .getOrDefault(item.getFeature().getCode(), List.of()).stream()
                .filter(limit -> limit.resource().equals(item.getResource()))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException(
                        "Quota package " + item.getCode() + " has no included quota owner in the selection."));
        if (included.mode() != QuotaLimitMode.FINITE) {
            throw new InvalidRequestException(
                    "Quota package " + item.getCode() + " cannot increase an unlimited quota.");
        }
        try {
            Math.multiplyExact(item.getCapacityPerUnit(), selection.quantity());
        } catch (ArithmeticException exception) {
            throw new InvalidRequestException("Quota package capacity exceeds the supported range.");
        }
    }

    private boolean isQuotaPackageCatalogAvailable(
            QuotaPackage item,
            Plan plan,
            List<AddOn> compatibleAddOns,
            Map<String, FeatureDefinition> definitions
    ) {
        FeatureDefinition definition = definitions.get(item.getFeature().getCode());
        boolean featureAvailable = definition != null
                && definition.planAssignable()
                && item.getFeature().isNewSalesEnabled()
                && (item.getFeature().getStatus() == FeatureStatus.PUBLIC
                || item.getFeature().getStatus() == FeatureStatus.BETA);
        if (!featureAvailable) {
            return false;
        }
        boolean planOwnsQuota = item.getAllowedPlanCodes().contains(plan.getCode())
                && planFeatureRepository
                        .findByPlanIdAndFeature_Code(plan.getId(), item.getFeature().getCode())
                        .filter(planFeature -> planFeature.getMode() == PlanFeatureMode.INCLUDED)
                        .map(planFeature -> hasFiniteQuota(planFeature.getQuotaConfigs(), item.getResource()))
                        .orElse(false);
        boolean addOnOwnsQuota = compatibleAddOns.stream()
                .filter(addOn -> item.getAllowedAddOnCodes().contains(addOn.getCode()))
                .anyMatch(addOn -> addOnFeatureRepository
                        .findByAddOnIdAndFeature_Code(addOn.getId(), item.getFeature().getCode())
                        .map(feature -> hasFiniteQuota(feature.getQuotaConfigs(), item.getResource()))
                        .orElse(false));
        return planOwnsQuota || addOnOwnsQuota;
    }

    private boolean hasFiniteQuota(List<QuotaLimitEntry> quotas, String resource) {
        return quotas != null && quotas.stream()
                .anyMatch(quota -> quota.resource().equals(resource)
                        && quota.mode() == QuotaLimitMode.FINITE);
    }

    private boolean isSelfServiceAvailable(PlanFeature planFeature, Map<String, FeatureDefinition> definitions) {
        FeatureDefinition definition = definitions.get(planFeature.getFeature().getCode());
        if (definition == null || !definition.planAssignable()) {
            return false;
        }
        return planFeature.getFeature().isNewSalesEnabled()
                && (planFeature.getFeature().getStatus() == FeatureStatus.PUBLIC
                || planFeature.getFeature().getStatus() == FeatureStatus.BETA);
    }

    private void validateSelectedAddOn(
            AddOn addOn,
            Plan plan,
            Set<String> selectedCodes,
            Map<String, PlanFeature> planFeatures,
            Set<String> entitledFeatureCodes,
            Map<String, FeatureDefinition> definitions
    ) {
        if (addOn.getStatus() != AddOnStatus.ACTIVE) {
            throw new InvalidRequestException("AddOn " + addOn.getCode() + " is not active.");
        }
        if (!isAddOnCompatibleWithPlan(addOn, plan, definitions)) {
            throw new InvalidRequestException(
                    "AddOn " + addOn.getCode() + " is not available for plan " + plan.getCode() + ".");
        }
        if (!selectedCodes.containsAll(addOn.getDependencyCodes())) {
            throw new InvalidRequestException(
                    "AddOn " + addOn.getCode() + " requires " + addOn.getDependencyCodes() + ".");
        }
        Set<String> selectedExclusions = new LinkedHashSet<>(addOn.getExclusionCodes());
        selectedExclusions.retainAll(selectedCodes);
        if (!selectedExclusions.isEmpty()) {
            throw new InvalidRequestException(
                    "AddOn " + addOn.getCode() + " conflicts with " + selectedExclusions + ".");
        }
        for (AddOnFeature addOnFeature : addOnFeatureRepository.findAllByAddOnId(addOn.getId())) {
            String featureCode = addOnFeature.getFeature().getCode();
            PlanFeature planFeature = planFeatures.get(featureCode);
            if (planFeature == null || planFeature.getMode() != PlanFeatureMode.OPTIONAL_ADD_ON) {
                throw new InvalidRequestException(
                        "Plan " + plan.getCode() + " does not allow AddOn feature " + featureCode + ".");
            }
            if (!entitledFeatureCodes.add(featureCode)) {
                throw new InvalidRequestException(
                        "Selected AddOns overlap entitlement for feature " + featureCode + ".");
            }
        }
    }

    private boolean isAddOnCompatibleWithPlan(
            AddOn addOn,
            Plan plan,
            Map<String, FeatureDefinition> definitions
    ) {
        return isAddOnCompatibleWithPlan(addOn, plan, definitions, new LinkedHashSet<>());
    }

    private boolean isAddOnCompatibleWithPlan(
            AddOn addOn,
            Plan plan,
            Map<String, FeatureDefinition> definitions,
            Set<String> visited
    ) {
        if (!visited.add(addOn.getCode()) || !addOn.isActive()) {
            return false;
        }
        boolean explicitlyAllowed = addOn.getAllowedPlanCodes() == null
                || addOn.getAllowedPlanCodes().isEmpty()
                || addOn.getAllowedPlanCodes().contains(plan.getCode());
        boolean blocked = addOn.getBlockedPlanCodes() != null
                && addOn.getBlockedPlanCodes().contains(plan.getCode());
        boolean featuresSupported = addOnFeatureRepository.findAllByAddOnId(addOn.getId()).stream()
                .allMatch(addOnFeature -> {
                    FeatureDefinition definition = definitions.get(addOnFeature.getFeature().getCode());
                    boolean selfServiceAvailable = definition != null
                            && definition.planAssignable()
                            && addOnFeature.getFeature().isNewSalesEnabled()
                            && (addOnFeature.getFeature().getStatus() == FeatureStatus.PUBLIC
                            || addOnFeature.getFeature().getStatus() == FeatureStatus.BETA);
                    return selfServiceAvailable && planFeatureRepository
                            .findByPlanIdAndFeature_Code(plan.getId(), addOnFeature.getFeature().getCode())
                            .map(planFeature -> planFeature.getMode() == PlanFeatureMode.OPTIONAL_ADD_ON)
                            .orElse(false);
                });
        boolean dependenciesSupported = addOn.getDependencyCodes().stream()
                .map(addOnRepository::findByCode)
                .allMatch(dependency -> dependency.isPresent()
                        && isAddOnCompatibleWithPlan(dependency.get(), plan, definitions, visited));
        visited.remove(addOn.getCode());
        return explicitlyAllowed
                && !blocked
                && featuresSupported
                && dependenciesSupported;
    }

    private SubscriptionChangeOperation newChangeOperation(
            Subscription current,
            Plan targetPlan,
            SubscriptionOverrides selection,
            SubscriptionEntitlementSnapshot targetSnapshot,
            SubscriptionChangeTiming timing
    ) {
        SubscriptionPeriodCalculator.Period targetPeriod = timing == SubscriptionChangeTiming.AT_RENEWAL
                ? subscriptionPeriodCalculator.recurring(targetSnapshot.billingCycle(), current.getCurrentPeriodEnd())
                : subscriptionPeriodCalculator.recurring(targetSnapshot.billingCycle());
        SubscriptionChangeOperation operation = new SubscriptionChangeOperation();
        operation.setAccount(current.getAccount());
        operation.setSourceSubscription(current);
        operation.setTargetPlan(targetPlan);
        operation.setTiming(timing);
        operation.setStatus(SubscriptionChangeStatus.PENDING);
        operation.setEffectiveAt(targetPeriod.startsAt());
        operation.setRequestedSelection(selection);
        operation.setBeforeSnapshot(subscriptionSnapshotReader.read(current.getEntitlementSnapshot())
                .orElseThrow(() -> new InvalidStateException(
                        "Current subscription has no entitlement snapshot.")));
        operation.setTargetSnapshot(
                targetSnapshot.withEffectivePeriod(targetPeriod.startsAt(), targetPeriod.endsAt()));
        return operation;
    }

    private SubscriptionChangeOperationDto toOperationDto(SubscriptionChangeOperation operation) {
        return new SubscriptionChangeOperationDto(
                operation.getId(),
                operation.getTiming(),
                operation.getStatus(),
                operation.getEffectiveAt(),
                operation.getSourceSubscription().getPlan().getCode(),
                operation.getTargetPlan().getCode(),
                operation.getAttentionReason(),
                subscriptionCheckoutService.toDto(operation.getCheckout()));
    }

    private SubscriptionDto toDto(Subscription subscription) {
        return new SubscriptionDto(
                subscription.getId(),
                new SubscriptionDto.PlanSummaryDto(
                        subscription.getPlan().getCode(),
                        subscription.getPlan().getName(),
                        subscription.getPlan().getPrice(),
                        subscription.getPlan().getCurrencyCode()),
                subscription.getStatus(),
                subscription.getCurrentPrice(),
                subscription.getCurrentPriceCurrencyCode(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd());
    }

    private record ChangeSelection(
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages
    ) {}

    private List<ClientPlanCatalogResponse.CatalogPrice> toCatalogPrices(List<ProductPrice> prices) {
        return (prices == null ? List.<ProductPrice>of() : prices).stream()
                .map(price -> new ClientPlanCatalogResponse.CatalogPrice(
                        price.getId(), price.getAmount(), price.getCurrencyCode(), price.getBillingCycle(),
                        price.getEffectiveFrom(), price.getEffectiveUntil()))
                .toList();
    }

    private record PriceOwnerKey(ProductPriceOwnerType type, UUID id) {}
}
