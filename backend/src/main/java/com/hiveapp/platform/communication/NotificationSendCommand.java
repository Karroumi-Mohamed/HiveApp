package com.hiveapp.platform.communication;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Retained idempotency receipt: a retry cannot change content or widen its frozen audience. */
@Entity
@Table(name = "notification_send_commands")
@Getter
@NoArgsConstructor
public class NotificationSendCommand {
  @Id
  @Column(length = 110)
  private String commandKey;

  @Column(nullable = false, length = 64)
  private String payloadHash;

  @Column(nullable = false)
  private Instant createdAt;

  private UUID commandId;
  private UUID accountId;
  private UUID senderUserId;
  @Column(length = 160) private String messageTitle;
  @Column(length = 10000) private String messageBody;
  private int recipients;

  public NotificationSendCommand(String commandKey, String payloadHash, Instant createdAt) {
    this.commandKey = commandKey;
    this.payloadHash = payloadHash;
    this.createdAt = createdAt;
  }

  public NotificationSendCommand(String key, String hash, Instant at, UUID account, UUID sender, CommunicationModels.InternalNotice notice) {
    this(key, hash, at);
    commandId = notice.commandId(); accountId = account; senderUserId = sender;
    messageTitle = notice.messageTitle().trim(); messageBody = notice.messageBody().trim(); recipients = notice.memberIds().size();
  }
}
