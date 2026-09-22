package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.admin.dto.OwnerEmailLookupRequest;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.CommercialOverviewDto;
import com.hiveapp.platform.client.plan.dto.DeletePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.PlanDeletionPreview;
import com.hiveapp.platform.client.plan.dto.PlanDetailDto;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.PlanOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.PlanChooserItemDto;
import com.hiveapp.platform.client.plan.dto.PlanFeatureDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberOwnerLookupDto;
import com.hiveapp.platform.client.plan.dto.PlanActivationPreviewDto;
import com.hiveapp.platform.client.plan.dto.PlanLifecycleRequest;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
public class PlanAdminController {

    private static final Map<String, String> PRODUCT_SORTS = Map.of(
            "code", "code",
            "name", "name",
            "status", "status",
            "revisionNumber", "revisionNumber",
            "extensionPolicy", "extensionPolicy",
            "salesVisibility", "salesVisibility",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private static final Map<String, String> CHOOSER_SORTS = Map.of(
            "code", "code", "name", "name");

    private final PlanAdminService planAdminService;

    @PostMapping("/{planId}/subscribers/{accountId}/version-preview")
    public com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.Preview previewVersionApplication(
            @PathVariable UUID planId, @PathVariable UUID accountId,
            @Valid @RequestBody com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.Request request) {
        return planAdminService.previewVersionApplication(planId, accountId, request);
    }

    @PostMapping("/{planId}/subscribers/{accountId}/apply-version")
    public com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.Result applyVersionNow(
            @PathVariable UUID planId, @PathVariable UUID accountId,
            @Valid @RequestBody com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.ApplyNow request) {
        return planAdminService.applyVersionNow(planId, accountId, request);
    }

    @GetMapping("/families")
    public PageResponse<com.hiveapp.platform.client.plan.dto.PlanVersionModels.Family> families(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(planAdminService.listPlanFamilies(search,
                CommercialProductPageRequest.of(page, size, "name", "ASC", PRODUCT_SORTS,
                        "name", Sort.Direction.ASC)));
    }

    @GetMapping("/{planId}/versions")
    public com.hiveapp.platform.client.plan.dto.PlanVersionModels.Versions versions(
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return planAdminService.listPlanVersions(planId,
                CommercialProductPageRequest.of(page, size, "revisionNumber", "DESC", PRODUCT_SORTS,
                        "revisionNumber", Sort.Direction.DESC));
    }

    @GetMapping("/{planId}/compare/{targetId}")
    public com.hiveapp.platform.client.plan.dto.PlanVersionModels.Comparison compare(
            @PathVariable UUID planId, @PathVariable UUID targetId) {
        return planAdminService.comparePlanVersions(planId, targetId);
    }

    @PostMapping("/{planId}/public-version")
    public com.hiveapp.platform.client.plan.dto.PlanVersionModels.Version selectPublic(@PathVariable UUID planId,
            @Valid @RequestBody com.hiveapp.platform.client.plan.dto.PlanVersionModels.SelectPublic request) {
        return planAdminService.selectPublicVersion(planId, request);
    }

    @PatchMapping("/{planId}/metadata")
    public com.hiveapp.platform.client.plan.dto.PlanVersionModels.Version metadata(@PathVariable UUID planId,
            @Valid @RequestBody com.hiveapp.platform.client.plan.dto.PlanVersionModels.Metadata request) {
        return planAdminService.updatePlanMetadata(planId, request);
    }

    @GetMapping("/overview")
    public CommercialOverviewDto overview() {
        return planAdminService.getCommercialOverview();
    }

    @GetMapping
    public PageResponse<PlanOperationalListItemDto> listPlans(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PlanStatus status,
            @RequestParam(required = false) ProductSalesVisibility salesVisibility,
            @RequestParam(required = false) PlanExtensionPolicy extensionPolicy,
            @RequestParam(required = false) UUID lineageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(planAdminService.listPlans(
                search, status, salesVisibility, extensionPolicy, lineageId,
                CommercialProductPageRequest.of(page, size, sort, direction, PRODUCT_SORTS,
                        "updatedAt", Sort.Direction.DESC)));
    }

    @GetMapping("/chooser")
    public PageResponse<PlanChooserItemDto> choosePlans(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductSalesVisibility salesVisibility,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(planAdminService.choosePlans(
                search, salesVisibility,
                CommercialProductPageRequest.of(page, size, sort, direction, CHOOSER_SORTS,
                        "name", Sort.Direction.ASC)));
    }

    @GetMapping("/chooser/selected")
    public List<PlanChooserItemDto> resolvePlanChoices(@RequestParam List<UUID> ids) {
        return planAdminService.resolvePlanChoices(ids);
    }

    @GetMapping("/chooser/selected-codes")
    public List<PlanChooserItemDto> resolvePlanChoicesByCode(@RequestParam List<String> codes) {
        return planAdminService.resolvePlanChoicesByCode(codes);
    }

    @GetMapping("/{planId}")
    public PlanDetailDto getPlanDetail(@PathVariable UUID planId) {
        return planAdminService.getPlanDetail(planId);
    }

    @GetMapping("/{planId}/operations")
    public PlanOperationalListItemDto operations(@PathVariable UUID planId) {
        return planAdminService.getPlanOperations(planId);
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
            @RequestParam long expectedVersion,
            @Valid @RequestBody PlanBranchRequest request
    ) {
        return planAdminService.duplicatePlan(sourcePlanId, expectedVersion, request);
    }

    @PostMapping({"/{sourcePlanId}/versions", "/{sourcePlanId}/revisions"})
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto createPlanVersion(
            @PathVariable UUID sourcePlanId,
            @RequestParam long expectedVersion,
            @Valid @RequestBody PlanBranchRequest request
    ) {
        // Keep the persisted permission identity and invoke its guarded service boundary.
        return planAdminService.createPlanVersion(sourcePlanId, expectedVersion, request);
    }

    @PutMapping("/{planId}")
    public PlanDto updatePlan(@PathVariable UUID planId, @Valid @RequestBody UpdatePlanRequest request) {
        return planAdminService.updatePlan(planId, request);
    }

    @PostMapping("/{planId}/lifecycle")
    public PlanDto transitionStatus(
            @PathVariable UUID planId,
            @Valid @RequestBody PlanLifecycleRequest request) {
        return planAdminService.transitionStatus(
                planId, request.action().targetStatus(), request.expectedVersion(), request.reason(),
                request.activationPreviewToken());
    }

    @GetMapping("/{planId}/activation-preview")
    public PlanActivationPreviewDto activationPreview(@PathVariable UUID planId) {
        return planAdminService.previewPlanActivation(planId);
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

    @GetMapping("/{planId}/family-subscribers")
    public PageResponse<com.hiveapp.platform.client.plan.dto.PlanSubscriberViewModels.Subscriber> familySubscribers(
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "ALL") com.hiveapp.platform.client.plan.dto.PlanSubscriberViewModels.View view,
            @RequestParam(required = false) String search, @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) com.hiveapp.platform.client.plan.domain.constant.BillingCycle cycle,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new com.hiveapp.shared.exception.InvalidRequestException("Choose a valid page and size from 1 to 100.");
        return PageResponse.from(planAdminService.listFamilySubscribers(planId, view, search, status, currency, cycle,
                org.springframework.data.domain.PageRequest.of(page, size)));
    }

