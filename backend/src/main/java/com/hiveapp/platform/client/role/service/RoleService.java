package com.hiveapp.platform.client.role.service;

import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.registry.dto.PermissionPickerModuleDto;
import com.hiveapp.platform.client.role.domain.constant.RoleChangeType;
import com.hiveapp.platform.client.role.dto.RoleImpactDto;

import java.util.List;
import java.util.UUID;

public interface RoleService {
    Role getRole(UUID id);
    List<Role> getAccountRoles(UUID accountId);
    List<Role> getCompanyRoles(UUID companyId);
    Role createRole(UUID accountId, UUID companyId, String name, String description);
    Role updateRole(UUID roleId, String name, String description, Long expectedVersion, Long confirmedAssignmentCount);
    default Role updateRole(UUID roleId, String name, String description) {
        return updateRole(roleId, name, description, null, null);
    }
    void deleteRole(UUID roleId);
    Role addPermissionToRole(UUID roleId, String permissionCode, Long expectedVersion, Long confirmedAssignmentCount);
    default Role addPermissionToRole(UUID roleId, String permissionCode) {
        return addPermissionToRole(roleId, permissionCode, null, null);
    }
    Role removePermissionFromRole(UUID roleId, String permissionCode, Long expectedVersion, Long confirmedAssignmentCount);
    default Role removePermissionFromRole(UUID roleId, String permissionCode) {
        return removePermissionFromRole(roleId, permissionCode, null, null);
    }
    RoleImpactDto previewRoleImpact(UUID roleId, RoleChangeType changeType, String permissionCode);
    Role activateRole(UUID roleId, Long expectedVersion, Long confirmedAssignmentCount);
    Role deactivateRole(UUID roleId, Long expectedVersion, Long confirmedAssignmentCount);
    Role archiveRole(UUID roleId, Long expectedVersion, Long confirmedAssignmentCount);
    Role duplicateRole(UUID roleId, String name, String description);
    List<PermissionPickerModuleDto> getPermissionCatalog(UUID accountId);
}
