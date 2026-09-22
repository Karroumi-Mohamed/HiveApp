package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationCatalogTest {
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

          public String requiredPermission() {
            return "business.tasks.read";
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

          public String requiredPermission() {
            return "business.tasks.read";
          }
        };
    assertThatThrownBy(() -> new NotificationCatalog(List.of(unsafe)))
        .isInstanceOf(IllegalStateException.class);
  }
}
