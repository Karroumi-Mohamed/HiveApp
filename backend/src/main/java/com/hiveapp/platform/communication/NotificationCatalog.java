package com.hiveapp.platform.communication;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class NotificationCatalog {
  private final Map<String, NotificationDefinition> definitions;

  public NotificationCatalog(List<NotificationDefinition> extensions) {
    var found = new LinkedHashMap<String, NotificationDefinition>();
    var all = new ArrayList<NotificationDefinition>(List.of(CoreNotification.values()));
    all.addAll(extensions);
    for (var definition : all) {
      if (definition.key() == null
          || !definition.key().matches("[a-z][a-z0-9_.]{2,99}")
          || definition.topic() == null
          || definition.kind() == null
          || definition.purpose() == null
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
    return definition;
  }

  public Collection<NotificationDefinition> all() {
    return definitions.values();
  }
}
