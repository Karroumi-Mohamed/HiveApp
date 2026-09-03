package com.hiveapp.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementEndInstruction;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementPricingMode;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementSettlementMode;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.SpecialCommercialAgreementRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.CommercialOfferEffectSnapshot;
import com.hiveapp.platform.client.plan.dto.SpecialAgreementModels;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.service.SpecialAgreementLifecycleService;
import com.hiveapp.platform.client.plan.service.SubscriptionLifecycleManager;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class SpecialCommercialAgreementIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private SpecialCommercialAgreementRepository agreements;
    @Autowired private BillingInvoiceRepository invoices;
    @Autowired private BillingPaymentAttemptRepository payments;
    @Autowired private SubscriptionRepository subscriptions;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private SpecialAgreementLifecycleService agreementLifecycle;
    @Autowired private SubscriptionLifecycleManager subscriptionLifecycle;

    @Test
    void manualAgreementCreatesInvoiceWithoutProviderAndSchedulesAfterSettlement() throws Exception {
        UUID accountId = registerAccount();
        String admin = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3_600);
        Instant end = start.atZone(ZoneOffset.UTC).plusMonths(2).toInstant();
        var definition = definition(
                start, end, SpecialAgreementPricingMode.CUSTOM_TOTAL,
                new BigDecimal("37.50"), SpecialAgreementSettlementMode.MANUAL,
                SpecialAgreementEndInstruction.END_ACCESS);

        String previewToken = preview(admin, accountId, definition)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreedTermAmount").value("37.50"))
                .andExpect(jsonPath("$.settlementMode").value("MANUAL"))
                .andExpect(jsonPath("$.confirmable").value(true))
                .andReturn().getResponse().getContentAsString();
        previewToken = objectMapper.readTree(previewToken).get("previewToken").asText();
        long paymentCount = payments.count();

        String createdBody = mockMvc.perform(post(
                        "/api/admin/subscriptions/account/{accountId}/agreements", accountId)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SpecialAgreementModels.ConfirmRequest(
                        definition, previewToken, "Negotiated two-month onboarding agreement"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agreement.summary.status").value("AWAITING_SETTLEMENT"))
                .andExpect(jsonPath("$.checkout.gatewayAttemptStatus").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdBody);
        UUID agreementId = UUID.fromString(created.at("/agreement/summary/id").asText());
        UUID checkoutId = UUID.fromString(created.at("/checkout/id").asText());
        assertThat(payments.count()).isEqualTo(paymentCount);
        assertThat(invoices.findByCheckoutId(checkoutId)).get()
                .extracting(invoice -> invoice.getStatus())
                .isEqualTo(BillingInvoiceStatus.OPEN);

        mockMvc.perform(post("/api/admin/subscriptions/checkouts/{checkoutId}/confirm-manual", checkoutId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reference", "bank-" + checkoutId,
                                "reason", "Bank transfer reconciled"))))
                .andExpect(status().isOk());
        assertThat(payments.count()).isEqualTo(paymentCount + 1);

        mockMvc.perform(get(
                        "/api/admin/subscriptions/account/{accountId}/agreements/{agreementId}",
                        accountId, agreementId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.checkout.confirmationSource").value("MANUAL_OPERATOR"))
                .andExpect(jsonPath("$.checkout.confirmationReference").doesNotExist())
                .andExpect(jsonPath("$.checkout.gatewayReference").doesNotExist());
        assertThat(agreements.findById(agreementId)).get()
                .extracting(item -> item.getStatus().name())
                .isEqualTo("SCHEDULED");
        assertThat(auditLogs.findAllByResourceTypeAndResourceIdOrderByOccurredAtDesc(
                        "SPECIAL_COMMERCIAL_AGREEMENT", agreementId.toString()))
                .extracting(item -> item.getAction())
                .doesNotContain("platform.client.subscription.special_agreement.start_attempt");

        mockMvc.perform(get("/api/admin/subscriptions/agreements")
                        .header("Authorization", bearer(admin))
                        .param("search", "FREE")
                .param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')].accountId"
                        .formatted(agreementId)).value(accountId.toString()))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].attentionReason"
                        .formatted(agreementId)).doesNotExist());
        mockMvc.perform(get("/api/admin/subscriptions/agreements/analytics")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manualSettlement").isNumber())
                .andExpect(jsonPath("$.currencies[?(@.currencyCode == 'USD')].invoicedValue")
                        .exists())
                .andExpect(jsonPath("$.currencies[?(@.currencyCode == 'USD')].collectedValue")
                        .exists());
    }

    @Test
    void complimentaryAgreementCreatesSettledZeroInvoiceWithoutPayment() throws Exception {
        UUID accountId = registerAccount();
        String admin = loginAdminAndGetToken();
        Instant start = Instant.now().minusSeconds(1);
        Instant end = start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        var definition = definition(
                start, end, SpecialAgreementPricingMode.COMPLIMENTARY,
                null, SpecialAgreementSettlementMode.NONE,
                SpecialAgreementEndInstruction.RESTORE_PREVIOUS_TERMS);
        String body = preview(admin, accountId, definition)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreedTermAmount").value("0.00"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("previewToken").asText();
        long paymentCount = payments.count();

        String createdBody = mockMvc.perform(post(
                        "/api/admin/subscriptions/account/{accountId}/agreements", accountId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SpecialAgreementModels.ConfirmRequest(
                                definition, token, "Complimentary recovery month"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agreement.summary.status").value("ACTIVE"))
                .andExpect(jsonPath("$.checkout.confirmationSource").value("NO_PAYMENT_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdBody);
        UUID agreementId = UUID.fromString(created.at("/agreement/summary/id").asText());
        UUID checkoutId = UUID.fromString(created.at("/checkout/id").asText());

        assertThat(payments.count()).isEqualTo(paymentCount);
        assertThat(invoices.findByCheckoutId(checkoutId)).get()
                .extracting(invoice -> invoice.getStatus())
                .isEqualTo(BillingInvoiceStatus.SETTLED_ZERO);
        assertThat(auditLogs.findAllByResourceTypeAndResourceIdOrderByOccurredAtDesc(
                        "SPECIAL_COMMERCIAL_AGREEMENT", agreementId.toString()))
                .extracting(item -> item.getAction())
                .contains("platform.client.subscription.special_agreement.start_attempt");
    }

    @Test
    void unsettledAgreementCanBeCancelledAndItsAccountSlotReused() throws Exception {
        UUID accountId = registerAccount();
        String admin = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3_600);
        Instant end = start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        var paid = definition(
                start, end, SpecialAgreementPricingMode.CUSTOM_TOTAL,
                new BigDecimal("12.00"), SpecialAgreementSettlementMode.MANUAL,
                SpecialAgreementEndInstruction.END_ACCESS);
        String previewBody = preview(admin, accountId, paid)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String createdBody = mockMvc.perform(post(
                        "/api/admin/subscriptions/account/{accountId}/agreements", accountId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SpecialAgreementModels.ConfirmRequest(
                                        paid,
                                        objectMapper.readTree(previewBody)
                                                .get("previewToken").asText(),
                                        "Pending manual agreement to cancel"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdBody);
        UUID agreementId = UUID.fromString(created.at("/agreement/summary/id").asText());
        UUID checkoutId = UUID.fromString(created.at("/checkout/id").asText());

        mockMvc.perform(post(
                        "/api/admin/subscriptions/account/{accountId}/agreements/{agreementId}/cancel",
                        accountId, agreementId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "Negotiation was withdrawn before settlement"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("CANCELLED"));
        assertThat(invoices.findByCheckoutId(checkoutId)).get()
                .extracting(invoice -> invoice.getStatus())
                .isEqualTo(BillingInvoiceStatus.CANCELLED);

        var replacement = definition(
                start, end, SpecialAgreementPricingMode.COMPLIMENTARY,
                null, SpecialAgreementSettlementMode.NONE,
                SpecialAgreementEndInstruction.END_ACCESS);
        String replacementPreview = preview(admin, accountId, replacement)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post(
                        "/api/admin/subscriptions/account/{accountId}/agreements", accountId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SpecialAgreementModels.ConfirmRequest(
                                        replacement,
                                        objectMapper.readTree(replacementPreview)
                                                .get("previewToken").asText(),
                                        "Replacement terms after cancellation"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agreement.summary.status").value("SCHEDULED"));
    }

    @Test
    void endInstructionsEndAccessRestorePreviousAndRequireManualReview() throws Exception {
        String admin = loginAdminAndGetToken();
        UUID endAccount = createActiveComplimentary(
                admin, SpecialAgreementEndInstruction.END_ACCESS);
        UUID restoreAccount = createActiveComplimentary(
                admin, SpecialAgreementEndInstruction.RESTORE_PREVIOUS_TERMS);
        UUID reviewAccount = createActiveComplimentary(
                admin, SpecialAgreementEndInstruction.MANUAL_REVIEW);
        Instant cutoff = Instant.now().plus(40, java.time.temporal.ChronoUnit.DAYS);

        agreementLifecycle.processDue(cutoff);

        assertThat(subscriptions.findCurrentByAccountId(endAccount)).isEmpty();
        assertThat(subscriptions.findCurrentByAccountId(restoreAccount)).get()
                .extracting(subscription -> subscription.getStatus().name())
                .isEqualTo("ACTIVE");
        assertThat(subscriptions.findCurrentByAccountId(reviewAccount)).get()
                .satisfies(subscription -> {
                    assertThat(subscription.getStatus().name()).isEqualTo("SUSPENDED");
                    assertThat(subscription.getSuspensionCause().name())
                            .isEqualTo("AGREEMENT_REVIEW");
                });
        assertThat(agreements.findAllByAccountId(
                        endAccount, org.springframework.data.domain.Pageable.unpaged()).getContent())
                .singleElement().extracting(item -> item.getStatus().name())
                .isEqualTo("COMPLETED");
        assertThat(agreements.findAllByAccountId(
                        restoreAccount, org.springframework.data.domain.Pageable.unpaged()).getContent())
                .singleElement().extracting(item -> item.getStatus().name())
                .isEqualTo("COMPLETED");
        assertThat(agreements.findAllByAccountId(
                        reviewAccount, org.springframework.data.domain.Pageable.unpaged()).getContent())
                .singleElement().satisfies(item -> {
                    assertThat(item.getStatus().name()).isEqualTo("NEEDS_ATTENTION");
                    assertThat(item.getAttentionStage().name()).isEqualTo("END");
                });

        UUID reviewAgreementId = agreements.findAllByAccountId(
                        reviewAccount, org.springframework.data.domain.Pageable.unpaged()).getContent()
                .getFirst().getId();
        mockMvc.perform(post(
                        "/api/admin/subscriptions/account/{accountId}/agreements/{agreementId}/resolve-manual-review",
                        reviewAccount, reviewAgreementId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "Reviewed follow-up decision"))))
                .andExpect(status().isConflict());

        var reviewSubscription = subscriptions.findCurrentByAccountId(reviewAccount).orElseThrow();
        subscriptionLifecycle.cancelImmediately(reviewSubscription, Instant.now());
        subscriptions.saveAndFlush(reviewSubscription);

        mockMvc.perform(post(
                        "/api/admin/subscriptions/account/{accountId}/agreements/{agreementId}/resolve-manual-review",
                        reviewAccount, reviewAgreementId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "Customer requested access termination after review"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("COMPLETED"));
    }

    @Test
    void clientReadsOnlyOwnSafeAgreementTerms() throws Exception {
        String admin = loginAdminAndGetToken();
        AccountSession first = registerAccountSession();
        AccountSession second = registerAccountSession();
        createActiveComplimentaryFor(
                admin, first.accountId(), SpecialAgreementEndInstruction.END_ACCESS);
        createActiveComplimentaryFor(
                admin, second.accountId(), SpecialAgreementEndInstruction.END_ACCESS);

        String body = mockMvc.perform(get("/api/v1/subscriptions/agreements")
                        .header("Authorization", bearer(first.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].planCode").value("FREE"))
                .andExpect(jsonPath("$.content[0].agreedTermAmount").value("0.00"))
                .andExpect(jsonPath("$.content[0].settlementMode").doesNotExist())
                .andExpect(jsonPath("$.content[0].reason").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdByUserId").doesNotExist())
                .andExpect(jsonPath("$.content[0].quotaBonuses").doesNotExist())
                .andExpect(jsonPath("$.content[0].attentionReason").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(second.accountId().toString());
        assertThat(body).doesNotContain(ADMIN_EMAIL);
    }

    @Test
    void previewBindsEveryTermAndRejectsAnAlteredConfirmation() throws Exception {
        UUID accountId = registerAccount();
        String admin = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3_600);
        Instant end = start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        var reviewed = definition(
                start, end, SpecialAgreementPricingMode.CUSTOM_TOTAL,
                new BigDecimal("15.00"), SpecialAgreementSettlementMode.MANUAL,
                SpecialAgreementEndInstruction.END_ACCESS);
        String token = objectMapper.readTree(preview(admin, accountId, reviewed)
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("previewToken").asText();
        var altered = definition(
                start, end, SpecialAgreementPricingMode.CUSTOM_TOTAL,
                new BigDecimal("5.00"), SpecialAgreementSettlementMode.MANUAL,
                SpecialAgreementEndInstruction.END_ACCESS);

        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}/agreements", accountId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SpecialAgreementModels.ConfirmRequest(
                                        altered, token, "This altered amount was not reviewed"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_SPECIAL_AGREEMENT_PREVIEW"));
        assertThat(agreements.findAllByAccountId(
                accountId, org.springframework.data.domain.Pageable.unpaged())).isEmpty();
    }

    @Test
    void privateQuotaBonusIsValidatedAndIncludedInTheReviewedTerm() throws Exception {
        UUID accountId = registerAccount();
        String admin = loginAdminAndGetToken();
        Instant start = Instant.now().plusSeconds(3_600);
        Instant end = start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        var base = definition(
                start, end, SpecialAgreementPricingMode.COMPLIMENTARY,
                null, SpecialAgreementSettlementMode.NONE,
                SpecialAgreementEndInstruction.END_ACCESS);
        var withBonus = new SpecialAgreementModels.Definition(
                base.selection(),
                List.of(new CommercialOfferEffectSnapshot.QuotaBonus(
                        "platform.staff", "members", 5)),
                base.startsAt(), base.endsAt(), base.pricingMode(), base.customTotal(),
                base.currencyCode(), base.settlementMode(), base.endInstruction(),
                base.followOnPricingMode(), base.followOnCustomAmount());

        preview(admin, accountId, withBonus)
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.termEntitlements.effectiveQuotaLimits[?(@.featureCode == 'platform.staff' && @.resource == 'members')].effectiveLimit")
                        .value(8));

        var unknownBonus = new SpecialAgreementModels.Definition(
                base.selection(),
                List.of(new CommercialOfferEffectSnapshot.QuotaBonus(
                        "platform.staff", "unknown-capacity", 5)),
                base.startsAt(), base.endsAt(), base.pricingMode(), base.customTotal(),
                base.currencyCode(), base.settlementMode(), base.endInstruction(),
                base.followOnPricingMode(), base.followOnCustomAmount());
        preview(admin, accountId, unknownBonus)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void continuationInstallsTheReviewedRecurringAmount() throws Exception {
        UUID accountId = registerAccount();
        String admin = loginAdminAndGetToken();
        Instant start = Instant.now().minusSeconds(1);
        Instant end = start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        var definition = new SpecialAgreementModels.Definition(
                new SubscriptionChangeRequest("FREE", Set.of(), List.of()),
                List.of(), start, end, SpecialAgreementPricingMode.COMPLIMENTARY,
                null, "USD", SpecialAgreementSettlementMode.NONE,
                SpecialAgreementEndInstruction.CONTINUE_REVIEWED_TERMS,
                SpecialAgreementPricingMode.CUSTOM_TOTAL, new BigDecimal("4.00"));
        String previewBody = preview(admin, accountId, definition)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followOnAmount").value("4.00"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}/agreements", accountId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SpecialAgreementModels.ConfirmRequest(
                                        definition,
                                        objectMapper.readTree(previewBody).get("previewToken").asText(),
                                        "Continue on reviewed recurring terms"))))
                .andExpect(status().isCreated());

        agreementLifecycle.processDue(Instant.now().plus(40, java.time.temporal.ChronoUnit.DAYS));

        assertThat(subscriptions.findCurrentByAccountId(accountId)).get()
                .satisfies(subscription -> {
                    assertThat(subscription.getStatus().name()).isEqualTo("ACTIVE");
                    assertThat(subscription.currentMoney().amount())
                            .isEqualByComparingTo("4.00");
                });
        assertThat(agreements.findAllByAccountId(
                        accountId, org.springframework.data.domain.Pageable.unpaged()).getContent())
                .singleElement().satisfies(item -> {
                    assertThat(item.getStatus().name()).isEqualTo("COMPLETED");
                    assertThat(auditLogs.findAllByResourceTypeAndResourceIdOrderByOccurredAtDesc(
                                    "SPECIAL_COMMERCIAL_AGREEMENT", item.getId().toString()))
                            .extracting(log -> log.getAction())
                            .contains("platform.client.subscription.special_agreement.end_attempt");
                });
    }

    private UUID createActiveComplimentary(
            String admin, SpecialAgreementEndInstruction instruction) throws Exception {
        UUID accountId = registerAccount();
        createActiveComplimentaryFor(admin, accountId, instruction);
        return accountId;
    }

    private void createActiveComplimentaryFor(
            String admin, UUID accountId, SpecialAgreementEndInstruction instruction)
            throws Exception {
        Instant start = Instant.now().minusSeconds(1);
        Instant end = start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        var definition = definition(
                start, end, SpecialAgreementPricingMode.COMPLIMENTARY,
                null, SpecialAgreementSettlementMode.NONE, instruction);
        String previewBody = preview(admin, accountId, definition)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}/agreements", accountId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SpecialAgreementModels.ConfirmRequest(
                                        definition,
                                        objectMapper.readTree(previewBody).get("previewToken").asText(),
                                        "End instruction integration coverage"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agreement.summary.status").value("ACTIVE"));
    }

    private org.springframework.test.web.servlet.ResultActions preview(
            String token, UUID accountId, SpecialAgreementModels.Definition definition) throws Exception {
        return mockMvc.perform(post(
                        "/api/admin/subscriptions/account/{accountId}/agreements/preview", accountId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SpecialAgreementModels.PreviewRequest(definition))));
    }

    private SpecialAgreementModels.Definition definition(
            Instant start,
            Instant end,
            SpecialAgreementPricingMode pricing,
            BigDecimal total,
            SpecialAgreementSettlementMode settlement,
            SpecialAgreementEndInstruction endInstruction) {
        return new SpecialAgreementModels.Definition(
                new SubscriptionChangeRequest("FREE", Set.of(), List.of()),
                List.of(), start, end, pricing, total, "USD", settlement,
                endInstruction, null, null);
    }

    private UUID registerAccount() throws Exception {
        return registerAccountSession().accountId();
    }

    private AccountSession registerAccountSession() throws Exception {
        String email = "agreement-" + UUID.randomUUID() + "@example.com";
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, CLIENT_PASSWORD, "Special", "Account", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID accountId = accounts.findByOwner_Id(users.findByEmail(email).orElseThrow().getId())
                .orElseThrow().getId();
        return new AccountSession(accountId, objectMapper.readTree(response).get("accessToken").asText());
    }

    private record AccountSession(UUID accountId, String token) {}
}
