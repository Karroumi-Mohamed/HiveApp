package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Transactional outbox: business commit and durable notification intent are one transaction. */
@Entity
@Table(
    name = "notification_events",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_notification_event_key", columnNames = "dedupe_key"),
    indexes = {
      @Index(name = "idx_notification_event_due", columnList = "state,next_attempt_at,id"),
      @Index(name = "idx_notification_event_resource", columnList = "definition_key,resource_id")
    })
@Getter
@Setter
@org.hibernate.annotations.Check(
    constraints =
        "(audience='ACCOUNT' and account_id is not null and recipient_user_id is null) or"
            + " (audience='MEMBER' and account_id is not null and recipient_user_id is not null) or"
            + " (audience='PLATFORM' and account_id is null and recipient_user_id is null and"
            + " company_id is null) or (audience='OPERATOR' and account_id is null and"
            + " recipient_user_id is not null and company_id is null)")
public class NotificationEvent extends BaseEntity {
  public enum State {
    PENDING,
    DELIVERED,
    FAILED
  }

  @Version private long version;

  @Column(name = "dedupe_key", nullable = false, length = 64, updatable = false)
  private String dedupeKey;

  @Column(nullable = false, length = 100, updatable = false)
  private String definitionKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Audience audience;

  private UUID accountId;
  private UUID recipientUserId;
  private UUID companyId;
  private UUID resourceId;
  private UUID senderUserId;

  @Column(length = 255)
  private String senderName;

  @Column(nullable = false, length = 160)
  private String messageTitle;

  @Column(nullable = false, length = 10000)
  private String messageBody;

  @Embedded private ManualNotificationLanguages languages;

  private boolean email;

  @Column(nullable = false)
  private Instant availableAt;

  private Instant expiresAt;
  private Instant resolvedAt;
  private boolean cancelled;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private State state = State.PENDING;

  private int attempts;

  @Column(nullable = false)
  private Instant nextAttemptAt;

  @Column(length = 120)
  private String failureCode;
}
