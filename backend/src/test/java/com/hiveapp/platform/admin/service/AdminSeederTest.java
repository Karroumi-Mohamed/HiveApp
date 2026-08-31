package com.hiveapp.platform.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.service.IdentityService;
import com.hiveapp.identity.service.NewUserCommand;
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

    @Mock private IdentityService identityService;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsConfiguredAdminWithoutUsingFixedCredentials() {
        when(adminUserRepository.findByUser_Email(EMAIL)).thenReturn(Optional.empty());
        when(identityService.emailExists(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded");
        when(identityService.createUser(any(NewUserCommand.class)))
                .thenAnswer(invocation -> userFrom(invocation.getArgument(0)));

        seeder(enabledProperties()).seedAdmin();

        ArgumentCaptor<NewUserCommand> commandCaptor = ArgumentCaptor.forClass(NewUserCommand.class);
        verify(identityService).createUser(commandCaptor.capture());
        assertThat(commandCaptor.getValue().email()).isEqualTo(EMAIL);
        assertThat(commandCaptor.getValue().passwordHash()).isEqualTo("encoded");
        assertThat(commandCaptor.getValue().firstName()).isEqualTo("Bootstrap");
        assertThat(commandCaptor.getValue().lastName()).isEqualTo("Administrator");

        ArgumentCaptor<AdminUser> adminCaptor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserRepository).save(adminCaptor.capture());
        assertThat(adminCaptor.getValue().isSuperAdmin()).isTrue();
        assertThat(adminCaptor.getValue().isActive()).isTrue();
    }

    @Test
    void refusesToPromoteAnExistingNonAdminUser() {
        when(adminUserRepository.findByUser_Email(EMAIL)).thenReturn(Optional.empty());
        when(identityService.emailExists(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> seeder(enabledProperties()).seedAdmin())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to promote");

        verify(identityService, never()).createUser(any());
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void rejectsMissingBootstrapCredentials() {
        AdminBootstrapProperties invalid = new AdminBootstrapProperties(
                true, "", "short", "Platform", "Administrator");

        assertThatThrownBy(() -> seeder(invalid).seedAdmin())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email is required");

        verify(identityService, never()).createUser(any());
        verify(adminUserRepository, never()).save(any());
    }

    private AdminSeeder seeder(AdminBootstrapProperties properties) {
        return new AdminSeeder(identityService, adminUserRepository, passwordEncoder, properties);
    }

    private AdminBootstrapProperties enabledProperties() {
        return new AdminBootstrapProperties(
                true, EMAIL, PASSWORD, "Bootstrap", "Administrator");
    }

    private static User userFrom(NewUserCommand command) {
        User user = new User();
        user.setUsername(command.username());
        user.setEmail(command.email());
        user.setPasswordHash(command.passwordHash());
        user.setFirstName(command.firstName());
        user.setLastName(command.lastName());
        user.setActive(command.active());
        user.setEmailVerified(command.emailVerified());
        return user;
    }
}
