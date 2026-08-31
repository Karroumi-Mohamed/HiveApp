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
        assertThat(snapshot.get().schemaVersion()).isEqualTo(SubscriptionEntitlementSnapshot.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void writesAndReadsSnapshotJson() {
        var source = SubscriptionEntitlementSnapshot.empty(
                "FREE", BigDecimal.ZERO, "USD",
                com.hiveapp.platform.client.plan.domain.constant.BillingCycle.MONTHLY);

        var json = reader.write(source);
        var parsed = reader.read(json);

        assertThat(parsed).contains(source);
    }

    @Test
    void readsPersistedSchemaV1WithoutPriceEntryIdentities() {
        var snapshot = reader.read("""
                {"schemaVersion":1,"planCode":"PRO","planName":"Pro","planDefinitionVersion":1,
                 "basePrice":29.99,"currencyCode":"USD","billingCycle":"MONTHLY",
                 "features":[],
                 "addOns":[{"code":"TOOLS","name":"Tools","definitionVersion":1,
                    "price":3.00,"currencyCode":"USD","billingCycle":"MONTHLY","featureCodes":[]}],
                 "quotaPackages":[{"code":"MEMBERS","name":"Members","definitionVersion":1,
                    "featureCode":"platform.staff","resource":"members","capacityPerUnit":5,
                    "quantity":1,"unitPrice":2.00,"currencyCode":"USD","billingCycle":"MONTHLY"}]}
                """);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().schemaVersion()).isEqualTo(1);
        assertThat(snapshot.get().planPriceEntryId()).isNull();
        assertThat(snapshot.get().addOns().getFirst().priceEntryId()).isNull();
        assertThat(snapshot.get().quotaPackages().getFirst().priceEntryId()).isNull();
    }

    @Test
    void rejectsInvalidSnapshotJsonAsInvalidRequest() {
        assertThatThrownBy(() -> reader.read("{"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid subscription entitlement snapshot JSON.");
    }

    @Test
    void rejectsUnsupportedSnapshotSchemaVersion() {
        assertThatThrownBy(() -> reader.read(Map.of(
                "schemaVersion", 99,
                "planCode", "PRO",
                "basePrice", BigDecimal.TEN,
                "currencyCode", "USD",
                "billingCycle", "MONTHLY"
        )))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid subscription entitlement snapshot.");
    }
}
