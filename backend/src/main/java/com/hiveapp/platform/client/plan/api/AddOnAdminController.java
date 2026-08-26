package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.dto.AddOnDto;
import com.hiveapp.platform.client.plan.dto.AddOnOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.AddOnChooserItemDto;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.platform.client.plan.dto.AssignAddOnFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.UpdateAddOnRequest;
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
@RequestMapping("/api/admin/add-ons")
@RequiredArgsConstructor
public class AddOnAdminController {

    private static final Map<String, String> PRODUCT_SORTS = Map.of(
            "code", "code",
            "name", "name",
            "status", "status",
            "revisionNumber", "revisionNumber",
            "salesVisibility", "salesVisibility",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");
    private static final Map<String, String> CHOOSER_SORTS = Map.of(
            "code", "code", "name", "name");

    private final PlanAdminService planAdminService;

    @GetMapping
    public PageResponse<AddOnOperationalListItemDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AddOnStatus status,
            @RequestParam(required = false) ProductSalesVisibility salesVisibility,
            @RequestParam(required = false) UUID lineageId,
            @RequestParam(required = false) String featureCode,
            @RequestParam(required = false) String targetPlanCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(planAdminService.listAddOns(
                search, status, salesVisibility, lineageId, featureCode, targetPlanCode,
                CommercialProductPageRequest.of(page, size, sort, direction, PRODUCT_SORTS,
                        "name", Sort.Direction.ASC)));
    }

    @GetMapping("/chooser")
    public PageResponse<AddOnChooserItemDto> choose(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductSalesVisibility salesVisibility,
            @RequestParam(required = false) String featureCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(planAdminService.chooseAddOns(
                search, salesVisibility, featureCode,
                CommercialProductPageRequest.of(page, size, sort, direction, CHOOSER_SORTS,
                        "name", Sort.Direction.ASC)));
    }

    @GetMapping("/chooser/selected")
    public List<AddOnChooserItemDto> resolveChoices(@RequestParam List<UUID> ids) {
        return planAdminService.resolveAddOnChoices(ids);
    }

    @GetMapping("/chooser/selected-codes")
    public List<AddOnChooserItemDto> resolveChoicesByCode(@RequestParam List<String> codes) {
        return planAdminService.resolveAddOnChoicesByCode(codes);
    }

    @GetMapping("/{addOnId}")
    public AddOnDto get(@PathVariable UUID addOnId) {
        return planAdminService.getAddOn(addOnId);
    }

    @GetMapping("/{addOnId}/operations")
    public AddOnOperationalListItemDto operations(@PathVariable UUID addOnId) {
        return planAdminService.getAddOnOperations(addOnId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddOnDto create(@Valid @RequestBody CreateAddOnRequest request) {
        return planAdminService.createAddOn(request);
    }

    @PostMapping("/{sourceAddOnId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public AddOnDto revise(
            @PathVariable UUID sourceAddOnId,
            @RequestParam long expectedVersion) {
        return planAdminService.reviseAddOn(sourceAddOnId, expectedVersion);
    }

    @PutMapping("/{addOnId}")
    public AddOnDto update(@PathVariable UUID addOnId, @Valid @RequestBody UpdateAddOnRequest request) {
        return planAdminService.updateAddOn(addOnId, request);
    }

    @PatchMapping("/{addOnId}/status")
    public AddOnDto transitionStatus(
            @PathVariable UUID addOnId,
            @RequestParam AddOnStatus status,
            @RequestParam long expectedVersion,
            @RequestParam(required = false) String reason) {
        return planAdminService.transitionAddOnStatus(addOnId, status, expectedVersion, reason);
    }

    @DeleteMapping("/{addOnId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID addOnId,
            @RequestParam long expectedVersion) {
        planAdminService.deleteAddOn(addOnId, expectedVersion);
    }

    @PostMapping("/{addOnId}/features")
    @ResponseStatus(HttpStatus.CREATED)
    public AddOnDto.FeatureItem assignFeature(
            @PathVariable UUID addOnId,
            @RequestParam long expectedVersion,
            @Valid @RequestBody AssignAddOnFeatureRequest request) {
        return planAdminService.assignAddOnFeature(addOnId, expectedVersion, request);
    }

    @PutMapping("/{addOnId}/features/{addOnFeatureId}")
    public AddOnDto.FeatureItem updateFeature(
            @PathVariable UUID addOnId,
            @PathVariable UUID addOnFeatureId,
            @RequestParam long expectedVersion,
            @Valid @RequestBody AssignAddOnFeatureRequest request) {
        return planAdminService.updateAddOnFeature(addOnId, addOnFeatureId, expectedVersion, request);
    }

    @DeleteMapping("/{addOnId}/features/{addOnFeatureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFeature(@PathVariable UUID addOnId, @PathVariable UUID addOnFeatureId,
                              @RequestParam long expectedVersion) {
        planAdminService.removeAddOnFeature(addOnId, addOnFeatureId, expectedVersion);
    }

}
