package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationCatalogTest {
  @Test
  void coreNotificationsUseGeneratedPermissionReferences() {
    var declared =
        java.util.Arrays.stream(com.hiveapp.platform.generated.PlatformPermissions.all())
            .map(dev.karroumi.permissionizer.Permission::path)
            .toList();
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

          public String actionPath(java.util.UUID id) {
            return "/app/tasks/" + id;
          }
        };
    var catalog = new NotificationCatalog(List.of(task), registry("business.tasks.read"));
    catalog.validatePermissions();
    assertThat(catalog.require("tasks.assigned")).isSameAs(task);
    var invalid = new NotificationCatalog(List.of(task), registry());
    assertThatThrownBy(invalid::validatePermissions)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("business.tasks.read");
    assertThatThrownBy(() -> invalid.require(task.key()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tasks.assigned");
  }

  @Test
  void priorityIsIndependentAndMarketingCannotClaimHighUrgency() {
    assertThat(CoreNotification.PAYMENT_FAILED.priority()).isEqualTo(Priority.HIGH);
    assertThat(CoreNotification.OFFER_AVAILABLE.priority()).isEqualTo(Priority.NORMAL);
    var marketing =
        new NotificationDefinition() {
          public String key() {
            return "marketing.information";
          }

          public Topic topic() {
            return Topic.COMMERCIAL;
          }

          public Kind kind() {
            return Kind.NOTICE;
          }

          public Priority priority() {
            return Priority.HIGH;
          }

          public Purpose purpose() {
            return Purpose.MARKETING;
          }

          public dev.karroumi.permissionizer.Permission requiredPermission() {
            return null;
          }
        };
    assertThatThrownBy(() -> new NotificationCatalog(List.of(marketing), registry()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsDuplicateContractsAndOptionalWarnings() {
    assertThatThrownBy(
            () -> new NotificationCatalog(List.of(CoreNotification.MEMBER_CREATED), registry()))
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
    assertThatThrownBy(() -> new NotificationCatalog(List.of(unsafe), registry()))
        .isInstanceOf(IllegalStateException.class);
  }

  private com.hiveapp.platform.registry.service.CurrentRegistrySnapshot registry(String... extra) {
    var paths = new java.util.HashSet<String>();
    for (var permission : com.hiveapp.platform.generated.PlatformPermissions.all())
      paths.add(permission.path());
    paths.addAll(List.of(extra));
    var registry = new com.hiveapp.platform.registry.service.CurrentRegistrySnapshot();
    registry.install(
        new com.hiveapp.platform.registry.service.RegistrySnapshot(
            List.of(), List.of(), paths, "test"));
    return registry;
  }
}
