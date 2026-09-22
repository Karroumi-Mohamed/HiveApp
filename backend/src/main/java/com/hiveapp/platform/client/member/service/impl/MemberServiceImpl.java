package com.hiveapp.platform.client.member.service.impl;

import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;
import com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideScope;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.service.MemberService;
import com.hiveapp.platform.client.member.dto.MemberPermissionOverrideDto;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.platform.client.member.dto.InitialRoleAssignmentRequest;
import com.hiveapp.platform.client.member.dto.MemberAccessResult;
import com.hiveapp.platform.client.member.dto.MemberAccessStatusResponse;
import com.hiveapp.platform.client.member.dto.MemberCreationResult;
import com.hiveapp.platform.client.member.dto.MemberDto;
import com.hiveapp.platform.client.member.dto.MemberAuthorizationDto;
import com.hiveapp.platform.client.member.dto.MemberRoleAssignmentDto;
import com.hiveapp.platform.client.member.mapper.MemberMapper;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.company.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.identity.service.IdentityService;
import com.hiveapp.identity.service.NewUserCommand;
import com.hiveapp.identity.domain.EmailIdentity;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.service.MemberCredentialService;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.shared.email.delivery.EmailDeliveryTracker;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.shared.security.DelegationCeilingService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@PermissionNode(key = StaffFeature.KEY, description = "Member Management", guard = PermissionNode.Guard.ON)
public class MemberServiceImpl extends ClientWorkspaceFeatureService implements MemberService {
    private final com.hiveapp.platform.communication.BusinessNotifications notifications;

    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final MemberPermissionOverrideRepository memberOverrideRepository;
    private final IdentityService identityService;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;
    private final QuotaEnforcer quotaEnforcer;
    private final MemberCredentialService memberCredentialService;
    private final PlanEntitlementService planEntitlementService;
    private final DelegationCeilingService delegationCeilingService;
    private final EmailDeliveryTracker emailDeliveryTracker;
    private final MemberMapper memberMapper;

    @Override
    protected FeatureDefinition featureDefinition() {
        return StaffFeature.definition();
    }

    private Member getMember(UUID id) {
        UUID accountId = currentAccountId();
        return memberRepository.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
    }

