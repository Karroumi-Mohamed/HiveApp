package com.hiveapp.platform.security;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionIntegrityIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionOverrideReader subscriptionOverrideReader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void databaseRejectsActiveAndTrialingSubscriptionsForSameAccount() throws Exception {
        Account account = registeredAccount();
        Plan free = planRepository.findByCode("FREE").orElseThrow();

        Subscription trial = newSubscription(account, free, SubscriptionStatus.TRIALING);

        assertThatThrownBy(() -> subscriptionRepository.saveAndFlush(trial))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancelledSubscriptionReleasesUsableSlotForReplacement() throws Exception {
        Account account = registeredAccount();
        Subscription initial = subscriptionRepository.findActiveByAccountId(account.getId()).orElseThrow();
        Plan free = planRepository.findByCode("FREE").orElseThrow();
        initial.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.saveAndFlush(initial);

        Subscription replacement = newSubscription(account, free, SubscriptionStatus.TRIALING);
        subscriptionRepository.saveAndFlush(replacement);

        List<Subscription> usable = subscriptionRepository.findAllByAccountIdAndStatusIn(
                account.getId(), List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING));
        assertThat(usable).hasSize(1);
        assertThat(usable.getFirst().getId()).isEqualTo(replacement.getId());
        assertThat(usable.getFirst().getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
    }

    @Test
    void simultaneousAdminPlanAssignmentsLeaveOneUsableSubscription() throws Exception {
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        String adminToken = loginAdminAndGetToken();

        CompletableFuture<Integer> pro = assignPlanAsync(adminToken, accountId, "PRO");
        CompletableFuture<Integer> enterprise = assignPlanAsync(adminToken, accountId, "ENTERPRISE");

        assertThat(pro.join()).isEqualTo(201);
        assertThat(enterprise.join()).isEqualTo(201);
        assertThat(subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .hasSize(1);
    }

    @Test
    void databaseRejectsUsableStatusWithoutItsAccountSlot() throws Exception {
        Account account = registeredAccount();
        Plan free = planRepository.findByCode("FREE").orElseThrow();
        Timestamp now = Timestamp.from(Instant.now());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into subscriptions
                            (id, account_id, plan_id, status, created_at, updated_at, usable_account_id)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(), account.getId(), free.getId(), "TRIALING", now, now, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CompletableFuture<Integer> assignPlanAsync(String adminToken, UUID accountId, String planCode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}", accountId)
                                .param("planCode", planCode)
                                .header("Authorization", bearer(adminToken)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getStatus();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private Account registeredAccount() throws Exception {
        return accountRepository.findById(currentAccountId(registerClientAndGetToken())).orElseThrow();
    }

    private UUID currentAccountId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private Subscription newSubscription(Account account, Plan plan, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setAccount(account);
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setCustomOverrides(subscriptionOverrideReader.write(SubscriptionOverrides.empty()));
        subscription.setCurrentPrice(plan.getPrice() != null ? plan.getPrice() : BigDecimal.ZERO);
        return subscription;
    }
}
