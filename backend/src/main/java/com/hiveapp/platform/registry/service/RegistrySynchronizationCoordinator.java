package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.constant.RegistrySyncStatus;
import com.hiveapp.platform.registry.domain.entity.RegistrySyncLock;
import com.hiveapp.platform.registry.domain.entity.RegistrySyncRun;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncLockRepository;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RegistrySynchronizationCoordinator {

    static final String LOCK_NAME = "authoritative-registry";

    private final RegistrySyncLockRepository lockRepository;
    private final RegistrySyncRunRepository runRepository;
    private final RegistrySyncLockInitializer lockInitializer;
    private final FeatureSeeder featureSeeder;
    private final PermissionSeeder permissionSeeder;

    @Transactional
    public RegistrySyncRun synchronize(
            RegistrySnapshot snapshot,
            String buildVersion,
            Instant startedAt) {
        try {
            lockInitializer.ensureExists(LOCK_NAME);
        } catch (DataIntegrityViolationException ignoredConcurrentFirstInsert) {
            // Another node won the first-start insert. Its committed row is the lock authority.
        }
        RegistrySyncLock lock = lockRepository.findByLockNameForUpdate(LOCK_NAME)
                .orElseThrow(() -> new IllegalStateException("Registry synchronization lock is unavailable"));

        FeatureSeeder.SeedResult featureResult =
                featureSeeder.synchronize(snapshot.featureDefinitions());
        PermissionSeeder.SeedResult permissionResult =
                permissionSeeder.seedPermissions(snapshot.actionPermissions());
        Instant completedAt = Instant.now();

        RegistrySyncRun run = new RegistrySyncRun();
        run.setBuildVersion(buildVersion);
        run.setSnapshotHash(snapshot.hash());
        run.setStatus(RegistrySyncStatus.SUCCEEDED);
        run.setStartedAt(startedAt);
        run.setCompletedAt(completedAt);
        run.setDiscoveredModules((int) snapshot.featureDefinitions().stream()
                .map(definition -> definition.moduleCode())
                .distinct()
                .count());
        run.setDiscoveredFeatures(snapshot.featureDefinitions().size());
        run.setDiscoveredPermissions(snapshot.actionPermissions().size());
        run.setCreatedModules(featureResult.modulesCreated());
        run.setCreatedFeatures(featureResult.featuresCreated());
        run.setUpdatedFeatures(featureResult.featuresUpdated());
        run.setCreatedPermissions(permissionResult.permissionsCreated());
        run.setUpdatedPermissions(permissionResult.permissionsUpdated());
        run.setOrphanedPermissions(permissionResult.orphanedPermissionCodes().size());
        run.setDetails(successDetails(permissionResult.orphanedPermissionCodes()));
        runRepository.save(run);

        lock.setLastSnapshotHash(snapshot.hash());
        lock.setLastBuildVersion(buildVersion);
        lock.setLastCompletedAt(completedAt);
        lockRepository.save(lock);
        return run;
    }

    private String successDetails(java.util.List<String> orphanedPermissionCodes) {
        if (orphanedPermissionCodes.isEmpty()) {
            return "Authoritative registry snapshot synchronized";
        }
        String details = "Authoritative registry snapshot synchronized; orphaned permission rows: "
                + String.join(", ", orphanedPermissionCodes);
        return details.length() <= 2000 ? details : details.substring(0, 2000);
    }
}
