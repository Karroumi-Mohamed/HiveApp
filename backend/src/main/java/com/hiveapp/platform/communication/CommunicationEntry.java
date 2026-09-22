package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.platform.client.plan.domain.entity.CommercialNoticeDeliveryState;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(
    name = "communication_entries",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_communication_source",
            columnNames = {"source", "source_id", "account_id"}),
    indexes = {
      @Index(name = "idx_communication_inbox", columnList = "account_id,available_at,id"),
      @Index(name = "idx_communication_publication", columnList = "publication_id,id"),
      @Index(name = "idx_communication_delivery", columnList = "notice_delivery,available_at,id")
    })
@Getter
@Setter
public class CommunicationEntry extends BaseEntity {
  @Version private long version;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Column(name = "publication_id", updatable = false)
  private UUID publicationId;

  @Column(nullable = false, length = 40, updatable = false)
  private String source;

  @Column(name = "source_id", nullable = false, updatable = false)
  private UUID sourceId;

  @Column(nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private Kind kind;

  @Column(nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private Purpose purpose;

  @Column(nullable = false, length = 160)
  private String messageTitle;

  @Column(nullable = false, length = 10000)
  private String messageBody;

  private String requiredPermission;
  private String actionPath;

  @Column(name = "available_at", nullable = false)
  private Instant availableAt;

  private Instant expiresAt;
  private boolean cancelled;
  private boolean hidden;
  private boolean replies;
  private boolean closed;
  private Instant lastReplyAt;
  @Embedded private CommercialNoticeDeliveryState delivery = new CommercialNoticeDeliveryState();
}
