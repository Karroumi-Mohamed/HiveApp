package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.company.dto.UpdateCompanyRequest;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.shared.quota.QuotaLimitMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationContractTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        FACTORY.close();
    }

    @Test
    void commercialPricesRejectNegativeValuesAndUnsupportedDatabaseScale() {
        var negative = new CreatePlanRequest(
                "TEAM", "Team", null, new BigDecimal("-0.01"), "USD", BillingCycle.MONTHLY);
        var excessiveScale = new CreatePlanRequest(
                "TEAM", "Team", null, new BigDecimal("1.00001"), "USD", BillingCycle.MONTHLY);

        assertThat(violationPaths(negative)).contains("price");
        assertThat(violationPaths(excessiveScale)).contains("price");
    }

    @Test
    void nestedQuotaEntriesAreValidated() {
        var request = new AssignPlanFeatureRequest(
                "platform.staff",
                PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitRequest("x".repeat(101), null, 5L)));

        assertThat(violationPaths(request)).contains("quotaConfigs[0].resource");
    }

    @Test
    void quotaModeIsRequiredNeverInferred() {
        // PLAN-FLOW-007: sending a limit is not the same statement as declaring FINITE; an
        // omitted mode must be rejected instead of guessed from the limit's presence.
        var missingMode = new AssignPlanFeatureRequest(
                "platform.staff",
                PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitRequest("members", null, 5L)));

        assertThat(violationPaths(missingMode)).contains("quotaConfigs[0].mode");
    }

    @Test
    void quotaModeAndLimitCombinationIsValidatedBeforeDomainConversion() {
        var negative = new AssignPlanFeatureRequest(
                "platform.staff",
                PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitRequest("members", QuotaLimitMode.FINITE, -1L)));
        var contradictory = new AssignPlanFeatureRequest(
                "platform.staff",
                PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitRequest("members", QuotaLimitMode.UNLIMITED, 5L)));

        assertThat(violationPaths(negative)).contains("quotaConfigs[0].limit");
        assertThat(violationPaths(contradictory))
                .contains("quotaConfigs[0].modeAndLimitConsistent");
    }

    @Test
    void optionalUpdateNameRejectsBlankWhenSupplied() {
        var request = new UpdateCompanyRequest("   ", null, null, null, null, null, null);

        assertThat(violationPaths(request)).contains("name");
    }

    private static List<String> violationPaths(Object value) {
        return VALIDATOR.validate(value).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .toList();
    }
}
