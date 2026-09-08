package com.hiveapp.platform.registry.definition;

import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.service.CurrentRegistrySnapshot;
import com.hiveapp.platform.registry.service.RegistrySnapshot;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyCollection;

class PermissionGrantValidatorTest {

    private final FeatureDefinitionCollector collector =
            new FeatureDefinitionCollector(List.of(() -> List.of(
                    FeatureDefinition.clientWorkspace("platform.company")
                            .displayName("Companies")
                            .ownerOnlyActions("delete")
                            .b2bDelegatableActions("read")
                            .build(),
                    FeatureDefinition.platformControl("platform.plans")
                            .displayName("Plans")
                            .build()
            )));
    private final CurrentRegistrySnapshot snapshot = snapshot();
    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private PermissionGrantValidator validator;

    @BeforeEach
    void setUp() {
        when(featureRepository.findByCode("platform.company"))
                .thenReturn(java.util.Optional.of(feature("platform.company")));
        when(featureRepository.findByCode("platform.plans"))
                .thenReturn(java.util.Optional.of(feature("platform.plans")));
        validator = new PermissionGrantValidator(snapshot, featureRepository);
    }

    @Test
    void allowsClientWorkspacePermissionsForClientRoles() {
        assertThat(validator.isClientRoleGrantable(permission("platform.company.read"))).isTrue();
    }

    @Test
    void rejectsPlatformControlPermissionsForClientRoles() {
        assertThatThrownBy(() -> validator.requireClientRoleGrantable(permission("platform.plans.create")))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessageContaining("client role");
    }

    @Test
    void rejectsOwnerOnlyActionForOrdinaryClientRole() {
        assertThatThrownBy(() -> validator.requireClientRoleGrantable(
                permission("platform.company.delete")))
                .isInstanceOf(InvalidPermissionGrantException.class);
    }

    @Test
    void onlyExplicitClientWorkspaceFeaturesCanBeB2bDelegated() {
        validator.requireB2bDelegatable(permission("platform.company.read"));

        assertThatThrownBy(() -> validator.requireB2bDelegatable(permission("platform.plans.create")))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessageContaining("B2B collaboration");
    }

    @Test
    void rejectsClientWorkspaceActionsThatAreNotB2bDelegatable() {
        assertThatThrownBy(() -> validator.requireB2bDelegatable(permission("platform.company.delete")))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessageContaining("B2B collaboration");
    }

    @Test
    void allowsPlatformControlPermissionsForPlatformAdminRoles() {
        validator.requirePlatformAdminRoleGrantable("platform.plans.create");
    }

    @Test
    void rejectsStaleDatabaseActionMissingFromCurrentPermissionizerSnapshot() {
        assertThatThrownBy(() -> validator.requireClientRoleGrantable(
                permission("platform.company.removed_action")))
                .isInstanceOf(InvalidPermissionGrantException.class)
                .hasMessageContaining("client role");
    }

    @Test
    void bulkAdminEvaluationUsesOneFeatureControlQueryForHundredsOfPermissions() {
        Set<String> codes = java.util.stream.IntStream.range(0, 400)
                .mapToObj(index -> "platform.plans.action_" + index)
                .collect(java.util.stream.Collectors.toSet());
        snapshot.install(new RegistrySnapshot(collector.collect(), List.of(), codes, "large"));
        when(featureRepository.findGrantControlsByCodeIn(Set.of("platform.plans")))
                .thenReturn(List.of(controls("platform.plans", true, true)));
        var input = new java.util.ArrayList<>(codes);
        input.add("platform.plans.removed_action");
        input.add("platform.company.read");
        input.add(null);

        assertThat(validator.platformAdminRoleGrantableCodes(input)).containsExactlyInAnyOrderElementsOf(codes);
        verify(featureRepository).findGrantControlsByCodeIn(Set.of("platform.plans"));
        verify(featureRepository, never()).findByCode(anyString());
    }

