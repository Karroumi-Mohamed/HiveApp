package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommercialSegmentRepository extends JpaRepository<CommercialSegment, UUID>,
        JpaSpecificationExecutor<CommercialSegment> {

    boolean existsByCode(String code);

    Optional<CommercialSegment> findByCode(String code);

    @Query("select segment.lineageId from CommercialSegment segment where segment.id = :segmentId")
    Optional<UUID> findLineageIdById(@Param("segmentId") UUID segmentId);

    @Query("select segment.owner.id from CommercialSegment segment where segment.id = :segmentId")
    Optional<UUID> findOwnerAdminUserIdById(@Param("segmentId") UUID segmentId);

    @Override
    Page<CommercialSegment> findAll(Specification<CommercialSegment> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"explicitAccounts", "currentPlanRevisionIds",
            "subscriptionStatuses", "currencyCodes", "billingCycles", "productHoldings",
            "sourceSegment"})
    @Query("select distinct segment from CommercialSegment segment where segment.id = :segmentId")
    Optional<CommercialSegment> findDetailById(@Param("segmentId") UUID segmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select segment from CommercialSegment segment where segment.id = :segmentId")
    Optional<CommercialSegment> findByIdForUpdate(@Param("segmentId") UUID segmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select segment from CommercialSegment segment where segment.lineageId = :lineageId "
            + "order by segment.revisionNumber asc, segment.id asc")
    List<CommercialSegment> findLineageForUpdate(@Param("lineageId") UUID lineageId);

    Page<CommercialSegment> findAllByLineageId(UUID lineageId, Pageable pageable);

    @Query("select coalesce(max(segment.revisionNumber), 0) from CommercialSegment segment "
            + "where segment.lineageId = :lineageId")
    int findMaximumRevisionNumber(@Param("lineageId") UUID lineageId);

    @Query("select segment.lineageId, max(segment.revisionNumber) from CommercialSegment segment "
            + "where segment.lineageId in :lineageIds group by segment.lineageId")
    List<Object[]> findMaximumRevisionNumbers(@Param("lineageIds") Collection<UUID> lineageIds);

    @Query("select segment from CommercialSegment segment where segment.lineageId = :lineageId "
            + "and segment.status = :status order by segment.revisionNumber desc")
    List<CommercialSegment> findByLineageIdAndStatus(
            @Param("lineageId") UUID lineageId,
            @Param("status") CommercialSegmentStatus status);

    @Query("select segment.id, count(account) from CommercialSegment segment "
            + "left join segment.explicitAccounts account where segment.id in :segmentIds "
            + "group by segment.id")
    List<Object[]> countExplicitAccountsBySegmentIds(@Param("segmentIds") Collection<UUID> segmentIds);

    @Query("select account.id from CommercialSegment segment join segment.explicitAccounts account "
            + "where segment.id = :segmentId order by account.id")
    List<UUID> findExplicitAccountIds(@Param("segmentId") UUID segmentId);
}
