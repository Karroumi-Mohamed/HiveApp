package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.dto.AdminRoleResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminRoleService {
    AdminRoleResponseDto getAdminRole(UUID id);
    Page<AdminRoleResponseDto> getAdminRoles(Pageable pageable);
    AdminRoleResponseDto createAdminRole(String name, String description);
    AdminRoleResponseDto updateAdminRole(UUID id, String name, String description);
    void toggleActive(UUID id);
    void grantPermission(UUID adminRoleId, UUID permissionId);
    void revokePermission(UUID adminRoleId, UUID permissionId);
}
