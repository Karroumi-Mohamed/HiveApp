package com.hiveapp.platform.communication;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "communication_interactions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_communication_interaction",
            columnNames = {"entry_id", "user_id"}))
@Getter
@Setter
public class CommunicationInteraction extends BaseEntity {
  @Column(name = "entry_id", nullable = false)
  private UUID entryId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  private Instant acknowledgedAt;
  private Instant archivedAt;
}
