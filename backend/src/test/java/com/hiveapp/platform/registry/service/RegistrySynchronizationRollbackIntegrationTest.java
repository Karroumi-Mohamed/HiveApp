package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncRunRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import dev.karroumi.permissionizer.CollectedPermission;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

class RegistrySynchronizationRollbackIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private RegistrySynchronizationCoordinator coordinator;
    @Autowired private FeatureRepository featureRepository;
    @Autowired private ModuleRepository moduleRepository;
    @Autowired private RegistrySyncRunRepository runRepository;
    @SpyBean private PermissionSeeder permissionSeeder;

    @Test
    void permissionWriteFailureRollsBackFeatureLayerAndSuccessReport() {
        FeatureDefinition definition = FeatureDefinition.clientWorkspace("atomic.sample")
                .displayName("Atomic Sample")
                .build();
        CollectedPermission permission =
                new CollectedPermission("atomic.sample.read", "Read", "atomic.sample");
        RegistrySnapshot snapshot = new RegistrySnapshot(
                List.of(definition),
                List.of(permission),
                Set.of(permission.path()),
                "atomic-rollback-snapshot");
        long runsBefore = runRepository.count();
        doThrow(new IllegalStateException("simulated permission write failure"))
                .when(permissionSeeder).seedPermissions(anyList());

        try {
            assertThatThrownBy(() -> coordinator.synchronize(
                    snapshot, "rollback-test", Instant.now()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("simulated permission write failure");

            assertThat(featureRepository.findByCode(definition.code())).isEmpty();
            assertThat(moduleRepository.findByCode(definition.moduleCode())).isEmpty();
            assertThat(runRepository.count()).isEqualTo(runsBefore);
        } finally {
            Mockito.reset(permissionSeeder);
        }
    }
}
