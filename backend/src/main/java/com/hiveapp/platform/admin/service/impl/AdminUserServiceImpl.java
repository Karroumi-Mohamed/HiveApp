package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminUserService;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final UserRepository userRepository;

    @Override
    public AdminUser getAdminUser(UUID id) {
        return adminUserRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AdminUser not found"));
    }

    @Override
    public List<AdminUser> getAllAdminUsers() {
        return adminUserRepository.findAll();
    }

    @Override
    @Transactional
    public AdminUser createAdminUser(UUID userId, boolean isSuperAdmin) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            
        AdminUser adminUser = new AdminUser();
        adminUser.setUser(user);
        adminUser.setSuperAdmin(isSuperAdmin);
        adminUser.setActive(true);
        return adminUserRepository.save(adminUser);
    }

    @Override
    @Transactional
    public void deactivateAdminUser(UUID id) {
        var adminUser = getAdminUser(id);
        adminUser.setActive(false);
        adminUserRepository.save(adminUser);
    }
}
