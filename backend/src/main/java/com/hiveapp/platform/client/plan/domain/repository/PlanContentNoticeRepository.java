package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.PlanContentNotice;
import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PlanContentNoticeRepository extends JpaRepository<PlanContentNotice, UUID> {
  Optional<PlanContentNotice> findByCommandId(UUID commandId);

  List<PlanContentNotice> findAllByCommandIdIn(Collection<UUID> commandIds);

  Page<PlanContentNotice> findAllByAccountId(UUID accountId, Pageable pageable);

  Optional<PlanContentNotice> findByIdAndAccountId(UUID id, UUID accountId);

  @Query("select n.account.id from PlanContentNotice n where n.id = :id")
  Optional<UUID> accountId(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from PlanContentNotice n where n.id = :id")
  Optional<PlanContentNotice> lock(@Param("id") UUID id);

  @Query(
      "select n.id from PlanContentNotice n where n.delivery.delivery = :pending or"
          + " (n.delivery.delivery = :sending and n.delivery.claimedAt < :abandoned) order by"
          + " n.createdAt, n.id")
  List<UUID> due(
      @Param("pending") Delivery pending,
      @Param("sending") Delivery sending,
      @Param("abandoned") Instant abandoned,
      Pageable pageable);

  @Query(
      "select n.id from PlanContentNotice n where n.jobId = :jobId and n.delivery.delivery in"
          + " :states order by n.id")
  List<UUID> retryCandidates(
      @Param("jobId") UUID jobId, @Param("states") Collection<Delivery> states, Pageable pageable);

  @Modifying
  @Query(
      "update PlanContentNotice n set n.state = :cancelled, n.version = n.version + 1 "
          + "where n.jobId = :jobId and n.state <> :applied")
  int cancelUnapplied(
      @Param("jobId") UUID jobId,
      @Param("cancelled")
          com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.State cancelled,
      @Param("applied") com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.State applied);
}
