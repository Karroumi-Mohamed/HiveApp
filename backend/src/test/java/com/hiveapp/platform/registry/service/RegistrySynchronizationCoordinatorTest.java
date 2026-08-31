package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.domain.constant.RegistrySyncStatus;
import com.hiveapp.platform.registry.domain.entity.RegistrySyncLock;
import com.hiveapp.platform.registry.domain.entity.RegistrySyncRun;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncLockRepository;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncRunRepository;
import dev.karroumi.permissionizer.CollectedPermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrySynchronizationCoordinatorTest {

    @Mock private RegistrySyncLockRepository lockRepository;
    @Mock private RegistrySyncRunRepository runRepository;
    @Mock private RegistrySyncLockInitializer lockInitializer;
    @Mock private FeatureSeeder featureSeeder;
    @Mock private PermissionSeeder permissionSeeder;

    @Test
    void locksThenSynchronizesBothLayersAndPersistsInspectableSuccess() {
        RegistrySyncLock lock = new RegistrySyncLock();
        lock.setLockName(RegistrySynchronizationCoordinator.LOCK_NAME);
        RegistrySnapshot snapshot = snapshot();
        when(lockRepository.findByLockNameForUpdate(RegistrySynchronizationCoordinator.LOCK_NAME))
                .thenReturn(Optional.of(lock));
        when(featureSeeder.synchronize(snapshot.featureDefinitions()))
                .thenReturn(new FeatureSeeder.SeedResult(1, 2, 3));
        when(permissionSeeder.seedPermissions(snapshot.actionPermissions()))
                .thenReturn(new PermissionSeeder.SeedResult(
                        4, 5, 0, List.of("platform.company.removed")));

        RegistrySynchronizationCoordinator coordinator = new RegistrySynchronizationCoordinator(
                lockRepository, runRepository, lockInitializer, featureSeeder, permissionSeeder);
        Instant startedAt = Instant.now();
        coordinator.synchronize(snapshot, "test-build", startedAt);

        ArgumentCaptor<RegistrySyncRun> run = ArgumentCaptor.forClass(RegistrySyncRun.class);
        verify(runRepository).save(run.capture());
        assertThat(run.getValue().getStatus()).isEqualTo(RegistrySyncStatus.SUCCEEDED);
        assertThat(run.getValue().getSnapshotHash()).isEqualTo(snapshot.hash());
        assertThat(run.getValue().getDiscoveredFeatures()).isEqualTo(1);
        assertThat(run.getValue().getDiscoveredPermissions()).isEqualTo(1);
        assertThat(run.getValue().getCreatedModules()).isEqualTo(1);
        assertThat(run.getValue().getCreatedFeatures()).isEqualTo(2);
        assertThat(run.getValue().getUpdatedFeatures()).isEqualTo(3);
        assertThat(run.getValue().getCreatedPermissions()).isEqualTo(4);
        assertThat(run.getValue().getUpdatedPermissions()).isEqualTo(5);
        assertThat(run.getValue().getOrphanedPermissions()).isEqualTo(1);
        assertThat(run.getValue().getDetails()).contains("platform.company.removed");
        assertThat(lock.getLastSnapshotHash()).isEqualTo(snapshot.hash());
        assertThat(lock.getLastBuildVersion()).isEqualTo("test-build");
        verify(lockInitializer).ensureExists(RegistrySynchronizationCoordinator.LOCK_NAME);
    }

    private RegistrySnapshot snapshot() {
        FeatureDefinition definition = FeatureDefinition.clientWorkspace("platform.company")
                .displayName("Companies")
                .build();
        CollectedPermission permission =
                new CollectedPermission("platform.company.read", "Read", "platform.company");
        return new RegistrySnapshot(
                List.of(definition),
                List.of(permission),
                Set.of(permission.path()),
                "snapshot-hash");
    }
}
