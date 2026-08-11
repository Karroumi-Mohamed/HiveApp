package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.dto.AdminUserResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminUserService {
    AdminUserResponseDto getAdminUser(UUID id);
    Page<AdminUserResponseDto> getAdminUsers(Pageable pageable);
    AdminUserResponseDto createAdminUser(UUID userId, boolean isSuperAdmin);
    com.hiveapp.platform.admin.dto.AdminMeDto getAdminDetails(UUID userId);
    void toggleActive(UUID id);
    void assignRole(UUID adminUserId, UUID adminRoleId);
    void removeRole(UUID adminUserId, UUID adminRoleId);
}
