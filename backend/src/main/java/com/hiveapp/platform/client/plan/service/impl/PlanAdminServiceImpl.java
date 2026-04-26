package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.service.PlanAdminService;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = "plans", description = "Plan Catalogue Management")
public class PlanAdminServiceImpl implements PlanAdminService {

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final FeatureRepository featureRepository;

    @Override
    @PermissionNode(key = "list", description = "List all plans")
    public List<Plan> listPlans() {
        return planRepository.findAll();
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create a new plan")
    public Plan createPlan(CreatePlanRequest request) {
        if (planRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateResourceException("Plan", "code", request.code());
        }
        Plan plan = new Plan();
        plan.setCode(request.code());
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setPrice(request.price());
        plan.setBillingCycle(request.billingCycle());
        return planRepository.save(plan);
    }

    @Override
    @Transactional
    @PermissionNode(key = "toggle_active", description = "Activate or deactivate a plan")
    public Plan toggleActive(UUID planId, boolean active) {
        var plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        plan.setActive(active);
        return planRepository.save(plan);
    }

    @Override
    @PermissionNode(key = "list_features", description = "List features assigned to a plan")
    public List<PlanFeature> listPlanFeatures(UUID planId) {
        if (!planRepository.existsById(planId)) {
            throw new ResourceNotFoundException("Plan", "id", planId);
        }
        return planFeatureRepository.findAllByPlanId(planId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "assign_feature", description = "Assign a feature to a plan")
    public PlanFeature assignFeature(UUID planId, AssignPlanFeatureRequest request) {
        var plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        var feature = featureRepository.findByCode(request.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "code", request.featureCode()));

        planFeatureRepository.findByPlanIdAndFeature_Code(planId, request.featureCode())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("PlanFeature", "featureCode", request.featureCode());
                });

        PlanFeature pf = new PlanFeature();
        pf.setPlan(plan);
        pf.setFeature(feature);
        pf.setAddOnPrice(request.addOnPrice());
        pf.setQuotaConfigs(request.quotaConfigs() != null ? request.quotaConfigs() : new ArrayList<>());
        return planFeatureRepository.save(pf);
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
        pf.setAddOnPrice(request.addOnPrice());
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
}
