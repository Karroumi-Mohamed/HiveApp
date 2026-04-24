package com.hiveapp.platform.client.role.service;

import com.hiveapp.platform.client.role.domain.entity.Role;

import java.util.List;
import java.util.UUID;

public interface RoleService {
    Role getRole(UUID id);
    List<Role> getAccountRoles(UUID accountId);
    List<Role> getCompanyRoles(UUID companyId);
    Role createRole(UUID accountId, UUID companyId, String name, String description);
    Role updateRole(UUID roleId, String name, String description);
    void deleteRole(UUID roleId);
    void addPermissionToRole(UUID roleId, String permissionCode);
    void removePermissionFromRole(UUID roleId, String permissionCode);
}
