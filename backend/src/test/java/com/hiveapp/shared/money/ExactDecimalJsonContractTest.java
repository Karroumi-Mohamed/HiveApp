package com.hiveapp.shared.money;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.dto.CreateProductPriceRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceDto;
import com.hiveapp.platform.client.plan.dto.AddOnDto;
import com.hiveapp.platform.client.plan.dto.AssignablePlanPriceDto;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.PlanDetailDto;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot;
import com.hiveapp.platform.client.plan.dto.UpdateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.platform.client.plan.dto.UpdateProductPriceRequest;
import com.hiveapp.platform.client.plan.dto.UpdateQuotaPackageRequest;
import com.hiveapp.platform.admin.dto.AdminSubscriptionDto;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ExactDecimalJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesCommercialMoneyWithoutIeee754Coercion() throws Exception {
        BigDecimal exact = new BigDecimal("999999999999999.9999");
        ProductPriceDto dto = new ProductPriceDto(
                UUID.randomUUID(), ProductPriceOwnerType.PLAN, UUID.randomUUID(), "ENTERPRISE",
                "Enterprise", exact, "CLF", BillingCycle.YEARLY, ProductPriceStatus.DRAFT,
                Instant.parse("2026-09-01T00:00:00Z"), null, UUID.randomUUID(), 1, null,
                false, 0, Instant.parse("2026-08-26T00:00:00Z"),
                Instant.parse("2026-08-26T00:00:00Z"), List.of(), List.of());

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(dto));

        assertThat(json.path("amount").isTextual()).isTrue();
        assertThat(json.path("amount").textValue()).isEqualTo("999999999999999.9999");
    }

    @Test
    void serializesCommercialMoneyWithoutScientificNotation() throws Exception {
        ProductPriceDto dto = new ProductPriceDto(
                UUID.randomUUID(), ProductPriceOwnerType.PLAN, UUID.randomUUID(), "ENTERPRISE",
                "Enterprise", new BigDecimal("1E+3"), "CLF", BillingCycle.YEARLY,
                ProductPriceStatus.DRAFT, Instant.parse("2026-09-01T00:00:00Z"), null,
                UUID.randomUUID(), 1, null, false, 0,
                Instant.parse("2026-08-26T00:00:00Z"),
                Instant.parse("2026-08-26T00:00:00Z"), List.of(), List.of());

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(dto));

        assertThat(json.path("amount").textValue()).isEqualTo("1000");
    }

    @Test
    void acceptsExactDecimalStringInMutationRequests() throws Exception {
        CreateProductPriceRequest request = objectMapper.readValue("""
                {
                  "amount": "123456789012345.6789",
                  "currencyCode": "MAD",
                  "billingCycle": "MONTHLY",
                  "effectiveFrom": "2026-09-01T00:00:00Z",
                  "effectiveUntil": null
                }
                """, CreateProductPriceRequest.class);

        assertThat(request.amount()).isEqualByComparingTo("123456789012345.6789");
    }

    @Test
    void keepsNumericRequestCompatibilityDuringContractMigration() throws Exception {
        CreateProductPriceRequest request = objectMapper.readValue("""
                {
                  "amount": 12.3400,
                  "currencyCode": "MAD",
                  "billingCycle": "MONTHLY",
                  "effectiveFrom": "2026-09-01T00:00:00Z",
                  "effectiveUntil": null
                }
                """, CreateProductPriceRequest.class);

        assertThat(request.amount()).isEqualByComparingTo("12.3400");
    }

    @Test
    void everyCommercialApiBigDecimalComponentDeclaresTheExactWireContract() {
        Stream.of(
                        ProductPriceDto.class,
                        AssignablePlanPriceDto.class,
                        PlanDto.class,
                        PlanDetailDto.class,
                        AddOnDto.class,
                        QuotaPackageDto.class,
                        ClientPlanCatalogResponse.CurrentSubscription.class,
                        ClientPlanCatalogResponse.CatalogPlan.class,
                        ClientPlanCatalogResponse.CatalogAddOn.class,
                        ClientPlanCatalogResponse.CatalogQuotaPackage.class,
                        ClientPlanCatalogResponse.CatalogPrice.class,
                        SubscriptionDto.class,
                        SubscriptionDto.PlanSummaryDto.class,
                        SubscriptionCheckoutDto.class,
                        PlanSubscriberDto.class,
                        SubscriptionChangePreviewResponse.class,
                        SubscriptionEntitlementSnapshot.class,
                        SubscriptionAddOnSnapshot.class,
                        SubscriptionQuotaPackageSnapshot.class,
                        CreateProductPriceRequest.class,
                        UpdateProductPriceRequest.class,
                        CreatePlanRequest.class,
                        UpdatePlanRequest.class,
                        PlanBranchRequest.class,
                        CreateAddOnRequest.class,
                        UpdateAddOnRequest.class,
                        CreateQuotaPackageRequest.class,
                        UpdateQuotaPackageRequest.class,
                        AdminSubscriptionDto.class)
                .flatMap(type -> Stream.of(type.getRecordComponents()))
                .filter(component -> component.getType() == BigDecimal.class)
                .forEach(component -> assertThat(component.isAnnotationPresent(ExactDecimal.class))
                        .as("%s.%s must use the exact-decimal JSON contract",
                                component.getDeclaringRecord().getSimpleName(), component.getName())
                        .isTrue());
    }
}
