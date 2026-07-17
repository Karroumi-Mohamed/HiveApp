package com.hiveapp.shared.security.policy;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import dev.karroumi.permissionizer.PermissionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRolePolicyTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MemberRoleRepository memberRoleRepository;
    @Mock private MemberPermissionOverrideRepository overrideRepository;

    private UserRolePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new UserRolePolicy(memberRepository, memberRoleRepository, overrideRepository);
    }

    @Test
    void activeApplicableDenyWinsOverGrantAndRole() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = member(accountId, userId);
        Permission permission = permission("platform.company.delete");
        HiveAppPermissionContext context = context(accountId, userId, companyId);

        when(memberRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(member));
        when(overrideRepository.findApplicable(member.getId(), companyId)).thenReturn(List.of(
                exception(member, null, permission, PermissionOverrideDecision.GRANT,
                        Instant.now().plusSeconds(3600)),
                exception(member, company(companyId, member.getAccount()), permission,
                        PermissionOverrideDecision.DENY, null)));

        var decision = policy.evaluate(
                new dev.karroumi.permissionizer.Permission(permission.getCode()), context);

        assertThat(decision).isEqualTo(PermissionPolicy.Decision.DENIED);
        verifyNoInteractions(memberRoleRepository);
    }

    @Test
    void expiredDenyIsIgnoredAndRoleCanGrant() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = member(accountId, userId);
        Permission permission = permission("platform.company.delete");
        HiveAppPermissionContext context = context(accountId, userId, companyId);

        when(memberRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(member));
        when(overrideRepository.findApplicable(member.getId(), companyId)).thenReturn(List.of(
                exception(member, null, permission, PermissionOverrideDecision.DENY,
                        Instant.now().minusSeconds(1))));
        when(memberRoleRepository.existsByMemberIdAndPermissionCode(
                member.getId(), permission.getCode(), companyId)).thenReturn(true);

        var decision = policy.evaluate(
                new dev.karroumi.permissionizer.Permission(permission.getCode()), context);

        assertThat(decision).isEqualTo(PermissionPolicy.Decision.GRANTED);
    }

    @Test
    void inactiveCompanyGrantProvidesNoAuthority() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = member(accountId, userId);
        Permission permission = permission("platform.company.delete");
        Company company = company(companyId, member.getAccount());
        company.setActive(false);
        HiveAppPermissionContext context = context(accountId, userId, companyId);

        when(memberRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(member));
        when(overrideRepository.findApplicable(member.getId(), companyId)).thenReturn(List.of(
                exception(member, company, permission, PermissionOverrideDecision.GRANT,
                        Instant.now().plusSeconds(3600))));

        var decision = policy.evaluate(
                new dev.karroumi.permissionizer.Permission(permission.getCode()), context);

        assertThat(decision).isEqualTo(PermissionPolicy.Decision.ABSTAIN);
    }

    private static HiveAppPermissionContext context(UUID accountId, UUID userId, UUID companyId) {
        return new HiveAppPermissionContext(userId, accountId, accountId, companyId, null, false);
    }

    private static Account account(UUID id) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", id);
        account.setName("Acme");
        account.setSlug("acme");
        return account;
    }

    private static Company company(UUID id, Account account) {
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", id);
        company.setAccount(account);
        company.setName("Company");
        company.setCountry("MA");
        company.setActive(true);
        return company;
    }

    private static Member member(UUID accountId, UUID userId) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", userId);
        Member member = new Member();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        member.setAccount(account(accountId));
        member.setUser(user);
        member.setActive(true);
        return member;
    }

    private static Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setName(code);
        return permission;
    }

    private static MemberPermissionOverride exception(
            Member member,
            Company company,
            Permission permission,
            PermissionOverrideDecision decision,
            Instant expiresAt) {
        MemberPermissionOverride exception = new MemberPermissionOverride();
        exception.setMember(member);
        exception.setScopeCompany(company);
        exception.setPermission(permission);
        exception.setDecision(decision);
        exception.setExpiresAt(expiresAt);
        return exception;
    }
}
