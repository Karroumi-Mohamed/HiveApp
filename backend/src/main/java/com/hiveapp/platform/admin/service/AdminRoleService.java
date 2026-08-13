package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.dto.AdminRoleResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminRoleService {
    AdminRoleResponseDto getAdminRole(UUID id);
    Page<AdminRoleResponseDto> getAdminRoles(String search, Boolean active, Pageable pageable);
    default Page<AdminRoleResponseDto> getAdminRoles(Pageable pageable) {
        return getAdminRoles(null, null, pageable);
    }
    AdminRoleResponseDto createAdminRole(String name, String description);
    AdminRoleResponseDto updateAdminRole(UUID id, String name, String description);
    /** Who currently holds this role — what a change to it would actually affect. */
    java.util.List<com.hiveapp.platform.admin.dto.RoleHolderDto> getRoleHolders(UUID id);

    void toggleActive(UUID id);

    /** Explicit end state rather than a toggle: a mixed selection has no coherent toggle. */
    com.hiveapp.platform.admin.dto.BulkOperationResult setActiveBulk(
            java.util.List<UUID> ids, boolean active);
    void grantPermission(UUID adminRoleId, UUID permissionId);
    void revokePermission(UUID adminRoleId, UUID permissionId);
}
