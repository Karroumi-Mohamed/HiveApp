package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;
import com.hiveapp.platform.admin.dto.AdminPermissionSummaryDto;
import com.hiveapp.platform.admin.dto.AdminRoleImpactDto;
import com.hiveapp.platform.admin.dto.AdminRoleHistoryEntryDto;
import com.hiveapp.platform.admin.dto.AdminRolePresetDto;
import com.hiveapp.platform.admin.dto.AdminRoleResponseDto;
import com.hiveapp.platform.admin.dto.BulkOperationResult;
import com.hiveapp.platform.admin.dto.RoleHolderDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AdminRoleService {
    AdminRoleResponseDto getAdminRole(UUID id);

    Page<AdminRoleResponseDto> getAdminRoles(String search, AdminRoleStatus status, Pageable pageable);

    default Page<AdminRoleResponseDto> getAdminRoles(Pageable pageable) {
        return getAdminRoles(null, null, pageable);
    }

    AdminRoleResponseDto createAdminRole(String name, String description, List<UUID> permissionIds);

    default AdminRoleResponseDto createAdminRole(String name, String description) {
        return createAdminRole(name, description, List.of());
    }

    AdminRoleResponseDto createAdminRoleFromPreset(
            String presetCode, String name, String description, List<UUID> permissionIds);

    AdminRoleResponseDto duplicateAdminRole(
            UUID sourceRoleId, String name, String description);

    AdminRoleResponseDto updateAdminRole(
            UUID id, String name, String description, Long expectedVersion);

    default AdminRoleResponseDto updateAdminRole(UUID id, String name, String description) {
        return updateAdminRole(id, name, description, null);
    }

    List<AdminRolePresetDto> getAvailablePresets();

    List<AdminPermissionSummaryDto> getGrantablePermissions();

    AdminRoleImpactDto previewImpact(
            UUID roleId, List<UUID> proposedPermissionIds, AdminRoleStatus proposedStatus);

    AdminRoleResponseDto replacePermissions(
            UUID roleId,
            List<UUID> permissionIds,
            Long expectedVersion,
            Long confirmedAssignmentCount);

    AdminRoleResponseDto transitionStatus(
            UUID roleId,
            AdminRoleStatus status,
            Long expectedVersion,
            Long confirmedAssignmentCount);

    void deleteRole(UUID roleId);

    List<RoleHolderDto> getRoleHolders(UUID id);

    List<AdminRoleHistoryEntryDto> getRoleHistory(UUID id);

    /** Compatibility for existing clients; new UI uses explicit status transitions. */
    void toggleActive(UUID id);

    BulkOperationResult setActiveBulk(List<UUID> ids, boolean active);

    /** Compatibility for unused-role setup; assigned roles use replacePermissions with preview. */
    void grantPermission(UUID adminRoleId, UUID permissionId);

    /** Compatibility for unused-role setup; assigned roles use replacePermissions with preview. */
    void revokePermission(UUID adminRoleId, UUID permissionId);
}
