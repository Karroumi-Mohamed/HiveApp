package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.RetainedEntitlementState;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
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
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
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
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.CommercialSelectionFinalizer;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final CommercialCatalogResolver commercialCatalogResolver;
    private final CommercialSelectionFinalizer commercialSelectionFinalizer;

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
        UUID currentPlanId = current.getPlan().getId();
        CommercialCatalogResolver.CatalogResolution catalog = commercialCatalogResolver
                .resolveCatalog(CommercialCatalogResolver.Audience.CLIENT_CATALOG);
        CommercialCatalogResolver.PlanResolution currentResolution = catalog.plans().stream()
                .filter(result -> result.plan().getId().equals(currentPlanId))
                .findFirst().orElseThrow(() -> new InvalidStateException(
                        "The current Plan revision is missing from the commercial catalogue."));
        var plans = catalog.plans().stream()
                // A held direct-only or retired exact revision remains readable as the
                // Account's current product, but is never exposed to another Account and
                // cannot be selected again through self service.
                .filter(result -> result.clientVisible()
                        || result.plan().getId().equals(currentPlanId))
                .sorted(Comparator.comparing(
                                (CommercialCatalogResolver.PlanResolution result) -> result.plan().getPrice(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(result -> result.plan().getCode()))
                .map(result -> toCatalogPlan(
                        result, current, definitions, usage, result.clientVisible()))
                .toList();
        SubscriptionEntitlementSnapshot currentSnapshot = current.getEntitlementSnapshot();

        return new ClientPlanCatalogResponse(
                new ClientPlanCatalogResponse.CurrentSubscription(
                        current.getId(),
                        current.getPlan().getCode(),
                        current.getStatus(),
                        current.getCurrentPrice(),
                        current.getCurrentPriceCurrencyCode(),
                        currentSnapshot.planPriceEntryId(),
                        currentSnapshot.billingCycle(),
                        current.getCurrentPeriodStart(),
                        current.getCurrentPeriodEnd(),
                        current.isCancelAtPeriodEnd(),
                        currentOverrides.addOnCodes(),
                        currentOverrides.quotaPackages(),
                        retainedAddOns(currentSnapshot, currentResolution),
                        retainedQuotaPackages(currentSnapshot, currentResolution)),
                plans);
    }

    private List<ClientPlanCatalogResponse.RetainedAddOn> retainedAddOns(
            SubscriptionEntitlementSnapshot snapshot,
            CommercialCatalogResolver.PlanResolution currentResolution
    ) {
        Map<String, CommercialCatalogResolver.AddOnResolution> currentByCode =
                currentResolution.addOns().stream().collect(Collectors.toMap(
                        CommercialCatalogResolver.AddOnResolution::code, Function.identity()));
        return snapshot.addOns().stream()
                .sorted(Comparator.comparing(com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot::code))
                .map(held -> {
                    CommercialCatalogResolver.AddOnResolution current = currentByCode.get(held.code());
                    boolean selectable = currentResolution.clientVisible()
                            && current != null && current.clientVisible();
                    RetainedEntitlementState state = selectable
                            ? RetainedEntitlementState.SELECTABLE
                            : current == null
                                    ? RetainedEntitlementState.HISTORICAL_ONLY
                                    : RetainedEntitlementState.RETAINED_ONLY;
                    return new ClientPlanCatalogResponse.RetainedAddOn(
                            held.code(), held.name(), held.definitionVersion(), held.price(),
                            held.currencyCode(), held.billingCycle(), List.copyOf(held.featureCodes()),
                            held.priceEntryId(), state, true, selectable);
                })
                .toList();
    }

    private List<ClientPlanCatalogResponse.RetainedQuotaPackage> retainedQuotaPackages(
            SubscriptionEntitlementSnapshot snapshot,
            CommercialCatalogResolver.PlanResolution currentResolution
    ) {
        Map<String, CommercialCatalogResolver.QuotaPackageResolution> currentByCode =
                currentResolution.quotaPackages().stream().collect(Collectors.toMap(
                        CommercialCatalogResolver.QuotaPackageResolution::code, Function.identity()));
        return snapshot.quotaPackages().stream()
                .sorted(Comparator.comparing(
                        com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::code))
                .map(held -> {
                    CommercialCatalogResolver.QuotaPackageResolution current = currentByCode.get(held.code());
                    boolean selectable = currentResolution.clientVisible()
                            && current != null && current.clientVisible();
                    RetainedEntitlementState state = selectable
                            ? RetainedEntitlementState.SELECTABLE
                            : current == null
                                    ? RetainedEntitlementState.HISTORICAL_ONLY
                                    : RetainedEntitlementState.RETAINED_ONLY;
                    return new ClientPlanCatalogResponse.RetainedQuotaPackage(
                            held.code(), held.name(), held.definitionVersion(), held.featureCode(),
                            held.resource(), held.capacityPerUnit(), held.quantity(), held.unitPrice(),
                            held.currencyCode(), held.billingCycle(), held.priceEntryId(), state, true,
                            selectable, selectable ? current.quotaPackage().getMaximumQuantity() : null);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview", description = "Preview a subscription change")
    public SubscriptionChangePreviewResponse previewChange(UUID accountId, SubscriptionChangeRequest request) {
        Subscription current = getSubscription(accountId);
        ClientPlanSelection target = requireClientPlanSelection(
                request.targetPlanCode(), request.planPriceSelection());
        Plan targetPlan = target.plan();
        ProductPrice planPrice = target.price();
        requireSameSubscriptionCurrency(current, planPrice);
        ChangeSelection selection = validateSelection(targetPlan, request,
                CommercialCatalogResolver.PriceTuple.from(planPrice),
                CommercialCatalogResolver.Audience.CLIENT_CATALOG,
                retainedSelection(current.getEntitlementSnapshot(), targetPlan, request));
        SubscriptionEntitlementSnapshot targetSnapshot = targetSnapshot(
                current, targetPlan, selection, planPrice, request.planPriceSelection());
        return buildPreview(accountId, current, targetPlan, targetSnapshot, selection);
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
        Subscription current = getSubscription(accountId);
        // Preserve the ordinary client privacy boundary before the lock-taking finalizer performs
        // exact price work. Hidden and unknown guessed Plans still fail identically.
        requireClientPlanSelection(request.targetPlanCode(), request.planPriceSelection());
        Set<String> requestedAddOns = normalizeAddOnCodes(request.addOnCodes());
        List<QuotaPackageSelection> requestedPackages = normalizeQuotaPackages(request.quotaPackages());
        CommercialCatalogResolver.RetainedSelection retained = retainedSelection(
                current.getEntitlementSnapshot(), current.getPlan(), request);
        var finalized = commercialSelectionFinalizer.finalizeSelection(
                request.targetPlanCode(), request.planPriceSelection(), requestedAddOns, requestedPackages,
                CommercialCatalogResolver.Audience.CLIENT_CATALOG, retained,
                current.getEntitlementSnapshot());
        requireResolvedSelection(finalized.resolution(), CommercialCatalogResolver.Audience.CLIENT_CATALOG);
        // The finalizer clears the persistence context before the exact price lock pass.
        current = getSubscription(accountId);
        Plan targetPlan = finalized.plan();
        ProductPrice selectedPlanPrice = finalized.planPrice();
        requireSameSubscriptionCurrency(current, selectedPlanPrice);
        ChangeSelection selection = new ChangeSelection(requestedAddOns, requestedPackages);
        var targetSnapshot = finalized.snapshot();
        SubscriptionChangePreviewResponse preview = buildPreview(
                accountId, current, targetPlan, targetSnapshot, selection);
        if (request.effectiveTiming() == SubscriptionChangeTiming.IMMEDIATE && !preview.immediateAllowed()) {
            throw new InvalidStateException("Subscription change cannot be applied until conflicts are resolved.");
        }
        if (isNoOp(current, targetSnapshot, selection)) {
            throw new InvalidStateException("Requested subscription change does not modify the current subscription.");
        }
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
        return createSubscription(accountId, planCode, null);
    }

    @Override
    @Transactional
    public Subscription createSubscription(
            UUID accountId,
            String planCode,
            com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest priceSelection
    ) {
        accountRepository.findByIdForSubscriptionUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var finalized = commercialSelectionFinalizer.finalizeSelection(
                planCode, priceSelection, Set.of(), List.of(),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                CommercialCatalogResolver.RetainedSelection.none(), null);
        requireResolvedSelection(finalized.resolution(), CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
        var plan = finalized.plan();
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

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
        ProductPrice selectedPrice = finalized.planPrice();
        sub.setEntitlementSnapshot(subscriptionSnapshotReader.write(finalized.snapshot()));
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
        return createTrial(accountId, planCode, trialDays, null);
    }

    @Override
    @Transactional
    public Subscription createTrial(
            UUID accountId,
            String planCode,
            int trialDays,
            com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest priceSelection
    ) {
        accountRepository.findByIdForSubscriptionUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var finalized = commercialSelectionFinalizer.finalizeSelection(
                planCode, priceSelection, Set.of(), List.of(),
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                CommercialCatalogResolver.RetainedSelection.none(), null);
        requireResolvedSelection(finalized.resolution(), CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
        Plan plan = finalized.plan();
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var usableSubscriptions = subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING));
        usableSubscriptions.forEach(subscriptionLifecycleManager::closeForReplacement);
        subscriptionRepository.saveAllAndFlush(usableSubscriptions);

        Subscription trial = new Subscription();
        trial.setAccount(account);
        trial.setPlan(plan);
        trial.setCustomOverrides(SubscriptionOverrides.empty());
        ProductPrice selectedPrice = finalized.planPrice();
        trial.setEntitlementSnapshot(finalized.snapshot());
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
        accountRepository.findByIdForSubscriptionUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var sub = getSubscription(accountId);
        SubscriptionEntitlementSnapshot currentSnapshot = sub.getEntitlementSnapshot();
        SubscriptionOverrides currentOverrides = subscriptionOverrideReader.read(sub.getCustomOverrides());
        Set<String> requestedAddOns = normalizeAddOnCodes(addOnCodes);
        List<QuotaPackageSelection> requestedPackages = normalizeQuotaPackages(quotaPackages);
        if (currentOverrides.addOnCodes().equals(requestedAddOns)
                && currentOverrides.quotaPackages().equals(requestedPackages)) {
            // An unchanged override is not a new sale. It must remain a true no-op even after
            // the exact historical prices are paused or superseded.
            return sub;
        }
        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                sub.getPlan().getCode(), requestedAddOns, requestedPackages);
        CommercialCatalogResolver.RetainedSelection retained = retainedSelection(
                currentSnapshot, sub.getPlan(), request);
        var finalized = commercialSelectionFinalizer.finalizeSelection(
                sub.getPlan().getCode(), null, requestedAddOns, requestedPackages,
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR, retained, currentSnapshot);
        requireResolvedSelection(finalized.resolution(), CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
        sub = getSubscription(accountId);
        ChangeSelection selection = new ChangeSelection(requestedAddOns, requestedPackages);
        var overrides = new SubscriptionOverrides(selection.addOnCodes(), selection.quotaPackages());
        sub.setCustomOverrides(subscriptionOverrideReader.write(overrides));
        sub.setEntitlementSnapshot(finalized.snapshot()
                .withEffectivePeriod(sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd()));
        sub.setCurrentMoney(billingCalculator.calculateMoney(sub));
        return subscriptionRepository.save(sub);
    }

    private ClientPlanCatalogResponse.CatalogPlan toCatalogPlan(
            CommercialCatalogResolver.PlanResolution result,
            Subscription current,
            Map<String, FeatureDefinition> definitions,
            Map<String, Long> usage,
            boolean selectable
    ) {
        Plan plan = result.plan();
        var features = result.planFeatures().stream()
                .filter(planFeature -> isSelfServiceAvailable(planFeature, definitions))
                .sorted(Comparator.comparing(planFeature -> definitions.get(planFeature.getFeature().getCode()).sortOrder()))
                .map(planFeature -> toCatalogFeature(planFeature, definitions.get(planFeature.getFeature().getCode()), usage))
                .toList();
        Set<String> visibleAddOnCodes = selectable ? result.addOns().stream()
                .filter(CommercialCatalogResolver.AddOnResolution::clientVisible)
                .map(CommercialCatalogResolver.AddOnResolution::code)
                .collect(Collectors.toUnmodifiableSet()) : Set.of();
        var addOns = (selectable ? result.addOns().stream() : java.util.stream.Stream
                .<CommercialCatalogResolver.AddOnResolution>empty())
                .filter(CommercialCatalogResolver.AddOnResolution::clientVisible)
                .map(addOnResult -> {
                    AddOn addOn = addOnResult.addOn();
                    return new ClientPlanCatalogResponse.CatalogAddOn(
                        addOn.getCode(), addOn.getName(), addOn.getDescription(), addOn.getPrice(),
                        addOn.getCurrencyCode(), addOn.getBillingCycle(), addOn.getDefinitionVersion(),
                        addOn.getDependencyCodes().stream().filter(visibleAddOnCodes::contains)
                                .collect(Collectors.toUnmodifiableSet()),
                        addOn.getExclusionCodes().stream().filter(visibleAddOnCodes::contains)
                                .collect(Collectors.toUnmodifiableSet()),
                        addOn.getFeatures().stream()
                                .sorted(Comparator.comparing(feature -> feature.getFeature().getCode()))
                                .map(feature -> toCatalogAddOnFeature(feature, definitions, usage))
                                .toList(),
                        toCatalogPrices(addOnResult.prices()));
                })
                .toList();
        var quotaPackages = (selectable ? result.quotaPackages().stream() : java.util.stream.Stream
                .<CommercialCatalogResolver.QuotaPackageResolution>empty())
                .filter(CommercialCatalogResolver.QuotaPackageResolution::clientVisible)
                .map(packageResult -> {
                    QuotaPackage item = packageResult.quotaPackage();
                    return new ClientPlanCatalogResponse.CatalogQuotaPackage(
                        item.getCode(), item.getName(), item.getDescription(), item.getDefinitionVersion(),
                        item.getFeature().getCode(), item.getResource(), item.getCapacityPerUnit(),
                        item.getPrice(), item.getCurrencyCode(), item.getBillingCycle(), item.isRepeatable(),
                        item.getMaximumQuantity(),
                        packageResult.directlySelectable() ? Set.of(plan.getCode()) : Set.of(),
                        packageResult.requiredAddOnCodes(),
                        toCatalogPrices(packageResult.prices()), packageResult.directlySelectable(),
                        packageResult.requiredAddOnCodes());
                })
                .toList();
        return new ClientPlanCatalogResponse.CatalogPlan(
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getCurrencyCode(),
                plan.getBillingCycle(),
                current.getPlan().getId().equals(plan.getId()),
                selectable,
                features,
                addOns,
                quotaPackages,
                selectable ? toCatalogPrices(result.prices()) : List.of());
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
                                              CommercialCatalogResolver.PriceTuple priceTuple,
                                              CommercialCatalogResolver.Audience audience,
                                              CommercialCatalogResolver.RetainedSelection retained) {
        Set<String> addOnCodes = normalizeAddOnCodes(request.addOnCodes());
        List<QuotaPackageSelection> packageSelections = normalizeQuotaPackages(request.quotaPackages());
        Set<String> packageCodes = new LinkedHashSet<>();
        for (QuotaPackageSelection selection : packageSelections) {
            if (selection.packageCode() == null || selection.packageCode().isBlank()) {
                throw new InvalidRequestException("Quota package code is required.");
            }
            if (!packageCodes.add(selection.packageCode())) {
                throw new InvalidRequestException("Duplicate quota package selection: " + selection.packageCode());
            }
        }

        CommercialCatalogResolver.SelectionResolution resolved = commercialCatalogResolver.resolveSelection(
                targetPlan, priceTuple, addOnCodes, packageSelections, audience, retained);
        requireResolvedSelection(resolved, audience);

        return new ChangeSelection(addOnCodes, packageSelections);
    }

    private SubscriptionEntitlementSnapshot targetSnapshot(
            Subscription current,
            Plan targetPlan,
            ChangeSelection selection,
            ProductPrice selectedPlanPrice,
            ProductPriceSelectionRequest requestedPrice
    ) {
        SubscriptionEntitlementSnapshot currentSnapshot = current.getEntitlementSnapshot();
        boolean sameExactPlan = Objects.equals(current.getPlan().getId(), targetPlan.getId());
        boolean sameExactPrice = requestedPrice == null
                || (requestedPrice.priceEntryId() != null
                && requestedPrice.priceEntryId().equals(currentSnapshot.planPriceEntryId()));
        if (sameExactPlan && sameExactPrice) {
            return subscriptionSnapshotFactory.fromPlanPreservingPrices(
                    targetPlan, selection.addOnCodes(), selection.quotaPackages(), currentSnapshot);
        }
        return subscriptionSnapshotFactory.fromPlan(
                targetPlan, selection.addOnCodes(), selection.quotaPackages(), selectedPlanPrice);
    }

    private CommercialCatalogResolver.RetainedSelection retainedSelection(
            SubscriptionEntitlementSnapshot current,
            Plan targetPlan,
            SubscriptionChangeRequest request
    ) {
        if (current == null || !targetPlan.getCode().equals(current.planCode())) {
            return CommercialCatalogResolver.RetainedSelection.none();
        }
        ProductPriceSelectionRequest requestedPlanPrice = request.planPriceSelection();
        if (requestedPlanPrice != null) {
            boolean exactHeldIdentity = requestedPlanPrice.priceEntryId() != null
                    && requestedPlanPrice.priceEntryId().equals(current.planPriceEntryId());
            boolean heldTuple = requestedPlanPrice.priceEntryId() == null
                    && requestedPlanPrice.currencyCode() != null
                    && requestedPlanPrice.billingCycle() != null
                    && com.hiveapp.shared.money.Money.normalizeCurrencyCode(
                            requestedPlanPrice.currencyCode()).equals(current.currencyCode())
                    && requestedPlanPrice.billingCycle() == current.billingCycle();
            if (!exactHeldIdentity && !heldTuple) {
                return CommercialCatalogResolver.RetainedSelection.none();
            }
        }
        Set<String> requestedAddOns = request.addOnCodes() == null
                ? Set.of() : request.addOnCodes();
        Set<String> requestedPackages = (request.quotaPackages() == null
                ? List.<QuotaPackageSelection>of() : request.quotaPackages()).stream()
                .filter(Objects::nonNull)
                .map(QuotaPackageSelection::packageCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> retainedAddOns = current.addOns().stream()
                .map(com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot::code)
                .filter(requestedAddOns::contains)
                .collect(Collectors.toSet());
        Map<String, Integer> retainedPackages = current.quotaPackages().stream()
                .filter(item -> requestedPackages.contains(item.code()))
                .collect(Collectors.toMap(
                        com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::code,
                        com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::quantity));
        return new CommercialCatalogResolver.RetainedSelection(
                retainedAddOns, retainedPackages, current.planPriceEntryId());
    }

    private Set<String> normalizeAddOnCodes(Set<String> requested) {
        if (requested == null) return Set.of();
        if (requested.size() > 100) {
            throw new InvalidRequestException("Subscription overrides support at most 100 AddOns.");
        }
        if (requested.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new InvalidRequestException("AddOn codes cannot be null or blank.");
        }
        return Set.copyOf(requested);
    }

    private List<QuotaPackageSelection> normalizeQuotaPackages(List<QuotaPackageSelection> requested) {
        if (requested == null) return List.of();
        if (requested.size() > 100) {
            throw new InvalidRequestException(
                    "Subscription overrides support at most 100 capacity packages.");
        }
        if (requested.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRequestException("Quota package selection cannot be null.");
        }
        List<QuotaPackageSelection> normalized = List.copyOf(requested);
        Set<String> packageCodes = new LinkedHashSet<>();
        for (QuotaPackageSelection selection : normalized) {
            if (selection.packageCode() == null || selection.packageCode().isBlank()) {
                throw new InvalidRequestException("Quota package code is required.");
            }
            if (!packageCodes.add(selection.packageCode())) {
                throw new InvalidRequestException(
                        "Duplicate quota package selection: " + selection.packageCode());
            }
        }
        return normalized;
    }

    private void requireResolvedSelection(
            CommercialCatalogResolver.SelectionResolution resolved,
            CommercialCatalogResolver.Audience audience
    ) {
        CommercialCatalogResolver.requireSelectable(resolved, audience);
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

    private SubscriptionChangePreviewResponse buildPreview(
            UUID accountId,
            Subscription current,
            Plan targetPlan,
            SubscriptionEntitlementSnapshot targetSnapshot,
            ChangeSelection selection
    ) {
        List<SubscriptionChangeConflict> conflicts = subscriptionImpactAnalyzer.analyze(
                accountId, current, targetSnapshot);
        Money price = previewPrice(current, targetPlan, targetSnapshot, selection);
        return new SubscriptionChangePreviewResponse(
                current.getPlan().getCode(),
                targetPlan.getCode(),
                current.getCurrentPrice(),
                price.amount(),
                price.currencyCode(),
                conflicts.isEmpty(),
                targetSnapshot.features().stream()
                        .map(feature -> feature.featureCode())
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                subscriptionImpactAnalyzer.effectiveQuotaLimits(targetSnapshot),
                selection.addOnCodes(),
                selection.quotaPackages(),
                conflicts);
    }

    private boolean isNoOp(
            Subscription current,
            SubscriptionEntitlementSnapshot targetSnapshot,
            ChangeSelection selection
    ) {
        SubscriptionOverrides currentOverrides = subscriptionOverrideReader.read(current.getCustomOverrides());
        SubscriptionEntitlementSnapshot currentSnapshot = current.getEntitlementSnapshot();
        return current.getPlan().getCode().equals(targetSnapshot.planCode())
                && Objects.equals(currentOverrides.addOnCodes(), selection.addOnCodes())
                && Objects.equals(currentOverrides.quotaPackages(), selection.quotaPackages())
                && sameSelectedPrices(currentSnapshot, targetSnapshot);
    }

    private boolean sameSelectedPrices(
            SubscriptionEntitlementSnapshot current,
            SubscriptionEntitlementSnapshot target
    ) {
        if (!samePrice(
                current.planPriceEntryId(), current.basePrice(), current.currencyCode(), current.billingCycle(),
                target.planPriceEntryId(), target.basePrice(), target.currencyCode(), target.billingCycle())) {
            return false;
        }
        Map<String, com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot> currentAddOns =
                current.addOns().stream().collect(Collectors.toMap(
                        com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot::code,
                        Function.identity()));
        if (currentAddOns.size() != target.addOns().size()
                || !currentAddOns.keySet().equals(target.addOns().stream()
                    .map(com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot::code)
                    .collect(Collectors.toSet()))) {
            return false;
        }
        for (var targetAddOn : target.addOns()) {
            var currentAddOn = currentAddOns.get(targetAddOn.code());
            if (currentAddOn == null || !samePrice(
                    currentAddOn.priceEntryId(), currentAddOn.price(), currentAddOn.currencyCode(),
                    currentAddOn.billingCycle(), targetAddOn.priceEntryId(), targetAddOn.price(),
                    targetAddOn.currencyCode(), targetAddOn.billingCycle())) {
                return false;
            }
        }
        Map<String, com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot> currentPackages =
                current.quotaPackages().stream().collect(Collectors.toMap(
                        com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::code,
                        Function.identity()));
        if (currentPackages.size() != target.quotaPackages().size()
                || !currentPackages.keySet().equals(target.quotaPackages().stream()
                    .map(com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::code)
                    .collect(Collectors.toSet()))) {
            return false;
        }
        for (var targetPackage : target.quotaPackages()) {
            var currentPackage = currentPackages.get(targetPackage.code());
            if (currentPackage == null || !samePrice(
                    currentPackage.priceEntryId(), currentPackage.unitPrice(), currentPackage.currencyCode(),
                    currentPackage.billingCycle(), targetPackage.priceEntryId(), targetPackage.unitPrice(),
                    targetPackage.currencyCode(), targetPackage.billingCycle())) {
                return false;
            }
        }
        return true;
    }

    private boolean samePrice(
            UUID currentId,
            BigDecimal currentAmount,
            String currentCurrency,
            com.hiveapp.platform.client.plan.domain.constant.BillingCycle currentCycle,
            UUID targetId,
            BigDecimal targetAmount,
            String targetCurrency,
            com.hiveapp.platform.client.plan.domain.constant.BillingCycle targetCycle
    ) {
        if (currentId != null && targetId != null) {
            return currentId.equals(targetId);
        }
        return currentAmount != null
                && targetAmount != null
                && currentAmount.compareTo(targetAmount) == 0
                && Objects.equals(currentCurrency, targetCurrency)
                && currentCycle == targetCycle;
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

    private ClientPlanSelection requireClientPlanSelection(
            String planCode,
            ProductPriceSelectionRequest requestedPrice
    ) {
        if (planCode == null || planCode.isBlank()) {
            throw unavailableClientSelection();
        }
        CommercialCatalogResolver.PlanResolution resolution = commercialCatalogResolver
                .resolveCatalog(CommercialCatalogResolver.Audience.CLIENT_CATALOG)
                .plans().stream()
                .filter(CommercialCatalogResolver.PlanResolution::clientVisible)
                .filter(candidate -> candidate.plan().getCode().equals(planCode))
                .findFirst()
                .orElseThrow(this::unavailableClientSelection);
        // Only after the Plan is known to be client-visible may exact price lookup return its
        // normal owner/tuple errors. Hidden and unknown Plans fail identically before this point.
        return new ClientPlanSelection(
                resolution.plan(), productPriceResolver.resolvePlan(resolution.plan(), requestedPrice));
    }

    private InvalidRequestException unavailableClientSelection() {
        return new InvalidRequestException("The requested commercial selection is unavailable.");
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

    private boolean isSelfServiceAvailable(PlanFeature planFeature, Map<String, FeatureDefinition> definitions) {
        FeatureDefinition definition = definitions.get(planFeature.getFeature().getCode());
        if (definition == null || !definition.planAssignable()) {
            return false;
        }
        return planFeature.getFeature().isNewSalesEnabled()
                && (planFeature.getFeature().getStatus() == FeatureStatus.PUBLIC
                || planFeature.getFeature().getStatus() == FeatureStatus.BETA);
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

    private record ClientPlanSelection(Plan plan, ProductPrice price) {}

    private List<ClientPlanCatalogResponse.CatalogPrice> toCatalogPrices(List<ProductPrice> prices) {
        return (prices == null ? List.<ProductPrice>of() : prices).stream()
                .map(price -> new ClientPlanCatalogResponse.CatalogPrice(
                        price.getId(), price.getAmount(), price.getCurrencyCode(), price.getBillingCycle(),
                        price.getEffectiveFrom(), price.getEffectiveUntil()))
                .toList();
    }

}
