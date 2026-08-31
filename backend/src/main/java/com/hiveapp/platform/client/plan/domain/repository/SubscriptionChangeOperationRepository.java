package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionChangeOperationRepository
    extends JpaRepository<SubscriptionChangeOperation, UUID> {

  long countByStatus(SubscriptionChangeStatus status);

  Optional<SubscriptionChangeOperation> findByAccountIdAndStatus(
      UUID accountId, SubscriptionChangeStatus status);

  Optional<SubscriptionChangeOperation> findTopByAccountIdAndStatusIn(
      UUID accountId, Collection<SubscriptionChangeStatus> statuses);

  boolean existsByAccountIdAndStatusIn(
      UUID accountId, Collection<SubscriptionChangeStatus> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"checkout"})
  @Query("select operation from SubscriptionChangeOperation operation "
      + "where operation.account.id = :accountId and operation.status in :statuses")
  List<SubscriptionChangeOperation> findOutstandingForLifecycleUpdate(
      @Param("accountId") UUID accountId,
      @Param("statuses") Collection<SubscriptionChangeStatus> statuses);

  @EntityGraph(attributePaths = {"sourceSubscription.plan", "targetPlan", "checkout"})
  Optional<SubscriptionChangeOperation> findByIdAndAccountId(UUID id, UUID accountId);

  @EntityGraph(attributePaths = {"sourceSubscription.plan", "targetPlan", "checkout"})
  Optional<SubscriptionChangeOperation> findByOfferRedemptionId(UUID offerRedemptionId);

  @EntityGraph(attributePaths = {"sourceSubscription.plan", "targetPlan", "checkout"})
  Optional<SubscriptionChangeOperation> findByOfferRedemptionIdAndAccountId(
      UUID offerRedemptionId, UUID accountId);

  @EntityGraph(attributePaths = {"sourceSubscription.plan", "targetPlan", "checkout"})
  @Query(
      "select operation from SubscriptionChangeOperation operation "
          + "where operation.offerRedemptionId in :redemptionIds")
  List<SubscriptionChangeOperation> findAllByOfferRedemptionIdIn(
      @Param("redemptionIds") Collection<UUID> redemptionIds);

  @EntityGraph(attributePaths = {"sourceSubscription.plan", "targetPlan", "checkout"})
  Page<SubscriptionChangeOperation> findAllByAccountId(UUID accountId, Pageable pageable);

  long countByTargetPlan_Id(UUID planId);

  @Query(
      """
      select operation.targetPlan.id, count(operation)
      from SubscriptionChangeOperation operation
      where operation.targetPlan.id in :planIds
      group by operation.targetPlan.id
      """)
  List<Object[]> countByTargetPlanIds(@Param("planIds") Collection<UUID> planIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select operation from SubscriptionChangeOperation operation "
          + "where operation.status = :status and operation.effectiveAt <= :cutoff")
  List<SubscriptionChangeOperation> findDueForUpdate(
      @Param("status") SubscriptionChangeStatus status, @Param("cutoff") Instant cutoff);
}
