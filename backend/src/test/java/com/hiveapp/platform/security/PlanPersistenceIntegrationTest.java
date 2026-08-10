package com.hiveapp.platform.security;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
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
    @Autowired private AddOnRepository addOnRepository;
    @Autowired private AddOnFeatureRepository addOnFeatureRepository;
    @Autowired private QuotaPackageRepository quotaPackageRepository;
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
        duplicate.setMode(PlanFeatureMode.INCLUDED);
        duplicate.setQuotaConfigs(new ArrayList<>());

        assertThatThrownBy(() -> planFeatureRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNullPlanBillingCycleAndLifecycleStatus() {
        assertThatThrownBy(() -> insertRawPlan(null, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawPlan("MONTHLY", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateAddOnCodes() {
        String code = "ADDON_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AddOn first = addOn(code);
        addOnRepository.saveAndFlush(first);

        try {
            assertThatThrownBy(() -> addOnRepository.saveAndFlush(addOn(code)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            addOnRepository.findByCode(code).ifPresent(addOnRepository::delete);
        }
    }

    @Test
    void databaseRejectsDuplicateFeatureWithinOneAddOn() {
        AddOn addOn = addOnRepository.saveAndFlush(addOn(
                "ADDON_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
        var company = featureRepository.findByCode("platform.company").orElseThrow();
        AddOnFeature first = new AddOnFeature();
        first.setAddOn(addOn);
        first.setFeature(company);
        addOnFeatureRepository.saveAndFlush(first);

        AddOnFeature duplicate = new AddOnFeature();
        duplicate.setAddOn(addOn);
        duplicate.setFeature(company);
        try {
            assertThatThrownBy(() -> addOnFeatureRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            addOnFeatureRepository.deleteAll(addOnFeatureRepository.findAllByAddOnId(addOn.getId()));
            addOnRepository.delete(addOn);
        }
    }

    @Test
    void databaseRejectsDuplicateQuotaPackageCodes() {
        String code = "QUOTA_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        QuotaPackage first = quotaPackage(code);
        quotaPackageRepository.saveAndFlush(first);

        try {
            assertThatThrownBy(() -> quotaPackageRepository.saveAndFlush(quotaPackage(code)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            quotaPackageRepository.findByCode(code).ifPresent(quotaPackageRepository::delete);
        }
    }

    private void insertRawPlan(String billingCycle, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                        insert into plans
                            (id, code, name, price, currency_code, billing_cycle, status, version, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(), "RAW_" + UUID.randomUUID().toString().replace("-", ""), "Raw Plan",
                BigDecimal.ZERO, "USD", billingCycle, status, 0L, now, now);
    }

    private Plan plan(String code) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(code);
        plan.setMoney(Money.zero("USD"));
        plan.setBillingCycle(BillingCycle.MONTHLY);
        return plan;
    }

    private AddOn addOn(String code) {
        AddOn addOn = new AddOn();
        addOn.setCode(code);
        addOn.setName(code);
        addOn.setMoney(Money.of(BigDecimal.TEN, "USD"));
        addOn.setBillingCycle(BillingCycle.MONTHLY);
        return addOn;
    }

    private QuotaPackage quotaPackage(String code) {
        QuotaPackage item = new QuotaPackage();
        item.setCode(code);
        item.setName(code);
        item.setFeature(featureRepository.findByCode("platform.workspace").orElseThrow());
        item.setResource("members");
        item.setCapacityPerUnit(5);
        item.setMoney(Money.of(BigDecimal.ONE, "USD"));
        item.setBillingCycle(BillingCycle.MONTHLY);
        item.setMaximumQuantity(1);
        return item;
    }
}
