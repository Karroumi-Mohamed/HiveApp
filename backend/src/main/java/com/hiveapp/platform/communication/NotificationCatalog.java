package com.hiveapp.platform.communication;

import com.hiveapp.platform.registry.service.CurrentRegistrySnapshot;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class NotificationCatalog {
  private final Map<String, NotificationDefinition> definitions;
  private final CurrentRegistrySnapshot registry;

  public NotificationCatalog(
      List<NotificationDefinition> extensions, CurrentRegistrySnapshot registry) {
    this.registry = registry;
    var found = new LinkedHashMap<String, NotificationDefinition>();
    var all = new ArrayList<NotificationDefinition>(List.of(CoreNotification.values()));
    all.addAll(extensions);
    for (var definition : all) {
      if (definition.key() == null
          || !definition.key().matches("[a-z][a-z0-9_.]{2,99}")
          || definition.topic() == null
          || definition.kind() == null
          || definition.purpose() == null
          || definition.priority() == null
          || (definition.purpose() == CommunicationModels.Purpose.MARKETING
              && definition.priority() != CommunicationModels.Priority.NORMAL)
          || definition.kind() == CommunicationModels.Kind.MESSAGE
          || (definition.platform()
              && definition.purpose() == CommunicationModels.Purpose.MARKETING)
          || (definition.kind() == CommunicationModels.Kind.OFFER
              && definition.purpose() != CommunicationModels.Purpose.MARKETING)
          || (definition.optional()
              && (definition.kind() == CommunicationModels.Kind.WARNING
                  || definition.kind() == CommunicationModels.Kind.ACTION))
          || (definition.kind() == CommunicationModels.Kind.WARNING
              && definition.purpose() == CommunicationModels.Purpose.MARKETING)
          || found.putIfAbsent(definition.key(), definition) != null)
        throw new IllegalStateException("Invalid or duplicate notification definition.");
    }
    definitions = Map.copyOf(found);
  }

  public NotificationDefinition require(String key) {
    var definition = definitions.get(key);
    if (definition == null)
      throw new IllegalArgumentException("Unregistered notification type: " + key);
    validatePermission(definition);
    return definition;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(2) // The registry synchronizer installs the validated snapshot at order 1.
  public void validatePermissions() {
    definitions.values().forEach(this::validatePermission);
  }

  private void validatePermission(NotificationDefinition definition) {
    var permission = definition.requiredPermission();
    if (permission != null && !registry.containsAction(permission.path()))
      throw new IllegalStateException(
          "Notification "
              + definition.key()
              + " references an unregistered permission: "
              + permission.path());
  }

  public Collection<NotificationDefinition> all() {
    return definitions.values();
  }
}