    @GetMapping("/{planId}/version-history")
    public PageResponse<com.hiveapp.platform.client.plan.service.PlanVersionHistory.Event> versionHistory(
            @PathVariable UUID planId,
            @RequestParam(required = false) com.hiveapp.platform.client.plan.service.PlanVersionHistory.Kind kind,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) java.time.Instant from,
            @RequestParam(required = false) java.time.Instant until,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new com.hiveapp.shared.exception.InvalidRequestException("Choose a valid page and size from 1 to 100.");
        return PageResponse.from(planAdminService.planVersionHistory(planId, kind, actorId, from, until,
                org.springframework.data.domain.PageRequest.of(page, size)));
    }

    @PostMapping("/{planId}/subscribers/by-owner-email")
    public PageResponse<PlanSubscriberOwnerLookupDto> subscribersByOwnerEmail(
            @PathVariable UUID planId,
            @Valid @RequestBody OwnerEmailLookupRequest request,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(planAdminService.findPlanSubscribersByOwnerEmail(
                planId, request.ownerEmail(), pageable));
    }

    @PostMapping("/{planId}/features")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanFeatureDto assignFeature(@PathVariable UUID planId,
                                        @RequestParam long expectedVersion,
                                        @Valid @RequestBody AssignPlanFeatureRequest request) {
        return planAdminService.assignFeature(planId, expectedVersion, request);
    }

    @PutMapping("/{planId}/features/{planFeatureId}")
    public PlanFeatureDto updateFeature(@PathVariable UUID planId,
                                        @PathVariable UUID planFeatureId,
                                        @RequestParam long expectedVersion,
                                        @Valid @RequestBody AssignPlanFeatureRequest request) {
        return planAdminService.updateFeature(planId, planFeatureId, expectedVersion, request);
    }

    @DeleteMapping("/{planId}/features/{planFeatureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFeature(@PathVariable UUID planId, @PathVariable UUID planFeatureId,
                              @RequestParam long expectedVersion) {
        planAdminService.removeFeature(planId, planFeatureId, expectedVersion);
    }

}
