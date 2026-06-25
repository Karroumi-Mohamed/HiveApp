package com.hiveapp.platform.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.admin.config.AdminBootstrapProperties;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    private static final String EMAIL = "bootstrap@example.com";
    private static final String PASSWORD = "bootstrap-password";

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsConfiguredAdminWithoutUsingFixedCredentials() {
        when(adminUserRepository.findByUser_Email(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder(enabledProperties()).seedAdmin();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded");
        assertThat(userCaptor.getValue().getFirstName()).isEqualTo("Bootstrap");
        assertThat(userCaptor.getValue().getLastName()).isEqualTo("Administrator");

        ArgumentCaptor<AdminUser> adminCaptor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserRepository).save(adminCaptor.capture());
        assertThat(adminCaptor.getValue().isSuperAdmin()).isTrue();
        assertThat(adminCaptor.getValue().isActive()).isTrue();
    }

    @Test
    void refusesToPromoteAnExistingNonAdminUser() {
        when(adminUserRepository.findByUser_Email(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> seeder(enabledProperties()).seedAdmin())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to promote");

        verify(userRepository, never()).save(any());
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void rejectsMissingBootstrapCredentials() {
        AdminBootstrapProperties invalid = new AdminBootstrapProperties(
                true, "", "short", "Platform", "Administrator");

        assertThatThrownBy(() -> seeder(invalid).seedAdmin())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email is required");

        verify(userRepository, never()).save(any());
        verify(adminUserRepository, never()).save(any());
    }

    private AdminSeeder seeder(AdminBootstrapProperties properties) {
        return new AdminSeeder(userRepository, adminUserRepository, passwordEncoder, properties);
    }

    private AdminBootstrapProperties enabledProperties() {
        return new AdminBootstrapProperties(
                true, EMAIL, PASSWORD, "Bootstrap", "Administrator");
    }
}
