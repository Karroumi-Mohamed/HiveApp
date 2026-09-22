package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.karroumi.permissionizer.Permission;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationActionContractTest {
  @Test
  void rejectsIncompleteOrUnsafeFutureModuleActionsBeforePersistence() {
    for (String path : new String[] {null, "https://evil.test/", "/app/../admin/", "/app/tasks/ bad", "/app//tasks"}) {
      var definition = definition(path);
      var catalog = mock(NotificationCatalog.class);
      when(catalog.require(definition.key())).thenReturn(definition);
      var events = mock(NotificationEventRepository.class);
      var publisher = new NotificationPublisher(catalog, events, mock(CommunicationEntryRepository.class),
          mock(NotificationOccurrenceLock.class), mock(NotificationAccess.class), Clock.systemUTC());
      assertThatThrownBy(() -> publisher.publish(definition, "assigned", NotificationPublisher.Target.account(UUID.randomUUID()),
          UUID.randomUUID(), "Task assigned", "Open the task", false, null, null))
          .isInstanceOf(IllegalArgumentException.class);
      verifyNoInteractions(events);
    }
  }

  @Test
  void validFutureModuleActionStillRequiresItsResourceIdentity() {
    var definition = definition("/app/tasks");
    var catalog = mock(NotificationCatalog.class);
    when(catalog.require(definition.key())).thenReturn(definition);
    var publisher = new NotificationPublisher(catalog, mock(NotificationEventRepository.class),
        mock(CommunicationEntryRepository.class), mock(NotificationOccurrenceLock.class),
        mock(NotificationAccess.class), Clock.systemUTC());
    assertThatThrownBy(() -> publisher.publish(definition, "assigned", NotificationPublisher.Target.account(UUID.randomUUID()),
        null, "Task assigned", "Open the task", false, null, null)).isInstanceOf(IllegalArgumentException.class);
  }

  private NotificationDefinition definition(String path) {
    return new NotificationDefinition() {
      public String key() { return "tasks.assigned"; }
      public Topic topic() { return Topic.TASKS; }
      public Kind kind() { return Kind.ACTION; }
      public Permission requiredPermission() { return new Permission("business.tasks.read"); }
      public String actionPath(UUID id) { return path; }
    };
  }
}
