package com.hiveapp.platform.client.collaboration.service;

import com.hiveapp.platform.client.collaboration.dto.CollaborationCommandRequest;
import com.hiveapp.platform.client.collaboration.dto.CollaborationDto;
import com.hiveapp.platform.client.collaboration.dto.CollaborationGrantDto;
import com.hiveapp.platform.client.collaboration.dto.CollaborationInitiationResult;
import com.hiveapp.platform.client.collaboration.dto.CompanyShareCodeDto;
import com.hiveapp.platform.client.collaboration.dto.CompanyShareResolutionDto;
import com.hiveapp.platform.client.collaboration.dto.InitiateCollaborationRequest;
import com.hiveapp.platform.registry.dto.PermissionPickerCatalogDto;
import java.util.List;
import java.util.UUID;

public interface CollaborationService {
    CollaborationDto getCollaboration(UUID id);
    CollaborationInitiationResult initiateCollaboration(UUID clientAccountId, InitiateCollaborationRequest request);
    CollaborationDto acceptCollaboration(UUID providerAccountId, UUID id, CollaborationCommandRequest request);
    CollaborationDto rejectCollaboration(UUID providerAccountId, UUID id, CollaborationCommandRequest request);
    CollaborationDto cancelRequest(UUID clientAccountId, UUID id, CollaborationCommandRequest request);
    CollaborationDto suspendCollaboration(UUID providerAccountId, UUID id, CollaborationCommandRequest request);
    CollaborationDto resumeCollaboration(UUID providerAccountId, UUID id, CollaborationCommandRequest request);
    CollaborationDto revokeCollaboration(UUID accountId, UUID id, CollaborationCommandRequest request);
    List<CollaborationDto> getClientCollaborations(UUID accountId);
    List<CollaborationDto> getProviderCollaborations(UUID accountId);

    void grantPermission(UUID providerAccountId, UUID collaborationId, String permissionCode, String registryVersion);
    void revokePermission(UUID providerAccountId, UUID collaborationId, String permissionCode);
    List<CollaborationGrantDto> getPermissions(UUID collaborationId);
    PermissionPickerCatalogDto getPermissionCatalog(UUID providerAccountId, UUID collaborationId);

    CompanyShareCodeDto regenerateCompanyShareCode(UUID providerAccountId, UUID companyId);
    CompanyShareCodeDto getCompanyShareCode(UUID providerAccountId, UUID companyId);
    CompanyShareCodeDto setCompanyShareCodeEnabled(UUID providerAccountId, UUID companyId, boolean enabled);
    CompanyShareResolutionDto resolveCompanyShareCode(String shareCode);
}