    @Override
    @PermissionNode(key = "read", description = "List account members")
    public List<MemberDto> getAccountMembers(UUID accountId) {
        requireCurrentAccount(accountId);
        return memberRepository.findWithUserByAccountId(accountId).stream()
                .map(memberMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Create member identity and account membership")
    public MemberCreationResult createMember(UUID accountId, CreateMemberRequest request) {
        requireCurrentAccount(accountId);
        var account = accountRepository.findByIdForQuotaUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        if (!account.isActive()) {
            throw new InvalidStateException("Workspace account is suspended");
        }

        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = EmailIdentity.canonicalize(request.email());
        email = email == null || email.isBlank() ? null : email;
        String employeeNumber = normalizeOptional(request.employeeNumber());
        if (identityService.usernameExists(username)) {
            throw new InvalidStateException("Username is already in use");
        }
        if (email != null && identityService.emailExists(email)) {
            throw new InvalidStateException("Email is already in use");
        }
        if (employeeNumber != null
                && memberRepository.existsByAccountIdAndEmployeeNumber(accountId, employeeNumber)) {
            throw new InvalidStateException("Employee number is already in use in this workspace");
        }

        quotaEnforcer.check(
                StaffFeature.definition(),
                StaffFeature.MEMBERS,
                accountId,
                () -> memberRepository.countByAccountIdAndIsActiveTrue(accountId)
        );

        List<ValidatedRoleAssignment> assignments = validateInitialRoles(
                accountId, request.initialRoles());

        // Identity owns creating the row, its uniqueness rules, and translating their violation.
        User user = identityService.createUser(NewUserCommand.withoutCredentials(
                username,
                email,
                request.firstName().trim(),
                request.lastName().trim(),
                normalizeOptional(request.phone())));
        // MemberCredentialService persists its own credential-state changes.
        var initialAccess = memberCredentialService.initialize(user, account);

        Member member = new Member();
        member.setAccount(account);
        member.setUser(user);
        member.setDisplayName(normalizeDisplayName(request, user));
        member.setEmployeeNumber(employeeNumber);
        member.setActive(true);
        try {
            member = memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            throw new InvalidStateException("Username, email, or employee number is already in use");
        }

        for (ValidatedRoleAssignment assignment : assignments) {
            MemberRole memberRole = new MemberRole();
            memberRole.setMember(member);
            memberRole.setRole(assignment.role());
            memberRole.setEffectScope(assignment.scope());
            memberRole.setScopeCompany(assignment.company());
            memberRoleRepository.save(memberRole);
            if (!assignment.role().isEverAssigned()) {
                assignment.role().setEverAssigned(true);
                roleRepository.save(assignment.role());
            }
        }
        memberRoleRepository.flush();
        roleRepository.flush();
        notifications.memberCreated(member);
        return new MemberCreationResult(memberMapper.toDto(member), initialAccess);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update", description = "Update member profile")
    public MemberDto updateMember(UUID memberId, String displayName) {
        var member = getMember(memberId);
        requireCurrentAccount(member);
        if (displayName != null) {
            member.setDisplayName(displayName);
        }
        return memberMapper.toDto(memberRepository.save(member));
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete", description = "Deactivate member")
    public void deactivateMember(UUID id, String reason) {
        requireLifecycleReason(reason);
        var member = getMember(id);
        requireCurrentAccount(member);
        UUID actorUserId = HiveAppContextHolder.getContext().actorUserId();
        if (member.getUser().getId().equals(actorUserId)) {
            throw new ForbiddenException("Members cannot deactivate themselves");
        }
        if (member.isOwner()) {
            throw new ForbiddenException("Workspace owner cannot be deactivated. Transfer ownership first.");
        }
        if (!member.isActive()) return;
        memberCredentialService.invalidatePendingAccess(member.getUser());
        member.setActive(false);
        memberRepository.saveAndFlush(member);
        notifications.memberChanged(member);
    }

    @Override
    @Transactional
    @PermissionNode(key = "reactivate", description = "Reactivate member")
    public MemberDto reactivateMember(UUID id, String reason) {
        requireLifecycleReason(reason);
        UUID accountId = currentAccountId();
        var account = accountRepository.findByIdForQuotaUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        if (!account.isActive()) {
            throw new InvalidStateException("Workspace account is suspended");
        }
        var member = memberRepository.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
        if (member.isActive()) {
            throw new InvalidStateException("Member is already active");
        }
        quotaEnforcer.check(
                StaffFeature.definition(),
                StaffFeature.MEMBERS,
                accountId,
                () -> memberRepository.countByAccountIdAndIsActiveTrue(accountId)
        );
        member.setActive(true);
        var saved = memberRepository.saveAndFlush(member);
        notifications.memberChanged(saved);
        return memberMapper.toDto(saved);
    }

    private void requireLifecycleReason(String reason) {
        String normalized = normalizeOptional(reason);
        if (normalized == null) {
            throw new InvalidStateException("Member lifecycle changes require a reason");
        }
        if (normalized.length() > 500) {
            throw new InvalidStateException("Member lifecycle reasons cannot exceed 500 characters");
        }
    }

    @Override
    @Transactional
    @PermissionNode(key = "regenerate_access", description = "Regenerate unactivated member access")
    public MemberAccessResult regenerateInitialAccess(UUID memberId) {
        Member member = requireActiveManagedMember(memberId);
        var material = memberCredentialService.regenerate(member.getUser(), member.getAccount());
        return new MemberAccessResult(member.getId(), material);
    }

    @Override
    @Transactional
    @PermissionNode(key = "reset_access", description = "Reset activated member access")
    public MemberAccessResult resetAccess(UUID memberId) {
        Member member = requireActiveManagedMember(memberId);
        var material = memberCredentialService.reset(member.getUser(), member.getAccount());
        return new MemberAccessResult(member.getId(), material);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_access", description = "Read member credential and email delivery status")
    public MemberAccessStatusResponse getAccessStatus(UUID memberId) {
        Member member = getMember(memberId);
        requireCurrentAccount(member);
        User user = member.getUser();
        InitialAccessMethod method = user.getEmail() == null
                ? InitialAccessMethod.TEMPORARY_PASSWORD
                : InitialAccessMethod.EMAIL_LINK;
        var delivery = emailDeliveryTracker.findLatestSummary(
                member.getAccount().getId(), user.getId()).orElse(null);
        return new MemberAccessStatusResponse(
                member.getId(), method, user.getCredentialState(),
                user.getCredentialTokenExpiresAt(), delivery);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_authorization", description = "View member roles and permission overrides")
    public MemberAuthorizationDto getMemberAuthorization(UUID memberId) {
        Member member = getMember(memberId);
        requireCurrentAccount(member);
        List<MemberRoleAssignmentDto> roles = memberRoleRepository
                .findAllForAuthorizationByMemberId(memberId)
                .stream()
                .sorted(Comparator
                        .comparing((MemberRole assignment) -> assignment.getRole().getName())
                        .thenComparing(MemberRole::getId))
                .map(assignment -> new MemberRoleAssignmentDto(
                        assignment.getId(),
                        assignment.getRole().getId(),
                        assignment.getRole().getName(),
                        assignment.getRole().getStatus(),
                        assignment.getEffectScope(),
                        assignment.getScopeCompany() == null
                                ? null : assignment.getScopeCompany().getId(),
                        assignment.getScopeCompany() == null
                                ? null : assignment.getScopeCompany().getName()))
                .toList();
        Instant now = Instant.now();
        List<MemberPermissionOverrideDto> overrides = memberOverrideRepository
                .findAllForAuthorizationByMemberId(memberId)
                .stream()
                .sorted(Comparator
                        .comparing((MemberPermissionOverride override) ->
                                override.getPermission().getCode())
                        .thenComparing(MemberPermissionOverride::getId))
                .map(override -> toOverrideDto(override, now))
                .toList();
        return new MemberAuthorizationDto(memberMapper.toDto(member), roles, overrides);
    }

    @Override
    @Transactional
    @PermissionNode(key = "unlock_access", description = "Unlock member initial access")
    public void unlockInitialAccess(UUID memberId) {
        Member member = requireActiveManagedMember(memberId);
        memberCredentialService.unlock(member.getUser());
    }

    @Override
    @Transactional
    @PermissionNode(key = "assign_role", description = "Assign role to member")
    public void assignRole(UUID memberId, UUID roleId, RoleAssignmentScope scope, UUID companyId) {
        var member = getMember(memberId);
        UUID accountId = member.getAccount().getId();
        var role = roleRepository.findByIdAndAccountIdForUpdate(roleId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));
        validateAssignmentScope(scope, companyId);
        var company = scope == RoleAssignmentScope.COMPANY
                ? companyRepository.findByIdAndAccountId(companyId, accountId)
                        .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId))
                : null;

        requireCurrentAccount(member);
        requireSameAccount(member, role);
        requireNonOwnerTarget(member);
        if (company != null) {
            requireSameAccount(member, company);
            if (!company.isActive()) {
                throw new InvalidStateException("Roles cannot be assigned inside an inactive company");
            }
        }
        if (role.getBoundaryCompany() != null
                && (company == null || !role.getBoundaryCompany().getId().equals(company.getId()))) {
            throw new ForbiddenException("Company-scoped role can only be assigned inside its company");
        }
        if (!role.isActive()) {
            throw new InvalidStateException("Inactive roles cannot be assigned");
        }
        delegationCeilingService.requireActorCanDelegate(
                accountId, companyId,
                java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(StaffFeature.CODE + ".assign_role"),
                        role.getPermissions().stream().map(rp -> rp.getPermission().getCode()))
                        .toList());
        boolean duplicate = scope == RoleAssignmentScope.ACCOUNT
                ? memberRoleRepository.existsByMemberIdAndRoleIdAndScopeCompanyIsNull(memberId, roleId)
                : memberRoleRepository.existsByMemberIdAndRoleIdAndScopeCompanyId(memberId, roleId, companyId);
        if (duplicate) {
            throw new InvalidStateException("Role is already assigned to this member in the requested scope");
        }

        MemberRole mr = new MemberRole();
        mr.setMember(member);
        mr.setRole(role);
        mr.setEffectScope(scope);
        mr.setScopeCompany(company);
        try {
            memberRoleRepository.saveAndFlush(mr);
        } catch (DataIntegrityViolationException ex) {
            throw new InvalidStateException("Role is already assigned to this member in the requested scope");
        }
        if (!role.isEverAssigned()) {
            role.setEverAssigned(true);
            roleRepository.saveAndFlush(role);
        }
    }

    @Override
    @Transactional
    @PermissionNode(key = "remove_role", description = "Remove role from member")
    public void removeRole(UUID memberId, UUID roleId, RoleAssignmentScope scope, UUID companyId) {
        var member = getMember(memberId);
        var role = roleRepository.findByIdAndAccountIdForUpdate(roleId, member.getAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));
        requireCurrentAccount(member);
        requireSameAccount(member, role);
        requireNonOwnerTarget(member);
        validateAssignmentScope(scope, companyId);
        delegationCeilingService.requireActorCanDelegate(
                member.getAccount().getId(), companyId,
                List.of(StaffFeature.CODE + ".remove_role"));
        int removed = scope == RoleAssignmentScope.ACCOUNT
                ? memberRoleRepository.deleteAccountAssignment(memberId, roleId)
                : memberRoleRepository.deleteCompanyAssignment(memberId, roleId, companyId);
        if (removed == 0) {
            throw new ResourceNotFoundException("MemberRole", "scope", scope);
        }
    }

    @Override
    @Transactional
    @PermissionNode(key = "grant_permission", description = "Grant or deny direct permission override")
    public void grantPermissionOverride(
            UUID memberId, String permissionCode, PermissionOverrideScope scope,
            UUID companyId, PermissionOverrideDecision decision,
            String reason, Instant expiresAt) {
        var member = getMember(memberId);
        UUID accountId = member.getAccount().getId();
        var company = resolveExceptionCompany(accountId, scope, companyId);
        var permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        permissionGrantValidator.requireClientRoleGrantable(permission);
        requireCurrentAccount(member);
        requireNonOwnerTarget(member);
        requireNonSelfTarget(member);
        if (company != null && !company.isActive()) {
            throw new InvalidStateException("Permission overrides cannot be granted inside an inactive company");
        }
        String normalizedReason = normalizeOptional(reason);
        if (normalizedReason == null) {
            throw new InvalidStateException("Permission exceptions require a reason");
        }
        if (normalizedReason.length() > 500) {
            throw new InvalidStateException("Permission exception reasons cannot exceed 500 characters");
        }
        if (decision == null) {
            throw new InvalidStateException("Permission exception decision is required");
        }
        if (decision == PermissionOverrideDecision.GRANT && expiresAt == null) {
            throw new InvalidStateException("Permission GRANT exceptions require an expiry");
        }
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new InvalidStateException("Permission exception expiry must be in the future");
        }
        delegationCeilingService.requireActorCanDelegate(
                accountId, companyId,
                List.of(StaffFeature.CODE + ".grant_permission", permissionCode));

        var override = findException(memberId, companyId, permission.getId())
                .orElseGet(MemberPermissionOverride::new);

        override.setMember(member);
        override.setScope(scope);
        override.setScopeCompany(company);
        override.setPermission(permission);
        override.setDecision(decision);
        override.setReason(normalizedReason);
        override.setExpiresAt(expiresAt);
        if (override.getCreatedBy() == null) {
            override.setCreatedBy(currentActorMember(accountId));
        }
        try {
            memberOverrideRepository.saveAndFlush(override);
        } catch (DataIntegrityViolationException ex) {
            throw new InvalidStateException(
                    "A permission exception already exists in the requested scope");
        }
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke_permission", description = "Remove direct permission override")
    public void revokePermissionOverride(
            UUID memberId, String permissionCode, PermissionOverrideScope scope, UUID companyId) {
        var member = getMember(memberId);
        UUID accountId = member.getAccount().getId();
        resolveExceptionCompany(accountId, scope, companyId);
        var permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        requireCurrentAccount(member);
        requireNonOwnerTarget(member);
        requireNonSelfTarget(member);
        var exception = findException(memberId, companyId, permission.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MemberPermissionOverride", "scope", scope));
        List<String> requiredPermissions = new java.util.ArrayList<>();
        requiredPermissions.add(StaffFeature.CODE + ".revoke_permission");
        if (exception.getDecision() == PermissionOverrideDecision.DENY
                && planEntitlementService.isPermissionEntitled(accountId, permissionCode)) {
            requiredPermissions.add(permissionCode);
        }
        delegationCeilingService.requireActorCanDelegate(
                accountId, companyId, requiredPermissions);
        memberOverrideRepository.delete(exception);
    }

    @Override
    @PermissionNode(key = "read_overrides", description = "View member permission overrides")
    @Transactional(readOnly = true)
    public List<MemberPermissionOverrideDto> getMemberOverrides(
            UUID memberId, PermissionOverrideScope scope, UUID companyId) {
        var member = getMember(memberId);
        resolveExceptionCompany(member.getAccount().getId(), scope, companyId);
        requireCurrentAccount(member);
        List<MemberPermissionOverride> exceptions = scope == PermissionOverrideScope.ACCOUNT
                ? memberOverrideRepository.findAllByMemberIdAndScopeCompanyIsNull(memberId)
                : memberOverrideRepository.findAllByMemberIdAndScopeCompanyId(memberId, companyId);
        Instant now = Instant.now();
        return exceptions
                .stream()
                .map(override -> toOverrideDto(override, now))
                .toList();
    }

    private MemberPermissionOverrideDto toOverrideDto(
            MemberPermissionOverride override,
            Instant now
    ) {
        return new MemberPermissionOverrideDto(
                override.getId(),
                override.getMember().getId(),
                override.getScope(),
                override.getScopeCompany() == null ? null : override.getScopeCompany().getId(),
                override.getPermission().getCode(),
                override.getDecision(),
                override.getReason(),
                override.getCreatedBy().getId(),
                override.getExpiresAt(),
                override.isEffectiveAt(now),
                override.getCreatedAt(),
                override.getUpdatedAt());
    }

    private com.hiveapp.platform.client.company.domain.entity.Company resolveExceptionCompany(
            UUID accountId, PermissionOverrideScope scope, UUID companyId) {
        if (scope == null) {
            throw new InvalidStateException("Permission exception scope is required");
        }
        if (scope == PermissionOverrideScope.ACCOUNT) {
            if (companyId != null) {
                throw new InvalidStateException("Account permission exceptions cannot declare a company");
            }
            return null;
        }
        if (companyId == null) {
            throw new InvalidStateException("Company permission exceptions require a company");
        }
        return companyRepository.findByIdAndAccountId(companyId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
    }

    private java.util.Optional<MemberPermissionOverride> findException(
            UUID memberId, UUID companyId, UUID permissionId) {
        return companyId == null
                ? memberOverrideRepository.findByMemberIdAndScopeCompanyIsNullAndPermissionId(
                        memberId, permissionId)
                : memberOverrideRepository.findByMemberIdAndScopeCompanyIdAndPermissionId(
                        memberId, companyId, permissionId);
    }

    private Member currentActorMember(UUID accountId) {
        UUID actorUserId = HiveAppContextHolder.getContext().actorUserId();
        return memberRepository.findByAccountIdAndUserId(accountId, actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "userId", actorUserId));
    }

    private void requireCurrentAccount(Member member) {
        requireCurrentAccount(member.getAccount().getId());
    }

    private List<ValidatedRoleAssignment> validateInitialRoles(
            UUID accountId,
            List<InitialRoleAssignmentRequest> requestedAssignments
    ) {
        if (requestedAssignments.isEmpty()) {
            return List.of();
        }
        Set<String> assignmentKeys = new HashSet<>();
        java.util.ArrayList<ValidatedRoleAssignment> result = new java.util.ArrayList<>();
        for (InitialRoleAssignmentRequest requested : requestedAssignments) {
            validateAssignmentScope(requested.scope(), requested.companyId());
            String key = requested.roleId() + ":" + requested.scope() + ":" + requested.companyId();
            if (!assignmentKeys.add(key)) {
                throw new InvalidStateException("Duplicate initial role assignment");
            }
            var role = roleRepository.findByIdAndAccountIdForUpdate(requested.roleId(), accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "id", requested.roleId()));
            if (!role.isActive()) {
                throw new InvalidStateException("Inactive roles cannot be assigned");
            }
            var company = requested.scope() == RoleAssignmentScope.ACCOUNT ? null
                    : companyRepository.findByIdAndAccountId(requested.companyId(), accountId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Company", "id", requested.companyId()));
            if (company != null && !company.isActive()) {
                throw new InvalidStateException("Roles cannot be assigned inside an inactive company");
            }
            if (role.getBoundaryCompany() != null
                    && (company == null || !role.getBoundaryCompany().getId().equals(company.getId()))) {
                throw new ForbiddenException("Company-scoped role can only be assigned inside its company");
            }
            role.getPermissions().forEach(rolePermission -> {
                String code = rolePermission.getPermission().getCode();
                if (!planEntitlementService.isPermissionEntitled(accountId, code)) {
                    throw new InvalidStateException("Role contains a permission unavailable in the current plan: " + code);
                }
            });
            delegationCeilingService.requireActorCanDelegate(
                    accountId, requested.companyId(),
                    java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(StaffFeature.CODE + ".assign_role"),
                            role.getPermissions().stream().map(rp -> rp.getPermission().getCode()))
                            .toList());
            result.add(new ValidatedRoleAssignment(role, requested.scope(), company));
        }
        return List.copyOf(result);
    }

    private Member requireActiveManagedMember(UUID memberId) {
        Member member = getMember(memberId);
        requireCurrentAccount(member);
        if (member.isOwner()) {
            throw new ForbiddenException("Owner credentials are not managed through member administration");
        }
        if (!member.isActive()) {
            throw new InvalidStateException("Member is inactive");
        }
        return member;
    }

    private void requireNonOwnerTarget(Member member) {
        if (member.isOwner()) {
            throw new ForbiddenException("Workspace owner access cannot be changed through role or override management");
        }
    }

    private void requireNonSelfTarget(Member member) {
        UUID actorUserId = HiveAppContextHolder.getContext().actorUserId();
        if (member.getUser().getId().equals(actorUserId)) {
            throw new ForbiddenException("Members cannot create, edit, or revoke their own permission exceptions");
        }
    }

    private void validateAssignmentScope(RoleAssignmentScope scope, UUID companyId) {
        if (scope == null) {
            throw new InvalidStateException("Role assignment scope is required");
        }
        if (scope == RoleAssignmentScope.ACCOUNT && companyId != null) {
            throw new InvalidStateException("Account role assignments cannot declare a company");
        }
        if (scope == RoleAssignmentScope.COMPANY && companyId == null) {
            throw new InvalidStateException("Company role assignments require a company");
        }
    }

    private String normalizeDisplayName(CreateMemberRequest request, User user) {
        String displayName = normalizeOptional(request.displayName());
        return displayName == null ? user.getFullName() : displayName;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidatedRoleAssignment(
            com.hiveapp.platform.client.role.domain.entity.Role role,
            RoleAssignmentScope scope,
            com.hiveapp.platform.client.company.domain.entity.Company company
    ) {
    }

    private void requireCurrentAccount(UUID accountId) {
        UUID currentAccountId = currentAccountId();
        if (!accountId.equals(currentAccountId)) {
            throw new ForbiddenException("Member does not belong to your account");
        }
    }

    private UUID currentAccountId() {
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.currentAccountId() == null) {
            throw new ForbiddenException("An active workspace context is required");
        }
        return context.currentAccountId();
    }

    private void requireSameAccount(Member member, com.hiveapp.platform.client.role.domain.entity.Role role) {
        if (!role.getAccount().getId().equals(member.getAccount().getId())) {
            throw new ForbiddenException("Role does not belong to the member account");
        }
    }

    private void requireSameAccount(Member member, com.hiveapp.platform.client.company.domain.entity.Company company) {
        if (!company.getAccount().getId().equals(member.getAccount().getId())) {
            throw new ForbiddenException("Company does not belong to the member account");
        }
    }
}
