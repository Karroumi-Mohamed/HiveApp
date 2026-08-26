package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductType;
import com.hiveapp.platform.client.plan.dto.AddOnDto;
import com.hiveapp.platform.client.plan.dto.CommercialAvailabilityHistoryEntryDto;
import com.hiveapp.platform.client.plan.dto.ExtensionCompatibilityDto;
import com.hiveapp.platform.client.plan.dto.PlanAvailabilityMutationRequest;
import com.hiveapp.platform.client.plan.dto.PlanAvailabilityPreviewDto;
import com.hiveapp.platform.client.plan.dto.PlanAvailabilityPreviewRequest;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.ProductVisibilityMutationRequest;
import com.hiveapp.platform.client.plan.dto.ProductVisibilityPreviewDto;
import com.hiveapp.platform.client.plan.dto.ProductVisibilityPreviewRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;
import com.hiveapp.platform.client.plan.service.CommercialAvailabilityService;
import com.hiveapp.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class CommercialAvailabilityAdminController {

    private final CommercialAvailabilityService service;

    @GetMapping("/plans/{planId}/extensions/compatibility")
    public PageResponse<ExtensionCompatibilityDto> inspect(
            @PathVariable UUID planId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CommercialProductType type,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) BillingCycle billingCycle,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PageResponse.from(service.inspect(
                planId, search, type, available, currencyCode, billingCycle, pageable));
    }

    @PostMapping("/plans/{planId}/commercial-availability/preview")
    public PlanAvailabilityPreviewDto previewPlan(
            @PathVariable UUID planId,
            @Valid @RequestBody PlanAvailabilityPreviewRequest request
    ) {
        return service.previewPlan(planId, request);
    }

    @PatchMapping("/plans/{planId}/commercial-availability")
    public PlanDto updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody PlanAvailabilityMutationRequest request
    ) {
        return service.updatePlan(planId, request);
    }

    @PostMapping("/add-ons/{addOnId}/sales-visibility/preview")
    public ProductVisibilityPreviewDto previewAddOn(
            @PathVariable UUID addOnId,
            @Valid @RequestBody ProductVisibilityPreviewRequest request
    ) {
        return service.previewAddOn(addOnId, request);
    }

    @PatchMapping("/add-ons/{addOnId}/sales-visibility")
    public AddOnDto updateAddOn(
            @PathVariable UUID addOnId,
            @Valid @RequestBody ProductVisibilityMutationRequest request
    ) {
        return service.updateAddOn(addOnId, request);
    }

    @PostMapping("/quota-packages/{quotaPackageId}/sales-visibility/preview")
    public ProductVisibilityPreviewDto previewQuotaPackage(
            @PathVariable UUID quotaPackageId,
            @Valid @RequestBody ProductVisibilityPreviewRequest request
    ) {
        return service.previewQuotaPackage(quotaPackageId, request);
    }

    @PatchMapping("/quota-packages/{quotaPackageId}/sales-visibility")
    public QuotaPackageDto updateQuotaPackage(
            @PathVariable UUID quotaPackageId,
            @Valid @RequestBody ProductVisibilityMutationRequest request
    ) {
        return service.updateQuotaPackage(quotaPackageId, request);
    }

    @GetMapping("/commercial-products/{productId}/availability-history")
    public PageResponse<CommercialAvailabilityHistoryEntryDto> history(
            @PathVariable UUID productId,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(service.history(productId, pageable));
    }
}
