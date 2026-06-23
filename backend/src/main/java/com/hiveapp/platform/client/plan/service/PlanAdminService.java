package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanDetailDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberDto;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;

import java.util.List;
import java.util.UUID;

public interface PlanAdminService {

    List<Plan> listPlans();

    PlanDetailDto getPlanDetail(UUID planId);

    Plan createPlan(CreatePlanRequest request);

    Plan updatePlan(UUID planId, UpdatePlanRequest request);

    Plan toggleActive(UUID planId, boolean active);

    void deletePlan(UUID planId);

    List<PlanFeature> listPlanFeatures(UUID planId);

    List<PlanSubscriberDto> listPlanSubscribers(UUID planId);

    PlanFeature assignFeature(UUID planId, AssignPlanFeatureRequest request);

    PlanFeature updateFeature(UUID planId, UUID planFeatureId, AssignPlanFeatureRequest request);

    void removeFeature(UUID planId, UUID planFeatureId);
}
