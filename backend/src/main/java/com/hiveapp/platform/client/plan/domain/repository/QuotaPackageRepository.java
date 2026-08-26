package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotaPackageRepository extends JpaRepository<QuotaPackage, UUID> {
    Optional<QuotaPackage> findByCode(String code);
    List<QuotaPackage> findAllByCodeIn(Collection<String> codes);

    @EntityGraph(attributePaths = "feature")
    List<QuotaPackage> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "feature")
    Optional<QuotaPackage> findDetailedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from QuotaPackage item where item.id = :quotaPackageId")
    Optional<QuotaPackage> findByIdForUpdate(@Param("quotaPackageId") UUID quotaPackageId);
}
