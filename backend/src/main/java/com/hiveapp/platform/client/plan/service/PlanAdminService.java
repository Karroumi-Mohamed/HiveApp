package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
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
import com.hiveapp.platform.client.plan.dto.CommercialOverviewDto;
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

import com.hiveapp.platform.client.plan.dto.PlanDto;

import com.hiveapp.platform.client.plan.dto.PlanFeatureDto;

import com.hiveapp.platform.client.plan.dto.AddOnDto;

import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;

public interface PlanAdminService {

    CommercialOverviewDto getCommercialOverview();

    List<PlanDto> listPlans();

    PlanDetailDto getPlanDetail(UUID planId);

    PlanDto createPlan(CreatePlanRequest request);

    PlanDto duplicatePlan(UUID sourcePlanId, PlanBranchRequest request);

    PlanDto revisePlan(UUID sourcePlanId, PlanBranchRequest request);

    PlanDto updatePlan(UUID planId, UpdatePlanRequest request);

    PlanDto transitionStatus(UUID planId, PlanStatus targetStatus);

    PlanDeletionPreview previewPlanDeletion(UUID planId);

    void deletePlan(UUID planId, DeletePlanRequest request);

    List<PlanFeatureDto> listPlanFeatures(UUID planId);

    Page<PlanSubscriberDto> listPlanSubscribers(
            UUID planId, String search, com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus status,
            Pageable pageable);

    Page<PlanSubscriberOwnerLookupDto> findPlanSubscribersByOwnerEmail(
            UUID planId, String ownerEmail, Pageable pageable);

    PlanFeatureDto assignFeature(UUID planId, AssignPlanFeatureRequest request);

    PlanFeatureDto updateFeature(UUID planId, UUID planFeatureId, AssignPlanFeatureRequest request);

    void removeFeature(UUID planId, UUID planFeatureId);

    List<AddOnDto> listAddOns();

    AddOnDto getAddOn(UUID addOnId);

    AddOnDto createAddOn(CreateAddOnRequest request);

    AddOnDto reviseAddOn(UUID sourceAddOnId);

    AddOnDto updateAddOn(UUID addOnId, UpdateAddOnRequest request);

    AddOnDto transitionAddOnStatus(UUID addOnId, AddOnStatus targetStatus);

    void deleteAddOn(UUID addOnId);

    AddOnDto.FeatureItem assignAddOnFeature(UUID addOnId, AssignAddOnFeatureRequest request);

    AddOnDto.FeatureItem updateAddOnFeature(UUID addOnId, UUID addOnFeatureId, AssignAddOnFeatureRequest request);

    void removeAddOnFeature(UUID addOnId, UUID addOnFeatureId);

    List<QuotaPackageDto> listQuotaPackages();

    QuotaPackageDto getQuotaPackage(UUID quotaPackageId);

    QuotaPackageDto createQuotaPackage(CreateQuotaPackageRequest request);

    QuotaPackageDto updateQuotaPackage(UUID quotaPackageId, UpdateQuotaPackageRequest request);

    QuotaPackageDto transitionQuotaPackageStatus(UUID quotaPackageId, QuotaPackageStatus targetStatus);

    void deleteQuotaPackage(UUID quotaPackageId);
}
