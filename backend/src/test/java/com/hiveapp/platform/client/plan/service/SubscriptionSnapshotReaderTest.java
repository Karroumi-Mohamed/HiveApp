package com.hiveapp.platform.client.plan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.shared.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionSnapshotReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubscriptionSnapshotReader reader = new SubscriptionSnapshotReader(objectMapper);

    @Test
    void treatsNullValuesAsMissingSnapshot() {
        assertThat(reader.read(null)).isEmpty();
        assertThat(reader.read("null")).isEmpty();
        assertThat(reader.read(objectMapper.nullNode())).isEmpty();
        assertThat(reader.read(objectMapper.getNodeFactory().textNode("null"))).isEmpty();
    }

    @Test
    void readsStructuredSnapshot() {
        var snapshot = reader.read(Map.of(
                "planCode", "PRO",
                "basePrice", BigDecimal.valueOf(10),
                "features", List.of(Map.of(
                        "featureCode", "platform.company",
                        "quotaConfigs", List.of()
                ))
        ));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().planCode()).isEqualTo("PRO");
        assertThat(snapshot.get().features()).hasSize(1);
        assertThat(snapshot.get().features().getFirst().featureCode()).isEqualTo("platform.company");
    }

    @Test
    void writesAndReadsSnapshotJson() {
        var source = SubscriptionEntitlementSnapshot.empty("FREE", BigDecimal.ZERO);

        var json = reader.write(source);
        var parsed = reader.read(json);

        assertThat(parsed).contains(source);
    }

    @Test
    void rejectsInvalidSnapshotJsonAsInvalidRequest() {
        assertThatThrownBy(() -> reader.read("{"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid subscription entitlement snapshot JSON.");
    }
}
