package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.dto.AssignAddOnFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.UpdateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.UpdateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.DeletePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.PlanDeletionPreview;
import com.hiveapp.platform.client.plan.dto.PlanDetailDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberOwnerLookupDto;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PlanAdminService {

    List<Plan> listPlans();

    PlanDetailDto getPlanDetail(UUID planId);

    Plan createPlan(CreatePlanRequest request);

    Plan duplicatePlan(UUID sourcePlanId, PlanBranchRequest request);

    Plan revisePlan(UUID sourcePlanId, PlanBranchRequest request);

    Plan updatePlan(UUID planId, UpdatePlanRequest request);

    Plan transitionStatus(UUID planId, PlanStatus targetStatus);

    PlanDeletionPreview previewPlanDeletion(UUID planId);

    void deletePlan(UUID planId, DeletePlanRequest request);

    List<PlanFeature> listPlanFeatures(UUID planId);

    Page<PlanSubscriberDto> listPlanSubscribers(
            UUID planId, String search, com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus status,
            Pageable pageable);

    Page<PlanSubscriberOwnerLookupDto> findPlanSubscribersByOwnerEmail(
            UUID planId, String ownerEmail, Pageable pageable);

    PlanFeature assignFeature(UUID planId, AssignPlanFeatureRequest request);

    PlanFeature updateFeature(UUID planId, UUID planFeatureId, AssignPlanFeatureRequest request);

    void removeFeature(UUID planId, UUID planFeatureId);

    List<AddOn> listAddOns();

    AddOn getAddOn(UUID addOnId);

    AddOn createAddOn(CreateAddOnRequest request);

    AddOn updateAddOn(UUID addOnId, UpdateAddOnRequest request);

    AddOn transitionAddOnStatus(UUID addOnId, AddOnStatus targetStatus);

    void deleteAddOn(UUID addOnId);

    AddOnFeature assignAddOnFeature(UUID addOnId, AssignAddOnFeatureRequest request);

    AddOnFeature updateAddOnFeature(UUID addOnId, UUID addOnFeatureId, AssignAddOnFeatureRequest request);

    void removeAddOnFeature(UUID addOnId, UUID addOnFeatureId);

    List<QuotaPackage> listQuotaPackages();

    QuotaPackage getQuotaPackage(UUID quotaPackageId);

    QuotaPackage createQuotaPackage(CreateQuotaPackageRequest request);

    QuotaPackage updateQuotaPackage(UUID quotaPackageId, UpdateQuotaPackageRequest request);

    QuotaPackage transitionQuotaPackageStatus(UUID quotaPackageId, QuotaPackageStatus targetStatus);

    void deleteQuotaPackage(UUID quotaPackageId);
}
