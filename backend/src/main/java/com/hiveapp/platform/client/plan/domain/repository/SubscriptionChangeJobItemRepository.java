package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeJobItem;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionChangeJobItemRepository
    extends JpaRepository<SubscriptionChangeJobItem, UUID>,
        JpaSpecificationExecutor<SubscriptionChangeJobItem> {

  interface StatusCount {
    UUID getJobId();
    SubscriptionChangeJobItemStatus getStatus();
    long getTotal();
  }

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

  @EntityGraph(attributePaths = "account")
  List<SubscriptionChangeJobItem> findAllByJobIdAndIdIn(
      UUID jobId, Collection<UUID> ids);

  @Query(
      "select item.job.id as jobId, item.status as status, count(item) as total "
          + "from SubscriptionChangeJobItem item where item.job.id in :jobIds "
          + "group by item.job.id, item.status")
  List<StatusCount> countStatusesByJobIds(@Param("jobIds") Collection<UUID> jobIds);
}
