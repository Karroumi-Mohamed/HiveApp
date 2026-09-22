package com.hiveapp.platform.communication;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "notification_language_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationLanguagePreference {
  @Id private UUID userId;

  @Column(nullable = false, length = 2)
  private String language = "fr";

  public NotificationLanguagePreference(UUID userId, String language) {
    this.userId = userId;
    this.language = language;
  }
}
