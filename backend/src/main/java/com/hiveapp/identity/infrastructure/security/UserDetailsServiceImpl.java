package com.hiveapp.identity.infrastructure.security;

import java.util.UUID;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.shared.security.HiveAppUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService{
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        UUID parsedUserId = parseUserId(userId);
        var user = userRepository.findById(parsedUserId)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new HiveAppUserDetails(
            user.getId(),
            user.getEmail() != null ? user.getEmail() : user.getUsername(),
            user.getPasswordHash(),
            user.isActive(),
            user.getCredentialState() == com.hiveapp.identity.domain.constant.CredentialState.ACTIVE
                    && !user.isPasswordChangeRequired());
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new UsernameNotFoundException("User not found");
        }
    }
}