    @Test
    void bulkChecksRereadFlagsAndFailClosedForMissingFeatureRows() {
        when(featureRepository.findGrantControlsByCodeIn(Set.of("platform.plans")))
                .thenReturn(List.of(controls("platform.plans", true, true)))
                .thenReturn(List.of(controls("platform.plans", false, true)))
                .thenReturn(List.of(controls("platform.plans", true, false)))
                .thenReturn(List.of());
        var codes = List.of("platform.plans.create");
        assertThat(validator.platformAdminRoleGrantableCodes(codes)).containsExactlyElementsOf(codes);
        for (int i = 0; i < 3; i++) assertThat(validator.platformAdminRoleGrantableCodes(codes)).isEmpty();
        verify(featureRepository, org.mockito.Mockito.times(4)).findGrantControlsByCodeIn(Set.of("platform.plans"));
    }

    @Test
    void installedSnapshotReplacementIsImmediatelyUsedWithoutRediscovery() {
        validator.requirePlatformAdminRoleGrantable("platform.plans.create");
        // Action and definition removal both fail closed, even though the DB row still exists.
        snapshot.install(new RegistrySnapshot(collector.collect(), List.of(), Set.of(), "removed-action"));
        assertThat(validator.isPlatformAdminRoleGrantable("platform.plans.create")).isFalse();
        snapshot.install(new RegistrySnapshot(List.of(), List.of(), Set.of("platform.plans.create"), "missing-definition"));
        assertThat(validator.isPlatformAdminRoleGrantable("platform.plans.create")).isFalse();
        assertThat(validator.platformAdminRoleGrantableCodes(List.of("platform.plans.create"))).isEmpty();
        verify(featureRepository, never()).findGrantControlsByCodeIn(anyCollection());
    }

    @Test
    void newGrantPauseDoesNotBecomeARuntimeVetoButEmergencyDisableDoes() {
        Feature controls = feature("platform.company");
        controls.setNewGrantsEnabled(false);
        when(featureRepository.findByCode("platform.company")).thenReturn(java.util.Optional.of(controls));
        assertThat(validator.isClientRoleGrantable(permission("platform.company.read"))).isFalse();
        assertThat(validator.isClientRoleRuntimeEligible("platform.company.read")).isTrue();
        assertThat(validator.isB2bRuntimeEligible("platform.company.read")).isTrue();
        controls.setRuntimeEnabled(false);
        assertThat(validator.isClientRoleRuntimeEligible("platform.company.read")).isFalse();
        assertThat(validator.isB2bRuntimeEligible("platform.company.read")).isFalse();
        assertThat(validator.isOwnerUsable(permission("platform.company.read"))).isFalse();
    }

    @Test
    void bulkRequireRejectsUnavailableCodesRatherThanSilentlyDroppingThem() {
        assertThatThrownBy(() -> validator.requirePlatformAdminRoleGrantablePermissions(
                List.of("platform.company.read")))
                .isInstanceOf(InvalidPermissionGrantException.class).hasMessageContaining("platform admin role");
        verify(featureRepository, never()).findGrantControlsByCodeIn(anyCollection());
    }

    private FeatureRepository.GrantControls controls(String code, boolean grants, boolean runtime) {
        return new FeatureRepository.GrantControls() {
            public String getCode() { return code; }
            public boolean getNewGrantsEnabled() { return grants; }
            public boolean getRuntimeEnabled() { return runtime; }
        };
    }

    private static Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        return permission;
    }

    private static Feature feature(String code) {
        Feature feature = new Feature();
        feature.setCode(code);
        feature.setNewGrantsEnabled(true);
        feature.setRuntimeEnabled(true);
        return feature;
    }

    private CurrentRegistrySnapshot snapshot() {
        CurrentRegistrySnapshot current = new CurrentRegistrySnapshot();
        current.install(new RegistrySnapshot(
                collector.collect(),
                List.of(),
                java.util.Set.of(
                        "platform.company.read",
                        "platform.company.delete",
                        "platform.plans.create"),
                "test-snapshot"));
        return current;
    }
}
