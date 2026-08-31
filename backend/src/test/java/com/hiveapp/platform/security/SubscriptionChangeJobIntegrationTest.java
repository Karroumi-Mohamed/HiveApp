package com.hiveapp.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeJobProcessor;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeJobService;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeJobTransitionService;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
    })
class SubscriptionChangeJobIntegrationTest extends PlatformShellIntegrationTestSupport {

  @Autowired private UserRepository users;
  @Autowired private AccountRepository accounts;
  @Autowired private SubscriptionChangeJobProcessor processor;
  @Autowired private SubscriptionChangeJobTransitionService transitions;
  @Autowired private SubscriptionChangeJobService jobs;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  void reviewedPopulationExecutesPerAccountAndKeepsIdentityBehindItsOwnEndpoint()
      throws Exception {
    String token = loginAdminAndGetToken();
    Account first = createSubscribedAccount("First");
    Account second = createSubscribedAccount("Second");
    JsonNode preview =
        preview(
            token,
            List.of(first.getId(), second.getId()),
            new SubscriptionChangeRequest("PRO", Set.of(), List.of()),
            null);

    assertThat(preview.get("targetCount").asInt()).isEqualTo(2);
    assertThat(preview.get("readyCount").asInt()).isEqualTo(2);
    assertThat(preview.path("sample").get(0).has("accountId")).isFalse();
    UUID jobId = UUID.fromString(preview.get("jobId").asText());
    confirm(token, preview);

    processor.processDue(Instant.now().plusSeconds(5));

    mockMvc
        .perform(
            get("/api/admin/subscription-change-jobs/{jobId}", jobId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.status").value("COMPLETED"))
        .andExpect(jsonPath("$.summary.targetCount").value(2));
    String resultsBody =
        mockMvc
            .perform(
                get("/api/admin/subscription-change-jobs/{jobId}/results", jobId)
                    .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[0].accountId").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode results = objectMapper.readTree(resultsBody).get("content");
    List<UUID> resultIds =
        List.of(
            UUID.fromString(results.get(0).get("id").asText()),
            UUID.fromString(results.get(1).get("id").asText()));

    mockMvc
        .perform(
            post("/api/admin/subscription-change-jobs/{jobId}/results/identities", jobId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new SubscriptionChangeJobModels.IdentityRequest(resultIds))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[*].accountId").isNotEmpty())
        .andExpect(jsonPath("$[*].accountName").isNotEmpty());
  }

  @Test
  void scheduledJobDoesNotRunEarlyAndCanBeCancelledWithStableResults() throws Exception {
    String token = loginAdminAndGetToken();
    Account account = createSubscribedAccount("Scheduled");
    Instant executeAt = Instant.now().plusSeconds(120);
    JsonNode preview =
        preview(
            token,
            List.of(account.getId()),
            new SubscriptionChangeRequest("PRO", Set.of(), List.of()),
            executeAt);
    UUID jobId = UUID.fromString(preview.get("jobId").asText());
    confirm(token, preview)
        .andExpect(jsonPath("$.summary.status").value("SCHEDULED"));

    processor.processDue(executeAt.minusSeconds(1));
    mockMvc
        .perform(
            get("/api/admin/subscription-change-jobs/{jobId}", jobId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.status").value("SCHEDULED"));

    mockMvc
        .perform(
            post("/api/admin/subscription-change-jobs/{jobId}/cancel", jobId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Campaign has been withdrawn\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.status").value("CANCELLED"))
        .andExpect(jsonPath("$.summary.cancelledCount").value(1));
  }

  @Test
  void runningJobIsResumedAfterAWorkerStopsBetweenClaimAndExecution() throws Exception {
    String token = loginAdminAndGetToken();
    Account account = createSubscribedAccount("Resumable");
    JsonNode preview =
        preview(
            token,
            List.of(account.getId()),
            new SubscriptionChangeRequest("PRO", Set.of(), List.of()),
            null);
    UUID jobId = UUID.fromString(preview.get("jobId").asText());
    confirm(token, preview);
    assertThat(transitions.claimOrResume(jobId, Instant.now().plusSeconds(5))).isTrue();

    mockMvc
        .perform(
            get("/api/admin/subscription-change-jobs/{jobId}", jobId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.status").value("RUNNING"));

    processor.processDue(Instant.now().plusSeconds(5));
    mockMvc
        .perform(
            get("/api/admin/subscription-change-jobs/{jobId}", jobId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.status").value("COMPLETED"))
        .andExpect(jsonPath("$.summary.awaitingPaymentCount").value(1));
  }

  @Test
  void failedPopulationCanBeRetriedWithoutRewritingOriginalReason() throws Exception {
    String token = loginAdminAndGetToken();
    Account account = createAccountWithoutSubscription("Missing");
    JsonNode preview =
        preview(
            token,
            List.of(account.getId()),
            new SubscriptionChangeRequest("PRO", Set.of(), List.of()),
            null);
    assertThat(preview.get("conflictCount").asInt()).isEqualTo(1);
    UUID jobId = UUID.fromString(preview.get("jobId").asText());
    confirm(token, preview);
    processor.processDue(Instant.now().plusSeconds(5));

    mockMvc
        .perform(
            post("/api/admin/subscription-change-jobs/{jobId}/retry", jobId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Subscription was expected to be provisioned\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.status").value("QUEUED"))
        .andExpect(jsonPath("$.summary.reason").value("Population correction review"))
        .andExpect(jsonPath("$.summary.retryCount").value(1))
        .andExpect(
            jsonPath("$.summary.lastRetryReason")
                .value("Subscription was expected to be provisioned"));

    processor.processDue(Instant.now().plusSeconds(5));
    mockMvc
        .perform(
            get("/api/admin/subscription-change-jobs/{jobId}", jobId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.status").value("COMPLETED_WITH_ERRORS"))
        .andExpect(jsonPath("$.summary.conflictCount").value(1));
  }

  @Test
  void duplicateTargetsAndUnboundedPagesAreRejected() throws Exception {
    String token = loginAdminAndGetToken();
    Account account = createSubscribedAccount("Duplicate");
    SubscriptionChangeJobModels.PreviewRequest request =
        new SubscriptionChangeJobModels.PreviewRequest(
            List.of(account.getId(), account.getId()),
            new SubscriptionChangeRequest("PRO", Set.of(), List.of()),
            "Duplicate population",
            null);
    mockMvc
        .perform(
            post("/api/admin/subscription-change-jobs/preview")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mockMvc
        .perform(
            get("/api/admin/subscription-change-jobs")
                .header("Authorization", bearer(token))
                .param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void operationalJobListUsesOneBulkStatusQueryInsteadOfOneQueryPerJob() throws Exception {
    String token = loginAdminAndGetToken();
    Account account = createSubscribedAccount("QueryBound");
    preview(
        token,
        List.of(account.getId()),
        new SubscriptionChangeRequest("PRO", Set.of(), List.of()),
        null);
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();

    assertThat(jobs.list(null, PageRequest.of(0, 100)).getContent()).isNotEmpty();

    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
  }

  private JsonNode preview(
      String token, List<UUID> accountIds, SubscriptionChangeRequest selection, Instant executeAt)
      throws Exception {
    SubscriptionChangeJobModels.PreviewRequest request =
        new SubscriptionChangeJobModels.PreviewRequest(
            accountIds, selection, "Population correction review", executeAt);
    String body =
        mockMvc
            .perform(
                post("/api/admin/subscription-change-jobs/preview")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private org.springframework.test.web.servlet.ResultActions confirm(String token, JsonNode preview)
      throws Exception {
    return mockMvc
        .perform(
            post(
                    "/api/admin/subscription-change-jobs/{jobId}/confirm",
                    UUID.fromString(preview.get("jobId").asText()))
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new SubscriptionChangeJobModels.ConfirmRequest(
                            preview.get("previewToken").asText()))))
        .andExpect(status().isOk());
  }

  private Account createSubscribedAccount(String suffix) throws Exception {
    String marker = UUID.randomUUID().toString().substring(0, 8);
    String email = marker + "-" + suffix.toLowerCase() + "@example.com";
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest(email, CLIENT_PASSWORD, suffix, "Owner", null))))
        .andExpect(status().isCreated());
    User owner = users.findByEmail(email).orElseThrow();
    Account account = accounts.findByOwner_Id(owner.getId()).orElseThrow();
    account.setName(marker + " " + suffix);
    return accounts.saveAndFlush(account);
  }

  private Account createAccountWithoutSubscription(String suffix) {
    String marker = UUID.randomUUID().toString().substring(0, 8);
    User owner =
        users.saveAndFlush(
            User.builder()
                .email(marker + "@example.com")
                .username(marker)
                .firstName(suffix)
                .lastName("Owner")
                .build());
    return accounts.saveAndFlush(
        Account.builder().owner(owner).name(marker + " " + suffix).slug(marker).build());
  }
}
