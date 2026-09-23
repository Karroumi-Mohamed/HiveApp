package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductType;
import com.hiveapp.platform.client.plan.dto.CommercialAvailabilityHistoryEntryDto;
import com.hiveapp.platform.client.plan.dto.ExtensionCompatibilityDto;
import com.hiveapp.platform.client.plan.dto.PlanAvailabilityMutationRequest;
import com.hiveapp.platform.client.plan.dto.PlanAvailabilityPreviewDto;
import com.hiveapp.platform.client.plan.dto.PlanAvailabilityPreviewRequest;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.ProductVisibilityMutationRequest;
import com.hiveapp.platform.client.plan.dto.ProductVisibilityPreviewDto;
import com.hiveapp.platform.client.plan.dto.ProductVisibilityPreviewRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CommercialAvailabilityService {
    Page<ExtensionCompatibilityDto> inspect(
            UUID planId, String search, CommercialProductType type, Boolean available,
            String currencyCode, BillingCycle billingCycle, String featureCode, Pageable pageable);

    PlanAvailabilityPreviewDto previewPlan(UUID planId, PlanAvailabilityPreviewRequest request);
    PlanDto updatePlan(UUID planId, PlanAvailabilityMutationRequest request);

    ProductVisibilityPreviewDto previewAddOn(UUID addOnId, ProductVisibilityPreviewRequest request);
    com.hiveapp.platform.client.plan.dto.AddOnDto updateAddOn(
            UUID addOnId, ProductVisibilityMutationRequest request);

    ProductVisibilityPreviewDto previewQuotaPackage(
            UUID quotaPackageId, ProductVisibilityPreviewRequest request);
    com.hiveapp.platform.client.plan.dto.QuotaPackageDto updateQuotaPackage(
            UUID quotaPackageId, ProductVisibilityMutationRequest request);

    Page<CommercialAvailabilityHistoryEntryDto> history(UUID productId, Pageable pageable);
}
