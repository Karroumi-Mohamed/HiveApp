package com.hiveapp.collectortest;

import com.hiveapp.platform.registry.definition.FeatureContributor;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.FeatureDefinitionException;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import dev.karroumi.permissionizer.PermissionNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeatureDefinitionCollectorRootValidationTest {

    @Test
    void acceptsGuardedFeatureServiceWhenPermissionizerRootMatchesDefinitionCode() {
        FeatureDefinitionCollector collector = new FeatureDefinitionCollector(List.of(new MatchingFeatureService()));

        assertThat(collector.collect())
                .extracting(FeatureDefinition::code)
                .containsExactly("collector.match");
    }

    @Test
    void rejectsGuardedFeatureServiceWhenPermissionizerRootDoesNotMatchDefinitionCode() {
        FeatureDefinitionCollector collector = new FeatureDefinitionCollector(List.of(new MismatchedFeatureService()));

        assertThatThrownBy(collector::collect)
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("does not match Permissionizer root collector.wrong")
                .hasMessageContaining("collector.match");
    }

    @Test
    void rejectsGuardedFeatureServiceWithMultipleDefinitions() {
        FeatureDefinitionCollector collector = new FeatureDefinitionCollector(List.of(new MultiFeatureService()));

        assertThatThrownBy(collector::collect)
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("must contribute exactly one feature definition");
    }

    @PermissionNode(key = "match", description = "Collector Match")
    private static final class MatchingFeatureService extends ClientWorkspaceFeatureService {
        @Override
        protected FeatureDefinition featureDefinition() {
            return FeatureDefinition.clientWorkspace("collector.match")
                    .displayName("Collector Match")
                    .build();
        }
    }

    @PermissionNode(key = "wrong", description = "Collector Wrong")
    private static final class MismatchedFeatureService extends ClientWorkspaceFeatureService {
        @Override
        protected FeatureDefinition featureDefinition() {
            return FeatureDefinition.clientWorkspace("collector.match")
                    .displayName("Collector Match")
                    .build();
        }
    }

    @PermissionNode(key = "multi", description = "Collector Multi")
    private static final class MultiFeatureService implements FeatureContributor {
        @Override
        public List<FeatureDefinition> featureDefinitions() {
            return List.of(
                    FeatureDefinition.clientWorkspace("collector.multi")
                            .displayName("Collector Multi")
                            .build(),
                    FeatureDefinition.clientWorkspace("collector.extra")
                            .displayName("Collector Extra")
                            .build()
            );
        }
    }
}
