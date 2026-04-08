package com.hiveapp.platform.client.collaboration.service;

import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import java.util.List;
import java.util.UUID;

public interface CollaborationService {
    Collaboration initiateCollaboration(UUID clientAccountId, UUID providerAccountId, UUID companyId);
    void acceptCollaboration(UUID id);
    void revokeCollaboration(UUID id);
    List<Collaboration> getClientCollaborations(UUID accountId);
}
