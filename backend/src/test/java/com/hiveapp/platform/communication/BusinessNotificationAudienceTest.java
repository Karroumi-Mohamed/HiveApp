package com.hiveapp.platform.communication;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BusinessNotificationAudienceTest {
  @Test
  void requestSenderGetsInformationWhileProviderGetsActionAndBothAreResolvedLater() {
    var publisher = mock(NotificationPublisher.class);
    var service = new BusinessNotifications(publisher);
    var client = new Account();
    var provider = new Account();
    ReflectionTestUtils.setField(client, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(provider, "id", UUID.randomUUID());
    var collaboration = new Collaboration();
    ReflectionTestUtils.setField(collaboration, "id", UUID.randomUUID());
    collaboration.setClientAccount(client);
    collaboration.setProviderAccount(provider);
    collaboration.setStatus(CollaborationStatus.PENDING);
    service.collaboration(collaboration);
    verify(publisher)
        .publish(
            eq(CoreNotification.B2B_REQUEST_SENT),
            anyString(),
            eq(NotificationPublisher.Target.account(client.getId())),
            eq(collaboration.getId()),
            anyString(),
            anyString(),
            eq(false),
            isNull(),
            isNull());
    verify(publisher)
        .publish(
            eq(CoreNotification.B2B_REQUEST),
            anyString(),
            eq(NotificationPublisher.Target.account(provider.getId())),
            eq(collaboration.getId()),
            anyString(),
            anyString(),
            eq(false),
            isNull(),
            isNull());
    collaboration.setStatus(CollaborationStatus.REJECTED);
    service.collaboration(collaboration);
    verify(publisher).resolve(CoreNotification.B2B_REQUEST, collaboration.getId());
    verify(publisher).resolve(CoreNotification.B2B_REQUEST_SENT, collaboration.getId());
  }
}
