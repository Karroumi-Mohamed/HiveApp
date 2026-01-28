package com.hiveapp.plan.api;

import com.hiveapp.plan.domain.dto.CreatePlanRequest;
import com.hiveapp.plan.domain.dto.PlanResponse;
import com.hiveapp.plan.domain.dto.UpdatePlanRequest;
import com.hiveapp.plan.domain.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.createPlan(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PlanResponse>> getAllActivePlans() {
        return ResponseEntity.ok(planService.getAllActivePlans());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlanResponse> getPlanById(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.getPlanById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponse> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanRequest request
    ) {
        return ResponseEntity.ok(planService.updatePlan(id, request));
    }

    @PostMapping("/{planId}/features/{featureId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addFeatureToPlan(@PathVariable UUID planId, @PathVariable UUID featureId) {
        planService.addFeatureToPlan(planId, featureId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{planId}/features/{featureId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeFeatureFromPlan(@PathVariable UUID planId, @PathVariable UUID featureId) {
        planService.removeFeatureFromPlan(planId, featureId);
        return ResponseEntity.noContent().build();
    }
}
