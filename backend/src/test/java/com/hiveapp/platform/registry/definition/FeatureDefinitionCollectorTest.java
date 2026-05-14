package com.hiveapp.platform.registry.definition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeatureDefinitionCollectorTest {

    @Test
    void collectsDefinitionsInStableOrder() {
        FeatureDefinitionCollector collector = new FeatureDefinitionCollector(List.of(
                () -> List.of(FeatureDefinition.clientWorkspace("platform.staff")
                        .displayName("Staff")
                        .sortOrder(20)
                        .build()),
                () -> List.of(FeatureDefinition.platformControl("platform.registry")
                        .displayName("Registry")
                        .sortOrder(10)
                        .build())
        ));

        assertThat(collector.collect())
                .extracting(FeatureDefinition::code)
                .containsExactly("platform.registry", "platform.staff");
    }

    @Test
    void rejectsDuplicateFeatureCodesAcrossContributors() {
        FeatureDefinition definition = FeatureDefinition.clientWorkspace("platform.staff")
                .displayName("Staff")
                .build();

        FeatureDefinitionCollector collector = new FeatureDefinitionCollector(List.of(
                () -> List.of(definition),
                () -> List.of(definition)
        ));

        assertThatThrownBy(collector::collect)
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("Duplicate feature definition");
    }

}
