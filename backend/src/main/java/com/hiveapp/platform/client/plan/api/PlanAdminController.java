package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.CommercialOverviewDto;
import com.hiveapp.platform.client.plan.dto.DeletePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.PlanDeletionPreview;
import com.hiveapp.platform.client.plan.dto.PlanDetailDto;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.PlanFeatureDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberOwnerLookupDto;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.platform.client.plan.service.PlanAdminService;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
public class PlanAdminController {

    private final PlanAdminService planAdminService;

    @GetMapping("/overview")
    public CommercialOverviewDto overview() {
        return planAdminService.getCommercialOverview();
    }

    @GetMapping
    public List<PlanDto> listPlans() {
        return planAdminService.listPlans();
    }

    @GetMapping("/{planId}")
    public PlanDetailDto getPlanDetail(@PathVariable UUID planId) {
        return planAdminService.getPlanDetail(planId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto createPlan(@Valid @RequestBody CreatePlanRequest request) {
        var p = planAdminService.createPlan(request);
        return p;
    }

    @PostMapping("/{sourcePlanId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto duplicatePlan(
            @PathVariable UUID sourcePlanId,
            @Valid @RequestBody PlanBranchRequest request
    ) {
        return planAdminService.duplicatePlan(sourcePlanId, request);
    }

    @PostMapping("/{sourcePlanId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto revisePlan(
            @PathVariable UUID sourcePlanId,
            @Valid @RequestBody PlanBranchRequest request
    ) {
        return planAdminService.revisePlan(sourcePlanId, request);
    }

    @PutMapping("/{planId}")
    public PlanDto updatePlan(@PathVariable UUID planId, @Valid @RequestBody UpdatePlanRequest request) {
        return planAdminService.updatePlan(planId, request);
    }

    @PatchMapping("/{planId}/status")
    public PlanDto transitionStatus(@PathVariable UUID planId, @RequestParam PlanStatus status) {
        var p = planAdminService.transitionStatus(planId, status);
        return p;
    }

    @GetMapping("/{planId}/deletion-preview")
    public PlanDeletionPreview previewDeletion(@PathVariable UUID planId) {
        return planAdminService.previewPlanDeletion(planId);
    }

    @DeleteMapping("/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody DeletePlanRequest request
    ) {
        planAdminService.deletePlan(planId, request);
    }

    // --- Feature composition ---

    @GetMapping("/{planId}/features")
    public List<PlanFeatureDto> listFeatures(@PathVariable UUID planId) {
        return planAdminService.listPlanFeatures(planId);
    }

    @GetMapping("/{planId}/subscribers")
    public PageResponse<PlanSubscriberDto> listSubscribers(
            @PathVariable UUID planId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubscriptionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(planAdminService.listPlanSubscribers(planId, search, status, pageable));
    }

    @GetMapping("/{planId}/subscribers/by-owner-email")
    public PageResponse<PlanSubscriberOwnerLookupDto> subscribersByOwnerEmail(
            @PathVariable UUID planId,
            @RequestParam String ownerEmail,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(planAdminService.findPlanSubscribersByOwnerEmail(planId, ownerEmail, pageable));
    }

    @PostMapping("/{planId}/features")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanFeatureDto assignFeature(@PathVariable UUID planId,
                                        @Valid @RequestBody AssignPlanFeatureRequest request) {
        return planAdminService.assignFeature(planId, request);
    }

    @PutMapping("/{planId}/features/{planFeatureId}")
    public PlanFeatureDto updateFeature(@PathVariable UUID planId,
                                        @PathVariable UUID planFeatureId,
                                        @Valid @RequestBody AssignPlanFeatureRequest request) {
        return planAdminService.updateFeature(planId, planFeatureId, request);
    }

    @DeleteMapping("/{planId}/features/{planFeatureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFeature(@PathVariable UUID planId, @PathVariable UUID planFeatureId) {
        planAdminService.removeFeature(planId, planFeatureId);
    }

}
