package com.hiveapp.platform.security;

import static com.hiveapp.platform.communication.CommunicationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.communication.*;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(
    properties = "spring.datasource.url=jdbc:h2:mem:notification-system;DB_CLOSE_DELAY=-1")
class NotificationSystemIntegrationTest extends PlatformShellIntegrationTestSupport {
  @Autowired NotificationPublisher publisher;
  @Autowired NotificationWorker worker;
  @Autowired NotificationEventRepository events;
  @Autowired CommunicationEntryRepository entries;
  @Autowired AccountRepository accounts;
  @Autowired MemberRepository members;
  @Autowired TransactionTemplate transactions;
  @Autowired CommunicationEmailSource email;
  @Autowired NotificationPreferenceRepository preferences;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired com.hiveapp.platform.registry.domain.repository.FeatureRepository features;
  String client, other, admin;
  UUID account, otherAccount, owner;
  static final String CLIENT = "/api/v1/communications", ADMIN = "/api/admin/notifications";

  @BeforeEach
  void setup() throws Exception {
    client = registerClientAndGetToken();
    other = registerClientAndGetToken();
    admin = loginAdminAndGetToken();
    account = account(client);
    otherAccount = account(other);
    owner = transactions.execute(tx -> accounts.findById(account).orElseThrow().getOwner().getId());
  }

  UUID account(String token) throws Exception {
    return UUID.fromString(
        objectMapper
            .readTree(
                mockMvc
                    .perform(get("/api/v1/accounts/me").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText());
  }

  UUID publish(
      NotificationDefinition type,
      String occurrence,
      NotificationPublisher.Target target,
      UUID resource) {
    return transactions.execute(
        tx ->
            publisher.publish(
                type,
                occurrence,
                target,
                resource,
                "Information",
                "Safe notification content",
                false,
                null,
                null));
  }

  UUID deliver(UUID event) {
    worker.deliver(event);
    return entries.findByEventId(event).orElseThrow().getId();
  }

  @Test
  void sourceCancellationWithdrawsBothDeliveredAndPendingWarningsWithoutPaymentReceipt() throws Exception {
    UUID invoice = UUID.randomUUID();
    UUID clientEvent = publish(CoreNotification.PAYMENT_FAILED, "cancel", NotificationPublisher.Target.account(account), invoice);
    UUID operatorEvent = publish(CoreNotification.BILLING_ATTENTION, "cancel", NotificationPublisher.Target.platform(), invoice);
    UUID clientEntry = deliver(clientEvent);
    long count = events.count();
    transactions.executeWithoutResult(tx -> {
      publisher.withdraw(CoreNotification.PAYMENT_FAILED, invoice);
      publisher.withdraw(CoreNotification.BILLING_ATTENTION, invoice);
    });
    UUID operatorEntry = deliver(operatorEvent);
    assertThat(entries.findById(clientEntry).orElseThrow().isCancelled()).isTrue();
    assertThat(entries.findById(operatorEntry).orElseThrow().isCancelled()).isTrue();
    assertThat(events.count()).isEqualTo(count);
    mockMvc.perform(get(CLIENT + "/" + clientEntry).header("Authorization", bearer(client)))
        .andExpect(status().isOk()).andExpect(jsonPath("sourceState").value("CANCELLED"))
        .andExpect(jsonPath("canAcknowledge").value(false));
    var claim = transactions.execute(tx -> email.claimNotice(clientEntry));
    assertThat(claim).isNull();
  }

  @Test
  void expiredEmailsBecomeTerminalEvenDuringBackoffOrAfterAnAbandonedLease() {
    for (boolean claimed : List.of(false, true)) {
      UUID event = transactions.execute(tx -> publisher.publish(CoreNotification.INTERNAL_INFORMATION,
          UUID.randomUUID().toString(), NotificationPublisher.Target.account(account), null,
          "Expired", "Do not deliver", true, null, null));
      UUID id = deliver(event);
      Instant now = Instant.now();
      transactions.executeWithoutResult(tx -> {
        var entry = entries.findById(id).orElseThrow();
        entry.setAvailableAt(now.minusSeconds(2000));
        entry.setExpiresAt(now.minusSeconds(1));
        entry.setNextEmailAttemptAt(now.plusSeconds(3600));
        if (claimed) entry.getDelivery().claim(owner, now.minusSeconds(1000));
      });
      assertThat(email.due(now, 100)).contains(id);
      assertThat(email.claimNotice(id)).isNull();
      assertThat(entries.findById(id).orElseThrow().getDelivery().getDelivery())
          .isEqualTo(com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery.CANCELLED);
      assertThat(email.due(now, 100)).doesNotContain(id);
    }
  }

  @Test
  void internalSentHistoryAndSenderOriginAreScopedAndMatchDelivery() throws Exception {
    UUID member = transactions.execute(tx -> members.findByAccountIdAndUserId(account, owner).orElseThrow().getId());
    UUID command = UUID.randomUUID();
    mockMvc.perform(post(CLIENT + "/internal").header("Authorization", bearer(client))
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
            new InternalNotice(command, "Internal announcement", "Reviewed content", List.of(member)))))
        .andExpect(status().isAccepted());
    mockMvc.perform(get(CLIENT + "/internal").header("Authorization", bearer(client)))
        .andExpect(status().isOk()).andExpect(jsonPath("content[0].pending").value(1));
    var event = events.findAll().stream().filter(e -> command.equals(e.getResourceId())).findFirst().orElseThrow();
    UUID id = deliver(event.getId());
    mockMvc.perform(get(CLIENT + "/" + id).header("Authorization", bearer(client)))
        .andExpect(status().isOk()).andExpect(jsonPath("senderName").isNotEmpty());
    mockMvc.perform(get(CLIENT + "/internal").header("Authorization", bearer(client)))
        .andExpect(status().isOk()).andExpect(jsonPath("content[0].delivered").value(1))
        .andExpect(jsonPath("content[0].pending").value(0));
    mockMvc.perform(get(CLIENT + "/internal").header("Authorization", bearer(other)))
        .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
  }

