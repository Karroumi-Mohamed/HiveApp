package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.dto.AddOnDto;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/add-ons")
@RequiredArgsConstructor
public class AddOnAdminController {

    private final PlanAdminService planAdminService;

    @GetMapping
    public List<AddOnDto> list() {
        return planAdminService.listAddOns();
    }

    @GetMapping("/{addOnId}")
    public AddOnDto get(@PathVariable UUID addOnId) {
        return planAdminService.getAddOn(addOnId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddOnDto create(@Valid @RequestBody CreateAddOnRequest request) {
        return planAdminService.createAddOn(request);
    }

    @PostMapping("/{sourceAddOnId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public AddOnDto revise(@PathVariable UUID sourceAddOnId) {
        return planAdminService.reviseAddOn(sourceAddOnId);
    }

    @PutMapping("/{addOnId}")
    public AddOnDto update(@PathVariable UUID addOnId, @Valid @RequestBody UpdateAddOnRequest request) {
        return planAdminService.updateAddOn(addOnId, request);
    }

    @PatchMapping("/{addOnId}/status")
    public AddOnDto transitionStatus(@PathVariable UUID addOnId, @RequestParam AddOnStatus status) {
        return planAdminService.transitionAddOnStatus(addOnId, status);
    }

    @DeleteMapping("/{addOnId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID addOnId) {
        planAdminService.deleteAddOn(addOnId);
    }

    @PostMapping("/{addOnId}/features")
    @ResponseStatus(HttpStatus.CREATED)
    public AddOnDto.FeatureItem assignFeature(
            @PathVariable UUID addOnId, @Valid @RequestBody AssignAddOnFeatureRequest request) {
        return planAdminService.assignAddOnFeature(addOnId, request);
    }

    @PutMapping("/{addOnId}/features/{addOnFeatureId}")
    public AddOnDto.FeatureItem updateFeature(
            @PathVariable UUID addOnId,
            @PathVariable UUID addOnFeatureId,
            @Valid @RequestBody AssignAddOnFeatureRequest request) {
        return planAdminService.updateAddOnFeature(addOnId, addOnFeatureId, request);
    }

    @DeleteMapping("/{addOnId}/features/{addOnFeatureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFeature(@PathVariable UUID addOnId, @PathVariable UUID addOnFeatureId) {
        planAdminService.removeAddOnFeature(addOnId, addOnFeatureId);
    }

}
