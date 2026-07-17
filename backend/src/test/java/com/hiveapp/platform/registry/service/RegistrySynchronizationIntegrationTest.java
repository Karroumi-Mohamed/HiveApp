package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncLockRepository;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncRunRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrySynchronizationIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private RegistrySnapshotFactory snapshotFactory;
    @Autowired private RegistrySynchronizationCoordinator coordinator;
    @Autowired private RegistrySyncLockRepository lockRepository;
    @Autowired private RegistrySyncRunRepository runRepository;
    @Autowired private ModuleRepository moduleRepository;
    @Autowired private FeatureRepository featureRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void synchronizationRepairsEveryCodeOwnedFeatureAndPermissionField() {
        RegistrySnapshot snapshot = snapshotFactory.discoverAndValidate();
        var expectedPermission = snapshot.actionPermissions().stream()
                .filter(permission -> "platform.registry.sync_status".equals(permission.path()))
                .findFirst()
                .orElseThrow();
        var feature = featureRepository.findByCode("platform.registry").orElseThrow();
        var permission = permissionRepository.findByCode(expectedPermission.path()).orElseThrow();
        Module corruptModule = new Module();
        corruptModule.setCode("corrupt_registry_module");
        corruptModule = moduleRepository.saveAndFlush(corruptModule);

        feature.setModule(corruptModule);
        feature.setStatus(FeatureStatus.DEPRECATED);
        feature.setSortOrder(-1);
        featureRepository.saveAndFlush(feature);
        permission.setName("stale");
        permission.setDescription("stale");
        permission.setAction("stale");
        permission.setResource("stale");
        permissionRepository.saveAndFlush(permission);

        coordinator.synchronize(snapshot, "repair-test", Instant.now());
        entityManager.clear();

        var repairedFeature = featureRepository.findByCode("platform.registry").orElseThrow();
        var repairedPermission = permissionRepository.findByCode(expectedPermission.path()).orElseThrow();
        assertThat(repairedFeature.getModule().getId()).isEqualTo(
                moduleRepository.findByCode("platform").orElseThrow().getId());
        assertThat(repairedFeature.getStatus()).isEqualTo(
                snapshot.definitionsByCode().get("platform.registry").lifecycleStatus());
        assertThat(repairedFeature.getSortOrder()).isEqualTo(
                snapshot.definitionsByCode().get("platform.registry").sortOrder());
        assertThat(repairedPermission.getFeature().getId()).isEqualTo(repairedFeature.getId());
        assertThat(repairedPermission.getName()).isEqualTo(expectedPermission.path());
        assertThat(repairedPermission.getDescription()).isEqualTo(expectedPermission.description());
        assertThat(repairedPermission.getAction()).isEqualTo(expectedPermission.key());
        assertThat(repairedPermission.getResource()).isEqualTo("platform.registry");

        moduleRepository.deleteById(corruptModule.getId());
    }

    @Test
    void concurrentSynchronizersSerializeOnTheDatabaseLockAndBothReportSuccess() throws Exception {
        RegistrySnapshot snapshot = snapshotFactory.discoverAndValidate();
        long runsBefore = runRepository.count();
        assertThat(lockRepository.count()).isEqualTo(1);

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return coordinator.synchronize(snapshot, "concurrent-a", Instant.now()).getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return coordinator.synchronize(snapshot, "concurrent-b", Instant.now()).getStatus();
            });
            start.countDown();

            assertThat(first.get(20, TimeUnit.SECONDS).name()).isEqualTo("SUCCEEDED");
            assertThat(second.get(20, TimeUnit.SECONDS).name()).isEqualTo("SUCCEEDED");
        }

        assertThat(runRepository.count()).isEqualTo(runsBefore + 2);
    }
}
