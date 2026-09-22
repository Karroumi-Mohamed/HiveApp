package com.hiveapp.platform.communication;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationReplyRepository extends JpaRepository<CommunicationReply, UUID> {
  @org.springframework.data.jpa.repository.Query(
      "select r.entryId, count(r) from CommunicationReply r where r.entryId in :ids group by"
          + " r.entryId")
  List<Object[]> counts(
      @org.springframework.data.repository.query.Param("ids") Collection<UUID> ids);

  Optional<CommunicationReply> findByEntryIdAndActorIdAndCommandId(
      UUID entryId, UUID actorId, UUID commandId);

  Page<CommunicationReply> findAllByEntryId(UUID id, Pageable page);

  long countByEntryId(UUID id);
}
