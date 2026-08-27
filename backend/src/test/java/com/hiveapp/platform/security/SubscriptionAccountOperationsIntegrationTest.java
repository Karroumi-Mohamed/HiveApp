package com.hiveapp.platform.security;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionAccountOperationsIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void operationalAccountTableFiltersTheDeterministicLatestSubscription() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Account active = createAccount(marker, "Alpha");
        Account trialing = createAccount(marker, "Bravo");
        Account suspended = createAccount(marker, "Charlie");
        Account withoutSubscription = createAccountWithoutSubscription(marker, "Delta");

        replaceSubscription(trialing, SubscriptionStatus.TRIALING);
        changeStatus(suspended, SubscriptionStatus.SUSPENDED);
        withoutSubscription.setActive(false);
        accountRepository.saveAndFlush(withoutSubscription);

        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(adminToken))
                        .param("query", "  " + marker + "  ")
                        .param("subscriptionStatus", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(suspended.getId().toString()))
                .andExpect(jsonPath("$.content[0].latestSubscription.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.content[0].latestSubscription.planCode").value("FREE"))
                .andExpect(jsonPath("$.content[0].latestSubscription.billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.content[0].latestSubscription.currentPeriodEnd").isNotEmpty())
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());

        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(adminToken))
                        .param("query", marker)
                        .param("subscriptionStatus", "TRIALING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(trialing.getId().toString()))
                .andExpect(jsonPath("$.content[0].latestSubscription.status").value("TRIALING"));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(adminToken))
                        .param("query", marker)
                        .param("subscriptionStatus", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(adminToken))
                        .param("query", marker)
                        .param("hasSubscription", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(withoutSubscription.getId().toString()))
                .andExpect(jsonPath("$.content[0].latestSubscription").doesNotExist());

        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(adminToken))
                        .param("query", marker)
                        .param("accountActive", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(withoutSubscription.getId().toString()));

        String firstPage = mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(adminToken))
                        .param("query", marker)
                        .param("sort", "name")
                        .param("direction", "asc")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andReturn().getResponse().getContentAsString();
        String secondPage = mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(adminToken))
                        .param("query", marker)
                        .param("sort", "name")
                        .param("direction", "asc")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(firstPage).path("content"))
                .extracting(row -> row.path("name").asText())
                .containsExactly(marker + " Alpha", marker + " Bravo");
        assertThat(objectMapper.readTree(secondPage).path("content"))
                .extracting(row -> row.path("name").asText())
                .containsExactly(marker + " Charlie", marker + " Delta");
        assertThat(active.getId()).isNotNull();
    }

    @Test
    void accountSearchKeepsOwnerIdentitySeparateAndSupportsExactIdsPlansAndLiteralTerms()
            throws Exception {
        String token = loginAdminAndGetToken();
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Account subscribed = createAccount(marker, "PlanFilter");
        Account literal = createAccountWithoutSubscription(marker, "Literal");
        literal.setName(marker + " 100%_ literal");
        accountRepository.saveAndFlush(literal);
        String ownerEmail = accountRepository.findOwnerEmailById(subscribed.getId()).orElseThrow();

        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("query", subscribed.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(subscribed.getId().toString()))
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("query", ownerEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("query", "%_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(literal.getId().toString()));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("query", subscribed.getId().toString())
                        .param("planCode", "free"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("query", subscribed.getId().toString())
                        .param("planCode", "MISSING_PLAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/admin/subscriptions/accounts/chooser")
                        .header("Authorization", bearer(token))
                        .param("query", ownerEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/chooser")
                        .header("Authorization", bearer(token))
                        .param("query", subscribed.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());

        mockMvc.perform(get("/api/admin/subscriptions/accounts/by-owner-email")
                        .header("Authorization", bearer(token))
                        .param("ownerEmail", ownerEmail.toUpperCase(java.util.Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].ownerEmail").value(ownerEmail))
                .andExpect(jsonPath("$.content[0].account.id").value(subscribed.getId().toString()))
                .andExpect(jsonPath("$.content[0].account.ownerEmail").doesNotExist());
    }

    @Test
    void latestSubscriptionUsesCreatedAtThenIdAsOneDeterministicOrder() throws Exception {
        String token = loginAdminAndGetToken();
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Account account = createAccount(marker, "Tie");
        replaceSubscription(account, SubscriptionStatus.EXPIRED);
        Instant tiedAt = Instant.parse("2026-08-27T01:02:03Z");
        jdbcTemplate.update(
                "update subscriptions set created_at = ?, updated_at = ? where account_id = ?",
                java.sql.Timestamp.from(tiedAt), java.sql.Timestamp.from(tiedAt), account.getId());
        UUID expectedId = jdbcTemplate.queryForObject(
                "select id from subscriptions where account_id = ? "
                        + "order by created_at desc, id desc fetch first 1 row only",
                UUID.class, account.getId());
        SubscriptionStatus expectedStatus = subscriptionRepository.findById(expectedId)
                .orElseThrow().getStatus();
        SubscriptionStatus otherStatus = expectedStatus == SubscriptionStatus.CANCELLED
                ? SubscriptionStatus.EXPIRED
                : SubscriptionStatus.CANCELLED;

        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("query", account.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].latestSubscription.id").value(expectedId.toString()))
                .andExpect(jsonPath("$.content[0].latestSubscription.status").value(expectedStatus.name()));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("query", account.getId().toString())
                        .param("subscriptionStatus", expectedStatus.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("query", account.getId().toString())
                        .param("subscriptionStatus", otherStatus.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void accountTableAndChooserRejectUnboundedOrContradictoryRequests() throws Exception {
        String token = loginAdminAndGetToken();

        assertInvalid(token, "/api/admin/subscriptions/accounts/search", "page", "-1");
        assertInvalid(token, "/api/admin/subscriptions/accounts/search", "size", "101");
        assertInvalid(token, "/api/admin/subscriptions/accounts/search", "sort", "latestSubscription.status");
        assertInvalid(token, "/api/admin/subscriptions/accounts/search", "direction", "sideways");
        assertInvalid(token, "/api/admin/subscriptions/accounts/search", "query", "x".repeat(161));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("subscriptionStatus", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
        for (String sort : List.of("name", "slug", "active", "createdAt")) {
            mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                            .header("Authorization", bearer(token))
                            .param("sort", sort)
                            .param("direction", "desc"))
                    .andExpect(status().isOk());
        }
        assertInvalid(token, "/api/admin/subscriptions/accounts/search", "sort", "ownerEmail");
        assertInvalid(token, "/api/admin/subscriptions/accounts/search", "planCode", "not-valid!");
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("hasSubscription", "false")
                        .param("subscriptionStatus", "ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("hasSubscription", "false")
                        .param("planCode", "FREE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/admin/subscriptions/accounts/by-owner-email")
                        .header("Authorization", bearer(token))
                        .param("ownerEmail", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        createAccountWithoutSubscription(UUID.randomUUID().toString().substring(0, 8), "Clamp");
        mockMvc.perform(get("/api/admin/subscriptions/accounts/search")
                        .header("Authorization", bearer(token))
                        .param("page", "999999")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(999999))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalPages").isNumber());

        List<String> tooMany = IntStream.range(0, 101)
                .mapToObj(ignored -> UUID.randomUUID().toString())
                .toList();
        var request = get("/api/admin/subscriptions/accounts/chooser/selected")
                .header("Authorization", bearer(token));
        tooMany.forEach(id -> request.param("ids", id));
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        UUID duplicate = UUID.randomUUID();
        mockMvc.perform(get("/api/admin/subscriptions/accounts/chooser/selected")
                        .header("Authorization", bearer(token))
                        .param("ids", duplicate.toString(), duplicate.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void chooserStaysNarrowAndSelectedHydrationKeepsInactiveAccounts() throws Exception {
        String token = loginAdminAndGetToken();
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Account active = createAccount(marker, "Active");
        Account inactive = createAccount(marker, "Inactive");
        inactive.setActive(false);
        accountRepository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/admin/subscriptions/accounts/chooser")
                        .header("Authorization", bearer(token))
                        .param("query", marker)
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(active.getId().toString()))
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist())
                .andExpect(jsonPath("$.content[0].latestSubscription").doesNotExist());

        mockMvc.perform(get("/api/admin/subscriptions/accounts/chooser/selected")
                        .header("Authorization", bearer(token))
                        .param("ids", active.getId().toString(), inactive.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value(marker + " Active"))
                .andExpect(jsonPath("$[1].name").value(marker + " Inactive"))
                .andExpect(jsonPath("$[0].ownerEmail").doesNotExist())
                .andExpect(jsonPath("$[1].active").value(false));
    }

    private Account createAccount(String marker, String suffix) throws Exception {
        String email = marker + "-" + suffix.toLowerCase() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, CLIENT_PASSWORD, suffix, "Owner", null))))
                .andExpect(status().isCreated());
        var user = userRepository.findByEmail(email).orElseThrow();
        Account account = accountRepository.findByOwner_Id(user.getId()).orElseThrow();
        account.setName(marker + " " + suffix);
        return accountRepository.saveAndFlush(account);
    }

    private Account createAccountWithoutSubscription(String marker, String suffix) {
        String key = marker + "-" + suffix.toLowerCase();
        User owner = User.builder()
                .email(key + "@example.com")
                .username(key)
                .firstName(suffix)
                .lastName("Owner")
                .build();
        owner = userRepository.saveAndFlush(owner);
        return accountRepository.saveAndFlush(Account.builder()
                .owner(owner)
                .name(marker + " " + suffix)
                .slug(key)
                .build());
    }

    private void changeStatus(Account account, SubscriptionStatus status) {
        Subscription subscription = subscriptionRepository.findUsableByAccountId(account.getId()).orElseThrow();
        subscription.setStatus(status);
        subscriptionRepository.saveAndFlush(subscription);
    }

    private Subscription replaceSubscription(Account account, SubscriptionStatus status) {
        Subscription previous = subscriptionRepository.findUsableByAccountId(account.getId()).orElseThrow();
        previous.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.saveAndFlush(previous);

        Subscription replacement = new Subscription();
        replacement.setAccount(account);
        replacement.setPlan(previous.getPlan());
        replacement.setEntitlementSnapshot(previous.getEntitlementSnapshot());
        replacement.setStatus(status);
        replacement.setCurrentPeriodStart(previous.getCurrentPeriodStart());
        replacement.setCurrentPeriodEnd(previous.getCurrentPeriodEnd());
        replacement.setCancelAtPeriodEnd(false);
        replacement.setCurrentMoney(previous.currentMoney());
        return subscriptionRepository.saveAndFlush(replacement);
    }

    private void assertInvalid(String token, String path, String parameter, String value) throws Exception {
        mockMvc.perform(get(path)
                        .header("Authorization", bearer(token))
                        .param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
