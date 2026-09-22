package com.hiveapp.platform.communication;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "notification_preferences",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_notification_preference",
            columnNames = {"user_id", "topic"}))
@Getter
@Setter
public class NotificationPreference extends BaseEntity {
  @Column(nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private CommunicationModels.Topic topic;

  private boolean inAppEnabled = true;
  private boolean emailEnabled = true;
}
