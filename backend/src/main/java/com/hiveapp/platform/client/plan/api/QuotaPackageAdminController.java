package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageLifecycleAction;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageChooserItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageRevisionRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageRevisionResult;
import com.hiveapp.platform.client.plan.dto.QuotaPackageActivationPreviewDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageLifecycleRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageComparisonDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageHistoryEntryDto;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.platform.client.plan.dto.UpdateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.service.PlanAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/admin/quota-packages")
@RequiredArgsConstructor
public class QuotaPackageAdminController {

    private static final Map<String, String> PRODUCT_SORTS = Map.of(
            "code", "code",
            "name", "name",
            "resource", "resource",
            "status", "status",
            "revisionNumber", "revisionNumber",
            "salesVisibility", "salesVisibility",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");
    private static final Map<String, String> CHOOSER_SORTS = Map.of(
            "code", "code", "name", "name", "resource", "resource");

    private final PlanAdminService planAdminService;

    @GetMapping
    public PageResponse<QuotaPackageOperationalListItemDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) QuotaPackageStatus status,
            @RequestParam(required = false) ProductSalesVisibility salesVisibility,
            @RequestParam(required = false) UUID lineageId,
            @RequestParam(required = false) String featureCode,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String targetPlanCode,
            @RequestParam(required = false) String targetAddOnCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(planAdminService.listQuotaPackages(
                search, status, salesVisibility, lineageId, featureCode, resource,
                targetPlanCode, targetAddOnCode,
                CommercialProductPageRequest.of(page, size, sort, direction, PRODUCT_SORTS,
                        "code", Sort.Direction.ASC)));
    }

    @GetMapping("/chooser")
    public PageResponse<QuotaPackageChooserItemDto> choose(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductSalesVisibility salesVisibility,
            @RequestParam(required = false) String featureCode,
            @RequestParam(required = false) String resource,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(planAdminService.chooseQuotaPackages(
                search, salesVisibility, featureCode, resource,
                CommercialProductPageRequest.of(page, size, sort, direction, CHOOSER_SORTS,
                        "name", Sort.Direction.ASC)));
    }

    @GetMapping("/chooser/selected")
    public List<QuotaPackageChooserItemDto> resolveChoices(@RequestParam List<UUID> ids) {
        return planAdminService.resolveQuotaPackageChoices(ids);
    }

    @GetMapping("/chooser/selected-codes")
    public List<QuotaPackageChooserItemDto> resolveChoicesByCode(@RequestParam List<String> codes) {
        return planAdminService.resolveQuotaPackageChoicesByCode(codes);
    }

    @GetMapping("/{quotaPackageId}")
    public QuotaPackageDto get(@PathVariable UUID quotaPackageId) {
        return planAdminService.getQuotaPackage(quotaPackageId);
    }

    @GetMapping("/{quotaPackageId}/operations")
    public QuotaPackageOperationalListItemDto operations(@PathVariable UUID quotaPackageId) {
        return planAdminService.getQuotaPackageOperations(quotaPackageId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuotaPackageDto create(@Valid @RequestBody CreateQuotaPackageRequest request) {
        return planAdminService.createQuotaPackage(request);
    }

    @PostMapping("/{sourceQuotaPackageId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuotaPackageRevisionResult revise(
            @PathVariable UUID sourceQuotaPackageId,
            @Valid @RequestBody QuotaPackageRevisionRequest request
    ) {
        return planAdminService.reviseQuotaPackage(sourceQuotaPackageId, request);
    }

    @GetMapping("/{quotaPackageId}/comparison")
    public QuotaPackageComparisonDto compare(
            @PathVariable UUID quotaPackageId,
            @RequestParam(required = false) UUID againstQuotaPackageId
    ) {
        return planAdminService.compareQuotaPackage(quotaPackageId, againstQuotaPackageId);
    }

    @GetMapping("/{quotaPackageId}/activation-preview")
    public QuotaPackageActivationPreviewDto activationPreview(@PathVariable UUID quotaPackageId) {
        return planAdminService.previewQuotaPackageActivation(quotaPackageId);
    }

    @PostMapping("/{quotaPackageId}/lifecycle")
    public QuotaPackageDto lifecycle(
            @PathVariable UUID quotaPackageId,
            @Valid @RequestBody QuotaPackageLifecycleRequest request
    ) {
        return planAdminService.changeQuotaPackageLifecycle(quotaPackageId, request);
    }

    @GetMapping("/{quotaPackageId}/history")
    public PageResponse<QuotaPackageHistoryEntryDto> history(
            @PathVariable UUID quotaPackageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(planAdminService.quotaPackageHistory(
                quotaPackageId,
                CommercialProductPageRequest.of(
                        page, size, "occurredAt", "desc",
                        Map.of("occurredAt", "occurredAt"), "occurredAt", Sort.Direction.DESC)));
    }

    @PutMapping("/{quotaPackageId}")
    public QuotaPackageDto update(
            @PathVariable UUID quotaPackageId,
            @Valid @RequestBody UpdateQuotaPackageRequest request
    ) {
        return planAdminService.updateQuotaPackage(quotaPackageId, request);
    }

    @PatchMapping("/{quotaPackageId}/status")
    public QuotaPackageDto transitionStatus(
            @PathVariable UUID quotaPackageId,
            @RequestParam QuotaPackageStatus status,
            @RequestParam long expectedVersion,
            @RequestParam String reason,
            @RequestParam(required = false) String activationPreviewToken
    ) {
        if (status == QuotaPackageStatus.DRAFT) {
            throw new InvalidRequestException("A capacity package cannot transition back to DRAFT.");
        }
        QuotaPackageLifecycleAction action = switch (status) {
            case ACTIVE -> QuotaPackageLifecycleAction.ACTIVATE;
            case INACTIVE -> QuotaPackageLifecycleAction.DEACTIVATE;
            case ARCHIVED -> QuotaPackageLifecycleAction.ARCHIVE;
            case DRAFT -> throw new IllegalStateException("Handled above");
        };
        return planAdminService.changeQuotaPackageLifecycle(
                quotaPackageId,
                new QuotaPackageLifecycleRequest(
                        action, expectedVersion, reason, activationPreviewToken));
    }

    @DeleteMapping("/{quotaPackageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID quotaPackageId,
            @RequestParam long expectedVersion
    ) {
        planAdminService.deleteQuotaPackage(quotaPackageId, expectedVersion);
    }

}
