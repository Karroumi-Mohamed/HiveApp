package com.hiveapp.platform.client.account.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotFactory;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.shared.exception.InvalidStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceProvisioningServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock AccountRepository accountRepository;
    @Mock MemberRepository memberRepository;
    @Mock PlanRepository planRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionOverrideReader subscriptionOverrideReader;
    @Mock SubscriptionSnapshotFactory subscriptionSnapshotFactory;
    @Mock SubscriptionSnapshotReader subscriptionSnapshotReader;

    @Test
    void missingFreePlanFailsBeforeWorkspaceCreation() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(accountRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(planRepository.findByCode("FREE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().provision(userId, "owner@example.com"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("required FREE plan is not configured");

        verify(accountRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void repeatedProvisioningReturnsCompletedWorkspaceWithoutDuplicates() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = account(accountId, user(userId), "existing-slug");
        Member owner = new Member();
        owner.setOwner(true);
        Subscription subscription = new Subscription();
        subscription.setEntitlementSnapshot("{\"planCode\":\"FREE\"}");

        when(userRepository.findById(userId)).thenReturn(Optional.of(account.getOwner()));
        when(accountRepository.findByOwner_Id(userId)).thenReturn(Optional.of(account));
        when(memberRepository.findByAccountIdAndUserId(accountId, userId)).thenReturn(Optional.of(owner));
        when(subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .thenReturn(List.of(subscription));

        var result = service().provision(userId, "owner@example.com");

        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.slug()).isEqualTo("existing-slug");
        assertThat(result.created()).isFalse();
        verify(accountRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void newProvisioningUsesUserBoundSlugAndCompleteSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        User user = user(userId);
        Plan freePlan = freePlan();
        SubscriptionEntitlementSnapshot snapshot = SubscriptionEntitlementSnapshot.empty("FREE", BigDecimal.ZERO);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", accountId);
            return account;
        });
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionOverrideReader.write(any())).thenReturn("{}");
        when(subscriptionSnapshotFactory.fromPlan(freePlan)).thenReturn(snapshot);
        when(subscriptionSnapshotReader.write(snapshot)).thenReturn("{\"planCode\":\"FREE\"}");
        when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().provision(userId, "Same.Name@example.com");

        assertThat(result.created()).isTrue();
        assertThat(result.slug()).isEqualTo(
                "samename-" + userId.toString().replace("-", ""));
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).saveAndFlush(subscriptionCaptor.capture());
        assertThat(subscriptionCaptor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscriptionCaptor.getValue().getEntitlementSnapshot()).contains("FREE");
    }

    private WorkspaceProvisioningServiceImpl service() {
        return new WorkspaceProvisioningServiceImpl(
                userRepository,
                accountRepository,
                memberRepository,
                planRepository,
                subscriptionRepository,
                subscriptionOverrideReader,
                subscriptionSnapshotFactory,
                subscriptionSnapshotReader);
    }

    private User user(UUID id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("owner@example.com");
        user.setFirstName("Owner");
        user.setLastName("User");
        return user;
    }

    private Account account(UUID id, User owner, String slug) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", id);
        account.setOwner(owner);
        account.setName("Owner's Workspace");
        account.setSlug(slug);
        account.setActive(true);
        return account;
    }

    private Plan freePlan() {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        plan.setCode("FREE");
        plan.setPrice(BigDecimal.ZERO);
        plan.setActive(true);
        return plan;
    }
}
