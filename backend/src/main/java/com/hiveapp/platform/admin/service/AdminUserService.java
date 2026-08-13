package com.hiveapp.platform.admin.service;

import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.platform.admin.dto.AdminUserResponseDto;
import com.hiveapp.platform.admin.dto.AdminAccessOverviewDto;
import com.hiveapp.platform.admin.dto.AdminUserCreationResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminUserService {
    AdminAccessOverviewDto getAccessOverview();
    AdminUserResponseDto getAdminUser(UUID id);
    Page<AdminUserResponseDto> getAdminUsers(String search, Boolean active, Pageable pageable);
    default Page<AdminUserResponseDto> getAdminUsers(Pageable pageable) {
        return getAdminUsers(null, null, pageable);
    }
    AdminUserCreationResponse createAdminUser(
            String firstName, String lastName, String email,
            InitialAccessMethod initialAccessMethod,
            boolean isSuperAdmin);
    /** What this operator may actually do, resolved from their active roles. */
    AdminUserResponseDto renameOperator(UUID id, String firstName, String lastName);
    java.util.List<String> getEffectivePermissions(UUID id);
    com.hiveapp.platform.admin.dto.AdminOperatorAccessResponse resendActivation(UUID id);
    com.hiveapp.platform.admin.dto.AdminOperatorAccessResponse generateTemporaryAccess(UUID id);
    void toggleActive(UUID id);

    /**
     * Sets active state explicitly rather than toggling: a mixed selection has no coherent
     * toggle, and the caller always knows which end state it wants.
     */
    com.hiveapp.platform.admin.dto.BulkOperationResult setActiveBulk(
            java.util.List<UUID> ids, boolean active);
    com.hiveapp.platform.admin.dto.BulkOperationResult assignRoleBulk(
            java.util.List<UUID> ids, UUID adminRoleId);
    com.hiveapp.platform.admin.dto.BulkOperationResult resendActivationBulk(java.util.List<UUID> ids);
    void assignRole(UUID adminUserId, UUID adminRoleId);
    void removeRole(UUID adminUserId, UUID adminRoleId);
}
