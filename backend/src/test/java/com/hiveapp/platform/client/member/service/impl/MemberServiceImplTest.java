package com.hiveapp.platform.client.member.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.service.IdentityService;
import com.hiveapp.identity.service.NewUserCommand;
import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.identity.service.CredentialAccessMaterial;
import com.hiveapp.identity.service.MemberCredentialService;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.company.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.company.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.platform.client.member.dto.InitialRoleAssignmentRequest;
import com.hiveapp.platform.client.member.dto.MemberDto;
import com.hiveapp.platform.client.member.mapper.MemberMapper;
import com.hiveapp.platform.client.member.mapper.MemberMapperImpl;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.client.role.domain.constant.RoleStatus;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.shared.security.DelegationCeilingService;
import com.hiveapp.shared.email.delivery.EmailDeliveryTracker;
import com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideScope;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.time.Instant;

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
    @Mock private IdentityService identityService;
    @Mock private AccountRepository accountRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionGrantValidator permissionGrantValidator;
    @Mock private QuotaEnforcer quotaEnforcer;
    @Mock private MemberCredentialService memberCredentialService;
    @Mock private PlanEntitlementService planEntitlementService;
    @Mock private DelegationCeilingService delegationCeilingService;
    @Mock private EmailDeliveryTracker emailDeliveryTracker;
    // Real MapStruct implementation so these assertions also cover the projection the
    // service now owns instead of the controller.
    @Spy private MemberMapper memberMapper = new MemberMapperImpl();

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
                        "temporary-secret", null, null));
        when(identityService.createUser(any(NewUserCommand.class)))
                .thenAnswer(invocation -> userFrom(invocation.getArgument(0)));
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = memberService.createMember(accountId, createRequest("nora"));

        assertThat(result.member().username()).isEqualTo("nora");
        assertThat(result.member().displayName()).isEqualTo("Nora Stone");
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

        verifyNoInteractions(quotaEnforcer, accountRepository, identityService);
    }

    @Test
    void createMemberRejectsDuplicateUsernameBeforeQuotaCheck() {
        UUID accountId = UUID.randomUUID();
        setContext(accountId);
        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account(accountId)));
        when(identityService.usernameExists("nora")).thenReturn(true);

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
        when(roleRepository.findByIdAndAccountIdForUpdate(roleId, accountId)).thenReturn(Optional.of(role));
        when(planEntitlementService.isPermissionEntitled(accountId, "platform.company.read_single"))
                .thenReturn(true);
        when(memberCredentialService.initialize(any(User.class), eq(account)))
                .thenReturn(new CredentialAccessMaterial(
                        InitialAccessMethod.TEMPORARY_PASSWORD,
                        CredentialState.TEMPORARY_PASSWORD,
                        "temporary-secret", null, null));
        when(identityService.createUser(any(NewUserCommand.class)))
                .thenAnswer(invocation -> userFrom(invocation.getArgument(0)));
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        memberService.createMember(accountId, new CreateMemberRequest(
                "nora", null, "Nora", "Stone", null, null, null,
                List.of(new InitialRoleAssignmentRequest(roleId, null))));

        ArgumentCaptor<MemberRole> assignment = ArgumentCaptor.forClass(MemberRole.class);
        verify(memberRoleRepository).save(assignment.capture());
        verify(memberRoleRepository).flush();
        assertThat(assignment.getValue().getRole()).isSameAs(role);
        assertThat(assignment.getValue().getScopeCompany()).isNull();
        assertThat(assignment.getValue().getEffectScope()).isEqualTo(RoleAssignmentScope.ACCOUNT);
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
        when(roleRepository.findByIdAndAccountIdForUpdate(roleId, accountId)).thenReturn(Optional.of(role));
        when(planEntitlementService.isPermissionEntitled(accountId, "platform.company.delete"))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new ForbiddenException(
                        "Cannot delegate a permission the acting member does not hold"))
                .when(delegationCeilingService)
                .requireActorCanDelegate(accountId, null,
                        List.of("platform.staff.assign_role", "platform.company.delete"));

        assertThatThrownBy(() -> memberService.createMember(accountId, new CreateMemberRequest(
                "nora", null, "Nora", "Stone", null, null, null,
                List.of(new InitialRoleAssignmentRequest(roleId, null)))))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("acting member does not hold");

        verify(identityService, org.mockito.Mockito.never()).createUser(any(NewUserCommand.class));
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
        when(roleRepository.findByIdAndAccountIdForUpdate(roleId, accountId)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> memberService.assignRole(memberId, roleId, RoleAssignmentScope.ACCOUNT, null))
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
        when(roleRepository.findByIdAndAccountIdForUpdate(roleId, accountId)).thenReturn(Optional.of(role));
        when(memberRoleRepository.existsByMemberIdAndRoleIdAndScopeCompanyIsNull(memberId, roleId)).thenReturn(true);

        assertThatThrownBy(() -> memberService.assignRole(memberId, roleId, RoleAssignmentScope.ACCOUNT, null))
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
        when(roleRepository.findByIdAndAccountIdForUpdate(roleId, accountId)).thenReturn(Optional.of(role));
        when(companyRepository.findByIdAndAccountId(companyId, accountId)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> memberService.assignRole(
                memberId, roleId, RoleAssignmentScope.COMPANY, companyId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("inactive company");

        verify(memberRoleRepository, org.mockito.Mockito.never()).saveAndFlush(any(MemberRole.class));
    }

    @Test
    void removeRoleDeletesOnlyTheRequestedAssignmentScope() {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        setContext(accountId);
        Account account = account(accountId);
        Member member = member(memberId, account, user(UUID.randomUUID()), false);
        Role role = role(roleId, account, true);
        when(memberRepository.findByIdAndAccountId(memberId, accountId)).thenReturn(Optional.of(member));
        when(roleRepository.findByIdAndAccountIdForUpdate(roleId, accountId)).thenReturn(Optional.of(role));
        when(memberRoleRepository.deleteAccountAssignment(memberId, roleId)).thenReturn(1);

        memberService.removeRole(memberId, roleId, RoleAssignmentScope.ACCOUNT, null);

        verify(memberRoleRepository).deleteAccountAssignment(memberId, roleId);
        verify(memberRoleRepository, org.mockito.Mockito.never())
                .deleteCompanyAssignment(any(), any(), any());
    }

    @Test
    void roleAndOverrideManagementCannotTargetTheWorkspaceOwner() {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        setContext(accountId);
        Account account = account(accountId);
        Member owner = member(memberId, account, user(UUID.randomUUID()), true);
        Role role = role(roleId, account, true);
        Company company = company(companyId, account, true);
        Permission permission = new Permission();
        permission.setCode("platform.company.read_single");
        when(memberRepository.findByIdAndAccountId(memberId, accountId)).thenReturn(Optional.of(owner));
        when(roleRepository.findByIdAndAccountIdForUpdate(roleId, accountId)).thenReturn(Optional.of(role));
        when(companyRepository.findByIdAndAccountId(companyId, accountId)).thenReturn(Optional.of(company));
        when(permissionRepository.findByCode(permission.getCode())).thenReturn(Optional.of(permission));

        assertThatThrownBy(() -> memberService.assignRole(
                memberId, roleId, RoleAssignmentScope.ACCOUNT, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("owner access");
        assertThatThrownBy(() -> memberService.grantPermissionOverride(
                memberId, permission.getCode(), PermissionOverrideScope.COMPANY, companyId,
                PermissionOverrideDecision.GRANT, "Temporary access", Instant.now().plusSeconds(3600)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("owner access");
    }

    @Test
    void memberCannotCreateOwnPermissionException() {
        UUID accountId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        setContext(accountId, actorUserId);
        Member actor = member(memberId, account(accountId), user(actorUserId), false);
        Permission permission = permission(UUID.randomUUID(), "platform.company.delete");
        when(memberRepository.findByIdAndAccountId(memberId, accountId)).thenReturn(Optional.of(actor));
        when(permissionRepository.findByCode(permission.getCode())).thenReturn(Optional.of(permission));

        assertThatThrownBy(() -> memberService.grantPermissionOverride(
                memberId, permission.getCode(), PermissionOverrideScope.ACCOUNT, null,
                PermissionOverrideDecision.GRANT, "Self escalation", Instant.now().plusSeconds(3600)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("own permission exceptions");

        verifyNoInteractions(delegationCeilingService);
    }

    @Test
    void grantPermissionExceptionRequiresFutureExpiry() {
        UUID accountId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        setContext(accountId, actorUserId);
        Member target = member(memberId, account(accountId), user(UUID.randomUUID()), false);
        Permission permission = permission(UUID.randomUUID(), "platform.company.delete");
        when(memberRepository.findByIdAndAccountId(memberId, accountId)).thenReturn(Optional.of(target));
        when(permissionRepository.findByCode(permission.getCode())).thenReturn(Optional.of(permission));

        assertThatThrownBy(() -> memberService.grantPermissionOverride(
                memberId, permission.getCode(), PermissionOverrideScope.ACCOUNT, null,
                PermissionOverrideDecision.GRANT, "Temporary access", null))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("require an expiry");
        assertThatThrownBy(() -> memberService.grantPermissionOverride(
                memberId, permission.getCode(), PermissionOverrideScope.ACCOUNT, null,
                PermissionOverrideDecision.DENY, "Expired restriction", Instant.now().minusSeconds(1)))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("future");
    }

    @Test
    void permissionExceptionRecordsCreatorDecisionReasonAndScope() {
        UUID accountId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        setContext(accountId, actorUserId);
        Account account = account(accountId);
        Member target = member(memberId, account, user(UUID.randomUUID()), false);
        Member actor = member(UUID.randomUUID(), account, user(actorUserId), false);
        Permission permission = permission(UUID.randomUUID(), "platform.company.delete");
        Instant expiry = Instant.now().plusSeconds(3600);
        when(memberRepository.findByIdAndAccountId(memberId, accountId)).thenReturn(Optional.of(target));
        when(memberRepository.findByAccountIdAndUserId(accountId, actorUserId)).thenReturn(Optional.of(actor));
        when(permissionRepository.findByCode(permission.getCode())).thenReturn(Optional.of(permission));
        when(memberOverrideRepository.findByMemberIdAndScopeCompanyIsNullAndPermissionId(
                memberId, permission.getId())).thenReturn(Optional.empty());
        when(memberOverrideRepository.saveAndFlush(any(MemberPermissionOverride.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        memberService.grantPermissionOverride(
                memberId, permission.getCode(), PermissionOverrideScope.ACCOUNT, null,
                PermissionOverrideDecision.GRANT, "  Temporary access  ", expiry);

        ArgumentCaptor<MemberPermissionOverride> saved =
                ArgumentCaptor.forClass(MemberPermissionOverride.class);
        verify(memberOverrideRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isSameAs(actor);
        assertThat(saved.getValue().getDecision()).isEqualTo(PermissionOverrideDecision.GRANT);
        assertThat(saved.getValue().getReason()).isEqualTo("Temporary access");
        assertThat(saved.getValue().getScope()).isEqualTo(PermissionOverrideScope.ACCOUNT);
        assertThat(saved.getValue().getScopeCompany()).isNull();
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(expiry);
    }

    @Test
    void authorizationDetailReturnsScopedRolesAndOverridesWithSafeIdentity() {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        setContext(accountId);
        Account account = account(accountId);
        Member target = member(memberId, account, user(UUID.randomUUID()), false);
        Role role = role(roleId, account, true);
        Company company = company(companyId, account, true);
        MemberRole assignment = new MemberRole();
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
        assignment.setMember(target);
        assignment.setRole(role);
        assignment.setEffectScope(RoleAssignmentScope.COMPANY);
        assignment.setScopeCompany(company);
        Permission permission = permission(UUID.randomUUID(), "platform.company.delete");
        MemberPermissionOverride override = new MemberPermissionOverride();
        ReflectionTestUtils.setField(override, "id", UUID.randomUUID());
        override.setMember(target);
        override.setCreatedBy(target);
        override.setPermission(permission);
        override.setScope(PermissionOverrideScope.ACCOUNT);
        override.setDecision(PermissionOverrideDecision.DENY);
        override.setReason("Temporary restriction");
        MemberDto summary = new MemberDto(
                memberId, target.getUser().getId(), target.getUser().getUsername(),
                target.getUser().getEmail(), "Nora", null, false, true,
                target.getUser().getCredentialState(), false, false);
        when(memberRepository.findByIdAndAccountId(memberId, accountId))
                .thenReturn(Optional.of(target));
        when(memberRoleRepository.findAllForAuthorizationByMemberId(memberId))
                .thenReturn(List.of(assignment));
        when(memberOverrideRepository.findAllForAuthorizationByMemberId(memberId))
                .thenReturn(List.of(override));
        when(memberMapper.toDto(target)).thenReturn(summary);

        var result = memberService.getMemberAuthorization(memberId);

        assertThat(result.member()).isEqualTo(summary);
        assertThat(result.roles()).singleElement().satisfies(item -> {
            assertThat(item.roleId()).isEqualTo(roleId);
            assertThat(item.scope()).isEqualTo(RoleAssignmentScope.COMPANY);
            assertThat(item.companyId()).isEqualTo(companyId);
            assertThat(item.roleStatus()).isEqualTo(RoleStatus.ACTIVE);
        });
        assertThat(result.overrides()).singleElement().satisfies(item -> {
            assertThat(item.permissionCode()).isEqualTo(permission.getCode());
            assertThat(item.decision()).isEqualTo(PermissionOverrideDecision.DENY);
            assertThat(item.scope()).isEqualTo(PermissionOverrideScope.ACCOUNT);
        });
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
        role.setStatus(active ? RoleStatus.ACTIVE : RoleStatus.INACTIVE);
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

    private static Permission permission(UUID id, String code) {
        Permission permission = new Permission();
        ReflectionTestUtils.setField(permission, "id", id);
        permission.setCode(code);
        permission.setName(code);
        return permission;
    }

    private static void addPermission(Role role, String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        RolePermission rolePermission = new RolePermission();
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        role.getPermissions().add(rolePermission);
    }


    private static User userFrom(NewUserCommand command) {
        User user = new User();
        user.setUsername(command.username());
        user.setEmail(command.email());
        user.setFirstName(command.firstName());
        user.setLastName(command.lastName());
        user.setPhone(command.phone());
        user.setActive(command.active());
        user.setEmailVerified(command.emailVerified());
        return user;
    }
}
