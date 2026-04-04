package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import java.util.List;
import java.util.UUID;

public interface AdminUserService {
    AdminUser getAdminUser(UUID id);
    List<AdminUser> getAllAdminUsers();
    AdminUser createAdminUser(UUID userId, boolean isSuperAdmin);
    void deactivateAdminUser(UUID id);
}
