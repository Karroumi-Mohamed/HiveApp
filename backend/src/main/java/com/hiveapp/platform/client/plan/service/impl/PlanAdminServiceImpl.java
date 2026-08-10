package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanCodes;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.AssignAddOnFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.PlanDetailDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberDto;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.platform.client.plan.dto.UpdateAddOnRequest;
import com.hiveapp.platform.client.plan.service.BillingConfigurationValidator;
import com.hiveapp.platform.client.plan.service.PlanAdminService;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PlansFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.money.Money;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@PermissionNode(key = PlansFeature.KEY, description = "Plan Catalogue Management", guard = PermissionNode.Guard.ON)
public class PlanAdminServiceImpl extends PlatformControlFeatureService implements PlanAdminService {

    private static final Pattern COMMERCIAL_CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingConfigurationValidator billingConfigurationValidator;
    private final AddOnRepository addOnRepository;
    private final AddOnFeatureRepository addOnFeatureRepository;

    @Override
    protected FeatureDefinition featureDefinition() {
        return PlansFeature.definition();
    }

    @Override
    @PermissionNode(key = "list", description = "List all plans")
    @Transactional(readOnly = true)
    public List<Plan> listPlans() {
        return planRepository.findAll();
    }

    @Override
    @PermissionNode(key = "read_detail", description = "View plan detail and operational counts")
    @Transactional(readOnly = true)
    public PlanDetailDto getPlanDetail(UUID planId) {
        Plan plan = requirePlan(planId);
        List<PlanFeature> planFeatures = planFeatureRepository.findAllByPlanId(planId);
        long activeSubscribers = subscriptionRepository.countByPlan_IdAndStatus(planId, SubscriptionStatus.ACTIVE);
        long trialingSubscribers = subscriptionRepository.countByPlan_IdAndStatus(planId, SubscriptionStatus.TRIALING);
        long currentSubscribers = activeSubscribers + trialingSubscribers;
        long historicalSubscribers = subscriptionRepository.countByPlan_Id(planId);
        Money currentRecurringPrice = subscriptionRepository
                .findAllByPlan_IdAndStatusInOrderByCreatedAtDesc(planId, usableStatuses())
                .stream()
                .map(subscription -> subscription.currentMoney() != null
                        ? subscription.currentMoney()
                        : Money.zero(plan.getCurrencyCode()))
                .reduce(Money.zero(plan.getCurrencyCode()), Money::add);

        return new PlanDetailDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getCurrencyCode(),
                plan.getBillingCycle(),
                plan.getStatus(),
                planFeatures.size(),
                (int) planFeatures.stream()
                        .filter(feature -> feature.getQuotaConfigs() != null && !feature.getQuotaConfigs().isEmpty())
                        .count(),
                activeSubscribers,
                trialingSubscribers,
                currentSubscribers,
                historicalSubscribers,
                currentRecurringPrice.amount(),
                currentRecurringPrice.currencyCode(),
                warnings(plan, planFeatures, currentSubscribers, historicalSubscribers)
        );
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create a new plan")
    public Plan createPlan(CreatePlanRequest request) {
        if (planRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateResourceException("Plan", "code", request.code());
        }
        Money price = validatePlanBasics(
                request.code(), request.name(), request.price(), request.currencyCode(), request.billingCycle());
        Plan plan = new Plan();
        plan.setCode(request.code());
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setMoney(price);
        plan.setBillingCycle(request.billingCycle());
        plan.setStatus(PlanStatus.DRAFT);
        Plan savedPlan = planRepository.save(plan);
        inheritPlanComposition(savedPlan, request.inheritFromPlanId());
        return savedPlan;
    }

    @Override
    @Transactional
    @PermissionNode(key = "update", description = "Update plan template basics")
    public Plan updatePlan(UUID planId, UpdatePlanRequest request) {
        Plan plan = requirePlan(planId);
        requireMutable(plan);
        Money price = validatePlanBasics(
                plan.getCode(), request.name(), request.price(), request.currencyCode(), request.billingCycle());
        if (!plan.getCurrencyCode().equals(price.currencyCode())
                && (planFeatureRepository.findAllByPlanId(planId).stream().anyMatch(this::hasPrice)
                || subscriptionRepository.countByPlan_Id(planId) > 0)) {
            throw new InvalidRequestException(
                    "Plan currency cannot change after feature pricing or subscription history exists.");
        }
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setMoney(price);
        plan.setBillingCycle(request.billingCycle());
        return planRepository.save(plan);
    }

