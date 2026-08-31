package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.constant.RegistrySyncStatus;
import com.hiveapp.platform.registry.domain.entity.RegistrySyncRun;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RegistrySyncRunRecorder {

    private final RegistrySyncRunRepository runRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String buildVersion,
            RegistrySnapshot snapshot,
            Instant startedAt,
            RuntimeException failure) {
        RegistrySyncRun run = new RegistrySyncRun();
        run.setBuildVersion(buildVersion);
        run.setSnapshotHash(snapshot == null ? null : snapshot.hash());
        run.setStatus(RegistrySyncStatus.FAILED);
        run.setStartedAt(startedAt);
        run.setCompletedAt(Instant.now());
        if (snapshot != null) {
            run.setDiscoveredModules((int) snapshot.featureDefinitions().stream()
                    .map(definition -> definition.moduleCode())
                    .distinct()
                    .count());
            run.setDiscoveredFeatures(snapshot.featureDefinitions().size());
            run.setDiscoveredPermissions(snapshot.actionPermissions().size());
        }
        run.setDetails(safeFailure(failure));
        runRepository.save(run);
    }

    private String safeFailure(RuntimeException failure) {
        String message = failure.getMessage();
        String details = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return details.length() <= 2000 ? details : details.substring(0, 2000);
    }
}
