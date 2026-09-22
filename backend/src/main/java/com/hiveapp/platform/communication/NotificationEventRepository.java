package com.hiveapp.platform.communication;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationEventRepository
    extends JpaRepository<NotificationEvent, UUID>, JpaSpecificationExecutor<NotificationEvent> {
  Optional<NotificationEvent> findByDedupeKey(String key);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from NotificationEvent e where e.id=:id")
  Optional<NotificationEvent> lock(@Param("id") UUID id);

  @Query(
      "select e.id from NotificationEvent e where"
          + " e.state=com.hiveapp.platform.communication.NotificationEvent$State.PENDING and"
          + " e.nextAttemptAt<=:now order by e.nextAttemptAt,e.id")
  List<UUID> due(@Param("now") Instant now, Pageable page);

  @Modifying
  @Query(
      "update NotificationEvent e set e.resolvedAt=:now where e.definitionKey=:type and"
          + " e.resourceId=:resource and e.resolvedAt is null")
  int resolve(
      @Param("type") String type, @Param("resource") UUID resource, @Param("now") Instant now);
}
