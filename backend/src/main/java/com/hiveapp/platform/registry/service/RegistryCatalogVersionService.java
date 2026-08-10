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

    public String currentVersion() {
        return format(currentLock());
    }

    public void requireCurrent(String suppliedVersion) {
        String current = currentVersion();
        if (suppliedVersion == null || suppliedVersion.isBlank() || !current.equals(suppliedVersion)) {
            throw new InvalidStateException(
                    "Registry catalog changed; refresh the permission catalog before saving. Current version: " + current);
        }
    }

    public RegistrySyncLock lockForMutation() {
        return lockRepository.findByLockNameForUpdate(RegistrySynchronizationCoordinator.LOCK_NAME)
                .orElseThrow(() -> new IllegalStateException("Registry synchronization lock is unavailable"));
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
}
