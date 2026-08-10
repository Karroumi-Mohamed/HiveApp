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
    private final RegistryCatalogVersionService service = new RegistryCatalogVersionService(repository);

    @Test
    void rejectsStaleWritesWithRefreshRequiredMessage() {
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

    private RegistrySyncLock lock(String hash, long revision) {
        RegistrySyncLock lock = new RegistrySyncLock();
        lock.setLockName(RegistrySynchronizationCoordinator.LOCK_NAME);
        lock.setLastSnapshotHash(hash);
        lock.setCatalogRevision(revision);
        return lock;
    }
}
