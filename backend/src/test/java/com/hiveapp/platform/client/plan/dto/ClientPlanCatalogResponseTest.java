package com.hiveapp.platform.client.plan.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.shared.quota.QuotaLimitMode;
import com.hiveapp.shared.quota.QuotaSlot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientPlanCatalogResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void quotaShapeCarriesResourceAndUnitOnlyInsideTheCanonicalSlot() throws Exception {
        var quota = new ClientPlanCatalogResponse.CatalogQuota(
                "platform.staff",
                QuotaSlot.count("members", "people"),
                QuotaLimitMode.FINITE,
                10L,
                false,
                3L);

        var json = objectMapper.readTree(objectMapper.writeValueAsBytes(quota));

        assertThat(json.has("resource")).isFalse();
        assertThat(json.has("unit")).isFalse();
        assertThat(json.path("slot").path("resource").asText()).isEqualTo("members");
        assertThat(json.path("slot").path("unit").asText()).isEqualTo("people");
    }
}
