package com.hiveapp.platform.client.member.service.impl;

import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.member.service.MemberService;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;

    @Override
    public Member getMember(UUID id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
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
        return memberRepository.save(member);
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
}
