package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.domain.entity.AdminRole;

import java.util.List;
import java.util.UUID;

public interface AdminRoleService {
    AdminRole getAdminRole(UUID id);
    List<AdminRole> getAllAdminRoles();
    AdminRole createAdminRole(String name, String description);
    AdminRole updateAdminRole(UUID id, String name, String description);
    void toggleActive(UUID id);
    void grantPermission(UUID adminRoleId, UUID adminPermissionId);
    void revokePermission(UUID adminRoleId, UUID adminPermissionId);
}