  @Test
  void domainRollbackLeavesNoNotificationIntentOrInboxEntry() {
    long before = events.count();
    transactions.executeWithoutResult(
        tx -> {
          publisher.publish(
              CoreNotification.INTERNAL_INFORMATION,
              UUID.randomUUID().toString(),
              NotificationPublisher.Target.account(account),
              null,
              "Information",
              "Rolled back",
              false,
              null,
              null);
          tx.setRollbackOnly();
        });
    assertThat(events.count()).isEqualTo(before);
  }

  @Test
  void concurrentProducersAndWorkersCreateExactlyOneDurableEntry() throws Exception {
    String occurrence = UUID.randomUUID().toString();
    try (var pool = Executors.newFixedThreadPool(4)) {
      var calls = new ArrayList<Callable<UUID>>();
      for (int i = 0; i < 4; i++)
        calls.add(
            () ->
                publish(
                    CoreNotification.INTERNAL_INFORMATION,
                    occurrence,
                    NotificationPublisher.Target.account(account),
                    null));
      var ids = new HashSet<UUID>();
      for (var f : pool.invokeAll(calls)) ids.add(f.get());
      assertThat(ids).hasSize(1);
      UUID event = ids.iterator().next();
      var deliveries = new ArrayList<Callable<Void>>();
      for (int i = 0; i < 4; i++)
        deliveries.add(
            () -> {
              worker.deliver(event);
              return null;
            });
      for (var f : pool.invokeAll(deliveries)) f.get();
      assertThat(entries.findByEventId(event)).isPresent();
      assertThat(events.findById(event).orElseThrow().getState())
          .isEqualTo(NotificationEvent.State.DELIVERED);
    }
  }

