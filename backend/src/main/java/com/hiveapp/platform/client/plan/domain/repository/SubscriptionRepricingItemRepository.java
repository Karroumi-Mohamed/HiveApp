package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.SubscriptionRepricingItem;
import com.hiveapp.platform.client.plan.dto.RepricingModels;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepricingItemRepository
    extends JpaRepository<SubscriptionRepricingItem, UUID> {
  @Query("select i.account.id from SubscriptionRepricingItem i where i.id = :id")
  Optional<UUID> accountId(@Param("id") UUID id);

  List<SubscriptionRepricingItem> findAllByJobIdOrderById(UUID jobId);

  @EntityGraph(
      attributePaths = {
        "job",
        "job.sourcePrice",
        "job.targetPrice",
        "operation",
        "operation.checkout"
      })
  Page<SubscriptionRepricingItem> findAllByJobId(UUID jobId, Pageable pageable);

  @EntityGraph(
      attributePaths = {
        "job",
        "job.sourcePrice",
        "job.targetPrice",
        "operation",
        "operation.checkout"
      })
  Page<SubscriptionRepricingItem> findAllByJobIdAndStatus(
      UUID jobId, RepricingModels.State status, Pageable pageable);

  @EntityGraph(
      attributePaths = {
        "job",
        "job.sourcePrice",
        "job.sourcePrice.plan",
        "job.sourcePrice.addOn",
        "job.sourcePrice.quotaPackage",
        "job.targetPrice",
        "operation",
        "operation.checkout"
      })
  Page<SubscriptionRepricingItem> findAllByAccountIdAndNoticeCreatedAtIsNotNull(
      UUID accountId, Pageable pageable);

  Optional<SubscriptionRepricingItem> findByIdAndAccountIdAndNoticeCreatedAtIsNotNull(
      UUID id, UUID accountId);

  @EntityGraph(attributePaths = "account")
  List<SubscriptionRepricingItem> findAllByJobIdAndIdIn(UUID jobId, Collection<UUID> ids);

  boolean existsByPendingAccountId(UUID accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from SubscriptionRepricingItem i where i.id = :id")
  Optional<SubscriptionRepricingItem> lock(@Param("id") UUID id);

  @Query(
      "select i.id from SubscriptionRepricingItem i where i.status = :status and i.effectiveAt <="
          + " :at order by i.effectiveAt, i.id")
  List<UUID> due(
      @Param("status") RepricingModels.State status, @Param("at") Instant at, Pageable pageable);

  @Query(
      "select i.id from SubscriptionRepricingItem i where i.delivery = :status order by"
          + " i.createdAt, i.id")
  List<UUID> emailDue(@Param("status") RepricingModels.Delivery status, Pageable pageable);

  @Query(
      "select i.id from SubscriptionRepricingItem i where i.delivery = :status and i.emailClaimedAt"
          + " < :at order by i.emailClaimedAt, i.id")
  List<UUID> abandonedEmailClaims(
      @Param("status") RepricingModels.Delivery status, @Param("at") Instant at, Pageable pageable);

  @Query(
      "select i.job.id, i.status, count(i) from SubscriptionRepricingItem i where i.job.id in :ids"
          + " group by i.job.id, i.status")
  List<Object[]> counts(@Param("ids") Collection<UUID> ids);
}
