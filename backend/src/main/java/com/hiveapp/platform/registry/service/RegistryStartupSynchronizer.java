package com.hiveapp.platform.registry.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegistryStartupSynchronizer {

    private final RegistrySnapshotFactory snapshotFactory;
    private final RegistrySynchronizationCoordinator coordinator;
    private final RegistrySyncRunRecorder runRecorder;
    private final CurrentRegistrySnapshot currentSnapshot;

    @Value("${hiveapp.build.version:development}")
    private String buildVersion;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void synchronize() {
        Instant startedAt = Instant.now();
        RegistrySnapshot snapshot = null;
        try {
            snapshot = snapshotFactory.discoverAndValidate();
            coordinator.synchronize(snapshot, buildVersion, startedAt);
            currentSnapshot.install(snapshot);
            log.info("Registry synchronization complete — hash: {}, features: {}, permissions: {}",
                    snapshot.hash(), snapshot.featureDefinitions().size(), snapshot.actionPermissions().size());
        } catch (RuntimeException failure) {
            try {
                runRecorder.recordFailure(buildVersion, snapshot, startedAt, failure);
            } catch (RuntimeException reportingFailure) {
                failure.addSuppressed(reportingFailure);
            }
            throw failure;
        }
    }
}
