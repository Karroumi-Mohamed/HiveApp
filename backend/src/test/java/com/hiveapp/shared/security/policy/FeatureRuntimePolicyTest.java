package com.hiveapp.shared.security.policy;

import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.service.CurrentRegistrySnapshot;
import com.hiveapp.platform.registry.service.RegistrySnapshot;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureRuntimePolicyTest {

    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private final CurrentRegistrySnapshot snapshot = snapshot();
    private final FeatureRuntimePolicy policy = new FeatureRuntimePolicy(featureRepository, snapshot);

    @Test
    void deniesCurrentActionImmediatelyWhenFeatureRuntimeIsDisabled() {
        when(featureRepository.findByCode("platform.company"))
                .thenReturn(Optional.of(feature(false)));

        assertThat(policy.evaluate(new Permission("platform.company.read"), null))
                .isEqualTo(PermissionPolicy.Decision.DENIED);
    }

    @Test
    void allowsCurrentActionAndFeatureRootToContinueThroughActorPolicies() {
        when(featureRepository.findByCode("platform.company"))
                .thenReturn(Optional.of(feature(true)));

        assertThat(policy.evaluate(new Permission("platform.company.read"), null))
                .isEqualTo(PermissionPolicy.Decision.ABSTAIN);
        assertThat(policy.evaluate(new Permission("platform.company"), null))
                .isEqualTo(PermissionPolicy.Decision.ABSTAIN);
    }

    @Test
    void staleConcreteActionFailsClosed() {
        when(featureRepository.findByCode("platform.company"))
                .thenReturn(Optional.of(feature(true)));

        assertThat(policy.evaluate(new Permission("platform.company.removed"), null))
                .isEqualTo(PermissionPolicy.Decision.DENIED);
    }

    private Feature feature(boolean runtimeEnabled) {
        Feature feature = new Feature();
        feature.setCode("platform.company");
        feature.setRuntimeEnabled(runtimeEnabled);
        return feature;
    }

    private CurrentRegistrySnapshot snapshot() {
        CurrentRegistrySnapshot current = new CurrentRegistrySnapshot();
        current.install(new RegistrySnapshot(
                List.of(), List.of(), Set.of("platform.company.read"), "hash"));
        return current;
    }
}
