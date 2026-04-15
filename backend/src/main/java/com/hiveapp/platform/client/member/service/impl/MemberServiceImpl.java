package com.hiveapp.platform.client.member.service.impl;

import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.service.MemberService;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final MemberPermissionOverrideRepository memberOverrideRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final PermissionRepository permissionRepository;

    @Override
    public Member getMember(UUID id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
    }

    @Override
    public List<Member> getAccountMembers(UUID accountId) {
        return memberRepository.findAllByAccountId(accountId);
    }

    @Override
    @Transactional
    public Member addMember(UUID accountId, UUID userId, String displayName) {
        var account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
            
        Member member = new Member();
        member.setAccount(account);
        member.setUser(user);
        member.setDisplayName(displayName);
        member.setActive(true);
        return memberRepository.save(member);
    }

    @Override
    @Transactional
    public void deactivateMember(UUID id) {
        var member = getMember(id);
        member.setActive(false);
        memberRepository.save(member);
    }

    @Override
    @Transactional
    public void assignRole(UUID memberId, UUID roleId, UUID companyId) {
        var member = getMember(memberId);
        var role = roleRepository.findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));
        var company = companyId != null ? companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId)) : null;
            
        MemberRole mr = new MemberRole();
        mr.setMember(member);
        mr.setRole(role);
        mr.setCompany(company);
        memberRoleRepository.save(mr);
    }

    @Override
    @Transactional
    public void grantPermissionOverride(UUID memberId, String permissionCode, UUID companyId, boolean decision) {
        var member = getMember(memberId);
        var company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        var permission = permissionRepository.findByCode(permissionCode)
            .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        
        var overrideOpt = memberOverrideRepository.findByMemberIdAndCompanyIdAndPermissionId(memberId, companyId, permission.getId());
        MemberPermissionOverride override = overrideOpt.orElseGet(MemberPermissionOverride::new);
        override.setMember(member);
        override.setCompany(company);
        override.setPermission(permission);
        override.setDecision(decision);
        
        memberOverrideRepository.save(override);
    }

    @Override
    @Transactional
    public void revokePermissionOverride(UUID memberId, String permissionCode, UUID companyId) {
        var permission = permissionRepository.findByCode(permissionCode)
            .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        
        memberOverrideRepository.findByMemberIdAndCompanyIdAndPermissionId(memberId, companyId, permission.getId())
            .ifPresent(memberOverrideRepository::delete);
    }

    @Override
    public List<MemberPermissionOverride> getMemberOverrides(UUID memberId, UUID companyId) {
        return memberOverrideRepository.findAllByMemberIdAndCompanyId(memberId, companyId);
    }
}
