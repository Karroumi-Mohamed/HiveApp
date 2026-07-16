package com.hiveapp.platform.client.member.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;
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
    @Mock private TokenSessionService tokenSessionService;

    @InjectMocks
    private MemberServiceImpl memberService;

    @AfterEach
    void clearContext() {
        HiveAppContextHolder.clearContext();
    }

    @Test
    void addMemberChecksMemberQuotaInsideService() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setContext(accountId);

        Account account = account(accountId);
        User user = user(userId);
        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(memberRepository.countByAccountIdAndIsActiveTrue(accountId)).thenReturn(2L);
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member result = memberService.addMember(accountId, userId, "Nora");

        assertThat(result.getAccount()).isSameAs(account);
        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getDisplayName()).isEqualTo("Nora");

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
    void addMemberRejectsDifferentAccountBeforeQuotaCheck() {
        UUID currentAccountId = UUID.randomUUID();
        UUID requestedAccountId = UUID.randomUUID();
        setContext(currentAccountId);

        assertThatThrownBy(() -> memberService.addMember(requestedAccountId, UUID.randomUUID(), "Nora"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Member does not belong to your account");

        verifyNoInteractions(quotaEnforcer, accountRepository, userRepository);
    }

    @Test
    void addMemberRejectsUserWhoAlreadyHasAnActiveWorkspaceMembership() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setContext(accountId);
        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account(accountId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(memberRepository.existsByUserIdAndIsActiveTrue(userId)).thenReturn(true);

        assertThatThrownBy(() -> memberService.addMember(accountId, userId, "Nora"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessage("User already has an active workspace membership");

        verifyNoInteractions(quotaEnforcer);
    }

    @Test
    void addMemberRejectsExistingMembershipInRequestedWorkspace() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        setContext(accountId);
        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account(accountId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(memberRepository.existsByAccountIdAndUserId(accountId, userId)).thenReturn(true);

        assertThatThrownBy(() -> memberService.addMember(accountId, userId, "Nora"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessage("User is already a member of this workspace");

        verifyNoInteractions(quotaEnforcer);
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

        verifyNoInteractions(tokenSessionService);
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
        verify(tokenSessionService).revokeAll(List.of(userId), TokenAudience.CLIENT);
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

    private static void setContext(UUID accountId) {
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                UUID.randomUUID(),
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

    private static Role role(UUID id, Account account, boolean active) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", id);
        role.setAccount(account);
        role.setName("Manager");
        role.setActive(active);
        return role;
    }

}
