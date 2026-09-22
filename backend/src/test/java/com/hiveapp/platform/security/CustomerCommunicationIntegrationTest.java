package com.hiveapp.platform.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.communication.*;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = "spring.datasource.url=jdbc:h2:mem:communications;DB_CLOSE_DELAY=-1")
class CustomerCommunicationIntegrationTest extends PlatformShellIntegrationTestSupport {
  @Autowired AccountRepository accounts;
  @Autowired UserRepository users;
  @Autowired CommunicationEntryRepository entries;
  @Autowired CommunicationPublicationRepository publications;
  @Autowired CommunicationEmailSource email;
  @Autowired CommunicationSources sources;
  @Autowired CommunicationBackfill backfill;

  @Autowired
  com.hiveapp.platform.client.plan.domain.repository.PlanContentNoticeRepository contentNotices;

  @Autowired
  com.hiveapp.platform.client.plan.domain.repository.CommercialNoticeReadRepository reads;

  @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
  String admin, client, other;
  UUID account, otherAccount;
  static final String ADMIN = "/api/admin/customer-communications",
      CLIENT = "/api/v1/communications";

  @BeforeEach
  void setup() throws Exception {
    admin = loginAdminAndGetToken();
    client = registerClientAndGetToken();
    other = registerClientAndGetToken();
    account = accountId(client);
    otherAccount = accountId(other);
  }

  UUID accountId(String token) throws Exception {
    var body =
        mockMvc
            .perform(get("/api/v1/accounts/me").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(body).get("id").asText());
  }

  Map<String, Object> draft(
      String kind, String purpose, List<UUID> ids, boolean email, boolean replies) {
    var d = new HashMap<String, Object>();
    d.put("kind", kind);
    d.put("purpose", purpose);
    d.put("messageTitle", "Important update");
    d.put("messageBody", "Private content");
    d.put("accountIds", ids);
    d.put("email", email);
    d.put("replies", replies);
    return d;
  }

  UUID create(Map<String, Object> d) throws Exception {
    var result =
        mockMvc
            .perform(
                post(ADMIN)
                    .header("Authorization", bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(d)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(result).get("id").asText());
  }

  void publish(UUID id) throws Exception {
    mockMvc
        .perform(
            post(ADMIN + "/" + id + "/publish")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"version\":"
                        + publications.findById(id).orElseThrow().getVersion()
                        + ",\"reason\":\"Reviewed recipients and content\"}"))
        .andExpect(status().isOk());
  }

  UUID entry(UUID publication, UUID account) {
    return entries
        .findBySourceAndSourceIdAndAccountId("ADMIN", publication, account)
        .orElseThrow()
        .getId();
  }

