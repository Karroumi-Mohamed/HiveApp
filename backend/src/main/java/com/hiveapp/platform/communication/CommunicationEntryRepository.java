package com.hiveapp.platform.communication;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CommunicationEntryRepository
    extends JpaRepository<CommunicationEntry, UUID>, JpaSpecificationExecutor<CommunicationEntry> {
  Optional<CommunicationEntry> findBySourceAndSourceIdAndAccountId(
      String source, UUID sourceId, UUID accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CommunicationEntry e where e.id=:id")
  Optional<CommunicationEntry> lock(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CommunicationEntry e where e.id=:id and e.accountId=:account")
  Optional<CommunicationEntry> lockOwn(@Param("id") UUID id, @Param("account") UUID account);

  Page<CommunicationEntry> findAllByPublicationId(UUID id, Pageable page);

  List<CommunicationEntry> findAllByPublicationIdOrderById(UUID id);

  Optional<CommunicationEntry> findByEventId(UUID id);

  @Modifying
  @Query(
      "update CommunicationEntry e set e.resolvedAt=:now where e.eventType=:type and"
          + " e.resourceId=:resource and e.resolvedAt is null")
  int resolveEvent(
      @Param("type") String type, @Param("resource") UUID resource, @Param("now") Instant now);

  @Modifying
  @Query(
      "update CommunicationEntry e set e.cancelled=true, e.resolvedAt=:now where e.eventType=:type"
          + " and e.resourceId=:resource and e.cancelled=false")
  int withdrawEvent(
      @Param("type") String type, @Param("resource") UUID resource, @Param("now") Instant now);

  @Query(
      "select e.id from CommunicationEntry e where e.source in ('ADMIN','EVENT') and"
          + " ((e.availableAt<=:now and (e.cancelled=true or e.hidden=true or (e.resolvedAt is not"
          + " null and e.kind in ('WARNING','ACTION')) or e.expiresAt<=:now or e.nextEmailAttemptAt"
          + " is null or e.nextEmailAttemptAt<=:now) and"
          + " e.delivery.delivery=com.hiveapp.platform.client.plan.dto.RepricingModels$Delivery.PENDING)"
          + " or (e.delivery.delivery=com.hiveapp.platform.client.plan.dto.RepricingModels$Delivery.SENDING"
          + " and e.delivery.claimedAt<:stale)) order by e.availableAt,e.id")
  List<UUID> due(@Param("now") Instant now, @Param("stale") Instant stale, Pageable page);
}
