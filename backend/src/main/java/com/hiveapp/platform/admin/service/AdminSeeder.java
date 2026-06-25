package com.hiveapp.platform.admin.service;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.admin.config.AdminBootstrapProperties;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "hiveapp.admin.bootstrap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder {

    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    @Order(4)
    @Transactional
    public void seedAdmin() {
        properties.validateForBootstrap();
        String adminEmail = properties.email().trim().toLowerCase(Locale.ROOT);

        if (adminUserRepository.findByUser_Email(adminEmail).isPresent()) {
            log.info("Admin bootstrap already completed for {}", adminEmail);
            return;
        }

        if (userRepository.findByEmail(adminEmail).isPresent()) {
            throw new IllegalStateException(
                    "Refusing to promote an existing non-admin user during admin bootstrap: " + adminEmail);
        }

        log.info("Creating configured bootstrap SuperAdmin for {}", adminEmail);

        User user = new User();
        user.setEmail(adminEmail);
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setFirstName(properties.firstName());
        user.setLastName(properties.lastName());
        user.setActive(true);
        user = userRepository.save(user);

        AdminUser admin = new AdminUser();
        admin.setUser(user);
        admin.setSuperAdmin(true);
        admin.setActive(true);
        adminUserRepository.save(admin);

        log.info("Configured bootstrap SuperAdmin created for {}", adminEmail);
    }
}
