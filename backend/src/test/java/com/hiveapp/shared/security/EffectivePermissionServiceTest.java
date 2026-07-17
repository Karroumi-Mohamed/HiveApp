package com.hiveapp.shared.security;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.client.role.domain.constant.RoleStatus;
import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectivePermissionServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MemberRoleRepository memberRoleRepository;
    @Mock private MemberPermissionOverrideRepository memberOverrideRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionGrantValidator permissionGrantValidator;
    @Mock private PlanEntitlementService planEntitlementService;

    private EffectivePermissionService service;

    @BeforeEach
    void setUp() {
        service = new EffectivePermissionService(
                memberRepository,
                memberRoleRepository,
                memberOverrideRepository,
                permissionRepository,
                permissionGrantValidator,
                planEntitlementService
        );
    }

    @Test
    void ownerEffectivePermissionsOnlyIncludeEntitledClientPermissions() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Member owner = member(memberId, account(accountId), user(userId), true);
        Permission included = permission("platform.company.read_single");
        Permission excluded = permission("platform.b2b.request");

        when(memberRepository.findByAccountIdAndUserId(accountId, userId)).thenReturn(Optional.of(owner));
        when(permissionRepository.findAll()).thenReturn(List.of(included, excluded));
        when(permissionGrantValidator.isClientRoleGrantable(included)).thenReturn(true);
        when(permissionGrantValidator.isClientRoleGrantable(excluded)).thenReturn(true);
        when(planEntitlementService.isPermissionEntitled(accountId, included.getCode())).thenReturn(true);
        when(planEntitlementService.isPermissionEntitled(accountId, excluded.getCode())).thenReturn(false);

        var result = service.getEffectivePermissions(userId, accountId);

        assertThat(result.isOwner()).isTrue();
        assertThat(result.permissions()).containsExactly(included.getCode());
    }

    @Test
    void inactiveAndArchivedRolesGrantNoRuntimePermissions() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, account(accountId), user(userId), false);
        Permission permission = permission("platform.company.read_single");
        Role inactive = role(accountId, RoleStatus.INACTIVE, permission);
        Role archived = role(accountId, RoleStatus.ARCHIVED, permission);

        when(memberRepository.findByAccountIdAndUserId(accountId, userId)).thenReturn(Optional.of(member));
        when(memberRoleRepository.findAllByMemberId(memberId))
                .thenReturn(Stream.of(inactive, archived).map(role -> assignment(member, role)).toList());
        when(memberOverrideRepository.findApplicable(memberId, null)).thenReturn(List.of());

        var result = service.getEffectivePermissions(userId, accountId);

        assertThat(result.permissions()).isEmpty();
    }

    @Test
    void companyEvaluationIncludesAccountAndMatchingCompanyAssignmentsOnly() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID firstCompanyId = UUID.randomUUID();
        UUID secondCompanyId = UUID.randomUUID();
        Account account = account(accountId);
        Member member = member(memberId, account, user(userId), false);
        Permission accountPermission = permission("platform.staff.read");
        Permission firstPermission = permission("platform.company.read_single");
        Permission secondPermission = permission("platform.company.delete");
        Company first = company(firstCompanyId, account);
        Company second = company(secondCompanyId, account);

        when(memberRepository.findByAccountIdAndUserId(accountId, userId)).thenReturn(Optional.of(member));
        when(memberRoleRepository.findAllByMemberId(memberId)).thenReturn(List.of(
                assignment(member, role(accountId, RoleStatus.ACTIVE, accountPermission)),
                assignment(member, role(accountId, RoleStatus.ACTIVE, firstPermission), first),
                assignment(member, role(accountId, RoleStatus.ACTIVE, secondPermission), second)));
        when(memberOverrideRepository.findApplicable(memberId, firstCompanyId)).thenReturn(List.of());
        when(memberOverrideRepository.findApplicable(memberId, null)).thenReturn(List.of());
        when(planEntitlementService.isPermissionEntitled(accountId, accountPermission.getCode())).thenReturn(true);
        when(planEntitlementService.isPermissionEntitled(accountId, firstPermission.getCode())).thenReturn(true);

        var companyAccess = service.getEffectivePermissions(userId, accountId, firstCompanyId);
        var accountAccess = service.getEffectivePermissions(userId, accountId, null);

        assertThat(companyAccess.permissions())
                .containsExactlyInAnyOrder(accountPermission.getCode(), firstPermission.getCode());
        assertThat(accountAccess.permissions()).containsExactly(accountPermission.getCode());
    }

    @Test
    void anyActiveApplicableDenyRemovesRoleAndGrantAuthority() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Account account = account(accountId);
        Member member = member(memberId, account, user(userId), false);
        Permission permission = permission("platform.company.delete");
        Company company = company(companyId, account);

        when(memberRepository.findByAccountIdAndUserId(accountId, userId)).thenReturn(Optional.of(member));
        when(memberRoleRepository.findAllByMemberId(memberId)).thenReturn(List.of(
                assignment(member, role(accountId, RoleStatus.ACTIVE, permission))));
        when(memberOverrideRepository.findApplicable(memberId, companyId)).thenReturn(List.of(
                permissionException(member, null, permission, PermissionOverrideDecision.GRANT,
                        Instant.now().plusSeconds(3600)),
                permissionException(member, company, permission, PermissionOverrideDecision.DENY, null)));
        var result = service.getEffectivePermissions(userId, accountId, companyId);

        assertThat(result.permissions()).doesNotContain(permission.getCode());
    }

    @Test
    void expiredGrantDoesNotProvideRuntimeAuthority() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Account account = account(accountId);
        Member member = member(memberId, account, user(userId), false);
        Permission permission = permission("platform.company.delete");

        when(memberRepository.findByAccountIdAndUserId(accountId, userId)).thenReturn(Optional.of(member));
        when(memberRoleRepository.findAllByMemberId(memberId)).thenReturn(List.of());
        when(memberOverrideRepository.findApplicable(memberId, null)).thenReturn(List.of(
                permissionException(member, null, permission, PermissionOverrideDecision.GRANT,
                        Instant.now().minusSeconds(1))));

        var result = service.getEffectivePermissions(userId, accountId);

        assertThat(result.permissions()).doesNotContain(permission.getCode());
    }

    private static Account account(UUID id) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", id);
        account.setName("Acme");
        account.setSlug("acme");
        return account;
    }

    private static User user(UUID id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("nora@example.com");
        user.setFirstName("Nora");
        user.setLastName("Stone");
        user.setPasswordHash("hash");
        return user;
    }

    private static Company company(UUID id, Account account) {
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", id);
        company.setAccount(account);
        company.setName("Company " + id);
        company.setCountry("MA");
        company.setActive(true);
        return company;
    }

    private static Member member(UUID id, Account account, User user, boolean owner) {
        Member member = new Member();
        ReflectionTestUtils.setField(member, "id", id);
        member.setAccount(account);
        member.setUser(user);
        member.setOwner(owner);
        member.setActive(true);
        return member;
    }

    private static Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setName(code);
        return permission;
    }

    private static MemberPermissionOverride permissionException(
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

    private static Role role(UUID accountId, RoleStatus status, Permission permission) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        role.setAccount(account(accountId));
        role.setName(status + " role");
        role.setStatus(status);
        RolePermission grant = new RolePermission();
        grant.setRole(role);
        grant.setPermission(permission);
        role.getPermissions().add(grant);
        return role;
    }

    private static MemberRole assignment(Member member, Role role) {
        MemberRole assignment = new MemberRole();
        assignment.setMember(member);
        assignment.setRole(role);
        return assignment;
    }

    private static MemberRole assignment(Member member, Role role, Company company) {
        MemberRole assignment = assignment(member, role);
        assignment.setEffectScope(RoleAssignmentScope.COMPANY);
        assignment.setScopeCompany(company);
        return assignment;
    }
}
