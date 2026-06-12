package com.hiveapp.platform.client.invitation.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.platform.client.invitation.domain.constant.InvitationStatus;
import com.hiveapp.platform.client.invitation.domain.entity.Invitation;
import com.hiveapp.platform.client.invitation.domain.repository.InvitationRepository;
import com.hiveapp.platform.client.invitation.dto.AcceptInvitationRequest;
import com.hiveapp.platform.client.invitation.dto.InvitationInfoDto;
import com.hiveapp.platform.client.invitation.service.PublicInvitationService;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicInvitationServiceImpl implements PublicInvitationService {

    private final InvitationRepository invitationRepository;
    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(readOnly = true)
    public InvitationInfoDto validate(String token) {
        var invitation = findValidInvitation(token);
        boolean requiresRegistration = !userRepository.existsByEmail(invitation.getEmail());
        return new InvitationInfoDto(
                invitation.getEmail(),
                invitation.getAccount().getName(),
                invitation.getInvitedBy().getUser().getFullName(),
                invitation.getExpiresAt(),
                requiresRegistration
        );
    }

    @Override
    @Transactional
    public AuthResponse accept(AcceptInvitationRequest request) {
        var invitation = findValidInvitation(request.token());
        User user = userRepository.findByEmail(invitation.getEmail()).orElse(null);
        if (user == null) {
            validateRegistrationFields(request);
            user = User.builder()
                    .email(invitation.getEmail())
                    .passwordHash(passwordEncoder.encode(request.password()))
                    .firstName(request.firstName().trim())
                    .lastName(request.lastName().trim())
                    .build();
            user = userRepository.save(user);
            log.info("New user created via invitation: email={}", user.getEmail());
        }

        UUID accountId = invitation.getAccount().getId();
        if (memberRepository.existsByAccountIdAndUserId(accountId, user.getId())) {
            log.warn("User={} is already a member of account={}, marking invitation accepted anyway", user.getId(), accountId);
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitationRepository.save(invitation);
            return issueClientToken(user);
        }

        Member member = new Member();
        member.setAccount(invitation.getAccount());
        member.setUser(user);
        member.setDisplayName(user.getFirstName());
        member.setOwner(false);
        member.setActive(true);
        member = memberRepository.save(member);

        if (invitation.getRole() != null) {
            MemberRole memberRole = new MemberRole();
            memberRole.setMember(member);
            memberRole.setRole(invitation.getRole());
            memberRole.setCompany(invitation.getCompany());
            memberRoleRepository.save(memberRole);
            log.info("Role={} pre-assigned to new member={}", invitation.getRole().getId(), member.getId());
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
        log.info("Invitation accepted: email={} joined account={}", user.getEmail(), accountId);
        return issueClientToken(user);
    }

    private Invitation findValidInvitation(String token) {
        var invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "token", "provided value"));

        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new InvalidStateException("This invitation has been revoked.");
        }
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new InvalidStateException("This invitation has already been accepted.");
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            if (invitation.getStatus() == InvitationStatus.PENDING) {
                invitation.setStatus(InvitationStatus.EXPIRED);
                invitationRepository.save(invitation);
            }
            throw new InvalidStateException("This invitation has expired. Please ask to be re-invited.");
        }
        return invitation;
    }

    private void validateRegistrationFields(AcceptInvitationRequest request) {
        if (request.firstName() == null || request.firstName().isBlank()) {
            throw new InvalidRequestException("firstName is required for new users.");
        }
        if (request.lastName() == null || request.lastName().isBlank()) {
            throw new InvalidRequestException("lastName is required for new users.");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new InvalidRequestException("password must be at least 8 characters for new users.");
        }
    }

    private AuthResponse issueClientToken(User user) {
        var claims = Map.<String, Object>of("tokenType", "CLIENT");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), claims);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        return AuthResponse.of(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }
}
