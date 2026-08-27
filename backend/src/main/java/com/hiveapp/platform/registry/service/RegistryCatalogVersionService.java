package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.entity.RegistrySyncLock;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncLockRepository;
import com.hiveapp.shared.exception.InvalidStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegistryCatalogVersionService {

    private final RegistrySyncLockRepository lockRepository;
    private final CurrentRegistrySnapshot currentRegistrySnapshot;

    public String currentVersion() {
        RegistrySyncLock lock = currentLock();
        requireLocalSnapshotCurrent(lock);
        return format(lock);
    }

    public void requireCurrent(String suppliedVersion) {
        String current = currentVersion();
        if (suppliedVersion == null || suppliedVersion.isBlank() || !current.equals(suppliedVersion)) {
            throw new InvalidStateException(
                    "Registry catalog changed; refresh the permission catalog before saving. Current version: " + current);
        }
    }

    public RegistrySyncLock lockForMutation() {
        RegistrySyncLock lock = lockRepository.findByLockNameForUpdate(
                        RegistrySynchronizationCoordinator.LOCK_NAME)
                .orElseThrow(() -> new IllegalStateException("Registry synchronization lock is unavailable"));
        requireLocalSnapshotCurrent(lock);
        return lock;
    }

    public void bump(RegistrySyncLock lock) {
        lock.setCatalogRevision(lock.getCatalogRevision() + 1);
        lockRepository.save(lock);
    }

    private RegistrySyncLock currentLock() {
        return lockRepository.findByLockName(RegistrySynchronizationCoordinator.LOCK_NAME)
                .orElseThrow(() -> new IllegalStateException("Registry synchronization lock is unavailable"));
    }

    private String format(RegistrySyncLock lock) {
        String hash = lock.getLastSnapshotHash();
        if (hash == null || hash.isBlank()) {
            throw new IllegalStateException("Registry catalog has not been synchronized");
        }
        return hash + ":" + lock.getCatalogRevision();
    }

    /**
     * A node must never evaluate or mutate a database catalogue synchronized by different code.
     * Startup synchronization installs the local snapshot only after the database transaction
     * succeeds, so ordinary runtime calls fail closed until both sides agree.
     */
    private void requireLocalSnapshotCurrent(RegistrySyncLock lock) {
        String localHash = currentRegistrySnapshot.hash();
        String authoritativeHash = lock.getLastSnapshotHash();
        if (localHash == null || localHash.isBlank()) {
            throw new IllegalStateException("The local registry snapshot is not installed");
        }
        if (authoritativeHash == null || authoritativeHash.isBlank()) {
            throw new IllegalStateException("Registry catalog has not been synchronized");
        }
        if (!authoritativeHash.equals(localHash)) {
            throw new InvalidStateException(
                    "This application node has a stale registry snapshot and cannot serve "
                            + "registry-dependent operations. Restart or replace the node.");
        }
    }
}
