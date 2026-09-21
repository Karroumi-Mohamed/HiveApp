package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.repository.PlanPublicVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlanPublicSelection {
    private final PlanPublicVersionRepository repository;

    /** Legacy families keep V1, never the largest version. Inactive V1 does not promote V2. */
    @Transactional(readOnly = true)
    public Map<UUID, Plan> choices(Collection<Plan> roots) {
        Map<UUID, Plan> result = new HashMap<>();
        roots.stream().filter(plan -> plan.getRevisionNumber() == 1)
                .forEach(plan -> result.put(plan.getLineageId(), plan));
        var families = roots.stream().map(Plan::getLineageId).distinct().toList();
        if (!families.isEmpty()) repository.findAllByLineageIdIn(families)
                .forEach(choice -> result.put(choice.getLineageId(), choice.getPlan()));
        return result;
    }

    @Transactional(readOnly = true)
    public boolean isPublicChoice(Plan plan) {
        var explicit = repository.findAllByLineageIdIn(java.util.List.of(plan.getLineageId()));
        return explicit.isEmpty() ? plan.getRevisionNumber() == 1
                : explicit.getFirst().getPlan().getId().equals(plan.getId());
    }
}
