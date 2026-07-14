package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.EmailIdentity;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CredentialAuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public User authenticate(
            String email,
            String password,
            String invalidCredentialsMessage,
            String inactiveAccountMessage
    ) {
        User user = userRepository.findByEmail(EmailIdentity.canonicalize(email))
                .orElseThrow(() -> new UnauthorizedException(invalidCredentialsMessage));

        if (!user.isActive()) {
            throw new UnauthorizedException(inactiveAccountMessage);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException(invalidCredentialsMessage);
        }
        return user;
    }
}
