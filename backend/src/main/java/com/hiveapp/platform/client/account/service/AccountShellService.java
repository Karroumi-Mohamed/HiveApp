package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.account.dto.AccountDto;
import java.util.UUID;
import com.hiveapp.platform.communication.CommunicationModels.*;
import org.springframework.data.domain.*;

public interface AccountShellService {
    Page<Item> communicationInbox(Kind kind, boolean archived, boolean unread, Pageable page);
    Page<Item> notificationInbox(Kind kind, Topic topic, boolean archived, boolean unread, Pageable page);
    Page<MemberChoice> notificationRecipients(String search, Pageable page);
    void sendInternalNotification(InternalNotice notice);
    java.util.List<NotificationSetting> notificationSettings();
    NotificationSetting updateNotificationSetting(NotificationSetting setting);
    Item communicationDetail(UUID id);
    void readCommunication(UUID id);
    void acknowledgeCommunication(UUID id);
    void archiveCommunication(UUID id, boolean archived);
    Preference communicationPreference();
    Preference updateCommunicationPreference(Preference request);
    AccountDto getAccount(UUID id);
    void deactivateAccount(UUID id);
}
