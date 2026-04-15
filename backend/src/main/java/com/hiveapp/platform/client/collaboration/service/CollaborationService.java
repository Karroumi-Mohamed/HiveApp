package com.hiveapp.platform.client.collaboration.service;

import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.domain.entity.CollaborationPermission;
import java.util.List;
import java.util.UUID;

public interface CollaborationService {
    Collaboration getCollaboration(UUID id);
    Collaboration initiateCollaboration(UUID clientAccountId, UUID companyId);
    void acceptCollaboration(UUID id);
    void revokeCollaboration(UUID id);
    List<Collaboration> getClientCollaborations(UUID accountId);
    List<Collaboration> getProviderCollaborations(UUID accountId);

    void grantPermission(UUID collaborationId, String permissionCode);
    void revokePermission(UUID collaborationId, String permissionCode);
    List<CollaborationPermission> getPermissions(UUID collaborationId);
}
