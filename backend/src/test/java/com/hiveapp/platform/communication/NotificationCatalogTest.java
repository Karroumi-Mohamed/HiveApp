package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationCatalogTest {
  @Test
  void coreNotificationsUseGeneratedPermissionReferences() {
    var declared = java.util.Arrays.stream(
            com.hiveapp.platform.generated.PlatformPermissions.all())
        .map(dev.karroumi.permissionizer.Permission::path).toList();
    for (var definition : CoreNotification.values()) {
      if (definition.requiredPermission() != null)
        assertThat(declared).contains(definition.requiredPermission().path());
    }
  }

  @Test
  void futureTaskProducerCanRegisterWithoutInventingAChatOrTaskStore() {
    var task =
        new NotificationDefinition() {
          public String key() {
            return "tasks.assigned";
          }

          public Topic topic() {
            return Topic.TASKS;
          }

          public Kind kind() {
            return Kind.ACTION;
          }

          public dev.karroumi.permissionizer.Permission requiredPermission() {
            return new dev.karroumi.permissionizer.Permission("business.tasks.read");
          }
        };
    assertThat(new NotificationCatalog(List.of(task)).require("tasks.assigned")).isSameAs(task);
  }

  @Test
  void rejectsDuplicateContractsAndOptionalWarnings() {
    assertThatThrownBy(() -> new NotificationCatalog(List.of(CoreNotification.MEMBER_CREATED)))
        .isInstanceOf(IllegalStateException.class);
    var unsafe =
        new NotificationDefinition() {
          public String key() {
            return "tasks.overdue";
          }

          public Topic topic() {
            return Topic.TASKS;
          }

          public Kind kind() {
            return Kind.WARNING;
          }

          public boolean optional() {
            return true;
          }

          public dev.karroumi.permissionizer.Permission requiredPermission() {
            return new dev.karroumi.permissionizer.Permission("business.tasks.read");
          }
        };
    assertThatThrownBy(() -> new NotificationCatalog(List.of(unsafe)))
        .isInstanceOf(IllegalStateException.class);
  }
}
