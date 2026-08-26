package com.hiveapp.platform.client.plan.domain.repository;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
public interface PlanRepository extends JpaRepository<Plan, UUID>, JpaSpecificationExecutor<Plan> {
    Optional<Plan> findByCode(String code);

    List<Plan> findAllByCodeInOrderByIdAsc(Collection<String> codes);

    List<Plan> findAllByOrderByIdAsc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from Plan plan where plan.code in :codes order by plan.id")
    List<Plan> findAllByCodeInForUpdate(@Param("codes") Collection<String> codes);

    long countByStatus(PlanStatus status);

    Slice<Plan> findAllByStatus(PlanStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from Plan plan where plan.id = :planId")
    Optional<Plan> findByIdForUpdate(@Param("planId") UUID planId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from Plan plan where plan.id = :planId")
    Optional<Plan> findByIdForCompositionUpdate(@Param("planId") UUID planId);

    @Modifying(flushAutomatically = true)
    @Query("update Plan plan set plan.version = plan.version + 1 "
            + "where plan.id = :planId and plan.version = :expectedVersion")
    int advanceCompositionVersion(
            @Param("planId") UUID planId,
            @Param("expectedVersion") long expectedVersion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from Plan plan where plan.code = :code")
    Optional<Plan> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from Plan plan where plan.lineageId = :lineageId order by plan.revisionNumber, plan.id")
    List<Plan> findLineageForUpdate(@Param("lineageId") UUID lineageId);

    long countBySourcePlan_Id(UUID planId);

    @Query("""
            select source.id, count(revision.id)
            from Plan source, Plan revision
            where source.id in :sourceIds and revision.sourcePlan.id = source.id
            group by source.id
            """)
    List<Object[]> countSourceReferences(@Param("sourceIds") Collection<UUID> sourceIds);

    @Query("select coalesce(max(plan.revisionNumber), 0) from Plan plan where plan.lineageId = :lineageId")
    int findMaximumRevisionNumber(@Param("lineageId") UUID lineageId);

    @Query("""
            select plan.lineageId, max(plan.revisionNumber),
                   sum(case when plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.DRAFT
                       then 1 else 0 end)
            from Plan plan
            where plan.lineageId in :lineageIds
            group by plan.lineageId
            """)
    List<Object[]> findLineageSummaries(@Param("lineageIds") Collection<UUID> lineageIds);

    List<Plan> findAllByIdInOrderByNameAscIdAsc(Collection<UUID> ids);

    List<Plan> findAllByCodeInOrderByNameAscIdAsc(Collection<String> codes);
}
