package com.hiveapp.identity.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.identity.service.ClientCredentialAuthenticationService;
import com.hiveapp.identity.service.CredentialLifecycleService;
import com.hiveapp.platform.client.account.service.WorkspaceProvisioningService;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.security.IssuedTokens;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ClientCredentialAuthenticationService clientCredentialAuthenticationService;
    @Mock CredentialLifecycleService credentialLifecycleService;
    @Mock TokenSessionService tokenSessionService;
    @Mock WorkspaceProvisioningService workspaceProvisioningService;

    @Test
    void registrationCanonicalizesEmailBeforeCheckingAndSaving() {
        var service = service();
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenSessionService.issue(any(), eq(TokenAudience.CLIENT)))
                .thenReturn(new IssuedTokens("access", "refresh", 900));

        service.register(request(" Mixed@Example.COM "));

        verify(userRepository).existsByEmail("mixed@example.com");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("mixed@example.com");
    }

    @Test
    void registrationTranslatesFinalUniqueConstraintRace() {
        var service = service();
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("users.email unique"));

        assertThatThrownBy(() -> service.register(request("Race@Example.COM")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("race@example.com");

        verify(workspaceProvisioningService, never()).provision(any(), any());
    }

    private AuthServiceImpl service() {
        return new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                clientCredentialAuthenticationService,
                credentialLifecycleService,
                tokenSessionService,
                workspaceProvisioningService);
    }

    private RegisterRequest request(String email) {
        return new RegisterRequest(email, "password123", "First", "Last", null);
    }
}
