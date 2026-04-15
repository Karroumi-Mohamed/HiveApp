package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;

import java.util.List;
import java.util.UUID;

public interface PlanAdminService {

    List<Plan> listPlans();

    Plan createPlan(CreatePlanRequest request);

    Plan toggleActive(UUID planId, boolean active);

    List<PlanFeature> listPlanFeatures(UUID planId);

    PlanFeature assignFeature(UUID planId, AssignPlanFeatureRequest request);

    PlanFeature updateFeature(UUID planId, UUID planFeatureId, AssignPlanFeatureRequest request);

    void removeFeature(UUID planId, UUID planFeatureId);
}
