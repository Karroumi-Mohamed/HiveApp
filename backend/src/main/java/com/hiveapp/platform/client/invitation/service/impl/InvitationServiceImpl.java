package com.hiveapp.platform.client.invitation.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.invitation.domain.constant.InvitationStatus;
import com.hiveapp.platform.client.invitation.domain.entity.Invitation;
import com.hiveapp.platform.client.invitation.domain.repository.InvitationRepository;
import com.hiveapp.platform.client.invitation.dto.InvitationDto;
import com.hiveapp.platform.client.invitation.dto.SendInvitationRequest;
import com.hiveapp.platform.client.invitation.service.InvitationService;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.InvitationsFeature;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.shared.email.EmailService;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@PermissionNode(key = InvitationsFeature.KEY, description = "Workspace Invitation Management")
public class InvitationServiceImpl extends ClientWorkspaceFeatureService implements InvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final InvitationRepository invitationRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${hiveapp.invitation.expiry-days:7}")
    private int expiryDays;

    @Value("${hiveapp.invitation.base-url:http://localhost:3000}")
    private String baseUrl;

    @Override
    protected FeatureDefinition featureDefinition() {
        return InvitationsFeature.definition();
    }

    // ── Authenticated operations ──────────────────────────────────────────────

    @Override
    @Transactional
    @PermissionNode(key = "send", description = "Send workspace invitation")
    public InvitationDto send(UUID accountId, UUID invitedByUserId, SendInvitationRequest request) {
        requireCurrentAccount(accountId);
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        var inviter = memberRepository.findByAccountIdAndUserId(accountId, invitedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "userId", invitedByUserId));

        // Reject if target is already a member
        if (memberRepository.existsByAccountIdAndUserId(accountId,
                userRepository.findByEmail(request.email())
                        .map(User::getId).orElse(UUID.randomUUID()))) {
            throw new InvalidStateException("This user is already a member of the workspace.");
        }

        // Reject duplicate pending invite to same email
        if (invitationRepository.existsByAccountIdAndEmailAndStatus(
                accountId, request.email().toLowerCase(), InvitationStatus.PENDING)) {
            throw new DuplicateResourceException("Pending invitation", "email", request.email().toLowerCase());
        }

        var company = request.companyId() != null
                ? companyRepository.findById(request.companyId())
                        .orElseThrow(() -> new ResourceNotFoundException("Company", "id", request.companyId()))
                : null;

        if (company != null && !company.getAccount().getId().equals(accountId)) {
            throw new ForbiddenException("Company does not belong to this workspace.");
        }

        // Validate optional role belongs to this account and can be assigned at the requested company scope.
        var role = request.roleId() != null
                ? roleRepository.findById(request.roleId())
                        .orElseThrow(() -> new ResourceNotFoundException("Role", "id", request.roleId()))
                : null;
        if (request.roleId() != null) {
            if (!role.getAccount().getId().equals(accountId)) {
                throw new ForbiddenException("Role does not belong to this workspace.");
            }
            if (role.getCompany() != null && (company == null || !role.getCompany().getId().equals(company.getId()))) {
                throw new ForbiddenException("Company-scoped role can only be invited inside its company.");
            }
        }

        Invitation invitation = new Invitation();
        invitation.setAccount(account);
        invitation.setEmail(request.email().toLowerCase());
        invitation.setToken(generateToken());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS));
        invitation.setInvitedBy(inviter);

        invitation.setRole(role);
        invitation.setCompany(company);

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
        requireCurrentAccount(accountId);
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
        requireCurrentAccount(accountId);
        var invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "id", invitationId));

        if (!invitation.getAccount().getId().equals(accountId)) {
            throw new ForbiddenException("Invitation does not belong to this workspace.");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvalidStateException("Only PENDING invitations can be revoked. Current status: " + invitation.getStatus());
        }

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);
        log.info("Invitation revoked id={} email={}", invitationId, invitation.getEmail());
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

    private void requireCurrentAccount(UUID accountId) {
        var context = HiveAppContextHolder.getContext();
        if (context != null && !accountId.equals(context.currentAccountId())) {
            throw new ForbiddenException("Invitation does not belong to this workspace.");
        }
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
