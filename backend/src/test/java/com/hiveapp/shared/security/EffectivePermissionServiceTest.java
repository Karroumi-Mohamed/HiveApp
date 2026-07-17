package com.hiveapp.shared.security;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
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
        when(memberOverrideRepository.findAllByMemberId(memberId)).thenReturn(List.of());

        var result = service.getEffectivePermissions(userId, accountId);

        assertThat(result.permissions()).isEmpty();
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
}