  @Test
  void reusingOccurrenceForDifferentContentIsRejected() {
    String occurrence = UUID.randomUUID().toString();
    publish(
        CoreNotification.INTERNAL_INFORMATION,
        occurrence,
        NotificationPublisher.Target.account(account),
        null);
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    tx ->
                        publisher.publish(
                            CoreNotification.INTERNAL_INFORMATION,
                            occurrence,
                            NotificationPublisher.Target.account(account),
                            null,
                            "Changed",
                            "Different",
                            false,
                            null,
                            null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void personalNotificationsDoNotBecomeAnAccountBroadcastEvenForOwner() throws Exception {
    UUID member = createOrdinaryMember(client);
    UUID user =
        transactions.execute(tx -> members.findById(member).orElseThrow().getUser().getId());
    UUID id =
        deliver(
            publish(
                CoreNotification.INTERNAL_INFORMATION,
                UUID.randomUUID().toString(),
                NotificationPublisher.Target.member(account, user),
                null));
    mockMvc
        .perform(get(CLIENT + "/" + id).header("Authorization", bearer(client)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(CLIENT + "/" + id).header("Authorization", bearer(other)))
        .andExpect(status().isNotFound());
    UUID mine =
        deliver(
            publish(
                CoreNotification.INTERNAL_INFORMATION,
                UUID.randomUUID().toString(),
                NotificationPublisher.Target.member(account, owner),
                null));
    mockMvc
        .perform(get(CLIENT + "/" + mine).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("audience").value("MEMBER"))
        .andExpect(jsonPath("canReply").doesNotExist());
  }

  @Test
  void internalSendIsIdempotentAndCannotTargetAnotherAccount() throws Exception {
    UUID me = currentMemberId(client), outsider = currentMemberId(other);
    var command =
        Map.of(
            "commandId",
            UUID.randomUUID(),
            "messageTitle",
            "Internal information",
            "messageBody",
            "Team schedule",
            "memberIds",
            List.of(me));
    long before = events.count();
    for (int i = 0; i < 2; i++)
      mockMvc
          .perform(
              post(CLIENT + "/internal")
                  .header("Authorization", bearer(client))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(command)))
          .andExpect(status().isAccepted());
    assertThat(events.count()).isEqualTo(before + 1);
    var invalid =
        Map.of(
            "commandId",
            UUID.randomUUID(),
            "messageTitle",
            "Internal information",
            "messageBody",
            "No external broadcast",
            "memberIds",
            List.of(outsider));
    mockMvc
        .perform(
            post(CLIENT + "/internal")
                .header("Authorization", bearer(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(CLIENT + "/recipients").header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(1));
    UUID second = createOrdinaryMember(client);
    var widened = new HashMap<String, Object>(command);
    widened.put("memberIds", List.of(me, second));
    mockMvc
        .perform(
            post(CLIENT + "/internal")
                .header("Authorization", bearer(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(widened)))
        .andExpect(status().isBadRequest());
    var changed = new HashMap<String, Object>(command);
    changed.put("messageBody", "Changed content");
    mockMvc
        .perform(
            post(CLIENT + "/internal")
                .header("Authorization", bearer(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changed)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void platformInboxIsSeparateAndDomainWarningsResolveOnlyFromTheirSource() throws Exception {
    UUID invoice = UUID.randomUUID();
    UUID event =
        publish(
            CoreNotification.BILLING_ATTENTION,
            UUID.randomUUID().toString(),
            NotificationPublisher.Target.platform(),
            invoice);
    UUID id = deliver(event);
    mockMvc
        .perform(get(ADMIN + "/" + id).header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("actionPath").value("/admin/billing/invoices/" + invoice));
    mockMvc
        .perform(get(ADMIN + "/" + id).header("Authorization", bearer(client)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get(CLIENT + "/" + id).header("Authorization", bearer(client)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post(ADMIN + "/" + id + "/acknowledge").header("Authorization", bearer(admin)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(post(ADMIN + "/" + id + "/archive").header("Authorization", bearer(admin)))
        .andExpect(status().isConflict());
    transactions.executeWithoutResult(
        tx -> publisher.resolve(CoreNotification.BILLING_ATTENTION, invoice));
    mockMvc
        .perform(get(ADMIN + "/" + id).header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("resolved").value(true))
        .andExpect(jsonPath("canAcknowledge").value(false));
    mockMvc
        .perform(post(ADMIN + "/" + id + "/archive").header("Authorization", bearer(admin)))
        .andExpect(status().isNoContent());
  }

  @Test
  void resolutionBeforeWorkerDeliveryIsNotLost() throws Exception {
    UUID resource = UUID.randomUUID();
    UUID event =
        publish(
            CoreNotification.BILLING_ATTENTION,
            UUID.randomUUID().toString(),
            NotificationPublisher.Target.platform(),
            resource);
    transactions.executeWithoutResult(
        tx -> publisher.resolve(CoreNotification.BILLING_ATTENTION, resource));
    UUID id = deliver(event);
    mockMvc
        .perform(get(ADMIN + "/" + id).header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("resolved").value(true));
  }

  @Test
  void emailOnlyOptionalNoticeHasAnAuthorizedDirectLinkButIsAbsentFromTheFeed() throws Exception {
    transactions.executeWithoutResult(tx -> accounts.findById(account).orElseThrow().getOwner().setEmailVerified(true));
    UUID event = transactions.execute(tx -> publisher.publish(CoreNotification.INTERNAL_INFORMATION,
        UUID.randomUUID().toString(), NotificationPublisher.Target.member(account, owner), null,
        "Information", "Optional information", true, null, null));
    UUID id = deliver(event);
    mockMvc.perform(put(CLIENT + "/settings").header("Authorization", bearer(client))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"topic\":\"ACCOUNT\",\"inAppEnabled\":false,\"emailEnabled\":true}"))
        .andExpect(status().isOk());
    var claim = email.claimNotice(id);
    assertThat(claim).isNotNull();
    assertThat(claim.body()).contains("/app/communications?item=" + id);
    mockMvc.perform(get(CLIENT + "/" + id).header("Authorization", bearer(client))).andExpect(status().isOk());
    mockMvc.perform(get(CLIENT + "/" + id).header("Authorization", bearer(other))).andExpect(status().isNotFound());
    mockMvc.perform(get(CLIENT).param("topic", "ACCOUNT").header("Authorization", bearer(client)))
        .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
    assertThat(CoreNotification.PAYMENT_FAILED.actionPath(UUID.randomUUID()))
        .startsWith("/app/subscription?tab=invoices&invoice=").doesNotContain("/document");
  }

  @Test
  void optionalPreferencesDoNotMuteRequiredWarnings() throws Exception {
    UUID optional =
        deliver(
            publish(
                CoreNotification.INTERNAL_INFORMATION,
                UUID.randomUUID().toString(),
                NotificationPublisher.Target.member(account, owner),
                null));
    mockMvc
        .perform(
            put(CLIENT + "/settings")
                .header("Authorization", bearer(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topic\":\"ACCOUNT\",\"inAppEnabled\":false,\"emailEnabled\":false}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(CLIENT + "/" + optional).header("Authorization", bearer(client)))
        .andExpect(status().isOk());
    mockMvc.perform(get(CLIENT).param("topic", "ACCOUNT").header("Authorization", bearer(client)))
        .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
    mockMvc
        .perform(
            put(CLIENT + "/settings")
                .header("Authorization", bearer(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topic\":\"BILLING\",\"inAppEnabled\":false,\"emailEnabled\":false}"))
        .andExpect(status().isOk());
    UUID warning =
        deliver(
            publish(
                CoreNotification.PAYMENT_FAILED,
                UUID.randomUUID().toString(),
                NotificationPublisher.Target.account(account),
                UUID.randomUUID()));
    mockMvc
        .perform(get(CLIENT + "/" + warning).header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("kind").value("WARNING"));
    mockMvc
        .perform(get(CLIENT).param("topic", "BILLING").header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(1));
  }

  @Test
  void revokedMembershipAndDisabledSourceFeatureHideExistingItems() throws Exception {
    UUID id =
        deliver(
            publish(
                CoreNotification.MEMBER_CREATED,
                UUID.randomUUID().toString(),
                NotificationPublisher.Target.account(account),
                UUID.randomUUID()));
    var feature = features.findByCode("platform.staff").orElseThrow();
    boolean enabled = feature.isRuntimeEnabled();
    try {
      transactions.executeWithoutResult(
          tx -> {
            var f = features.findById(feature.getId()).orElseThrow();
            f.setRuntimeEnabled(false);
          });
      mockMvc
          .perform(get(CLIENT + "/" + id).header("Authorization", bearer(client)))
          .andExpect(status().isNotFound());
      mockMvc
          .perform(get(CLIENT).header("Authorization", bearer(client)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("totalElements").value(0));
    } finally {
      transactions.executeWithoutResult(
          tx -> features.findById(feature.getId()).orElseThrow().setRuntimeEnabled(enabled));
    }
    UUID member = currentMemberId(client);
    transactions.executeWithoutResult(
        tx -> members.findById(member).orElseThrow().setActive(false));
    mockMvc
        .perform(get(CLIENT + "/" + id).header("Authorization", bearer(client)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownDefinitionAndInvalidRecipientScopeFailClosed() {
    var fake =
        new NotificationDefinition() {
          public String key() {
            return "unknown.task";
          }

          public Topic topic() {
            return Topic.TASKS;
          }

          public Kind kind() {
            return Kind.ACTION;
          }

          public dev.karroumi.permissionizer.Permission requiredPermission() {
            return null;
          }
        };
    assertThatThrownBy(
            () ->
                publish(
                    fake, "once", NotificationPublisher.Target.account(account), UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                publish(
                    CoreNotification.INTERNAL_INFORMATION,
                    "wrong-account",
                    NotificationPublisher.Target.member(otherAccount, owner),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void automaticEmailOperationsAreVersionedMetadataOnlyAndDoNotBypassLiveRecipientChecks()
      throws Exception {
    UUID event =
        transactions.execute(
            tx ->
                publisher.publish(
                    CoreNotification.PAYMENT_FAILED,
                    UUID.randomUUID().toString(),
                    NotificationPublisher.Target.account(account),
                    UUID.randomUUID(),
                    "Private title",
                    "Private body",
                    true,
                    null,
                    null));
    UUID id = deliver(event);
    transactions.executeWithoutResult(
        tx -> entries.findById(id).orElseThrow().getDelivery().fail());
    var e = entries.findById(id).orElseThrow();
    var result =
        mockMvc
            .perform(
                get(ADMIN + "/delivery/emails")
                    .param("state", "FAILED")
                    .header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(result).contains(id.toString(), "billing.payment_failed");
    assertThat(result)
        .doesNotContain("Private title", "Private body", "messageBody", "recipientUserId");
    mockMvc
        .perform(get(ADMIN + "/delivery/emails").header("Authorization", bearer(client)))
        .andExpect(status().isForbidden());
    String payload = objectMapper.writeValueAsString(new Command(e.getVersion(), "SMTP recovered"));
    mockMvc
        .perform(
            post(ADMIN + "/delivery/emails/" + id + "/retry")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post(ADMIN + "/delivery/emails/" + id + "/retry")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isConflict());
    // Revoke verification after the retry: it cannot bypass live delivery checks.
    transactions.executeWithoutResult(
        tx -> accounts.findById(account).orElseThrow().getOwner().setEmailVerified(false));
    assertThat(email.claimNotice(id)).isNull();
    assertThat(entries.findById(id).orElseThrow().getDelivery().getDelivery().name())
        .isEqualTo("SUPPRESSED");
    transactions.executeWithoutResult(
        tx -> entries.findById(id).orElseThrow().setResolvedAt(Instant.now()));
    var resolved = entries.findById(id).orElseThrow();
    mockMvc
        .perform(
            post(ADMIN + "/delivery/emails/" + id + "/retry")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new Command(resolved.getVersion(), "Cannot retry resolved warning"))))
        .andExpect(status().isConflict());
  }

  @Test
  void companyScopedNotificationRequiresThatOwnCompanyContext() throws Exception {
    UUID company =
        UUID.fromString(createCompany(client, "Notification Company").get("id").asText());
    UUID event =
        publish(
            CoreNotification.INTERNAL_INFORMATION,
            UUID.randomUUID().toString(),
            new NotificationPublisher.Target(Audience.ACCOUNT, account, null, company),
            null);
    UUID id = deliver(event);
    mockMvc
        .perform(get(CLIENT + "/" + id).header("Authorization", bearer(client)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get(CLIENT + "/" + id)
                .header("Authorization", bearer(client))
                .header("X-Company-ID", company.toString()))
        .andExpect(status().isOk());
    assertThatThrownBy(
            () ->
                publish(
                    CoreNotification.INTERNAL_INFORMATION,
                    "foreign-company",
                    new NotificationPublisher.Target(Audience.ACCOUNT, otherAccount, null, company),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolvedAndPermissionRevokedWarningsAreNotEmailedButPreferencesCannotMuteRequiredWarnings()
      throws Exception {
    transactions.executeWithoutResult(
        tx -> accounts.findById(account).orElseThrow().getOwner().setEmailVerified(true));
    UUID resource = UUID.randomUUID();
    UUID event =
        transactions.execute(
            tx ->
                publisher.publish(
                    CoreNotification.PAYMENT_FAILED,
                    UUID.randomUUID().toString(),
                    NotificationPublisher.Target.account(account),
                    resource,
                    "Payment failed",
                    "Review billing",
                    true,
                    null,
                    null));
    UUID id = deliver(event);
    mockMvc
        .perform(
            put(CLIENT + "/settings")
                .header("Authorization", bearer(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topic\":\"BILLING\",\"inAppEnabled\":false,\"emailEnabled\":false}"))
        .andExpect(status().isOk());
    assertThat(email.claimNotice(id)).isNotNull();
    UUID resolved =
        transactions.execute(
            tx ->
                publisher.publish(
                    CoreNotification.PAYMENT_FAILED,
                    UUID.randomUUID().toString(),
                    NotificationPublisher.Target.account(account),
                    resource,
                    "Payment failed",
                    "Review billing",
                    true,
                    null,
                    null));
    UUID resolvedId = deliver(resolved);
    transactions.executeWithoutResult(
        tx -> publisher.resolve(CoreNotification.PAYMENT_FAILED, resource));
    assertThat(email.claimNotice(resolvedId)).isNull();
    UUID revoked =
        transactions.execute(
            tx ->
                publisher.publish(
                    CoreNotification.PAYMENT_FAILED,
                    UUID.randomUUID().toString(),
                    NotificationPublisher.Target.account(account),
                    UUID.randomUUID(),
                    "Payment failed",
                    "Review billing",
                    true,
                    null,
                    null));
    UUID revokedId = deliver(revoked);
    UUID member = currentMemberId(client);
    transactions.executeWithoutResult(
        tx -> members.findById(member).orElseThrow().setActive(false));
    assertThat(email.claimNotice(revokedId)).isNull();
  }

  @Test
  void deliveryFailureBacksOffAndRequiresVersionedOperatorRetry() throws Exception {
    UUID event =
        publish(
            CoreNotification.INTERNAL_INFORMATION,
            UUID.randomUUID().toString(),
            NotificationPublisher.Target.account(account),
            null);
    jdbc.update(
        "update notification_events set definition_key=? where id=?", "unregistered.event", event);
    for (int i = 0; i < 5; i++) {
      transactions.executeWithoutResult(
          tx -> events.findById(event).orElseThrow().setNextAttemptAt(Instant.EPOCH));
      worker.dispatch();
    }
    var failed = events.findById(event).orElseThrow();
    assertThat(failed.getState()).isEqualTo(NotificationEvent.State.FAILED);
    assertThat(failed.getAttempts()).isEqualTo(5);
    assertThat(failed.getNextAttemptAt()).isAfter(Instant.now());
    mockMvc
        .perform(
            post(ADMIN + "/delivery/" + event + "/retry")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("version", failed.getVersion() - 1, "reason", "Retry reviewed"))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post(ADMIN + "/delivery/" + event + "/retry")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("version", failed.getVersion(), "reason", "Retry reviewed"))))
        .andExpect(status().isNoContent());
    assertThat(events.findById(event).orElseThrow().getState())
        .isEqualTo(NotificationEvent.State.PENDING);
  }
}
