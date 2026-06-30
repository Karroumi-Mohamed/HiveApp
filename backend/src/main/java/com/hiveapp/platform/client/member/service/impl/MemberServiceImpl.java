package com.hiveapp.platform.client.member.service.impl;

import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.service.MemberService;
import com.hiveapp.platform.client.member.dto.MemberPermissionOverrideDto;
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
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    @Override
    protected FeatureDefinition featureDefinition() {
        return StaffFeature.definition();
    }

    @Override
    public Member getMember(UUID id) {
        return memberRepository.findById(id)
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
    @PermissionNode(key = "add", description = "Add member to account")
    public Member addMember(UUID accountId, UUID userId, String displayName) {
        requireCurrentAccount(accountId);
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        quotaEnforcer.check(
                WorkspaceFeature.definition(),
                WorkspaceFeature.MEMBERS,
                accountId,
                () -> (long) memberRepository.findAllByAccountId(accountId).size()
        );

        Member member = new Member();
        member.setAccount(account);
        member.setUser(user);
        member.setDisplayName(displayName);
        member.setActive(true);
        return memberRepository.save(member);
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
        member.setActive(false);
        memberRepository.save(member);
    }

    @Override
    @Transactional
    @PermissionNode(key = "assign_role", description = "Assign role to member")
    public void assignRole(UUID memberId, UUID roleId, UUID companyId) {
        var member = getMember(memberId);
        var role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));
        var company = companyId != null
                ? companyRepository.findById(companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId))
                : null;

        requireCurrentAccount(member);
        requireSameAccount(member, role);
        if (company != null) {
            requireSameAccount(member, company);
        }
        if (role.getCompany() != null && (company == null || !role.getCompany().getId().equals(company.getId()))) {
            throw new ForbiddenException("Company-scoped role can only be assigned inside its company");
        }

        MemberRole mr = new MemberRole();
        mr.setMember(member);
        mr.setRole(role);
        mr.setCompany(company);
        memberRoleRepository.save(mr);
    }

    @Override
    @Transactional
    @PermissionNode(key = "remove_role", description = "Remove role from member")
    public void removeRole(UUID memberId, UUID roleId) {
        var member = getMember(memberId);
        var role = roleRepository.findById(roleId)
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
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        var permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        permissionGrantValidator.requireClientRoleGrantable(permission);
        requireCurrentAccount(member);
        requireSameAccount(member, company);

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
        var company = companyRepository.findById(companyId)
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
        var company = companyRepository.findById(companyId)
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

    private void requireCurrentAccount(UUID accountId) {
        UUID currentAccountId = HiveAppContextHolder.getContext().currentAccountId();
        if (!accountId.equals(currentAccountId)) {
            throw new ForbiddenException("Member does not belong to your account");
        }
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
