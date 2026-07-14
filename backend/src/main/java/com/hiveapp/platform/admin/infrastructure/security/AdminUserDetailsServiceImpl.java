package com.hiveapp.platform.admin.infrastructure.security;

import java.util.UUID;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.shared.security.HiveAppUserDetails;

import lombok.RequiredArgsConstructor;

@Service("adminUserDetailsService")
@RequiredArgsConstructor
public class AdminUserDetailsServiceImpl implements UserDetailsService{
    private final AdminUserRepository adminUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        // userId = User.id (the token subject set by AdminAuthController)
        UUID parsedUserId = parseUserId(userId);
        var adminUser = adminUserRepository.findByUserId(parsedUserId)
            .orElseThrow(() -> new UsernameNotFoundException("Admin user not found"));

        // Store User.id in HiveAppUserDetails so ContextDetectionFilter and AdminPermissionPolicy
        // see the correct actorUserId and can call adminUserRepository.findByUserId() consistently
        return new HiveAppUserDetails(
            adminUser.getUser().getId(),
            adminUser.getUser().getEmail(),
            adminUser.getUser().getPasswordHash(),
            adminUser.isActive() && adminUser.getUser().isActive()
        );
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new UsernameNotFoundException("Admin user not found");
        }
    }
}
