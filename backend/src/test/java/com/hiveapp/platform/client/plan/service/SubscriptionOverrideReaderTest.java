package com.hiveapp.platform.client.plan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionOverrideReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubscriptionOverrideReader reader = new SubscriptionOverrideReader(objectMapper);

    @Test
    void treatsNullValuesAsEmptyOverrides() {
        assertThat(reader.read(null)).isEqualTo(SubscriptionOverrides.empty());
        assertThat(reader.read("null")).isEqualTo(SubscriptionOverrides.empty());
        assertThat(reader.read(objectMapper.nullNode())).isEqualTo(SubscriptionOverrides.empty());
        assertThat(reader.read(objectMapper.getNodeFactory().textNode("null"))).isEqualTo(SubscriptionOverrides.empty());
    }

    @Test
    void readsStructuredOverrides() {
        var overrides = reader.read(Map.of(
                "addOnCodes", Set.of("COMPANY_MODULE"),
                "quotaOverrides", Set.of()
        ));

        assertThat(overrides.addOnCodes()).containsExactly("COMPANY_MODULE");
        assertThat(overrides.quotaOverrides()).isEmpty();
    }

    @Test
    void rejectsInvalidOverrideJsonAsInvalidRequest() {
        assertThatThrownBy(() -> reader.read("{"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid subscription override JSON.");
    }

    @Test
    void rejectsInvalidStructuredOverridesAsInvalidRequest() {
        assertThatThrownBy(() -> reader.read(Map.of(
                "addOnCodes", 42,
                "quotaOverrides", Set.of()
        )))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid subscription overrides.");
    }
}
