package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanCodes;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanDetailDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberDto;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = PlansFeature.KEY, description = "Plan Catalogue Management", guard = PermissionNode.Guard.ON)
public class PlanAdminServiceImpl extends PlatformControlFeatureService implements PlanAdminService {

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingConfigurationValidator billingConfigurationValidator;

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
                plan.isActive(),
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
        Plan savedPlan = planRepository.save(plan);
        inheritPlanComposition(savedPlan, request.inheritFromPlanId());
        return savedPlan;
    }

    @Override
    @Transactional
    @PermissionNode(key = "update", description = "Update plan template basics")
    public Plan updatePlan(UUID planId, UpdatePlanRequest request) {
        Plan plan = requirePlan(planId);
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
    @PermissionNode(key = "toggle_active", description = "Activate or deactivate a plan")
    public Plan toggleActive(UUID planId, boolean active) {
        var plan = requirePlan(planId);
        if (PlanCodes.DEFAULT.equals(plan.getCode()) && !active) {
            throw new BusinessException("The default FREE plan cannot be deactivated because workspace provisioning requires it.");
        }
        plan.setActive(active);
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
        var feature = billingConfigurationValidator.validatePlanFeature(
                request.featureCode(), request.addOnPrice(), request.addOnCurrencyCode(),
                request.quotaConfigs(), plan.getCurrencyCode());

        planFeatureRepository.findByPlanIdAndFeature_Code(planId, request.featureCode())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("PlanFeature", "featureCode", request.featureCode());
                });

        PlanFeature pf = new PlanFeature();
        pf.setPlan(plan);
        pf.setFeature(feature);
        pf.setAddOnMoney(request.addOnPrice() != null
                ? Money.of(request.addOnPrice(), request.addOnCurrencyCode())
                : null);
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
        billingConfigurationValidator.validatePlanFeature(
                request.featureCode(), request.addOnPrice(), request.addOnCurrencyCode(),
                request.quotaConfigs(), pf.getPlan().getCurrencyCode());
        pf.setAddOnMoney(request.addOnPrice() != null
                ? Money.of(request.addOnPrice(), request.addOnCurrencyCode())
                : null);
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
        planFeatureRepository.delete(pf);
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
                            sourceFeature.getAddOnPrice(),
                            sourceFeature.getAddOnCurrencyCode(),
                            sourceFeature.getQuotaConfigs(),
                            sourcePlan.getCurrencyCode());
                    PlanFeature copy = new PlanFeature();
                    copy.setPlan(targetPlan);
                    copy.setFeature(sourceFeature.getFeature());
                    copy.setAddOnMoney(sourceFeature.addOnMoney());
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
        return planFeature.getAddOnPrice() != null
                || (planFeature.getQuotaConfigs() != null
                && planFeature.getQuotaConfigs().stream().anyMatch(quota -> quota.pricePerUnit() != null));
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
        if (billingCycle == BillingCycle.FOREVER && !PlanCodes.DEFAULT.equals(code)) {
            throw new InvalidRequestException("BillingCycle.FOREVER is reserved for the FREE plan.");
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
        if (!plan.isActive()) {
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
