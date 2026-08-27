package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.entity.RegistrySyncLock;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncLockRepository;
import com.hiveapp.shared.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistryCatalogVersionServiceTest {

    private final RegistrySyncLockRepository repository = mock(RegistrySyncLockRepository.class);
    private final CurrentRegistrySnapshot currentSnapshot = mock(CurrentRegistrySnapshot.class);
    private final RegistryCatalogVersionService service =
            new RegistryCatalogVersionService(repository, currentSnapshot);

    @Test
    void rejectsStaleWritesWithRefreshRequiredMessage() {
        when(currentSnapshot.hash()).thenReturn("hash");
        when(repository.findByLockName(RegistrySynchronizationCoordinator.LOCK_NAME))
                .thenReturn(Optional.of(lock("hash", 4)));

        assertThatThrownBy(() -> service.requireCurrent("hash:3"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("refresh the permission catalog")
                .hasMessageContaining("hash:4");
    }

    @Test
    void bumpIncrementsTheLockedCatalogRevision() {
        RegistrySyncLock lock = lock("hash", 4);

        service.bump(lock);

        assertThat(lock.getCatalogRevision()).isEqualTo(5);
        verify(repository).save(lock);
    }

    @Test
    void rejectsRegistryReadsBeforeTheLocalSnapshotIsInstalled() {
        when(repository.findByLockName(RegistrySynchronizationCoordinator.LOCK_NAME))
                .thenReturn(Optional.of(lock("hash", 4)));

        assertThatThrownBy(service::currentVersion)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local registry snapshot is not installed");
    }

    @Test
    void rejectsRegistryReadsAndMutationsFromAStaleApplicationNode() {
        when(currentSnapshot.hash()).thenReturn("old-hash");
        RegistrySyncLock lock = lock("new-hash", 5);
        when(repository.findByLockName(RegistrySynchronizationCoordinator.LOCK_NAME))
                .thenReturn(Optional.of(lock));
        when(repository.findByLockNameForUpdate(RegistrySynchronizationCoordinator.LOCK_NAME))
                .thenReturn(Optional.of(lock));

        assertThatThrownBy(service::currentVersion)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("stale registry snapshot");
        assertThatThrownBy(service::lockForMutation)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("stale registry snapshot");
    }

    @Test
    void returnsVersionAndMutationLockWhenLocalAndAuthoritativeSnapshotsMatch() {
        when(currentSnapshot.hash()).thenReturn("hash");
        RegistrySyncLock lock = lock("hash", 7);
        when(repository.findByLockName(RegistrySynchronizationCoordinator.LOCK_NAME))
                .thenReturn(Optional.of(lock));
        when(repository.findByLockNameForUpdate(RegistrySynchronizationCoordinator.LOCK_NAME))
                .thenReturn(Optional.of(lock));

        assertThat(service.currentVersion()).isEqualTo("hash:7");
        assertThat(service.lockForMutation()).isSameAs(lock);
    }

    private RegistrySyncLock lock(String hash, long revision) {
        RegistrySyncLock lock = new RegistrySyncLock();
        lock.setLockName(RegistrySynchronizationCoordinator.LOCK_NAME);
        lock.setLastSnapshotHash(hash);
        lock.setCatalogRevision(revision);
        return lock;
    }
}