    @Override
    @Transactional
    @PermissionNode(key = "transition_status", description = "Transition a plan lifecycle state")
    public Plan transitionStatus(UUID planId, PlanStatus targetStatus) {
        var plan = requirePlan(planId);
        if (targetStatus == null) {
            throw new InvalidRequestException("Target Plan status is required.");
        }
        if (plan.getStatus() == targetStatus) {
            return plan;
        }
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new BusinessException("Archived plans are terminal and cannot transition.");
        }
        if (targetStatus == PlanStatus.DRAFT) {
            throw new BusinessException("A Plan cannot transition back to DRAFT.");
        }
        if (plan.getStatus() == PlanStatus.DRAFT && targetStatus == PlanStatus.INACTIVE) {
            throw new BusinessException("A draft Plan must be activated or archived.");
        }
        if (PlanCodes.DEFAULT.equals(plan.getCode()) && targetStatus != PlanStatus.ACTIVE) {
            throw new BusinessException("The default FREE plan must remain ACTIVE because workspace provisioning requires it.");
        }
        if (targetStatus == PlanStatus.ACTIVE) {
            validateActivation(plan);
        }
        plan.setStatus(targetStatus);
        return planRepository.save(plan);
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete", description = "Delete an unused plan template")
    public void deletePlan(UUID planId) {
        Plan plan = requirePlan(planId);
        if (PlanCodes.DEFAULT.equals(plan.getCode())) {
            throw new BusinessException("The default FREE plan cannot be deleted because workspace provisioning requires it.");
        }
        long subscriptionHistory = subscriptionRepository.countByPlan_Id(planId);
        if (subscriptionHistory > 0) {
            throw new BusinessException("Plan " + plan.getCode()
                    + " has subscription history and cannot be deleted. Deactivate it instead.");
        }
        boolean referencedByAddOn = addOnRepository.findAll().stream()
                .anyMatch(addOn -> addOn.getAllowedPlanCodes().contains(plan.getCode())
                        || addOn.getBlockedPlanCodes().contains(plan.getCode()));
        if (referencedByAddOn) {
            throw new BusinessException(
                    "Plan " + plan.getCode() + " is referenced by an AddOn and cannot be deleted.");
        }
        planFeatureRepository.deleteAll(planFeatureRepository.findAllByPlanId(planId));
        planRepository.delete(plan);
    }

    @Override
    @PermissionNode(key = "list_features", description = "List features assigned to a plan")
    @Transactional(readOnly = true)
    public List<PlanFeature> listPlanFeatures(UUID planId) {
        if (!planRepository.existsById(planId)) {
            throw new ResourceNotFoundException("Plan", "id", planId);
        }
        return planFeatureRepository.findAllByPlanId(planId);
    }

