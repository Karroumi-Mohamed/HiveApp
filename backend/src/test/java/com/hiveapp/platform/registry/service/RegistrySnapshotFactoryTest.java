package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.FeatureDefinitionException;
import dev.karroumi.permissionizer.CollectedPermission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrySnapshotFactoryTest {

    private final FeatureDefinition definition = FeatureDefinition.clientWorkspace("platform.company")
            .displayName("Companies")
            .build();
    private final RegistrySnapshotFactory factory = new RegistrySnapshotFactory(
            new FeatureDefinitionCollector(List.of(() -> List.of(definition))));

    @Test
    void rejectsEmptyPermissionizerCollection() {
        assertThatThrownBy(() -> factory.validate(List.of(definition), Set.of(definition.code()), List.of()))
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("no permissions");
    }

    @Test
    void rejectsGuardedFeatureWithNoConcreteAction() {
        assertThatThrownBy(() -> factory.validate(
                List.of(definition),
                Set.of(definition.code()),
                List.of(permission("platform", "Platform", null),
                        permission("company", "Companies", "platform"))))
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("no concrete action");
    }

    @Test
    void rejectsActionWithoutMatchingCodeDefinition() {
        assertThatThrownBy(() -> factory.validate(
                List.of(definition),
                Set.of(),
                List.of(permission("read", "Read", "platform.unknown"))))
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("no matching FeatureDefinition");
    }

    @Test
    void buildsStableAuthoritativeActionSetAndHash() {
        CollectedPermission read = permission("read", "Read", "platform.company");
        CollectedPermission create = permission("create", "Create", "platform.company");

        RegistrySnapshot first = factory.validate(
                List.of(definition), Set.of(definition.code()), List.of(read, create));
        RegistrySnapshot second = factory.validate(
                List.of(definition), Set.of(definition.code()), List.of(create, read));

        assertThat(first.actionCodes())
                .containsExactlyInAnyOrder("platform.company.read", "platform.company.create");
        assertThat(first.hash()).isEqualTo(second.hash());
    }

    @Test
    void rejectsPartialCollectionEvenWhenTheGuardedFeatureStillHasAnotherAction() {
        assertThatThrownBy(() -> factory.validate(
                List.of(definition),
                Set.of(definition.code()),
                Set.of("platform.company.read", "platform.company.create"),
                List.of(permission("read", "Read", "platform.company"))))
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("missing=[platform.company.create]");
    }

    @Test
    void rejectsActionClassificationThatDoesNotMatchAGuardedMethod() {
        FeatureDefinition classified = FeatureDefinition.clientWorkspace("platform.company")
                .displayName("Companies")
                .ownerOnlyActions("delete")
                .build();
        RegistrySnapshotFactory classifiedFactory = new RegistrySnapshotFactory(
                new FeatureDefinitionCollector(List.of(() -> List.of(classified))));

        assertThatThrownBy(() -> classifiedFactory.validate(
                List.of(classified), Set.of(classified.code()),
                List.of(permission("read", "Read", "platform.company"))))
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("unknown owner-only actions")
                .hasMessageContaining("platform.company.delete");
    }

    @Test
    void rejectsOwnerOnlyActionThatIsAlsoB2bDelegatable() {
        FeatureDefinition conflicting = FeatureDefinition.clientWorkspace("platform.company")
                .displayName("Companies")
                .ownerOnlyActions("read")
                .b2bDelegatableActions("read")
                .build();
        RegistrySnapshotFactory conflictingFactory = new RegistrySnapshotFactory(
                new FeatureDefinitionCollector(List.of(() -> List.of(conflicting))));

        assertThatThrownBy(() -> conflictingFactory.validate(
                List.of(conflicting), Set.of(conflicting.code()),
                List.of(permission("read", "Read", "platform.company"))))
                .isInstanceOf(FeatureDefinitionException.class)
                .hasMessageContaining("both owner-only and B2B-delegatable");
    }

    private CollectedPermission permission(String key, String description, String parentPath) {
        String path = parentPath == null ? key : parentPath + "." + key;
        return new CollectedPermission(path, description, parentPath);
    }
}
