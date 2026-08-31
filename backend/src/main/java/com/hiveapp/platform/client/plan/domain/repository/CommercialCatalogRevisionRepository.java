package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.CommercialCatalogRevision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CommercialCatalogRevisionRepository
        extends JpaRepository<CommercialCatalogRevision, UUID> {

    Optional<CommercialCatalogRevision> findByLockName(String lockName);

    @Query("select revision.revision from CommercialCatalogRevision revision "
            + "where revision.lockName = :lockName")
    Optional<Long> findRevision(@Param("lockName") String lockName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select revision from CommercialCatalogRevision revision "
            + "where revision.lockName = :lockName")
    Optional<CommercialCatalogRevision> findByLockNameForUpdate(
            @Param("lockName") String lockName);

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("update CommercialCatalogRevision revision "
            + "set revision.revision = revision.revision + 1 "
            + "where revision.id = :id")
    int incrementRevision(@Param("id") UUID id);
}
