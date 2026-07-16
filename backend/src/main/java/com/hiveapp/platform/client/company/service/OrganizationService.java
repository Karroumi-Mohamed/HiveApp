package com.hiveapp.platform.client.company.service;

import com.hiveapp.platform.client.company.domain.constant.GroupTemplateScope;
import com.hiveapp.platform.client.company.dto.*;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {
    List<OrganizationGroupDto> listGroups(UUID companyId);
    OrganizationGroupDto createGroup(UUID companyId, UUID parentId, String name, String description, List<String> positions);
    OrganizationGroupDto updateGroup(UUID groupId, String name, String description, List<String> positions);
    OrganizationGroupDto moveGroup(UUID groupId, UUID parentId);
    void reorderGroups(UUID companyId, UUID parentId, List<UUID> orderedGroupIds);
    void archiveGroup(UUID groupId);
    void restoreGroup(UUID groupId);
    void deleteGroup(UUID groupId);
    List<GroupMembershipDto> listMemberships(UUID groupId, boolean includeDescendants);
    List<GroupMembershipDto> listMemberPlacements(UUID companyId, UUID memberId);
    GroupMembershipDto putMembership(UUID groupId, UUID memberId, String positionTitle);
    void removeMembership(UUID groupId, UUID memberId);
    GroupTemplateDto createTemplate(UUID companyId, UUID sourceGroupId, GroupTemplateScope scope, String name);
    List<GroupTemplateDto> listTemplates(UUID companyId);
    GroupTemplatePreviewDto previewTemplate(UUID templateId, UUID companyId, UUID parentId);
    List<OrganizationGroupDto> instantiateTemplate(UUID templateId, UUID companyId, UUID parentId);
    void deleteTemplate(UUID templateId);
}
