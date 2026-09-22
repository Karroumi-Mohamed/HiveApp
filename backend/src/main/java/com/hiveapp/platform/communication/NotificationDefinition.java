package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import java.util.UUID;

/** Code-owned contract. New business modules register a definition, not a client-supplied URL. */
public interface NotificationDefinition {
  default CommunicationModels.Priority priority() {
    return CommunicationModels.Priority.NORMAL;
  }

  default NotificationText.Content content(java.util.Locale locale) {
    return null;
  }

  String key();

  Topic topic();

  Kind kind();

  dev.karroumi.permissionizer.Permission requiredPermission();

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
