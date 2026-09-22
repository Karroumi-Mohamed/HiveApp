package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.account.dto.AccountDto;
import java.util.UUID;
import com.hiveapp.platform.communication.CommunicationModels.*;
import org.springframework.data.domain.*;

public interface AccountShellService {
    Page<Item> communicationInbox(Kind kind, boolean archived, boolean unread, Pageable page);
    Item communicationDetail(UUID id);
    void readCommunication(UUID id);
    void acknowledgeCommunication(UUID id);
    void archiveCommunication(UUID id, boolean archived);
    Page<Reply> communicationThread(UUID id, Pageable page);
    Reply replyCommunication(UUID id, ReplyRequest request);
    Preference communicationPreference();
    Preference updateCommunicationPreference(Preference request);
    AccountDto getAccount(UUID id);
    void deactivateAccount(UUID id);
}
