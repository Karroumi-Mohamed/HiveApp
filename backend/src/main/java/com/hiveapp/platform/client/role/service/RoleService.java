package com.hiveapp.platform.client.role.service;

import com.hiveapp.platform.registry.dto.picker.PermissionPickerCatalogDto;
import com.hiveapp.platform.client.role.domain.constant.RoleChangeType;
import com.hiveapp.platform.client.role.dto.RoleDto;
import com.hiveapp.platform.client.role.dto.RoleImpactDto;
import com.hiveapp.platform.client.role.domain.constant.RoleTemplateBoundary;

import java.util.List;
import java.util.UUID;

public interface RoleService {
    RoleDto getRole(UUID id);
    List<RoleDto> getAccountRoles(UUID accountId);
    List<RoleDto> getCompanyRoles(UUID companyId);
    RoleDto createRole(UUID accountId, RoleTemplateBoundary templateBoundary, UUID boundaryCompanyId,
                    String name, String description);
    RoleDto updateRole(UUID roleId, String name, String description, Long expectedVersion, Long confirmedAssignmentCount);
    default RoleDto updateRole(UUID roleId, String name, String description) {
        return updateRole(roleId, name, description, null, null);
    }
    void deleteRole(UUID roleId);
    RoleDto addPermissionToRole(UUID roleId, String permissionCode, String registryVersion,
                             Long expectedVersion, Long confirmedAssignmentCount);
    default RoleDto addPermissionToRole(UUID roleId, String permissionCode) {
        return addPermissionToRole(roleId, permissionCode, null, null, null);
    }
    RoleDto removePermissionFromRole(UUID roleId, String permissionCode, Long expectedVersion, Long confirmedAssignmentCount);
    default RoleDto removePermissionFromRole(UUID roleId, String permissionCode) {
        return removePermissionFromRole(roleId, permissionCode, null, null);
    }
    RoleImpactDto previewRoleImpact(UUID roleId, RoleChangeType changeType, String permissionCode);
    RoleDto activateRole(UUID roleId, Long expectedVersion, Long confirmedAssignmentCount);
    RoleDto deactivateRole(UUID roleId, Long expectedVersion, Long confirmedAssignmentCount);
    RoleDto archiveRole(UUID roleId, Long expectedVersion, Long confirmedAssignmentCount);
    RoleDto duplicateRole(UUID roleId, String name, String description);
    PermissionPickerCatalogDto getPermissionCatalog(UUID accountId, UUID roleId);
}
