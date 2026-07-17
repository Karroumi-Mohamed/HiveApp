package com.hiveapp.platform.security;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.client.role.domain.constant.RoleStatus;
import com.hiveapp.platform.client.role.domain.repository.RolePermissionRepository;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipGrantIntegrityIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private AccountRepository accountRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private MemberRoleRepository memberRoleRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private PermissionRepository permissionRepository;

    @Test
    void databaseRejectsDuplicateAccountScopedMemberRole() throws Exception {
        Account account = registeredAccount();
        Member owner = memberRepository.findByAccountIdAndUserId(account.getId(), account.getOwner().getId())
                .orElseThrow();
        Role role = role(account);

        memberRoleRepository.saveAndFlush(memberRole(owner, role));

        assertThatThrownBy(() -> memberRoleRepository.saveAndFlush(memberRole(owner, role)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateRolePermission() throws Exception {
        Account account = registeredAccount();
        Role role = role(account);
        var permission = permissionRepository.findByCode("platform.staff.read").orElseThrow();

        rolePermissionRepository.saveAndFlush(rolePermission(role, permission));

        assertThatThrownBy(() -> rolePermissionRepository.saveAndFlush(rolePermission(role, permission)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Account registeredAccount() throws Exception {
        String token = registerClientAndGetToken();
        UUID memberId = currentMemberId(token);
        return accountRepository.findById(memberRepository.findById(memberId).orElseThrow().getAccount().getId())
                .orElseThrow();
    }

    private Role role(Account account) {
        Role role = new Role();
        role.setAccount(account);
        role.setName("Integrity " + UUID.randomUUID());
        role.setStatus(RoleStatus.ACTIVE);
        return roleRepository.saveAndFlush(role);
    }

    private MemberRole memberRole(Member member, Role role) {
        MemberRole assignment = new MemberRole();
        assignment.setMember(member);
        assignment.setRole(role);
        return assignment;
    }

    private RolePermission rolePermission(
            Role role,
            com.hiveapp.platform.registry.domain.entity.Permission permission
    ) {
        RolePermission grant = new RolePermission();
        grant.setRole(role);
        grant.setPermission(permission);
        return grant;
    }
}
