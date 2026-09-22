package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeJob;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionChangeJobRepository extends JpaRepository<SubscriptionChangeJob, UUID> {

  Page<SubscriptionChangeJob> findAllByPlanLineageIdIsNull(Pageable pageable);
  Page<SubscriptionChangeJob> findAllByPlanLineageIdIsNullAndStatus(SubscriptionChangeJobStatus status, Pageable pageable);
  Page<SubscriptionChangeJob> findAllByPlanLineageId(UUID lineageId, Pageable pageable);
  boolean existsByIdAndPlanLineageIdIsNull(UUID id);

  Page<SubscriptionChangeJob> findAllByStatus(
      SubscriptionChangeJobStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "items")
  @Query("select job from SubscriptionChangeJob job where job.id = :id")
  Optional<SubscriptionChangeJob> findWithItemsById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select job from SubscriptionChangeJob job where job.id = :id")
  Optional<SubscriptionChangeJob> lockById(@Param("id") UUID id);

  @Query(
      "select job.id from SubscriptionChangeJob job "
          + "where job.status in :statuses and job.executeAt <= :cutoff order by job.executeAt, job.id")
  java.util.List<UUID> findDueIds(
      @Param("statuses") Collection<SubscriptionChangeJobStatus> statuses,
      @Param("cutoff") Instant cutoff,
      Pageable pageable);
}
