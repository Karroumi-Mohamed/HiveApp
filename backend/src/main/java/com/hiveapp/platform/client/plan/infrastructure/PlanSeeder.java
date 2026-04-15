package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanSeeder implements CommandLineRunner {

    private final PlanRepository planRepository;

    @Override
    public void run(String... args) throws Exception {
        if (planRepository.count() == 0) {
            Plan freePlan = new Plan();
            freePlan.setCode("FREE");
            freePlan.setName("Free Plan");
            freePlan.setPrice(new java.math.BigDecimal("0.00"));
            planRepository.save(freePlan);
            
            Plan proPlan = new Plan();
            proPlan.setCode("PRO");
            proPlan.setName("Pro Plan");
            proPlan.setPrice(new java.math.BigDecimal("29.99"));
            planRepository.save(proPlan);
            
            Plan enterprisePlan = new Plan();
            enterprisePlan.setCode("ENTERPRISE");
            enterprisePlan.setName("Enterprise Plan");
            enterprisePlan.setPrice(new java.math.BigDecimal("99.99"));
            planRepository.save(enterprisePlan);
        }
    }
}
