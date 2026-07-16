package com.hiveapp.platform.client.member.service.impl;

import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.service.MemberService;
import com.hiveapp.platform.client.member.dto.MemberPermissionOverrideDto;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.platform.client.member.dto.InitialRoleAssignmentRequest;
import com.hiveapp.platform.client.member.dto.MemberAccessResponse;
import com.hiveapp.platform.client.member.dto.MemberCreationResult;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.domain.EmailIdentity;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.service.MemberCredentialService;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.shared.security.EffectivePermissionService;
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

@Service
@RequiredArgsConstructor
@PermissionNode(key = StaffFeature.KEY, description = "Member Management", guard = PermissionNode.Guard.ON)
public class MemberServiceImpl extends ClientWorkspaceFeatureService implements MemberService {

    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final MemberPermissionOverrideRepository memberOverrideRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;
    private final QuotaEnforcer quotaEnforcer;
    private final MemberCredentialService memberCredentialService;
    private final PlanEntitlementService planEntitlementService;
    private final EffectivePermissionService effectivePermissionService;

    @Override
    protected FeatureDefinition featureDefinition() {
        return StaffFeature.definition();
    }

    @Override
    public Member getMember(UUID id) {
        UUID accountId = currentAccountId();
        return memberRepository.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
    }

    @Override
    @PermissionNode(key = "read", description = "List account members")
    public List<Member> getAccountMembers(UUID accountId) {
        requireCurrentAccount(accountId);
        return memberRepository.findAllByAccountId(accountId);
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
        if (userRepository.existsByUsername(username)) {
            throw new InvalidStateException("Username is already in use");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new InvalidStateException("Email is already in use");
        }
        if (employeeNumber != null
                && memberRepository.existsByAccountIdAndEmployeeNumber(accountId, employeeNumber)) {
            throw new InvalidStateException("Employee number is already in use in this workspace");
        }

        quotaEnforcer.check(
                WorkspaceFeature.definition(),
                WorkspaceFeature.MEMBERS,
                accountId,
                () -> memberRepository.countByAccountIdAndIsActiveTrue(accountId)
        );

        List<ValidatedRoleAssignment> assignments = validateInitialRoles(
                accountId, request.initialRoles());

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(normalizeOptional(request.phone()));
        user.setActive(true);
        user.setEmailVerified(false);
        var initialAccess = memberCredentialService.initialize(user, account);

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new InvalidStateException("Username, email, or employee number is already in use");
        }

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
            memberRole.setCompany(assignment.company());
            memberRoleRepository.save(memberRole);
        }
        memberRoleRepository.flush();
        return new MemberCreationResult(member, initialAccess);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update", description = "Update member profile")
    public Member updateMember(UUID memberId, String displayName) {
        var member = getMember(memberId);
        requireCurrentAccount(member);
        if (displayName != null) {
            member.setDisplayName(displayName);
        }
        return memberRepository.save(member);
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete", description = "Deactivate member")
    public void deactivateMember(UUID id) {
        var member = getMember(id);
        requireCurrentAccount(member);
        UUID actorUserId = HiveAppContextHolder.getContext().actorUserId();
        if (member.getUser().getId().equals(actorUserId)) {
            throw new ForbiddenException("Members cannot deactivate themselves");
        }
        if (member.isOwner()) {
            throw new ForbiddenException("Workspace owner cannot be deactivated. Transfer ownership first.");
        }
        memberCredentialService.invalidatePendingAccess(member.getUser());
        userRepository.saveAndFlush(member.getUser());
        member.setActive(false);
        memberRepository.saveAndFlush(member);
    }

    @Override
    @Transactional
    @PermissionNode(key = "regenerate_access", description = "Regenerate unactivated member access")
    public MemberAccessResponse regenerateInitialAccess(UUID memberId) {
        Member member = requireActiveManagedMember(memberId);
        var material = memberCredentialService.regenerate(member.getUser(), member.getAccount());
        userRepository.saveAndFlush(member.getUser());
        return accessResponse(member, material);
    }

    @Override
    @Transactional
    @PermissionNode(key = "reset_access", description = "Reset activated member access")
    public MemberAccessResponse resetAccess(UUID memberId) {
        Member member = requireActiveManagedMember(memberId);
        var material = memberCredentialService.reset(member.getUser(), member.getAccount());
        userRepository.saveAndFlush(member.getUser());
        return accessResponse(member, material);
    }

    @Override
    @Transactional
    @PermissionNode(key = "unlock_access", description = "Unlock member initial access")
    public void unlockInitialAccess(UUID memberId) {
        Member member = requireActiveManagedMember(memberId);
        memberCredentialService.unlock(member.getUser());
        userRepository.saveAndFlush(member.getUser());
    }

    @Override
    @Transactional
    @PermissionNode(key = "assign_role", description = "Assign role to member")
    public void assignRole(UUID memberId, UUID roleId, UUID companyId) {
        var member = getMember(memberId);
        UUID accountId = member.getAccount().getId();
        var role = roleRepository.findByIdAndAccountId(roleId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));
        var company = companyId != null
                ? companyRepository.findByIdAndAccountId(companyId, accountId)
                        .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId))
                : null;

        requireCurrentAccount(member);
        requireSameAccount(member, role);
        if (company != null) {
            requireSameAccount(member, company);
            if (!company.isActive()) {
                throw new InvalidStateException("Roles cannot be assigned inside an inactive company");
            }
        }
        if (role.getCompany() != null && (company == null || !role.getCompany().getId().equals(company.getId()))) {
            throw new ForbiddenException("Company-scoped role can only be assigned inside its company");
        }
        if (!role.isActive()) {
            throw new InvalidStateException("Inactive roles cannot be assigned");
        }
        boolean duplicate = company == null
                ? memberRoleRepository.existsByMemberIdAndRoleIdAndCompanyIsNull(memberId, roleId)
                : memberRoleRepository.existsByMemberIdAndRoleIdAndCompanyId(memberId, roleId, companyId);
        if (duplicate) {
            throw new InvalidStateException("Role is already assigned to this member in the requested scope");
        }

        MemberRole mr = new MemberRole();
        mr.setMember(member);
        mr.setRole(role);
        mr.setCompany(company);
        try {
            memberRoleRepository.saveAndFlush(mr);
        } catch (DataIntegrityViolationException ex) {
            throw new InvalidStateException("Role is already assigned to this member in the requested scope");
        }
    }

    @Override
    @Transactional
    @PermissionNode(key = "remove_role", description = "Remove role from member")
    public void removeRole(UUID memberId, UUID roleId) {
        var member = getMember(memberId);
        var role = roleRepository.findByIdAndAccountId(roleId, member.getAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));
        requireCurrentAccount(member);
        requireSameAccount(member, role);
        memberRoleRepository.deleteByMemberIdAndRoleId(memberId, roleId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "grant_permission", description = "Grant or deny direct permission override")
    public void grantPermissionOverride(UUID memberId, String permissionCode, UUID companyId, boolean decision) {
        var member = getMember(memberId);
        var company = companyRepository.findByIdAndAccountId(companyId, member.getAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        var permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        permissionGrantValidator.requireClientRoleGrantable(permission);
        requireCurrentAccount(member);
        requireSameAccount(member, company);
        if (!company.isActive()) {
            throw new InvalidStateException("Permission overrides cannot be granted inside an inactive company");
        }

        var override = memberOverrideRepository
                .findByMemberIdAndCompanyIdAndPermissionId(memberId, companyId, permission.getId())
                .orElseGet(MemberPermissionOverride::new);

        override.setMember(member);
        override.setCompany(company);
        override.setPermission(permission);
        override.setDecision(decision);
        memberOverrideRepository.save(override);
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke_permission", description = "Remove direct permission override")
    public void revokePermissionOverride(UUID memberId, String permissionCode, UUID companyId) {
        var member = getMember(memberId);
        var company = companyRepository.findByIdAndAccountId(companyId, member.getAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        var permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        requireCurrentAccount(member);
        requireSameAccount(member, company);

        memberOverrideRepository
                .findByMemberIdAndCompanyIdAndPermissionId(memberId, companyId, permission.getId())
                .ifPresent(memberOverrideRepository::delete);
    }

    @Override
    @PermissionNode(key = "read_overrides", description = "View member permission overrides")
    public List<MemberPermissionOverrideDto> getMemberOverrides(UUID memberId, UUID companyId) {
        var member = getMember(memberId);
        var company = companyRepository.findByIdAndAccountId(companyId, member.getAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        requireCurrentAccount(member);
        requireSameAccount(member, company);
        return memberOverrideRepository.findAllByMemberIdAndCompanyId(memberId, companyId)
                .stream()
                .map(override -> new MemberPermissionOverrideDto(
                        override.getMember().getId(),
                        override.getCompany().getId(),
                        override.getPermission().getCode(),
                        override.isDecision()))
                .toList();
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
        UUID actorUserId = HiveAppContextHolder.getContext().actorUserId();
        var actorAccess = effectivePermissionService.getEffectivePermissions(actorUserId, accountId);
        if (!actorAccess.permissions().contains(StaffFeature.CODE + ".assign_role")) {
            throw new ForbiddenException("Initial role assignment requires member role-assignment permission");
        }

        Set<String> assignmentKeys = new HashSet<>();
        java.util.ArrayList<ValidatedRoleAssignment> result = new java.util.ArrayList<>();
        for (InitialRoleAssignmentRequest requested : requestedAssignments) {
            String key = requested.roleId() + ":" + (requested.companyId() == null ? "account" : requested.companyId());
            if (!assignmentKeys.add(key)) {
                throw new InvalidStateException("Duplicate initial role assignment");
            }
            var role = roleRepository.findByIdAndAccountId(requested.roleId(), accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "id", requested.roleId()));
            if (!role.isActive()) {
                throw new InvalidStateException("Inactive roles cannot be assigned");
            }
            var company = requested.companyId() == null ? null
                    : companyRepository.findByIdAndAccountId(requested.companyId(), accountId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Company", "id", requested.companyId()));
            if (company != null && !company.isActive()) {
                throw new InvalidStateException("Roles cannot be assigned inside an inactive company");
            }
            if (role.getCompany() != null
                    && (company == null || !role.getCompany().getId().equals(company.getId()))) {
                throw new ForbiddenException("Company-scoped role can only be assigned inside its company");
            }
            role.getPermissions().forEach(rolePermission -> {
                String code = rolePermission.getPermission().getCode();
                if (!planEntitlementService.isPermissionEntitled(accountId, code)) {
                    throw new InvalidStateException("Role contains a permission unavailable in the current plan: " + code);
                }
                if (!actorAccess.permissions().contains(code)) {
                    throw new ForbiddenException("Cannot delegate a permission the acting member does not hold: " + code);
                }
            });
            result.add(new ValidatedRoleAssignment(role, company));
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

    private MemberAccessResponse accessResponse(
            Member member,
            com.hiveapp.identity.service.CredentialAccessMaterial material
    ) {
        return new MemberAccessResponse(
                member.getId(), material.method(), material.state(),
                material.temporaryPassword(), material.linkExpiresAt());
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
            com.hiveapp.platform.client.account.domain.entity.Company company
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

    private void requireSameAccount(Member member, com.hiveapp.platform.client.account.domain.entity.Company company) {
        if (!company.getAccount().getId().equals(member.getAccount().getId())) {
            throw new ForbiddenException("Company does not belong to the member account");
        }
    }
}
