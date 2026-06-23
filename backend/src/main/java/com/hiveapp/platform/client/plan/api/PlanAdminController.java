package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanDetailDto;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.PlanFeatureDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberDto;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.platform.client.plan.service.PlanAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
public class PlanAdminController {

    private final PlanAdminService planAdminService;

    @GetMapping
    public List<PlanDto> listPlans() {
        return planAdminService.listPlans().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{planId}")
    public PlanDetailDto getPlanDetail(@PathVariable UUID planId) {
        return planAdminService.getPlanDetail(planId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto createPlan(@Valid @RequestBody CreatePlanRequest request) {
        var p = planAdminService.createPlan(request);
        return toDto(p);
    }

    @PutMapping("/{planId}")
    public PlanDto updatePlan(@PathVariable UUID planId, @Valid @RequestBody UpdatePlanRequest request) {
        return toDto(planAdminService.updatePlan(planId, request));
    }

    @PatchMapping("/{planId}/active")
    public PlanDto toggleActive(@PathVariable UUID planId, @RequestParam boolean active) {
        var p = planAdminService.toggleActive(planId, active);
        return toDto(p);
    }

    @DeleteMapping("/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable UUID planId) {
        planAdminService.deletePlan(planId);
    }

    // --- Feature composition ---

    @GetMapping("/{planId}/features")
    public List<PlanFeatureDto> listFeatures(@PathVariable UUID planId) {
        return planAdminService.listPlanFeatures(planId).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{planId}/subscribers")
    public List<PlanSubscriberDto> listSubscribers(@PathVariable UUID planId) {
        return planAdminService.listPlanSubscribers(planId);
    }

    @PostMapping("/{planId}/features")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanFeatureDto assignFeature(@PathVariable UUID planId,
                                        @Valid @RequestBody AssignPlanFeatureRequest request) {
        return toDto(planAdminService.assignFeature(planId, request));
    }

    @PutMapping("/{planId}/features/{planFeatureId}")
    public PlanFeatureDto updateFeature(@PathVariable UUID planId,
                                        @PathVariable UUID planFeatureId,
                                        @Valid @RequestBody AssignPlanFeatureRequest request) {
        return toDto(planAdminService.updateFeature(planId, planFeatureId, request));
    }

    @DeleteMapping("/{planId}/features/{planFeatureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFeature(@PathVariable UUID planId, @PathVariable UUID planFeatureId) {
        planAdminService.removeFeature(planId, planFeatureId);
    }

    private PlanFeatureDto toDto(PlanFeature pf) {
        return new PlanFeatureDto(
                pf.getId(),
                pf.getFeature().getCode(),
                pf.getAddOnPrice(),
                pf.getQuotaConfigs()
        );
    }

    private PlanDto toDto(com.hiveapp.platform.client.plan.domain.entity.Plan p) {
        return new PlanDto(p.getId(), p.getCode(), p.getName(),
                p.getDescription(), p.getPrice(), p.getBillingCycle(), p.isActive());
    }
}
