package com.hiveapp.plan.domain.service;

import com.hiveapp.plan.domain.dto.*;
import com.hiveapp.plan.domain.entity.Plan;
import com.hiveapp.plan.domain.entity.PlanFeature;
import com.hiveapp.plan.domain.mapper.PlanMapper;
import com.hiveapp.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.plan.domain.repository.PlanRepository;
import com.hiveapp.plan.event.PlanFeaturesChangedEvent;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PlanMapper planMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PlanResponse createPlan(CreatePlanRequest request) {
        if (planRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Plan", "code", request.getCode());
        }

        Plan plan = Plan.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .billingCycle(request.getBillingCycle())
                .maxCompanies(request.getMaxCompanies())
                .maxMembers(request.getMaxMembers())
                .build();

        if (request.getFeatureIds() != null) {
            for (UUID featureId : request.getFeatureIds()) {
                PlanFeature pf = PlanFeature.builder()
                        .featureId(featureId)
                        .build();
                plan.addFeature(pf);
            }
        }

        Plan saved = planRepository.save(plan);
        log.info("Plan created: {} ({})", saved.getName(), saved.getCode());
        return planMapper.toResponse(saved);
    }

    @Transactional
    public PlanResponse updatePlan(UUID id, UpdatePlanRequest request) {
        Plan plan = findPlanOrThrow(id);

        if (request.getName() != null) plan.setName(request.getName());
        if (request.getDescription() != null) plan.setDescription(request.getDescription());
        if (request.getPrice() != null) plan.setPrice(request.getPrice());
        if (request.getBillingCycle() != null) plan.setBillingCycle(request.getBillingCycle());
        if (request.getMaxCompanies() != null) plan.setMaxCompanies(request.getMaxCompanies());
        if (request.getMaxMembers() != null) plan.setMaxMembers(request.getMaxMembers());

        Plan saved = planRepository.save(plan);
        log.info("Plan updated: {}", saved.getId());
        return planMapper.toResponse(saved);
    }

    @Transactional
    public void addFeatureToPlan(UUID planId, UUID featureId) {
        Plan plan = findPlanOrThrow(planId);
        PlanFeature pf = PlanFeature.builder()
                .featureId(featureId)
                .build();
        plan.addFeature(pf);
        planRepository.save(plan);
        eventPublisher.publishEvent(new PlanFeaturesChangedEvent(planId));
        log.info("Feature {} added to plan {}", featureId, planId);
    }

    @Transactional
    public void removeFeatureFromPlan(UUID planId, UUID featureId) {
        planFeatureRepository.deleteByPlanIdAndFeatureId(planId, featureId);
        eventPublisher.publishEvent(new PlanFeaturesChangedEvent(planId));
        log.info("Feature {} removed from plan {}", featureId, planId);
    }

    public PlanResponse getPlanById(UUID id) {
        Plan plan = planRepository.findByIdWithFeatures(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", id));
        return planMapper.toResponse(plan);
    }

    public List<PlanResponse> getAllActivePlans() {
        return planMapper.toResponseList(planRepository.findAllActiveWithFeatures());
    }

    public Set<UUID> getFeatureIdsForPlan(UUID planId) {
        return planFeatureRepository.findFeatureIdsByPlanId(planId);
    }

    public Plan findPlanOrThrow(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", id));
    }
}