    @Override
    @PermissionNode(key = "list_subscribers", description = "List accounts currently subscribed to a plan")
    @Transactional(readOnly = true)
    public List<PlanSubscriberDto> listPlanSubscribers(UUID planId) {
        Plan plan = requirePlan(planId);
        return subscriptionRepository.findAllByPlan_IdAndStatusInOrderByCreatedAtDesc(planId, usableStatuses())
                .stream()
                .map(subscription -> new PlanSubscriberDto(
                        subscription.getId(),
                        subscription.getAccount().getId(),
                        subscription.getAccount().getName(),
                        plan.getCode(),
                        subscription.getStatus(),
                        subscription.getCurrentPrice(),
                        subscription.getCurrentPriceCurrencyCode(),
                        subscription.getCurrentPeriodEnd()
                ))
                .toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = "assign_feature", description = "Assign a feature to a plan")
    public PlanFeature assignFeature(UUID planId, AssignPlanFeatureRequest request) {
        var plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        requireMutable(plan);
        var feature = billingConfigurationValidator.validatePlanFeature(
                request.featureCode(), request.mode(), request.quotaConfigs(), plan.getCurrencyCode());

        planFeatureRepository.findByPlanIdAndFeature_Code(planId, request.featureCode())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("PlanFeature", "featureCode", request.featureCode());
                });

        PlanFeature pf = new PlanFeature();
        pf.setPlan(plan);
        pf.setFeature(feature);
        pf.setMode(request.mode());
        pf.setQuotaConfigs(request.quotaConfigs() != null ? request.quotaConfigs() : new ArrayList<>());
        try {
            return planFeatureRepository.saveAndFlush(pf);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("PlanFeature", "featureCode", request.featureCode());
        }
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_feature", description = "Update a plan's feature quota/price config")
    public PlanFeature updateFeature(UUID planId, UUID planFeatureId, AssignPlanFeatureRequest request) {
        var pf = planFeatureRepository.findById(planFeatureId)
                .orElseThrow(() -> new ResourceNotFoundException("PlanFeature", "id", planFeatureId));
        if (!pf.getPlan().getId().equals(planId)) {
            throw new ResourceNotFoundException("PlanFeature", "id", planFeatureId);
        }
        if (!pf.getFeature().getCode().equals(request.featureCode())) {
            throw new InvalidRequestException("A plan feature update cannot change its feature code.");
        }
        requireMutable(pf.getPlan());
        billingConfigurationValidator.validatePlanFeature(
                request.featureCode(), request.mode(), request.quotaConfigs(), pf.getPlan().getCurrencyCode());
        pf.setMode(request.mode());
        pf.setQuotaConfigs(request.quotaConfigs() != null ? request.quotaConfigs() : new ArrayList<>());
        return planFeatureRepository.save(pf);
    }

    @Override
    @Transactional
    @PermissionNode(key = "remove_feature", description = "Remove a feature from a plan")
    public void removeFeature(UUID planId, UUID planFeatureId) {
        var pf = planFeatureRepository.findById(planFeatureId)
                .orElseThrow(() -> new ResourceNotFoundException("PlanFeature", "id", planFeatureId));
        if (!pf.getPlan().getId().equals(planId)) {
            throw new ResourceNotFoundException("PlanFeature", "id", planFeatureId);
        }
        requireMutable(pf.getPlan());
        planFeatureRepository.delete(pf);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list_add_ons", description = "List commercial AddOns")
    public List<AddOn> listAddOns() {
        return addOnRepository.findAllByOrderByCodeAsc();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_add_on", description = "Read commercial AddOn detail")
    public AddOn getAddOn(UUID addOnId) {
        return addOnRepository.findDetailedById(addOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
    }

    @Override
    @Transactional
    @PermissionNode(key = "create_add_on", description = "Create a commercial AddOn draft")
    public AddOn createAddOn(CreateAddOnRequest request) {
        String code = normalizeCommercialCode(request.code(), "AddOn code");
        if (addOnRepository.findByCode(code).isPresent()) {
            throw new DuplicateResourceException("AddOn", "code", code);
        }
        AddOn addOn = new AddOn();
        addOn.setCode(code);
        applyAddOnBasics(addOn, request.name(), request.description(), request.price(),
                request.currencyCode(), request.billingCycle(), request.allowedPlanCodes(),
                request.blockedPlanCodes(), request.dependencyCodes(), request.exclusionCodes());
        addOn.setStatus(AddOnStatus.DRAFT);
        return addOnRepository.save(addOn);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_add_on", description = "Update a commercial AddOn draft")
    public AddOn updateAddOn(UUID addOnId, UpdateAddOnRequest request) {
        AddOn addOn = requireEditableAddOn(addOnId);
        applyAddOnBasics(addOn, request.name(), request.description(), request.price(),
                request.currencyCode(), request.billingCycle(), request.allowedPlanCodes(),
                request.blockedPlanCodes(), request.dependencyCodes(), request.exclusionCodes());
        addOn.touchDefinition();
        addOnRepository.saveAndFlush(addOn);
        return addOnRepository.findDetailedById(addOnId).orElseThrow();
    }

    @Override
    @Transactional
    @PermissionNode(key = "transition_add_on", description = "Transition a commercial AddOn lifecycle state")
    public AddOn transitionAddOnStatus(UUID addOnId, AddOnStatus targetStatus) {
        AddOn addOn = requireAddOn(addOnId);
        if (targetStatus == null) {
            throw new InvalidRequestException("Target AddOn status is required.");
        }
        if (addOn.getStatus() == targetStatus) {
            return addOn;
        }
        if (addOn.getStatus() == AddOnStatus.ARCHIVED) {
            throw new BusinessException("Archived AddOns are terminal and cannot transition.");
        }
        if (targetStatus == AddOnStatus.DRAFT) {
            throw new BusinessException("An AddOn cannot transition back to DRAFT.");
        }
        if (addOn.getStatus() == AddOnStatus.DRAFT && targetStatus == AddOnStatus.INACTIVE) {
            throw new BusinessException("A draft AddOn must be activated or archived.");
        }
        if (targetStatus == AddOnStatus.ACTIVE) {
            validateAddOnActivation(addOn);
        }
        addOn.setStatus(targetStatus);
        addOnRepository.saveAndFlush(addOn);
        return addOnRepository.findDetailedById(addOnId).orElseThrow();
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete_add_on", description = "Delete an unused AddOn draft")
    public void deleteAddOn(UUID addOnId) {
        AddOn addOn = requireAddOn(addOnId);
        if (addOn.getStatus() != AddOnStatus.DRAFT) {
            throw new BusinessException("Only AddOn drafts may be deleted; deactivate or archive published AddOns.");
        }
        boolean referenced = addOnRepository.findAll().stream()
                .filter(candidate -> !candidate.getId().equals(addOnId))
                .anyMatch(candidate -> candidate.getDependencyCodes().contains(addOn.getCode())
                        || candidate.getExclusionCodes().contains(addOn.getCode()));
        if (referenced) {
            throw new BusinessException(
                    "AddOn " + addOn.getCode() + " is referenced by another AddOn and cannot be deleted.");
        }
        addOnFeatureRepository.deleteAll(addOnFeatureRepository.findAllByAddOnId(addOnId));
        addOnRepository.delete(addOn);
    }

    @Override
    @Transactional
    @PermissionNode(key = "assign_add_on_feature", description = "Assign a feature to an AddOn draft")
    public AddOnFeature assignAddOnFeature(UUID addOnId, AssignAddOnFeatureRequest request) {
        AddOn addOn = requireEditableAddOn(addOnId);
        var feature = billingConfigurationValidator.validateAddOnFeature(
                request.featureCode(), request.quotaConfigs(), addOn.getCurrencyCode());
        if (addOnFeatureRepository.findByAddOnIdAndFeature_Code(addOnId, request.featureCode()).isPresent()) {
            throw new DuplicateResourceException("AddOnFeature", "featureCode", request.featureCode());
        }
        AddOnFeature item = new AddOnFeature();
        item.setAddOn(addOn);
        item.setFeature(feature);
        item.setQuotaConfigs(request.quotaConfigs() != null ? request.quotaConfigs() : new ArrayList<>());
        addOn.touchDefinition();
        try {
            return addOnFeatureRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("AddOnFeature", "featureCode", request.featureCode());
        }
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_add_on_feature", description = "Update an AddOn feature configuration")
    public AddOnFeature updateAddOnFeature(
            UUID addOnId, UUID addOnFeatureId, AssignAddOnFeatureRequest request) {
        AddOn addOn = requireEditableAddOn(addOnId);
        AddOnFeature item = requireAddOnFeature(addOnId, addOnFeatureId);
        if (!item.getFeature().getCode().equals(request.featureCode())) {
            throw new InvalidRequestException("An AddOn feature update cannot change its feature code.");
        }
        billingConfigurationValidator.validateAddOnFeature(
                request.featureCode(), request.quotaConfigs(), addOn.getCurrencyCode());
        item.setQuotaConfigs(request.quotaConfigs() != null ? request.quotaConfigs() : new ArrayList<>());
        addOn.touchDefinition();
        return addOnFeatureRepository.save(item);
    }

    @Override
    @Transactional
    @PermissionNode(key = "remove_add_on_feature", description = "Remove a feature from an AddOn draft")
    public void removeAddOnFeature(UUID addOnId, UUID addOnFeatureId) {
        AddOn addOn = requireEditableAddOn(addOnId);
        AddOnFeature item = requireAddOnFeature(addOnId, addOnFeatureId);
        addOnFeatureRepository.delete(item);
        addOn.touchDefinition();
    }

    private void inheritPlanComposition(Plan targetPlan, UUID requestedSourcePlanId) {
        var sourcePlan = resolveInheritanceSource(requestedSourcePlanId).orElse(null);
        if (sourcePlan == null) {
            return;
        }
        var sourceFeatures = planFeatureRepository.findAllByPlanId(sourcePlan.getId());
        if (!targetPlan.getCurrencyCode().equals(sourcePlan.getCurrencyCode())
                && sourceFeatures.stream().anyMatch(this::hasPrice)) {
            throw new InvalidRequestException(
                    "Priced plan composition cannot be inherited across currencies without explicit conversion.");
        }

        var inheritedFeatures = sourceFeatures.stream()
                .map(sourceFeature -> {
                    billingConfigurationValidator.validatePlanFeature(
                            sourceFeature.getFeature().getCode(),
                            sourceFeature.getMode(),
                            sourceFeature.getQuotaConfigs(),
                            sourcePlan.getCurrencyCode());
                    PlanFeature copy = new PlanFeature();
                    copy.setPlan(targetPlan);
                    copy.setFeature(sourceFeature.getFeature());
                    copy.setMode(sourceFeature.getMode());
                    copy.setQuotaConfigs(sourceFeature.getQuotaConfigs() != null
                            ? new ArrayList<>(sourceFeature.getQuotaConfigs())
                            : new ArrayList<>());
                    return copy;
                })
                .toList();

        if (!inheritedFeatures.isEmpty()) {
            planFeatureRepository.saveAll(inheritedFeatures);
        }
    }

    private boolean hasPrice(PlanFeature planFeature) {
        return planFeature.getQuotaConfigs() != null
                && planFeature.getQuotaConfigs().stream().anyMatch(quota -> quota.pricePerUnit() != null);
    }

    private void requireMutable(Plan plan) {
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new BusinessException("Archived plans are read-only.");
        }
    }

    private void validateActivation(Plan plan) {
        List<PlanFeature> features = planFeatureRepository.findAllByPlanId(plan.getId());
        if (features.stream().noneMatch(feature -> feature.getMode() == PlanFeatureMode.INCLUDED)) {
            throw new BusinessException("A Plan requires at least one included feature before activation.");
        }
        for (PlanFeature feature : features) {
            billingConfigurationValidator.validatePlanFeature(
                    feature.getFeature().getCode(), feature.getMode(),
                    feature.getQuotaConfigs(), plan.getCurrencyCode());
        }
    }

    private java.util.Optional<Plan> resolveInheritanceSource(UUID requestedSourcePlanId) {
        if (requestedSourcePlanId != null) {
            return java.util.Optional.of(planRepository.findById(requestedSourcePlanId)
                    .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", requestedSourcePlanId)));
        }
        return planRepository.findByCode(PlanCodes.DEFAULT);
    }

    private Plan requirePlan(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
    }

    private AddOn requireAddOn(UUID addOnId) {
        return addOnRepository.findById(addOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
    }

    private AddOn requireEditableAddOn(UUID addOnId) {
        AddOn addOn = requireAddOn(addOnId);
        if (addOn.getStatus() == AddOnStatus.ACTIVE) {
            throw new BusinessException("Active AddOns are immutable; deactivate before editing.");
        }
        if (addOn.getStatus() == AddOnStatus.ARCHIVED) {
            throw new BusinessException("Archived AddOns are read-only.");
        }
        return addOn;
    }

    private AddOnFeature requireAddOnFeature(UUID addOnId, UUID addOnFeatureId) {
        AddOnFeature item = addOnFeatureRepository.findById(addOnFeatureId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOnFeature", "id", addOnFeatureId));
        if (!item.getAddOn().getId().equals(addOnId)) {
            throw new ResourceNotFoundException("AddOnFeature", "id", addOnFeatureId);
        }
        return item;
    }

    private void applyAddOnBasics(
            AddOn addOn,
            String name,
            String description,
            BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle,
            Set<String> allowedPlanCodes,
            Set<String> blockedPlanCodes,
            Set<String> dependencyCodes,
            Set<String> exclusionCodes
    ) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("AddOn name is required.");
        }
        Money money;
        try {
            money = Money.of(price, currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("AddOn price is invalid: " + exception.getMessage());
        }
        if (money.isNegative()) {
            throw new InvalidRequestException("AddOn price cannot be negative.");
        }
        if (billingCycle == null || billingCycle == BillingCycle.FOREVER) {
            throw new InvalidRequestException("AddOns require a MONTHLY or YEARLY billing cycle.");
        }

        Set<String> allowed = normalizeCodes(allowedPlanCodes, "Allowed Plan code");
        Set<String> blocked = normalizeCodes(blockedPlanCodes, "Blocked Plan code");
        Set<String> dependencies = normalizeCodes(dependencyCodes, "Dependency AddOn code");
        Set<String> exclusions = normalizeCodes(exclusionCodes, "Excluded AddOn code");
        if (!java.util.Collections.disjoint(allowed, blocked)) {
            throw new InvalidRequestException("An AddOn cannot both allow and block the same Plan.");
        }
        if (!java.util.Collections.disjoint(dependencies, exclusions)) {
            throw new InvalidRequestException("An AddOn cannot both require and exclude the same AddOn.");
        }
        for (String planCode : union(allowed, blocked)) {
            if (planRepository.findByCode(planCode).isEmpty()) {
                throw new InvalidRequestException("Unknown Plan code in AddOn availability: " + planCode);
            }
        }
        for (String relatedCode : union(dependencies, exclusions)) {
            if (relatedCode.equals(addOn.getCode())) {
                throw new InvalidRequestException("An AddOn cannot depend on or exclude itself.");
            }
            if (addOnRepository.findByCode(relatedCode).isEmpty()) {
                throw new InvalidRequestException("Unknown related AddOn code: " + relatedCode);
            }
        }

        addOn.setName(name);
        addOn.setDescription(description);
        addOn.setMoney(money);
        addOn.setBillingCycle(billingCycle);
        addOn.setAllowedPlanCodes(allowed);
        addOn.setBlockedPlanCodes(blocked);
        addOn.setDependencyCodes(dependencies);
        addOn.setExclusionCodes(exclusions);
    }

    private void validateAddOnActivation(AddOn addOn) {
        List<AddOnFeature> features = addOnFeatureRepository.findAllByAddOnId(addOn.getId());
        if (features.isEmpty()) {
            throw new BusinessException("An AddOn requires at least one feature before activation.");
        }
        for (String dependencyCode : addOn.getDependencyCodes()) {
            AddOn dependency = addOnRepository.findByCode(dependencyCode)
                    .orElseThrow(() -> new BusinessException("Missing AddOn dependency " + dependencyCode + "."));
            if (!dependency.isActive()) {
                throw new BusinessException("AddOn dependency " + dependencyCode + " must be ACTIVE.");
            }
        }
        for (AddOnFeature feature : features) {
            billingConfigurationValidator.validateAddOnFeature(
                    feature.getFeature().getCode(), feature.getQuotaConfigs(), addOn.getCurrencyCode());
        }
        List<Plan> availablePlans = addOn.getAllowedPlanCodes().isEmpty()
                ? planRepository.findAll().stream()
                        .filter(Plan::isActive)
                        .filter(plan -> !addOn.getBlockedPlanCodes().contains(plan.getCode()))
                        .filter(plan -> addOn.getCurrencyCode().equals(plan.getCurrencyCode()))
                        .filter(plan -> addOn.getBillingCycle() == plan.getBillingCycle())
                        .toList()
                : addOn.getAllowedPlanCodes().stream()
                        .map(planCode -> planRepository.findByCode(planCode)
                                .orElseThrow(() -> new BusinessException(
                                        "Allowed Plan no longer exists: " + planCode)))
                        .toList();
        if (availablePlans.isEmpty()) {
            throw new BusinessException("An AddOn must be available to at least one active compatible Plan.");
        }
        for (Plan plan : availablePlans) {
            if (!plan.isActive()
                    || !addOn.getCurrencyCode().equals(plan.getCurrencyCode())
                    || addOn.getBillingCycle() != plan.getBillingCycle()) {
                throw new BusinessException(
                        "Plan " + plan.getCode() + " is not active or uses incompatible currency/billing cycle.");
            }
            requirePlanSupportsAddOn(plan, features);
            requireDependenciesAvailableOnPlan(addOn, plan, new LinkedHashSet<>());
        }
    }

    private void requireDependenciesAvailableOnPlan(AddOn addOn, Plan plan, Set<String> visited) {
        if (!visited.add(addOn.getCode())) {
            throw new BusinessException("Cyclic AddOn dependency detected at " + addOn.getCode() + ".");
        }
        for (String dependencyCode : addOn.getDependencyCodes()) {
            AddOn dependency = addOnRepository.findByCode(dependencyCode)
                    .orElseThrow(() -> new BusinessException("Missing AddOn dependency " + dependencyCode + "."));
            boolean allowed = dependency.getAllowedPlanCodes().isEmpty()
                    || dependency.getAllowedPlanCodes().contains(plan.getCode());
            if (!dependency.isActive()
                    || !allowed
                    || dependency.getBlockedPlanCodes().contains(plan.getCode())
                    || !dependency.getCurrencyCode().equals(plan.getCurrencyCode())
                    || dependency.getBillingCycle() != plan.getBillingCycle()) {
                throw new BusinessException(
                        "AddOn dependency " + dependencyCode + " is not available for Plan "
                                + plan.getCode() + ".");
            }
            requirePlanSupportsAddOn(
                    plan, addOnFeatureRepository.findAllByAddOnId(dependency.getId()));
            requireDependenciesAvailableOnPlan(dependency, plan, visited);
        }
        visited.remove(addOn.getCode());
    }

    private void requirePlanSupportsAddOn(Plan plan, List<AddOnFeature> features) {
        var modes = planFeatureRepository.findAllByPlanId(plan.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        feature -> feature.getFeature().getCode(), PlanFeature::getMode));
        for (AddOnFeature feature : features) {
            if (modes.get(feature.getFeature().getCode()) != PlanFeatureMode.OPTIONAL_ADD_ON) {
                throw new BusinessException(
                        "Plan " + plan.getCode() + " does not permit AddOn feature "
                                + feature.getFeature().getCode() + ".");
            }
        }
    }

    private Set<String> normalizeCodes(Set<String> values, String label) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values == null ? Set.<String>of() : values) {
            normalized.add(normalizeCommercialCode(value, label));
        }
        return normalized;
    }

    private String normalizeCommercialCode(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(label + " is required.");
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!COMMERCIAL_CODE.matcher(normalized).matches()) {
            throw new InvalidRequestException(label + " must use uppercase letters, numbers, and underscores.");
        }
        return normalized;
    }

    private Set<String> union(Set<String> left, Set<String> right) {
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return union;
    }

    private Money validatePlanBasics(
            String code,
            String name,
            BigDecimal price,
            String currencyCode,
            BillingCycle billingCycle) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Plan name is required.");
        }
        Money money;
        try {
            money = Money.of(price, currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Plan price is invalid: " + exception.getMessage());
        }
        if (money.isNegative()) {
            throw new InvalidRequestException("Plan price cannot be negative.");
        }
        if (billingCycle == null) {
            throw new InvalidRequestException("Plan billing cycle is required.");
        }
        if (billingCycle == BillingCycle.FOREVER) {
            throw new InvalidRequestException("Perpetual commercial plans are deferred; use MONTHLY or YEARLY.");
        }
        return money;
    }

    private List<SubscriptionStatus> usableStatuses() {
        return List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING);
    }

    private List<String> warnings(
            Plan plan,
            List<PlanFeature> planFeatures,
            long currentSubscribers,
            long historicalSubscribers
    ) {
        List<String> warnings = new ArrayList<>();
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            warnings.add("PLAN_INACTIVE");
        }
        if (planFeatures.isEmpty()) {
            warnings.add("NO_FEATURES");
        }
        if (currentSubscribers > 0) {
            warnings.add("HAS_CURRENT_SUBSCRIBERS");
            warnings.add("TEMPLATE_EDITS_DO_NOT_UPDATE_EXISTING_SNAPSHOTS");
        } else if (historicalSubscribers > 0) {
            warnings.add("HAS_SUBSCRIPTION_HISTORY");
        }
        return warnings;
    }
}
