package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionChangeOperationRepository extends JpaRepository<SubscriptionChangeOperation, UUID> {

    long countByStatus(SubscriptionChangeStatus status);

    Optional<SubscriptionChangeOperation> findByAccountIdAndStatus(UUID accountId, SubscriptionChangeStatus status);

    Optional<SubscriptionChangeOperation> findTopByAccountIdAndStatusIn(
            UUID accountId, Collection<SubscriptionChangeStatus> statuses);

    Optional<SubscriptionChangeOperation> findByIdAndAccountId(UUID id, UUID accountId);

    List<SubscriptionChangeOperation> findAllByAccountIdOrderByCreatedAtDesc(UUID accountId);

    long countByTargetPlan_Id(UUID planId);

    @Query("""
            select operation.targetPlan.id, count(operation)
            from SubscriptionChangeOperation operation
            where operation.targetPlan.id in :planIds
            group by operation.targetPlan.id
            """)
    List<Object[]> countByTargetPlanIds(@Param("planIds") Collection<UUID> planIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operation from SubscriptionChangeOperation operation "
            + "where operation.status = :status and operation.effectiveAt <= :cutoff")
    List<SubscriptionChangeOperation> findDueForUpdate(
            @Param("status") SubscriptionChangeStatus status,
            @Param("cutoff") Instant cutoff);
}
