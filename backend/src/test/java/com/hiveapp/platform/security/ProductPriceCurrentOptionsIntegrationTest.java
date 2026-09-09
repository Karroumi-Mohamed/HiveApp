package com.hiveapp.platform.security;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.shared.money.Money;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductPriceCurrentOptionsIntegrationTest extends PlatformShellIntegrationTestSupport {
    @Autowired PlanRepository plans;
    @Autowired ProductPriceRepository prices;
    @Autowired TransactionTemplate transactions;
    java.util.UUID fixturePlanId;

    @org.junit.jupiter.api.AfterEach
    void cleanupFixture() {
        if (fixturePlanId == null) return;
        transactions.executeWithoutResult(status -> {
            prices.deleteAllInBatch(prices.findAllByPlanId(fixturePlanId));
            plans.deleteById(fixturePlanId);
        });
    }

    @Test
    void summarySelectsOnlyCurrentPricesAndKeepsIndependentCycles() throws Exception {
        var plan = transactions.execute(status -> {
            var owner = new com.hiveapp.platform.client.plan.domain.entity.Plan();
            owner.setCode("OPTIONS_" + java.util.UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT));
            owner.setName("Isolated tariff options");
            owner.setMoney(Money.of(java.math.BigDecimal.ZERO, "MAD"));
            owner.setBillingCycle(BillingCycle.MONTHLY);
            owner.setStatus(com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ACTIVE);
            plans.saveAndFlush(owner);
            fixturePlanId = owner.getId();
            var initial = ProductPrice.draft(owner, owner.money(), BillingCycle.MONTHLY, Instant.now().minusSeconds(60), null);
            initial.markCompatibilityDefault(); initial.activate(); prices.saveAndFlush(initial);
            return owner;
        });
        var ids = transactions.execute(status -> {
            var owner = plans.findById(plan.getId()).orElseThrow();
            var now = Instant.now();
            var annual = ProductPrice.draft(owner, Money.of(new java.math.BigDecimal("1000"), "MAD"), BillingCycle.YEARLY, now.minusSeconds(60), null);
            var future = ProductPrice.draft(owner, Money.of(new java.math.BigDecimal("90"), "EUR"), BillingCycle.MONTHLY, now.plusSeconds(3600), null);
            var expired = ProductPrice.draft(owner, Money.of(new java.math.BigDecimal("80"), "EUR"), BillingCycle.MONTHLY, now.minusSeconds(3600), now.minusSeconds(60));
            annual.activate(); future.activate(); expired.activate();
            prices.saveAllAndFlush(java.util.List.of(annual, future, expired));
            return java.util.List.of(annual.getId().toString(), future.getId().toString(), expired.getId().toString());
        });
        mockMvc.perform(get("/api/admin/product-prices").header("Authorization", "Bearer " + loginAdminAndGetToken())
                .param("ownerType", "PLAN").param("ownerIds", plan.getId().toString()).param("currentOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(ids.get(0))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(ids.get(1)))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(ids.get(2)))))
                .andExpect(jsonPath("$.content[*].billingCycle", hasItem("MONTHLY")))
                .andExpect(jsonPath("$.content[*].billingCycle", hasItem("YEARLY")));
    }

    @Test
    void bulkSummaryRequiresOwnerType() throws Exception {
        mockMvc.perform(get("/api/admin/product-prices").header("Authorization", "Bearer " + loginAdminAndGetToken())
                .param("ownerIds", java.util.UUID.randomUUID().toString()).param("currentOnly", "true"))
                .andExpect(status().isBadRequest());
    }
}