  @Test
  void privateNoticeHasIndependentReadAndArchiveWithNoCrossAccountAccess() throws Exception {
    var id = create(draft("NOTICE", "SERVICE", List.of(account), false, false));
    mockMvc
        .perform(get(CLIENT).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(0));
    publish(id);
    var entry = entry(id, account);
    mockMvc
        .perform(get(CLIENT).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[0].read").value(false));
    mockMvc
        .perform(get(CLIENT + "/" + entry).header("Authorization", bearer(other)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post(CLIENT + "/" + entry + "/read").header("Authorization", bearer(client)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(post(CLIENT + "/" + entry + "/read").header("Authorization", bearer(client)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(CLIENT).param("unread", "true").header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(0));
    mockMvc
        .perform(post(CLIENT + "/" + entry + "/archive").header("Authorization", bearer(client)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(CLIENT).param("archived", "true").header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(1));
    mockMvc
        .perform(
            post(CLIENT + "/" + entry + "/acknowledge").header("Authorization", bearer(client)))
        .andExpect(status().isConflict());
  }

  @Test
  void warningAcknowledgementDoesNotResolveOrArchiveIt() throws Exception {
    var id = create(draft("WARNING", "SERVICE", List.of(account), false, false));
    publish(id);
    var entry = entry(id, account);
    mockMvc
        .perform(
            post(CLIENT + "/" + entry + "/acknowledge").header("Authorization", bearer(client)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(CLIENT + "/" + entry).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("acknowledged").value(true))
        .andExpect(jsonPath("sourceState").value("PUBLISHED"))
        .andExpect(jsonPath("canArchive").value(false));
    mockMvc
        .perform(post(CLIENT + "/" + entry + "/archive").header("Authorization", bearer(client)))
        .andExpect(status().isConflict());
  }

  @Test
  void conversationCreationAndReplyEndpointsAreRemoved() throws Exception {
    mockMvc
        .perform(
            post(ADMIN)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        draft("MESSAGE", "SERVICE", List.of(account), false, true))))
        .andExpect(status().isUnprocessableEntity());
    var id = create(draft("NOTICE", "SERVICE", List.of(account), false, false));
    publish(id);
    var entry = entry(id, account);
    mockMvc
        .perform(
            post(CLIENT + "/" + entry + "/replies")
                .header("Authorization", bearer(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"replyBody\":\"Not a chat\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get(ADMIN + "/entries/" + entry + "/replies").header("Authorization", bearer(admin)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post(ADMIN + "/entries/" + entry + "/close")
                .param("closed", "true")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isNotFound());
  }

  @Test
  void marketingRequiresOptInAndCannotDisguiseAsWarning() throws Exception {
    mockMvc
        .perform(
            post(ADMIN)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        draft("WARNING", "MARKETING", List.of(account), false, false))))
        .andExpect(status().isBadRequest());
    var id = create(draft("NOTICE", "MARKETING", List.of(account), true, false));
    publish(id);
    var entry = entry(id, account);
    mockMvc
        .perform(get(CLIENT).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(0));
    assertThat(email.claimNotice(entry)).isNull();
    mockMvc
        .perform(
            put(CLIENT + "/preferences")
                .header("Authorization", bearer(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"marketingInApp\":true,\"marketingEmail\":false}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(CLIENT).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(1));
    mockMvc
        .perform(
            put(CLIENT + "/preferences")
                .header("Authorization", bearer(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"marketingInApp\":false,\"marketingEmail\":false}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(CLIENT + "/" + entry).header("Authorization", bearer(client)))
        .andExpect(status().isNotFound());
  }

  @Test
  void scheduledCancellationAndPublishedImmutability() throws Exception {
    var d = draft("NOTICE", "SERVICE", List.of(account), true, false);
    d.put("availableAt", Instant.now().plusSeconds(3600).toString());
    var id = create(d);
    publish(id);
    var entry = entry(id, account);
    assertThat(email.claimNotice(entry)).isNull();
    mockMvc
        .perform(get(CLIENT).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(0));
    mockMvc
        .perform(
            put(ADMIN + "/" + id)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "version",
                            publications.findById(id).orElseThrow().getVersion(),
                            "draft",
                            d))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post(ADMIN + "/" + id + "/cancel")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "version",
                            publications.findById(id).orElseThrow().getVersion(),
                            "reason",
                            "Cancel before availability"))))
        .andExpect(status().isOk());
    assertThat(entries.findById(entry).orElseThrow().isHidden()).isTrue();
  }

  @Test
  void unauthenticatedAndCrossSurfaceRequestsAreRejectedAndPagesBounded() throws Exception {
    mockMvc.perform(get(CLIENT)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(ADMIN).header("Authorization", bearer(client)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get(CLIENT).param("size", "101").header("Authorization", bearer(client)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(ADMIN).param("page", "-1").header("Authorization", bearer(admin)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void legacyMessageIsPresentedAsOneWayInformationWithoutReplyControls() throws Exception {
    var id = create(draft("MESSAGE", "SERVICE", List.of(account), false, false));
    publish(id);
    var entry = entry(id, account);
    mockMvc
        .perform(get(CLIENT + "/" + entry).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("kind").value("NOTICE"))
        .andExpect(jsonPath("canReply").doesNotExist());
    mockMvc
        .perform(post(CLIENT + "/" + entry + "/read").header("Authorization", bearer(client)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(post(CLIENT + "/" + entry + "/read").header("Authorization", bearer(client)))
        .andExpect(status().isNoContent());
    assertThat(reads.countByNoticeId(entry)).isEqualTo(1);
  }

  @Test
  void emailHasOneLiveLeaseAndExplicitRetryAndRespectsCancellation() throws Exception {
    transactions.executeWithoutResult(
        s -> {
          var owner = accounts.findById(account).orElseThrow().getOwner();
          owner.setEmailVerified(true);
          users.save(owner);
        });
    var id = create(draft("NOTICE", "SERVICE", List.of(account), true, false));
    publish(id);
    var entry = entry(id, account);
    var claim = email.claimNotice(entry);
    assertThat(claim).isNotNull();
    assertThat(claim.body()).doesNotContain("Private content");
    assertThat(email.claimNotice(entry)).isNull();
    email.completeNotice(
        new com.hiveapp.platform.client.plan.service.CommercialNoticeDeliverySource.Claim(
            entry, UUID.randomUUID(), claim.email(), claim.subject(), claim.body()),
        com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.SENT);
    assertThat(entries.findById(entry).orElseThrow().getDelivery().getDelivery())
        .isEqualTo(com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.SENDING);
    email.completeNotice(
        claim, com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.FAILED);
    assertThat(email.claimNotice(entry)).isNull();
    for (int attempt = 0; attempt < 2; attempt++) {
      transactions.executeWithoutResult(
          s ->
              entries
                  .lock(entry)
                  .orElseThrow()
                  .setNextEmailAttemptAt(Instant.now().minusSeconds(1)));
      var retried = email.claimNotice(entry);
      assertThat(retried).isNotNull();
      email.completeNotice(
          retried, com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.FAILED);
    }
    mockMvc
        .perform(
            post(ADMIN + "/entries/" + entry + "/retry-email")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post(ADMIN + "/" + id + "/cancel")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "version",
                            publications.findById(id).orElseThrow().getVersion(),
                            "reason",
                            "Withdraw communication"))))
        .andExpect(status().isOk());
    assertThat(email.claimNotice(entry)).isNull();
    mockMvc
        .perform(
            post(ADMIN + "/entries/" + entry + "/retry-email")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isConflict());
  }

  @Test
  void staleDraftValidationAndExpiredVisibilityAreEnforced() throws Exception {
    var d = draft("NOTICE", "SERVICE", List.of(account), false, false);
    var id = create(d);
    mockMvc
        .perform(
            put(ADMIN + "/" + id)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("version", -1, "draft", d))))
        .andExpect(status().isConflict());
    d.put("replies", true);
    mockMvc
        .perform(
            post(ADMIN)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(d)))
        .andExpect(status().isUnprocessableEntity());
    publish(id);
    var entry = entry(id, account);
    transactions.executeWithoutResult(
        s -> {
          var e = entries.lock(entry).orElseThrow();
          e.setExpiresAt(Instant.now().minusSeconds(1));
        });
    mockMvc
        .perform(get(CLIENT + "/" + entry).header("Authorization", bearer(client)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(CLIENT).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(0));
  }

  @Test
  void concurrentReadersShareOneReceiptAndCancelledAbandonedEmailIsFinalized() throws Exception {
    transactions.executeWithoutResult(
        s -> {
          var owner = accounts.findById(account).orElseThrow().getOwner();
          owner.setEmailVerified(true);
          users.save(owner);
        });
    var id = create(draft("NOTICE", "SERVICE", List.of(account), true, false));
    publish(id);
    var entry = entry(id, account);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      var tasks = new ArrayList<java.util.concurrent.Callable<Integer>>();
      for (int i = 0; i < 4; i++)
        tasks.add(
            () ->
                mockMvc
                    .perform(
                        post(CLIENT + "/" + entry + "/read")
                            .header("Authorization", bearer(client)))
                    .andReturn()
                    .getResponse()
                    .getStatus());
      for (var result : pool.invokeAll(tasks)) assertThat(result.get()).isEqualTo(204);
    }
    assertThat(reads.countByNoticeId(entry)).isEqualTo(1);
    var claim = email.claimNotice(entry);
    assertThat(claim).isNotNull();
    mockMvc
        .perform(
            post(ADMIN + "/" + id + "/cancel")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "version",
                            publications.findById(id).orElseThrow().getVersion(),
                            "reason",
                            "Withdraw while in transport"))))
        .andExpect(status().isOk());
    transactions.executeWithoutResult(
        s ->
            org.springframework.test.util.ReflectionTestUtils.setField(
                entries.lock(entry).orElseThrow().getDelivery(),
                "claimedAt",
                Instant.now().minusSeconds(301)));
    assertThat(email.due(Instant.now(), 100)).contains(entry);
    assertThat(email.claimNotice(entry)).isNull();
    assertThat(entries.findById(entry).orElseThrow().getDelivery().getDelivery())
        .isEqualTo(com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.CANCELLED);
    email.completeNotice(claim, com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.SENT);
    assertThat(entries.findById(entry).orElseThrow().getDelivery().getDelivery())
        .isEqualTo(com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.CANCELLED);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"CANCELLED", "APPLIED"})
  void sourceBackfillIsIdempotentAndSharesLegacyReadStateWithoutTakingOverBusinessControls(
      String terminalState) throws Exception {
    var impact =
        new com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Impact(
            1,
            2,
            java.math.BigDecimal.TEN,
            "MAD",
            List.of(),
            List.of(),
            List.of(),
            List.of("platform.staff"),
            List.of());
    var notice =
        contentNotices.saveAndFlush(
            new com.hiveapp.platform.client.plan.domain.entity.PlanContentNotice(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                accounts.findById(account).orElseThrow(),
                com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Policy.EMAIL_REQUIRED,
                "Flex",
                1,
                2,
                com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.Timing.NOW,
                Instant.now(),
                impact,
                Instant.now()));
    backfill.backfill();
    backfill.backfill();
    var entry =
        entries
            .findBySourceAndSourceIdAndAccountId("PLAN_CONTENT", notice.getId(), account)
            .orElseThrow()
            .getId();
    mockMvc
        .perform(get(CLIENT + "/" + entry).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("kind").value("WARNING"));
    mockMvc
        .perform(post(CLIENT + "/" + entry + "/read").header("Authorization", bearer(client)))
        .andExpect(status().isNoContent());
    assertThat(reads.countByNoticeId(notice.getId())).isEqualTo(1);
    assertThat(contentNotices.findById(notice.getId()).orElseThrow().getState())
        .isEqualTo(com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.State.SCHEDULED);
    mockMvc
        .perform(
            post(ADMIN + "/entries/" + entry + "/retry-email")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isConflict());
    transactions.executeWithoutResult(
        s -> {
          contentNotices.lock(notice.getId()).orElseThrow().conflicted();
        });
    mockMvc
        .perform(get(CLIENT + "/" + entry).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("sourceState").value("CONFLICT"))
        .andExpect(jsonPath("canArchive").value(false));
    transactions.executeWithoutResult(
        s -> {
          var source = contentNotices.lock(notice.getId()).orElseThrow();
          if ("APPLIED".equals(terminalState)) source.applied(Instant.now());
          else source.cancel();
        });
    mockMvc
        .perform(get(CLIENT + "/" + entry).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("sourceState").value(terminalState))
        .andExpect(jsonPath("canAcknowledge").value(false))
        .andExpect(jsonPath("canArchive").value(true));
    mockMvc
        .perform(post(CLIENT + "/" + entry + "/archive").header("Authorization", bearer(client)))
        .andExpect(status().isNoContent());
  }
}
