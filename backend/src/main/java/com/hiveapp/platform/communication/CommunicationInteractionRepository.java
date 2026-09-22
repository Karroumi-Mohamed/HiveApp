package com.hiveapp.platform.communication;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationInteractionRepository
    extends JpaRepository<CommunicationInteraction, UUID> {
  @org.springframework.data.jpa.repository.Query(
      "select r.entryId, count(r) from CommunicationInteraction r where r.entryId in :ids and"
          + " r.acknowledgedAt is not null group by r.entryId")
  List<Object[]> counts(
      @org.springframework.data.repository.query.Param("ids") Collection<UUID> ids);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "update CommunicationInteraction r set r.archivedAt=null where r.entryId=:id")
  void unarchiveThread(@org.springframework.data.repository.query.Param("id") UUID id);

  Optional<CommunicationInteraction> findByEntryIdAndUserId(UUID entryId, UUID userId);

  List<CommunicationInteraction> findAllByUserIdAndEntryIdIn(UUID userId, Collection<UUID> ids);

  long countByEntryIdAndAcknowledgedAtIsNotNull(UUID id);
}
