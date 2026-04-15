package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanRepository planRepository;

    @GetMapping
    public List<PlanDto> listActivePlans() {
        return planRepository.findAll().stream()
                .filter(p -> p.isActive())
                .map(p -> new PlanDto(p.getId(), p.getCode(), p.getName(),
                        p.getDescription(), p.getPrice(), p.getBillingCycle(), p.isActive()))
                .toList();
    }
}
