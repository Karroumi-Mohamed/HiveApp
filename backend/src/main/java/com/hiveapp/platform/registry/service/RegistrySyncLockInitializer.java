package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.entity.RegistrySyncLock;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrySyncLockInitializer {

    private final RegistrySyncLockRepository lockRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(String lockName) {
        if (lockRepository.findByLockName(lockName).isPresent()) {
            return;
        }
        RegistrySyncLock lock = new RegistrySyncLock();
        lock.setLockName(lockName);
        lock.setCatalogRevision(0);
        lockRepository.saveAndFlush(lock);
    }
}
