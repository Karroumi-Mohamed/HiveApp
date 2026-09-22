package com.hiveapp.platform.communication;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "communication_replies",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_communication_reply_command",
            columnNames = {"entry_id", "actor_id", "command_id"}),
    indexes = @Index(name = "idx_communication_thread", columnList = "entry_id,created_at,id"))
@Getter
@Setter
public class CommunicationReply extends BaseEntity {
  @Column(name = "entry_id", nullable = false)
  private UUID entryId;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(name = "command_id", nullable = false)
  private UUID commandId;

  private boolean fromAdmin;

  @Column(nullable = false, length = 4000)
  private String replyBody;
}
