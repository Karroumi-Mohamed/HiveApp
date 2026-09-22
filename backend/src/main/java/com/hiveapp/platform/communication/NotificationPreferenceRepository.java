package com.hiveapp.platform.communication;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreference, UUID> {
  List<NotificationPreference> findAllByUserId(UUID userId);

  Optional<NotificationPreference> findByUserIdAndTopic(
      UUID userId, CommunicationModels.Topic topic);
}
