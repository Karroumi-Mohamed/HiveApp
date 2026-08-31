package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotaPackageRepository extends JpaRepository<QuotaPackage, UUID>,
        JpaSpecificationExecutor<QuotaPackage> {
    Optional<QuotaPackage> findByCode(String code);
    @Query("select item.code from QuotaPackage item where item.code in :codes")
    List<String> findCodesByCodeIn(@Param("codes") Collection<String> codes);
    @EntityGraph(attributePaths = "feature")
    List<QuotaPackage> findAllByCodeIn(Collection<String> codes);

    @EntityGraph(attributePaths = "feature")
    List<QuotaPackage> findAllByOrderByCodeAsc();

    List<QuotaPackage> findAllByOrderByIdAsc(Pageable pageable);

    @EntityGraph(attributePaths = "feature")
    Optional<QuotaPackage> findDetailedById(UUID id);

    @EntityGraph(attributePaths = "feature")
    @Query("select item from QuotaPackage item where item.id in :ids order by item.id")
    List<QuotaPackage> findAllDetailedByIdIn(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from QuotaPackage item where item.id = :quotaPackageId")
    Optional<QuotaPackage> findByIdForUpdate(@Param("quotaPackageId") UUID quotaPackageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "feature")
    @Query("select item from QuotaPackage item where item.code in :codes order by item.id")
    List<QuotaPackage> findAllByCodeInForUpdate(@Param("codes") Collection<String> codes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "feature")
    @Query("select item from QuotaPackage item where item.lineageId = :lineageId order by item.revisionNumber, item.id")
    List<QuotaPackage> findLineageForUpdate(@Param("lineageId") UUID lineageId);

    @EntityGraph(attributePaths = "feature")
    List<QuotaPackage> findAllByLineageIdOrderByRevisionNumberDesc(UUID lineageId);

    @Query("select coalesce(max(item.revisionNumber), 0) from QuotaPackage item where item.lineageId = :lineageId")
    int findMaximumRevisionNumber(@Param("lineageId") UUID lineageId);

    @Query("""
            select item.lineageId, max(item.revisionNumber),
                   sum(case when item.status = com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.DRAFT
                       then 1 else 0 end)
            from QuotaPackage item
            where item.lineageId in :lineageIds
            group by item.lineageId
            """)
    List<Object[]> findLineageSummaries(@Param("lineageIds") Collection<UUID> lineageIds);

    long countBySourceQuotaPackage_Id(UUID quotaPackageId);

    @Query("""
            select target.id, count(distinct item.id)
            from Plan target, QuotaPackage item
            where target.id in :targetIds
              and locate(concat(concat('"', target.code), '"'),
                         cast(item.allowedPlanCodes as string)) > 0
            group by target.id
            """)
    List<Object[]> countPlanReferences(@Param("targetIds") Collection<UUID> targetIds);

    @Query("""
            select target.id, count(distinct item.id)
            from AddOn target, QuotaPackage item
            where target.id in :targetIds
              and item.status in :statuses
              and locate(concat(concat('"', target.code), '"'),
                         cast(item.allowedAddOnCodes as string)) > 0
            group by target.id
            """)
    List<Object[]> countAddOnReferencesByStatusIn(
            @Param("targetIds") Collection<UUID> targetIds,
            @Param("statuses") Collection<QuotaPackageStatus> statuses);

    @EntityGraph(attributePaths = "feature")
    List<QuotaPackage> findAllByIdInOrderByNameAscIdAsc(Collection<UUID> ids);

    @EntityGraph(attributePaths = "feature")
    List<QuotaPackage> findAllByCodeInOrderByNameAscIdAsc(Collection<String> codes);

    @Override
    @EntityGraph(attributePaths = "feature")
    Page<QuotaPackage> findAll(Specification<QuotaPackage> specification, Pageable pageable);
}
