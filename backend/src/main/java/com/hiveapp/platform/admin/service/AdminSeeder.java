package com.hiveapp.platform.admin.service;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder {

    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Order(4)
    @Transactional
    public void seedAdmin() {
        String adminEmail = "admin@hiveapp.com";
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }

        log.info("Seeding Super Admin user...");

        User user = new User();
        user.setEmail(adminEmail);
        user.setPasswordHash(passwordEncoder.encode("admin123"));
        user.setFirstName("Super");
        user.setLastName("Admin");
        user.setActive(true);
        user = userRepository.save(user);

        AdminUser admin = new AdminUser();
        admin.setUser(user);
        admin.setSuperAdmin(true);
        admin.setActive(true);
        adminUserRepository.save(admin);

        log.info("Super Admin created: admin@hiveapp.com / admin123");
    }
}
