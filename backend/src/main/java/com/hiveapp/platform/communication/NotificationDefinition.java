package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import java.util.UUID;

/** Code-owned contract. New business modules register a definition, not a client-supplied URL. */
public interface NotificationDefinition {
  String key();

  Topic topic();

  Kind kind();

  String requiredPermission();

  default Purpose purpose() {
    return Purpose.SERVICE;
  }

  default boolean platform() {
    return false;
  }

  default boolean optional() {
    return false;
  }

  default String actionPath(UUID resourceId) {
    return null;
  }
}
