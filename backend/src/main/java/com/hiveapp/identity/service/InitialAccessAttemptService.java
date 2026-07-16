package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InitialAccessAttemptService {

    static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        var user = userRepository.findByIdForCredentialUpdate(userId).orElse(null);
        if (user == null || user.getCredentialState() != CredentialState.TEMPORARY_PASSWORD) {
            return;
        }
        int failures = user.getInitialAccessFailedAttempts() + 1;
        user.setInitialAccessFailedAttempts(failures);
        if (failures >= MAX_FAILED_ATTEMPTS) {
            user.setInitialAccessLocked(true);
        }
        userRepository.saveAndFlush(user);
    }
}
