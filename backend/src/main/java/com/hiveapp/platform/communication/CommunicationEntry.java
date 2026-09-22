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
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_communication_source",
          columnNames = {"source", "source_id", "account_id"}),
      @UniqueConstraint(name = "uk_communication_event", columnNames = "event_id")
    },
    indexes = {
      @Index(name = "idx_communication_inbox", columnList = "account_id,available_at,id"),
      @Index(name = "idx_communication_publication", columnList = "publication_id,id"),
      @Index(name = "idx_communication_delivery", columnList = "notice_delivery,available_at,id"),
      @Index(
          name = "idx_communication_personal",
          columnList = "audience,recipient_user_id,account_id,available_at,id"),
      @Index(name = "idx_communication_resource", columnList = "event_type,resource_id")
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
public class CommunicationEntry extends BaseEntity {
  @Column(length = 255) private String senderName;
  @Column(length = 80) private String emailFailureCode;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
  private CommunicationModels.Priority priority = CommunicationModels.Priority.NORMAL;
  @Version private long version;

  @Column(name = "account_id", updatable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Audience audience = Audience.ACCOUNT;

  private UUID recipientUserId;
  private UUID companyId;
  private UUID eventId;

  @Column(length = 100)
  private String eventType;

  private UUID resourceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Topic topic = Topic.GENERAL;

  private Instant resolvedAt;
  private Instant nextEmailAttemptAt;
  private boolean optional;

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
