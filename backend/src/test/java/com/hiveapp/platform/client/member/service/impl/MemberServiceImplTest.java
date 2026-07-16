package com.hiveapp.platform.client.member.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.identity.service.CredentialAccessMaterial;
import com.hiveapp.identity.service.MemberCredentialService;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.platform.client.member.dto.InitialRoleAssignmentRequest;
import com.hiveapp.platform.client.member.dto.MemberPermissionDto;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.shared.security.EffectivePermissionService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MemberRoleRepository memberRoleRepository;
    @Mock private MemberPermissionOverrideRepository memberOverrideRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionGrantValidator permissionGrantValidator;
    @Mock private QuotaEnforcer quotaEnforcer;
    @Mock private MemberCredentialService memberCredentialService;
    @Mock private PlanEntitlementService planEntitlementService;
    @Mock private EffectivePermissionService effectivePermissionService;

    @InjectMocks
    private MemberServiceImpl memberService;

    @AfterEach
    void clearContext() {
        HiveAppContextHolder.clearContext();
    }

    @Test
    void createMemberChecksQuotaAndCreatesIdentityAndMembershipAtomically() {
        UUID accountId = UUID.randomUUID();
        setContext(accountId);

        Account account = account(accountId);
        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account));
        when(memberRepository.countByAccountIdAndIsActiveTrue(accountId)).thenReturn(2L);
        when(memberCredentialService.initialize(any(User.class), eq(account)))
                .thenReturn(new CredentialAccessMaterial(
                        InitialAccessMethod.TEMPORARY_PASSWORD,
                        CredentialState.TEMPORARY_PASSWORD,
                        "temporary-secret", null));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = memberService.createMember(accountId, createRequest("nora"));

        assertThat(result.member().getAccount()).isSameAs(account);
        assertThat(result.member().getUser().getUsername()).isEqualTo("nora");
        assertThat(result.member().getDisplayName()).isEqualTo("Nora Stone");
        assertThat(result.initialAccess().temporaryPassword()).isEqualTo("temporary-secret");

        ArgumentCaptor<LongSupplier> usageCaptor = ArgumentCaptor.forClass(LongSupplier.class);
        verify(quotaEnforcer).check(
                any(FeatureDefinition.class),
                eq(WorkspaceFeature.MEMBERS),
                eq(accountId),
                usageCaptor.capture()
        );
        assertThat(usageCaptor.getValue().getAsLong()).isEqualTo(2L);
        verify(memberRepository).saveAndFlush(any(Member.class));
    }

    @Test
    void createMemberRejectsDifferentAccountBeforeQuotaCheck() {
        UUID currentAccountId = UUID.randomUUID();
        UUID requestedAccountId = UUID.randomUUID();
        setContext(currentAccountId);

        assertThatThrownBy(() -> memberService.createMember(requestedAccountId, createRequest("nora")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Member does not belong to your account");

        verifyNoInteractions(quotaEnforcer, accountRepository, userRepository);
    }

    @Test
    void createMemberRejectsDuplicateUsernameBeforeQuotaCheck() {
        UUID accountId = UUID.randomUUID();
        setContext(accountId);
        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account(accountId)));
        when(userRepository.existsByUsername("nora")).thenReturn(true);

        assertThatThrownBy(() -> memberService.createMember(accountId, createRequest("nora")))
                .isInstanceOf(InvalidStateException.class)
                .hasMessage("Username is already in use");

        verifyNoInteractions(quotaEnforcer);
    }

    @Test
    void createMemberRejectsDuplicateEmployeeNumberBeforeQuotaCheck() {
        UUID accountId = UUID.randomUUID();
        setContext(accountId);
        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account(accountId)));
        when(memberRepository.existsByAccountIdAndEmployeeNumber(accountId, "EMP-1")).thenReturn(true);

        assertThatThrownBy(() -> memberService.createMember(
                        accountId,
                        new CreateMemberRequest(
                                "nora", null, "Nora", "Stone", null, null, "EMP-1", List.of())))
                .isInstanceOf(InvalidStateException.class)
                .hasMessage("Employee number is already in use in this workspace");

        verifyNoInteractions(quotaEnforcer);
    }

    @Test
    void createMemberPersistsValidatedInitialRoleInsideTheSameAdmission() {
        UUID accountId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        setContext(accountId, actorUserId);
        Account account = account(accountId);
        Role role = role(roleId, account, true);
        addPermission(role, "platform.company.read_single");

        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account));
        when(effectivePermissionService.getEffectivePermissions(actorUserId, accountId))
                .thenReturn(new MemberPermissionDto(
                        UUID.randomUUID(), false,
                        Set.of("platform.staff.assign_role", "platform.company.read_single")));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));
        when(planEntitlementService.isPermissionEntitled(accountId, "platform.company.read_single"))
                .thenReturn(true);
        when(memberCredentialService.initialize(any(User.class), eq(account)))
                .thenReturn(new CredentialAccessMaterial(
                        InitialAccessMethod.TEMPORARY_PASSWORD,
                        CredentialState.TEMPORARY_PASSWORD,
                        "temporary-secret", null));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        memberService.createMember(accountId, new CreateMemberRequest(
                "nora", null, "Nora", "Stone", null, null, null,
                List.of(new InitialRoleAssignmentRequest(roleId, null))));

        ArgumentCaptor<MemberRole> assignment = ArgumentCaptor.forClass(MemberRole.class);
        verify(memberRoleRepository).save(assignment.capture());
        verify(memberRoleRepository).flush();
        assertThat(assignment.getValue().getRole()).isSameAs(role);
        assertThat(assignment.getValue().getCompany()).isNull();
    }

    @Test
    void createMemberRejectsInitialRoleAboveActorDelegationCeilingBeforeIdentityInsert() {
        UUID accountId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        setContext(accountId, actorUserId);
        Account account = account(accountId);
        Role role = role(roleId, account, true);
        addPermission(role, "platform.company.delete");

        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account));
        when(effectivePermissionService.getEffectivePermissions(actorUserId, accountId))
                .thenReturn(new MemberPermissionDto(
                        UUID.randomUUID(), false, Set.of("platform.staff.assign_role")));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));
        when(planEntitlementService.isPermissionEntitled(accountId, "platform.company.delete"))
                .thenReturn(true);

        assertThatThrownBy(() -> memberService.createMember(accountId, new CreateMemberRequest(
                "nora", null, "Nora", "Stone", null, null, null,
                List.of(new InitialRoleAssignmentRequest(roleId, null)))))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("acting member does not hold");

        verify(userRepository, org.mockito.Mockito.never()).saveAndFlush(any(User.class));
        verify(memberRepository, org.mockito.Mockito.never()).saveAndFlush(any(Member.class));
    }

    @Test
    void deactivateMemberRejectsWorkspaceOwnerEvenWhenActorIsDifferent() {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        setContext(accountId);
        Member owner = member(memberId, account(accountId), user(UUID.randomUUID()), true);
        when(memberRepository.findByIdAndAccountId(memberId, accountId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> memberService.deactivateMember(memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Transfer ownership first");

        verifyNoInteractions(memberCredentialService);
    }

    @Test
    void deactivateMemberSuspendsMembershipAndRevokesClientRefreshSessions() {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setContext(accountId);
        Member member = member(memberId, account(accountId), user(userId), false);
        when(memberRepository.findByIdAndAccountId(memberId, accountId)).thenReturn(Optional.of(member));
        when(memberRepository.saveAndFlush(member)).thenReturn(member);

        memberService.deactivateMember(memberId);

        assertThat(member.isActive()).isFalse();
        verify(memberCredentialService).invalidatePendingAccess(member.getUser());
        verify(userRepository).saveAndFlush(member.getUser());
    }

    @Test
    void assignRoleRejectsInactiveRole() {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        setContext(accountId);
        Account account = account(accountId);
        Member member = member(memberId, account, user(UUID.randomUUID()), false);
        Role role = role(roleId, account, false);
        when(memberRepository.findByIdAndAccountId(memberId, accountId)).thenReturn(Optional.of(member));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> memberService.assignRole(memberId, roleId, null))
                .isInstanceOf(InvalidStateException.class)
                .hasMessage("Inactive roles cannot be assigned");
    }

    @Test
    void assignRoleRejectsDuplicateAccountScopedAssignment() {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        setContext(accountId);
        Account account = account(accountId);
        Member member = member(memberId, account, user(UUID.randomUUID()), false);
        Role role = role(roleId, account, true);
        when(memberRepository.findByIdAndAccountId(memberId, accountId)).thenReturn(Optional.of(member));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));
        when(memberRoleRepository.existsByMemberIdAndRoleIdAndCompanyIsNull(memberId, roleId)).thenReturn(true);

        assertThatThrownBy(() -> memberService.assignRole(memberId, roleId, null))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("already assigned");

        verify(memberRoleRepository, org.mockito.Mockito.never()).saveAndFlush(any(MemberRole.class));
    }

    @Test
    void assignRoleRejectsNewCompanyScopedGrantWhileCompanyIsInactive() {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        setContext(accountId);
        Account account = account(accountId);
        Member member = member(memberId, account, user(UUID.randomUUID()), false);
        Role role = role(roleId, account, true);
        Company company = company(companyId, account, false);
        when(memberRepository.findByIdAndAccountId(memberId, accountId)).thenReturn(Optional.of(member));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));
        when(companyRepository.findByIdAndAccountId(companyId, accountId)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> memberService.assignRole(memberId, roleId, companyId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("inactive company");

        verify(memberRoleRepository, org.mockito.Mockito.never()).saveAndFlush(any(MemberRole.class));
    }

    private static void setContext(UUID accountId) {
        setContext(accountId, UUID.randomUUID());
    }

    private static void setContext(UUID accountId, UUID actorUserId) {
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                actorUserId,
                accountId,
                accountId,
                null,
                null,
                false
        ));
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
        user.setUsername("nora");
        user.setFirstName("Nora");
        user.setLastName("Stone");
        user.setPasswordHash("hash");
        return user;
    }

    private static CreateMemberRequest createRequest(String username) {
        return new CreateMemberRequest(
                username, null, "Nora", "Stone", null, null, null, List.of());
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

    private static Role role(UUID id, Account account, boolean active) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", id);
        role.setAccount(account);
        role.setName("Manager");
        role.setActive(active);
        return role;
    }

    private static Company company(UUID id, Account account, boolean active) {
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", id);
        company.setAccount(account);
        company.setName("Acme Company");
        company.setCountry("US");
        company.setActive(active);
        return company;
    }

    private static void addPermission(Role role, String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        RolePermission rolePermission = new RolePermission();
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        role.getPermissions().add(rolePermission);
    }

}
