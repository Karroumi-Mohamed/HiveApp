package com.hiveapp.platform.registry.domain.repository;

import com.hiveapp.platform.registry.domain.entity.RegistrySyncLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RegistrySyncLockRepository extends JpaRepository<RegistrySyncLock, UUID> {

    Optional<RegistrySyncLock> findByLockName(String lockName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lock from RegistrySyncLock lock where lock.lockName = :lockName")
    Optional<RegistrySyncLock> findByLockNameForUpdate(@Param("lockName") String lockName);
}
