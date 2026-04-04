package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.domain.entity.AdminRole;
import com.hiveapp.platform.admin.domain.repository.AdminRoleRepository;
import com.hiveapp.platform.admin.service.AdminRoleService;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

    private final AdminRoleRepository adminRoleRepository;

    @Override
    public AdminRole getAdminRole(UUID id) {
        return adminRoleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AdminRole not found"));
    }

    @Override
    public List<AdminRole> getAllAdminRoles() {
        return adminRoleRepository.findAll();
    }

    @Override
    @Transactional
    public AdminRole createAdminRole(String name, String description) {
        AdminRole adminRole = new AdminRole();
        adminRole.setName(name);
        adminRole.setDescription(description);
        adminRole.setActive(true);
        return adminRoleRepository.save(adminRole);
    }

    @Override
    @Transactional
    public void deactivateAdminRole(UUID id) {
        var adminRole = getAdminRole(id);
        adminRole.setActive(false);
        adminRoleRepository.save(adminRole);
    }
}
