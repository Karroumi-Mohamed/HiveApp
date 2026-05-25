package com.hiveapp.platform.client.plan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
                "addedFeatures", Set.of("platform.company"),
                "quotaOverrides", Set.of()
        ));

        assertThat(overrides.addedFeatures()).containsExactly("platform.company");
        assertThat(overrides.quotaOverrides()).isEmpty();
    }
}
