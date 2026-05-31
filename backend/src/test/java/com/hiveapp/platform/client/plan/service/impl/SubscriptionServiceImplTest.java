package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.service.BillingCalculator;
import com.hiveapp.platform.client.plan.service.BillingConfigurationValidator;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.quota.QuotaOverride;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private BillingCalculator billingCalculator;
    @Mock private SubscriptionOverrideReader subscriptionOverrideReader;
    @Mock private BillingConfigurationValidator billingConfigurationValidator;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @Test
    void updateOverridesRejectsInvalidConfigurationBeforePersistence() {
        UUID accountId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(subscription));
        doThrow(new InvalidRequestException("Invalid feature"))
                .when(billingConfigurationValidator).validateSubscriptionOverrides(Set.of("platform.plans"), List.of());

        assertThatThrownBy(() -> subscriptionService.updateOverrides(accountId, Set.of("platform.plans"), List.of()))
                .isInstanceOf(InvalidRequestException.class);

        verify(subscriptionOverrideReader, never()).write(org.mockito.ArgumentMatchers.any());
        verify(subscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateOverridesPersistsValidatedWorkspaceQuotaIncrease() {
        UUID accountId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        List<QuotaOverride> quotaOverrides = List.of(new QuotaOverride("platform.workspace", "members", 10L));
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(subscription));
        when(subscriptionOverrideReader.write(org.mockito.ArgumentMatchers.any())).thenReturn("{\"quotaOverrides\":[]}");
        when(billingCalculator.calculate(subscription)).thenReturn(new BigDecimal("39.99"));
        when(subscriptionRepository.save(subscription)).thenReturn(subscription);

        Subscription result = subscriptionService.updateOverrides(accountId, Set.of(), quotaOverrides);

        verify(billingConfigurationValidator).validateSubscriptionOverrides(Set.of(), quotaOverrides);
        assertThat(result.getCustomOverrides()).isEqualTo("{\"quotaOverrides\":[]}");
        assertThat(result.getCurrentPrice()).isEqualByComparingTo("39.99");
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void planAssignmentCancelsExistingUsableSubscriptionsBeforeCreatingReplacement() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        Plan free = plan("FREE", true);
        Plan pro = plan("PRO", true);
        Subscription active = subscription(free, SubscriptionStatus.ACTIVE);
        Subscription trialing = subscription(free, SubscriptionStatus.TRIALING);

        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(pro));
        when(subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .thenReturn(List.of(active, trialing));
        when(subscriptionOverrideReader.write(org.mockito.ArgumentMatchers.any()))
                .thenReturn("{\"addedFeatures\":[],\"quotaOverrides\":[]}");
        when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Subscription result = subscriptionService.createSubscription(accountId, "PRO");

        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(trialing.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(result.getPlan()).isEqualTo(pro);
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getAccount()).isEqualTo(account);
        verify(subscriptionRepository).saveAllAndFlush(List.of(active, trialing));
    }

    @Test
    void planAssignmentRejectsInactivePlanBeforeChangingCurrentSubscription() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(new Account()));
        when(planRepository.findByCode("ARCHIVED")).thenReturn(Optional.of(plan("ARCHIVED", false)));

        assertThatThrownBy(() -> subscriptionService.createSubscription(accountId, "ARCHIVED"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Inactive plans");

        verify(subscriptionRepository, never()).findAllByAccountIdAndStatusIn(
                any(), org.mockito.ArgumentMatchers.anyCollection());
        verify(subscriptionRepository, never()).saveAndFlush(any(Subscription.class));
    }

    @Test
    void planAssignmentRejectsReassigningTheCurrentActivePlan() {
        UUID accountId = UUID.randomUUID();
        Plan pro = plan("PRO", true);
        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(new Account()));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(pro));
        when(subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .thenReturn(List.of(subscription(pro, SubscriptionStatus.ACTIVE)));

        assertThatThrownBy(() -> subscriptionService.createSubscription(accountId, "PRO"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("already subscribed");

        verify(subscriptionRepository, never()).saveAllAndFlush(org.mockito.ArgumentMatchers.anyCollection());
        verify(subscriptionRepository, never()).saveAndFlush(any(Subscription.class));
    }

    private Plan plan(String code, boolean active) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setActive(active);
        plan.setPrice(BigDecimal.ZERO);
        return plan;
    }

    private Subscription subscription(Plan plan, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setStatus(status);
        return subscription;
    }
}
