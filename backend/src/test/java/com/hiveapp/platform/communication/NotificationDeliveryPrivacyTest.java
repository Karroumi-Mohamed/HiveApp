package com.hiveapp.platform.communication;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import dev.karroumi.permissionizer.PermissionGuard;
import com.hiveapp.platform.generated.PlatformPermissions;

class NotificationDeliveryPrivacyTest {
  @Test
  void transportAccessDoesNotRevealBusinessLinksAndWithdrawnEventsCannotRetry() {
    var events = mock(NotificationEventRepository.class);
    var entry = new NotificationEvent();
    entry.setDefinitionKey(CoreNotification.PAYMENT_FAILED.key());
    entry.setResourceId(UUID.randomUUID()); entry.setState(NotificationEvent.State.FAILED);
    entry.setFailureCode("DELIVERY_FAILED");
    when(events.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<NotificationEvent>>any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entry)));
    var service = new OperatorNotificationService(mock(CommunicationService.class), events,
        mock(CommunicationEntryRepository.class), Clock.systemUTC());
    try (var scope = PermissionGuard.withPermissions(PlatformPermissions.Notifications.Read_delivery.permission())) {
      var result = service.events(null, PageRequest.of(0, 20)).getContent().getFirst();
      assertThat(result.sourcePath()).isNull();
      assertThat(result.failureCode()).isEqualTo("DELIVERY_FAILED");
      assertThat(result.canRetry()).isTrue();
    }
    try (var scope = PermissionGuard.withPermissions(PlatformPermissions.Billing.Read_invoice.permission())) {
      assertThat(service.events(null, PageRequest.of(0, 20)).getContent().getFirst().sourcePath())
          .isEqualTo("/admin/billing/invoices/" + entry.getResourceId());
    }
    entry.setCancelled(true);
    assertThat(service.events(null, PageRequest.of(0, 20)).getContent().getFirst().canRetry()).isFalse();
  }
}
