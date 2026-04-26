package com.hiveapp.platform.client.invitation.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.invitation.domain.constant.InvitationStatus;
import com.hiveapp.platform.client.invitation.domain.entity.Invitation;
import com.hiveapp.platform.client.invitation.domain.repository.InvitationRepository;
import com.hiveapp.platform.client.invitation.dto.AcceptInvitationRequest;
import com.hiveapp.platform.client.invitation.dto.InvitationDto;
import com.hiveapp.platform.client.invitation.dto.InvitationInfoDto;
import com.hiveapp.platform.client.invitation.dto.SendInvitationRequest;
import com.hiveapp.platform.client.invitation.service.InvitationService;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.shared.email.EmailService;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.UnauthorizedException;
import com.hiveapp.shared.security.JwtTokenProvider;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@PermissionNode(key = "invitations", description = "Workspace Invitation Management")
public class InvitationServiceImpl implements InvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final InvitationRepository invitationRepository;
    private final AccountRepository accountRepository;
    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    @Value("${hiveapp.invitation.expiry-days:7}")
    private int expiryDays;

    @Value("${hiveapp.invitation.base-url:http://localhost:3000}")
    private String baseUrl;

    // ── Authenticated operations ──────────────────────────────────────────────

    @Override
    @Transactional
    @PermissionNode(key = "send", description = "Send workspace invitation")
    public InvitationDto send(UUID accountId, UUID invitedByUserId, SendInvitationRequest request) {
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        var inviter = memberRepository.findByAccountIdAndUserId(accountId, invitedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "userId", invitedByUserId));

        // Reject if target is already a member
        if (memberRepository.existsByAccountIdAndUserId(accountId,
                userRepository.findByEmail(request.email())
                        .map(User::getId).orElse(UUID.randomUUID()))) {
            throw new BusinessException("This user is already a member of the workspace.");
        }

        // Reject duplicate pending invite to same email
        if (invitationRepository.existsByAccountIdAndEmailAndStatus(
                accountId, request.email().toLowerCase(), InvitationStatus.PENDING)) {
            throw new BusinessException(
                    "A pending invitation for " + request.email() + " already exists. Revoke it first to resend.");
        }

        // Validate optional role belongs to this account
        if (request.roleId() != null) {
            var role = roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "id", request.roleId()));
            if (!role.getAccount().getId().equals(accountId)) {
                throw new UnauthorizedException("Role does not belong to this workspace.");
            }
        }

        Invitation invitation = new Invitation();
        invitation.setAccount(account);
        invitation.setEmail(request.email().toLowerCase());
        invitation.setToken(generateToken());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS));
        invitation.setInvitedBy(inviter);

        if (request.roleId() != null) {
            invitation.setRole(roleRepository.findById(request.roleId()).orElse(null));
        }

        invitation = invitationRepository.save(invitation);

        String acceptUrl = baseUrl + "/invite/accept?token=" + invitation.getToken();
        emailService.sendInvitation(
                invitation.getEmail(),
                inviter.getDisplayName() != null ? inviter.getDisplayName() : inviter.getUser().getFullName(),
                account.getName(),
                acceptUrl
        );

        log.info("Invitation sent to={} for account={} by member={}", invitation.getEmail(), accountId, inviter.getId());
        return toDto(invitation);
    }

    @Override
    @PermissionNode(key = "list", description = "List pending workspace invitations")
    public List<InvitationDto> listPending(UUID accountId) {
        expireStale(accountId);
        return invitationRepository.findAllByAccountIdAndStatus(accountId, InvitationStatus.PENDING)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke", description = "Revoke a pending invitation")
    public void revoke(UUID invitationId, UUID accountId) {
        var invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "id", invitationId));

        if (!invitation.getAccount().getId().equals(accountId)) {
            throw new UnauthorizedException("Invitation does not belong to this workspace.");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessException("Only PENDING invitations can be revoked. Current status: " + invitation.getStatus());
        }

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);
        log.info("Invitation revoked id={} email={}", invitationId, invitation.getEmail());
    }

    // ── Public operations (no auth) ───────────────────────────────────────────

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

        // Get or create user
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

        // Idempotent — if already a member, just mark accepted and issue token
        if (memberRepository.existsByAccountIdAndUserId(accountId, user.getId())) {
            log.warn("User={} is already a member of account={}, marking invitation accepted anyway", user.getId(), accountId);
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitationRepository.save(invitation);
            return issueClientToken(user);
        }

        // Create member
        Member member = new Member();
        member.setAccount(invitation.getAccount());
        member.setUser(user);
        member.setDisplayName(user.getFirstName());
        member.setOwner(false);
        member.setActive(true);
        member = memberRepository.save(member);

        // Pre-assign role if invitation specifies one
        if (invitation.getRole() != null) {
            MemberRole mr = new MemberRole();
            mr.setMember(member);
            mr.setRole(invitation.getRole());
            mr.setCompany(invitation.getCompany());
            memberRoleRepository.save(mr);
            log.info("Role={} pre-assigned to new member={}", invitation.getRole().getId(), member.getId());
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        log.info("Invitation accepted: email={} joined account={}", user.getEmail(), accountId);
        return issueClientToken(user);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Invitation findValidInvitation(String token) {
        var invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "token", "provided value"));

        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new BusinessException("This invitation has been revoked.");
        }
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new BusinessException("This invitation has already been accepted.");
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            if (invitation.getStatus() == InvitationStatus.PENDING) {
                invitation.setStatus(InvitationStatus.EXPIRED);
                invitationRepository.save(invitation);
            }
            throw new BusinessException("This invitation has expired. Please ask to be re-invited.");
        }

        return invitation;
    }

    private void validateRegistrationFields(AcceptInvitationRequest request) {
        if (request.firstName() == null || request.firstName().isBlank()) {
            throw new BusinessException("firstName is required for new users.");
        }
        if (request.lastName() == null || request.lastName().isBlank()) {
            throw new BusinessException("lastName is required for new users.");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new BusinessException("password must be at least 8 characters for new users.");
        }
    }

    /** Expire any stale PENDING invitations for this account before listing. */
    private void expireStale(UUID accountId) {
        invitationRepository
                .findAllByStatusAndExpiresAtBefore(InvitationStatus.PENDING, Instant.now())
                .stream()
                .filter(i -> i.getAccount().getId().equals(accountId))
                .forEach(i -> {
                    i.setStatus(InvitationStatus.EXPIRED);
                    invitationRepository.save(i);
                });
    }

    private static String generateToken() {
        byte[] bytes = new byte[32]; // 256 bits
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes); // 64-char hex string
    }

    private AuthResponse issueClientToken(User user) {
        var claims = Map.<String, Object>of("tokenType", "CLIENT");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), claims);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        long expiresIn = jwtTokenProvider.getAccessTokenExpiration();
        return AuthResponse.of(accessToken, refreshToken, expiresIn);
    }

    private InvitationDto toDto(Invitation i) {
        return new InvitationDto(
                i.getId(),
                i.getEmail(),
                i.getStatus(),
                i.getExpiresAt(),
                i.getCreatedAt(),
                i.getInvitedBy().getUser().getFullName(),
                i.getRole() != null ? i.getRole().getId() : null,
                i.getRole() != null ? i.getRole().getName() : null,
                i.getCompany() != null ? i.getCompany().getId() : null,
                i.getCompany() != null ? i.getCompany().getName() : null
        );
    }
}
