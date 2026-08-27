package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddOnRepository extends JpaRepository<AddOn, UUID>, JpaSpecificationExecutor<AddOn> {
    Optional<AddOn> findByCode(String code);
    @Query("select addOn.code from AddOn addOn where addOn.code in :codes")
    List<String> findCodesByCodeIn(@Param("codes") Collection<String> codes);
    @EntityGraph(attributePaths = {"features", "features.feature"})
    List<AddOn> findAllByCodeIn(Collection<String> codes);

    @EntityGraph(attributePaths = {"features", "features.feature"})
    List<AddOn> findAllByOrderByNameAscRevisionNumberDesc();

    List<AddOn> findAllByOrderByIdAsc(Pageable pageable);

    @EntityGraph(attributePaths = {"features", "features.feature"})
    Optional<AddOn> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"features", "features.feature"})
    @Query("select distinct addOn from AddOn addOn where addOn.id in :ids order by addOn.id")
    List<AddOn> findAllDetailedByIdIn(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select addOn from AddOn addOn where addOn.id = :addOnId")
    Optional<AddOn> findByIdForUpdate(@Param("addOnId") UUID addOnId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"features", "features.feature"})
    @Query("select distinct addOn from AddOn addOn where addOn.code in :codes order by addOn.id")
    List<AddOn> findAllByCodeInForUpdate(@Param("codes") Collection<String> codes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select addOn from AddOn addOn where addOn.lineageId = :lineageId order by addOn.revisionNumber")
    List<AddOn> findLineageForUpdate(@Param("lineageId") UUID lineageId);

    @Query("select addOn from AddOn addOn where addOn.lineageId = :lineageId "
            + "order by addOn.revisionNumber, addOn.id")
    List<AddOn> findAllByLineageId(@Param("lineageId") UUID lineageId);

    @Query("select coalesce(max(addOn.revisionNumber), 0) from AddOn addOn where addOn.lineageId = :lineageId")
    int findMaximumRevisionNumber(@Param("lineageId") UUID lineageId);

    @Query("""
            select addOn.lineageId, max(addOn.revisionNumber),
                   sum(case when addOn.status = com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.DRAFT
                       then 1 else 0 end)
            from AddOn addOn
            where addOn.lineageId in :lineageIds
            group by addOn.lineageId
            """)
    List<Object[]> findLineageSummaries(@Param("lineageIds") Collection<UUID> lineageIds);

    @Query("""
            select target.id, count(distinct referring.id)
            from AddOn target, AddOn referring
            where target.id in :targetIds
              and referring.id <> target.id
              and (
                locate(concat(concat('"', target.code), '"'),
                       cast(referring.dependencyCodes as string)) > 0
                or locate(concat(concat('"', target.code), '"'),
                          cast(referring.exclusionCodes as string)) > 0
              )
            group by target.id
            """)
    List<Object[]> countInboundAddOnReferences(@Param("targetIds") Collection<UUID> targetIds);

    @Query("""
            select target.id, count(distinct referring.id)
            from AddOn target, AddOn referring
            where target.id in :targetIds
              and referring.id <> target.id
              and referring.status in :statuses
              and (
                locate(concat(concat('"', target.code), '"'),
                       cast(referring.dependencyCodes as string)) > 0
                or locate(concat(concat('"', target.code), '"'),
                          cast(referring.exclusionCodes as string)) > 0
              )
            group by target.id
            """)
    List<Object[]> countInboundAddOnReferencesByStatusIn(
            @Param("targetIds") Collection<UUID> targetIds,
            @Param("statuses") Collection<AddOnStatus> statuses);

    @Query("""
            select target.id, count(distinct quotaPackage.id)
            from AddOn target, QuotaPackage quotaPackage
            where target.id in :targetIds
              and locate(concat(concat('"', target.code), '"'),
                         cast(quotaPackage.allowedAddOnCodes as string)) > 0
            group by target.id
            """)
    List<Object[]> countInboundQuotaPackageReferences(
            @Param("targetIds") Collection<UUID> targetIds);

    @Query("""
            select target.id, count(distinct addOn.id)
            from Plan target, AddOn addOn
            where target.id in :targetIds
              and (
                locate(concat(concat('"', target.code), '"'),
                       cast(addOn.allowedPlanCodes as string)) > 0
                or locate(concat(concat('"', target.code), '"'),
                          cast(addOn.blockedPlanCodes as string)) > 0
              )
            group by target.id
            """)
    List<Object[]> countPlanReferences(@Param("targetIds") Collection<UUID> targetIds);

    List<AddOn> findAllByIdInOrderByNameAscIdAsc(Collection<UUID> ids);

    List<AddOn> findAllByCodeInOrderByNameAscIdAsc(Collection<String> codes);
}
