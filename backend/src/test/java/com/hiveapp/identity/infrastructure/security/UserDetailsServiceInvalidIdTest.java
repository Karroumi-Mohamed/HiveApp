package com.hiveapp.identity.infrastructure.security;

import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.infrastructure.security.AdminUserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceInvalidIdTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminUserRepository adminUserRepository;

    @Test
    void clientLookupTranslatesMalformedUuid() {
        var service = new UserDetailsServiceImpl(userRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("not-a-uuid"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found");
        verifyNoInteractions(userRepository);
    }

    @Test
    void adminLookupTranslatesMalformedUuid() {
        var service = new AdminUserDetailsServiceImpl(adminUserRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("not-a-uuid"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Admin user not found");
        verifyNoInteractions(adminUserRepository);
    }
}
