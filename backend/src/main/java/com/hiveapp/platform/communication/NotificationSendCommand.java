package com.hiveapp.platform.communication;

import jakarta.persistence.*;
import java.time.Instant;
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

  public NotificationSendCommand(String commandKey, String payloadHash, Instant createdAt) {
    this.commandKey = commandKey;
    this.payloadHash = payloadHash;
    this.createdAt = createdAt;
  }
}
