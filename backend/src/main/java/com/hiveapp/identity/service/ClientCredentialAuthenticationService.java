package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.EmailIdentity;
import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClientCredentialAuthenticationService {

    private static final String INVALID_CREDENTIALS = "Invalid login credentials";

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final InitialAccessAttemptService initialAccessAttemptService;

    @Transactional
    public ClientAuthenticationResult authenticate(LoginRequest request) {
        User candidate = findUser(request);
        Member member = memberRepository.findByUserIdAndIsActiveTrue(candidate.getId())
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        if (!candidate.isActive() || !member.getAccount().isActive()) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (candidate.isInitialAccessLocked()) {
            throw new UnauthorizedException("Initial access is locked. Ask an account administrator to regenerate access.");
        }
        if (candidate.getCredentialState() == CredentialState.EMAIL_ACTIVATION_PENDING
                || candidate.getCredentialState() == CredentialState.EMAIL_RESET_PENDING) {
            throw new UnauthorizedException("Email activation or password reset must be completed first");
        }
        if (candidate.getCredentialState() == CredentialState.INITIAL_PASSWORD_CHANGE) {
            throw new UnauthorizedException("Temporary access was already used. Ask an account administrator to regenerate access.");
        }
        if (candidate.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), candidate.getPasswordHash())) {
            if (candidate.getCredentialState() == CredentialState.TEMPORARY_PASSWORD) {
                initialAccessAttemptService.recordFailure(candidate.getId());
            }
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (candidate.getCredentialState() != CredentialState.TEMPORARY_PASSWORD) {
            return new ClientAuthenticationResult(candidate, false);
        }

        User locked = userRepository.findByIdForCredentialUpdate(candidate.getId())
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        if (locked.getCredentialState() != CredentialState.TEMPORARY_PASSWORD
                || locked.isInitialAccessLocked()
                || locked.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), locked.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        locked.setPasswordHash(null);
        locked.setCredentialState(CredentialState.INITIAL_PASSWORD_CHANGE);
        locked.setPasswordChangeRequired(true);
        locked.setInitialAccessFailedAttempts(0);
        userRepository.saveAndFlush(locked);
        return new ClientAuthenticationResult(locked, true);
    }

    private User findUser(LoginRequest request) {
        if (hasText(request.accountCode()) || hasText(request.employeeNumber())) {
            if (!hasText(request.accountCode()) || !hasText(request.employeeNumber())) {
                throw new UnauthorizedException(INVALID_CREDENTIALS);
            }
            return memberRepository.findByAccount_SlugAndEmployeeNumber(
                            request.accountCode().trim().toLowerCase(Locale.ROOT),
                            request.employeeNumber().trim())
                    .map(Member::getUser)
                    .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        }
        String identifier = request.identifier().trim();
        if (identifier.contains("@")) {
            return userRepository.findByEmail(EmailIdentity.canonicalize(identifier))
                    .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        }
        return userRepository.findByUsername(identifier.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
