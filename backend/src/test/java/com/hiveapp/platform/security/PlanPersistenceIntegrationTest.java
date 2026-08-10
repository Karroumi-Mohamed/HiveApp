package com.hiveapp.platform.security;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.money.Money;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanPersistenceIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private PlanRepository planRepository;
    @Autowired private PlanFeatureRepository planFeatureRepository;
    @Autowired private FeatureRepository featureRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void databaseRejectsDuplicatePlanCodesThatBypassTheServicePrecheck() {
        String code = "DUP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Plan first = plan(code);
        planRepository.saveAndFlush(first);

        try {
            assertThatThrownBy(() -> planRepository.saveAndFlush(plan(code)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            planRepository.findByCode(code).ifPresent(planRepository::delete);
        }
    }

    @Test
    void databaseRejectsDuplicatePlanFeatureMappingsThatBypassTheServicePrecheck() {
        Plan free = planRepository.findByCode("FREE").orElseThrow();
        var workspace = featureRepository.findByCode("platform.workspace").orElseThrow();
        PlanFeature duplicate = new PlanFeature();
        duplicate.setPlan(free);
        duplicate.setFeature(workspace);
        duplicate.setQuotaConfigs(new ArrayList<>());

        assertThatThrownBy(() -> planFeatureRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNullPlanBillingCycleAndActiveState() {
        assertThatThrownBy(() -> insertRawPlan(null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawPlan("MONTHLY", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertRawPlan(String billingCycle, Boolean active) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                        insert into plans
                            (id, code, name, price, currency_code, billing_cycle, is_active, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(), "RAW_" + UUID.randomUUID().toString().replace("-", ""), "Raw Plan",
                BigDecimal.ZERO, "USD", billingCycle, active, now, now);
    }

    private Plan plan(String code) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(code);
        plan.setMoney(Money.zero("USD"));
        plan.setBillingCycle(BillingCycle.MONTHLY);
        return plan;
    }
}
