package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeJobItem;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface SubscriptionChangeJobItemRepository
    extends JpaRepository<SubscriptionChangeJobItem, UUID>,
        JpaSpecificationExecutor<SubscriptionChangeJobItem> {

  interface StatusCount {
    UUID getJobId();
    SubscriptionChangeJobItemStatus getStatus();
    long getTotal();
  }

  interface ConflictCount {
    String getCode();
    long getTotal();
  }

  @Query("select item.outcomeCode as code, count(item) as total from SubscriptionChangeJobItem item "
      + "where item.job.id = :jobId and item.status = com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus.CONFLICT "
      + "group by item.outcomeCode order by count(item) desc, item.outcomeCode")
  List<ConflictCount> countPrimaryConflicts(@Param("jobId") UUID jobId);

  @EntityGraph(attributePaths = {"account", "job"})
  Page<SubscriptionChangeJobItem> findAllByJobId(UUID jobId, Pageable pageable);

  @EntityGraph(attributePaths = {"account", "job"})
  Optional<SubscriptionChangeJobItem> findByIdAndJobId(UUID id, UUID jobId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"account", "job"})
  @Query("select item from SubscriptionChangeJobItem item where item.id = :id")
  Optional<SubscriptionChangeJobItem> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      "select item.id from SubscriptionChangeJobItem item "
          + "where item.job.id = :jobId and item.status in :statuses order by item.id")
  List<UUID> findIdsByJobAndStatuses(
      @Param("jobId") UUID jobId,
      @Param("statuses") Collection<SubscriptionChangeJobItemStatus> statuses);

  @Query("select item.id from SubscriptionChangeJobItem item where item.job.id = :jobId "
      + "and item.status in :statuses and (item.nextAttemptAt is null or item.nextAttemptAt <= :now) "
      + "order by item.nextAttemptAt, item.id")
  List<UUID> findDueItemIds(@Param("jobId") UUID jobId,
      @Param("statuses") Collection<SubscriptionChangeJobItemStatus> statuses,
      @Param("now") Instant now, Pageable pageable);

  @Query("select min(item.nextAttemptAt) from SubscriptionChangeJobItem item where item.job.id = :jobId and item.status in :statuses")
  Instant earliestAttempt(@Param("jobId") UUID jobId,
      @Param("statuses") Collection<SubscriptionChangeJobItemStatus> statuses);

  @Modifying
  @Query("update SubscriptionChangeJobItem item set item.status = :target, item.outcomeCode = :code, "
      + "item.completedAt = :now, item.version = item.version + 1 "
      + "where item.job.id = :jobId and item.status in :sources")
  int transitionItems(@Param("jobId") UUID jobId,
      @Param("sources") Collection<SubscriptionChangeJobItemStatus> sources,
      @Param("target") SubscriptionChangeJobItemStatus target,
      @Param("code") String code, @Param("now") Instant now);

  @Modifying
  @Query("update SubscriptionChangeJobItem item set item.status = :ready, item.outcomeCode = null, "
      + "item.completedAt = null, item.nextAttemptAt = :now, item.version = item.version + 1 "
      + "where item.job.id = :jobId and item.status = :failed")
  int retryTechnicalItems(@Param("jobId") UUID jobId,
      @Param("ready") SubscriptionChangeJobItemStatus ready,
      @Param("failed") SubscriptionChangeJobItemStatus failed, @Param("now") Instant now);

  @Query("select count(item) from SubscriptionChangeJobItem item where item.account.id = :accountId "
      + "and item.job.planLineageId is not null and item.job.confirmedAt is not null "
      + "and item.job.status <> com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus.CANCELLED "
      + "and item.status in (com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus.READY, "
      + "com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus.WAITING) "
      + "and (:ignoredItemId is null or item.id <> :ignoredItemId)")
  long countOtherPendingContent(@Param("accountId") UUID accountId, @Param("ignoredItemId") UUID ignoredItemId);

  @Query("select count(other) from SubscriptionChangeJobItem other where other.job.id <> :jobId "
      + "and other.job.planLineageId is not null and other.job.confirmedAt is not null "
      + "and other.job.status <> com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus.CANCELLED "
      + "and other.status in (com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus.READY, "
      + "com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus.WAITING) "
      + "and other.account.id in (select candidate.account.id from SubscriptionChangeJobItem candidate "
      + "where candidate.job.id = :jobId and candidate.status = com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus.READY)")
  long countCompetingContent(@Param("jobId") UUID jobId);


  @EntityGraph(attributePaths = "account")
  List<SubscriptionChangeJobItem> findAllByJobIdAndIdIn(
      UUID jobId, Collection<UUID> ids);

  @Query(
      "select item.job.id as jobId, item.status as status, count(item) as total "
          + "from SubscriptionChangeJobItem item where item.job.id in :jobIds "
          + "group by item.job.id, item.status")
  List<StatusCount> countStatusesByJobIds(@Param("jobIds") Collection<UUID> jobIds);
}
