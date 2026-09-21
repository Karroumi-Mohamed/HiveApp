package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyDecisionOutcome;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicySource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyRequests;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.service.CommercialPolicyEvaluator;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
class CommercialPolicySubscriptionIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionChangeOperationRepository changeOperationRepository;
    @Autowired private CommercialPolicyRepository policyRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private AddOnRepository addOnRepository;
    @Autowired private QuotaPackageRepository quotaPackageRepository;
    @Autowired private CommercialPolicyEvaluator policyEvaluator;
    @Autowired private SubscriptionService subscriptionService;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void clientCatalogAndExplicitApplyExposeOnlyPrivacySafeAcceptedPolicyTerms() throws Exception {
        String adminToken = loginAdminAndGetToken();
        String email = "policy-client-" + UUID.randomUUID() + "@example.com";
        JsonNode registration = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, CLIENT_PASSWORD, "Policy", "Client", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String clientToken = registration.get("accessToken").asText();
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        Account account = accountRepository.findByOwner_Id(userId).orElseThrow();
        var subscription = subscriptionRepository.findUsableByAccountId(account.getId()).orElseThrow();
        String currentPlanCode = subscription.getEntitlementSnapshot().planCode();

        JsonNode draft = createPolicy(adminToken, policyRequest(
                "Client reviewed price", account.getId(), 30,
                List.of(fixedPrice(BigDecimal.ZERO,
                        subscription.getCurrentPriceCurrencyCode(),
                        subscription.getEntitlementSnapshot().billingCycle()))));
        UUID policyId = UUID.fromString(draft.at("/summary/id").asText());
        activate(adminToken, policyId, "Enable client review");
        JsonNode lowerDraft = createPolicy(adminToken, policyRequest(
                "Internal fallback price", account.getId(), 1,
                List.of(fixedPrice(BigDecimal.ONE,
                        subscription.getCurrentPriceCurrencyCode(),
                        subscription.getEntitlementSnapshot().billingCycle()))));
        UUID lowerPolicyId = UUID.fromString(lowerDraft.at("/summary/id").asText());
        activate(adminToken, lowerPolicyId, "Enable fallback review");

        JsonNode catalog = objectMapper.readTree(mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(catalog.at("/commercialPolicyDecisions/0/effectType").asText())
                .isEqualTo("FIXED_SUBSCRIPTION_PRICE");
        assertClientPolicyPrivacy(catalog);

        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                currentPlanCode, Set.of(), List.of(),
                SubscriptionChangeTiming.IMMEDIATE, null);
        JsonNode preview = objectMapper.readTree(mockMvc.perform(post("/api/v1/subscriptions/preview")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertClientPolicyPrivacy(preview);
        JsonNode appliedResponse = objectMapper.readTree(mockMvc.perform(post("/api/v1/subscriptions/apply")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubscriptionChangeApplyRequest(
                                request, preview.get("previewToken").asText()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertClientPolicyPrivacy(appliedResponse);

        JsonNode clientHistory = objectMapper.readTree(mockMvc.perform(get("/api/v1/subscriptions/changes")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertClientPolicyPrivacy(clientHistory);

        JsonNode adminHistory = objectMapper.readTree(mockMvc.perform(get(
                                "/api/admin/subscriptions/account/{accountId}/changes", account.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(adminHistory.findValues("policyId").stream().map(JsonNode::asText).toList())
                .contains(policyId.toString(), lowerPolicyId.toString());
        assertThat(adminHistory.findValue("activationId").asText()).isNotBlank();
        assertThat(adminHistory.findValue("effectId").asText()).isNotBlank();
        assertThat(adminHistory.findValues("outcome").stream().map(JsonNode::asText).toList())
                .contains("APPLIED", "REJECTED_LOWER_PRECEDENCE");

        var accepted = subscriptionRepository.findUsableByAccountId(account.getId())
                .orElseThrow().getEntitlementSnapshot().commercialPolicyEvaluation();
        assertThat(accepted.finalRecurringPrice()).isEqualByComparingTo("0.00");
        assertThat(accepted.decisions()).extracting(decision -> decision.policyId()).contains(policyId);
    }

    @Test
    void explicitApplyAcceptsZeroPriceGrantsAndPreservesTheirProvenanceAfterPause() throws Exception {
        String token = loginAdminAndGetToken();
        UUID actorId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        Account account = registerAccount("policy-grants");
        var targetPlan = planRepository.findByCode("FLEX").orElseThrow();
        var addOn = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        var quotaPackage = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
        var incompatiblePackage = quotaPackageRepository.findByCode("MEMBERS_25").orElseThrow();

        JsonNode draft = createPolicy(token, policyRequest(
                "Reviewed zero-price grants", account.getId(), 50,
                List.of(
                        fixedPrice(BigDecimal.ZERO, targetPlan.getCurrencyCode(), targetPlan.getBillingCycle()),
                        productEffect(CommercialPolicyEffectType.GRANT_ADD_ON,
                                CommercialPolicyProductType.ADD_ON, addOn.getId()),
                        productEffect(CommercialPolicyEffectType.GRANT_QUOTA_PACKAGE,
                                CommercialPolicyProductType.QUOTA_PACKAGE, quotaPackage.getId()),
                        additiveQuotaBonus(StaffFeature.CODE, StaffFeature.MEMBERS, 3L),
                        productEffect(CommercialPolicyEffectType.GRANT_QUOTA_PACKAGE,
                                CommercialPolicyProductType.QUOTA_PACKAGE,
                                incompatiblePackage.getId()))));
        UUID policyId = UUID.fromString(draft.at("/summary/id").asText());
        JsonNode active = activate(token, policyId, "Approve bounded grants");

        SubscriptionChangeRequest selection = new SubscriptionChangeRequest(
                targetPlan.getCode(), Set.of(), List.of(), SubscriptionChangeTiming.IMMEDIATE, null);
        var preview = subscriptionService.previewChangeAsOperator(account.getId(), actorId, selection);

        assertThat(preview.previewPrice()).isEqualByComparingTo("0.00");
        assertThat(preview.addOnCodes()).contains(addOn.getCode());
        assertThat(preview.quotaPackages()).containsExactly(
                new QuotaPackageSelection(quotaPackage.getCode(), 1));
        assertThat(preview.effectiveQuotaLimits())
                .filteredOn(item -> item.featureCode().equals(StaffFeature.CODE)
                        && item.resource().equals(StaffFeature.MEMBERS))
                .singleElement().satisfies(item -> {
                    assertThat(item.includedLimit()).isEqualTo(8L);
                    assertThat(item.purchasedCapacity()).isEqualTo(5L);
                    assertThat(item.effectiveLimit()).isEqualTo(13L);
                });
        assertThat(preview.quotaPackages())
                .extracting(QuotaPackageSelection::packageCode)
                .doesNotContain(incompatiblePackage.getCode());
        assertThat(preview.commercialPolicyEvaluation().decisions())
                .filteredOn(decision -> incompatiblePackage.getId().equals(decision.productId()))
                .singleElement().extracting(decision -> decision.outcome())
                .isEqualTo(CommercialPolicyDecisionOutcome.REJECTED_INCOMPATIBLE);
        assertThat(preview.commercialPolicyEvaluation().decisions())
                .filteredOn(decision -> decision.outcome() == CommercialPolicyDecisionOutcome.APPLIED)
                .extracting(decision -> decision.effectType())
                .contains(CommercialPolicyEffectType.GRANT_ADD_ON,
                        CommercialPolicyEffectType.GRANT_QUOTA_PACKAGE,
                        CommercialPolicyEffectType.ADDITIVE_QUOTA_BONUS,
                        CommercialPolicyEffectType.FIXED_SUBSCRIPTION_PRICE);

        var applied = subscriptionService.applyChangeAsOperator(
                account.getId(), actorId,
                new SubscriptionChangeApplyRequest(selection, preview.previewToken()),
                "Accept reviewed policy terms");
        var operation = changeOperationRepository.findById(applied.operation().id()).orElseThrow();
        assertThat(operation.getCommercialPolicyEvaluation().finalRecurringPrice())
                .isEqualByComparingTo("0.00");
        assertThat(operation.getTargetSnapshot().addOns())
                .filteredOn(item -> item.code().equals(addOn.getCode()))
                .singleElement().extracting(item -> item.price())
                .isEqualTo(new BigDecimal("0.00"));
        assertThat(operation.getTargetSnapshot().quotaPackages())
                .filteredOn(item -> item.code().equals(quotaPackage.getCode()))
                .singleElement().satisfies(item -> {
                    assertThat(item.quantity()).isEqualTo(1);
                    assertThat(item.unitPrice()).isEqualByComparingTo("0.00");
                });

        JsonNode successor = objectMapper.readTree(mockMvc.perform(post(
                                "/api/admin/commercial-policies/{id}/revisions", policyId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CommercialPolicyRequests.VersionReason(
                                        active.at("/summary/version").asLong(),
                                        "Revise accepted policy terms"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID successorId = UUID.fromString(successor.at("/summary/id").asText());
        activate(token, successorId, "Activate successor terms");
        var acceptedSnapshot = subscriptionRepository.findUsableByAccountId(account.getId())
                .orElseThrow().getEntitlementSnapshot();
        assertThat(acceptedSnapshot.commercialPolicyEvaluation().finalRecurringPrice())
                .isEqualByComparingTo("0.00");
        assertThat(acceptedSnapshot.commercialPolicyEvaluation().decisions())
                .extracting(decision -> decision.policyId()).contains(policyId);
        assertThat(acceptedSnapshot.commercialPolicyEvaluation().decisions())
                .extracting(decision -> decision.policyId()).doesNotContain(successorId);
        assertThat(policyEvaluator.evaluate(
                account.getId(), Instant.now().plusSeconds(7200)).catalogDecisions()).isEmpty();

        JsonNode serialized = objectMapper.valueToTree(acceptedSnapshot.commercialPolicyEvaluation());
        assertThat(serialized.findValue("ownerAdminUserId")).isNull();
        assertThat(serialized.findValue("reason")).isNull();
        assertThat(serialized.findValue("contractReference")).isNull();
        assertThat(serialized.findValue("approvalReference")).isNull();
    }

    @Test
    void directPolicyWinsBroadDiscountAndEqualPriorityRestrictionBlocksNewPlanSelection() throws Exception {
        String token = loginAdminAndGetToken();
        UUID actorId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        RegisteredAccount registration = registerAccountWithToken("policy-precedence");
        Account account = registration.account();
        var subscription = subscriptionRepository.findUsableByAccountId(account.getId()).orElseThrow();
        var plan = planRepository.findByCode(subscription.getEntitlementSnapshot().planCode()).orElseThrow();

        JsonNode broadDraft = createPolicy(token, new CommercialPolicyRequests.Create(
                "Broad Plan discount", null, Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600), CommercialPolicySource.RETENTION, 900,
                "Broad reviewed terms", null, null,
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.PLAN_REVISION_SUBSCRIBERS,
                        null, Set.of(), plan.getId(), null),
                List.of(fixedDiscount("1.00", subscription.getCurrentPriceCurrencyCode()))));
        UUID broadId = UUID.fromString(broadDraft.at("/summary/id").asText());
        activate(token, broadId, "Activate broad terms");

        JsonNode directDraft = createPolicy(token, policyRequest(
                "Direct Account discount", account.getId(), 1,
                List.of(fixedDiscount("2.00", subscription.getCurrentPriceCurrencyCode()))));
        UUID directId = UUID.fromString(directDraft.at("/summary/id").asText());
        activate(token, directId, "Activate direct terms");

        JsonNode allowDraft = createPolicy(token, policyRequest(
                "Allow exact Plan", account.getId(), 100,
                List.of(productEffect(CommercialPolicyEffectType.ALLOW_PRODUCT_SELECTION,
                        CommercialPolicyProductType.PLAN, plan.getId()))));
        activate(token, UUID.fromString(allowDraft.at("/summary/id").asText()), "Allow exact product");

        SubscriptionChangeRequest immediate = new SubscriptionChangeRequest(
                plan.getCode(), Set.of(), List.of(), SubscriptionChangeTiming.IMMEDIATE, null);
        SubscriptionChangeRequest renewal = new SubscriptionChangeRequest(
                plan.getCode(), Set.of(), List.of(), SubscriptionChangeTiming.AT_RENEWAL, null);
        var reviewedBeforeRestriction = subscriptionService.previewChangeAsOperator(
                account.getId(), actorId, immediate);
        JsonNode blockDraft = createPolicy(token, policyRequest(
                "Block exact Plan", account.getId(), 100,
                List.of(productEffect(CommercialPolicyEffectType.BLOCK_PRODUCT_SELECTION,
                        CommercialPolicyProductType.PLAN, plan.getId()))));
        UUID blockId = UUID.fromString(blockDraft.at("/summary/id").asText());
        activate(token, blockId, "Block exact product");

        var evaluation = policyEvaluator.evaluate(account.getId(), Instant.now());
        assertThat(evaluation.discount().winner().policy().getId()).isEqualTo(directId);
        assertThat(evaluation.discount().rejected())
                .extracting(candidate -> candidate.policy().getId()).contains(broadId);
        assertThat(evaluation.product(CommercialPolicyProductType.PLAN, plan.getId())
                .winner().policy().getId()).isEqualTo(blockId);

        JsonNode clientCatalog = objectMapper.readTree(mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(registration.accessToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(catalogPlan(clientCatalog, plan.getCode()).get("selectable").asBoolean()).isFalse();
        var operatorCatalog = subscriptionService.catalogAsOperator(account.getId());
        assertThat(operatorCatalog.plans())
                .filteredOn(item -> item.code().equals(plan.getCode()))
                .singleElement().extracting(item -> item.selectable()).isEqualTo(false);

        assertThatThrownBy(() -> subscriptionService.previewChangeAsOperator(
                account.getId(), actorId, immediate))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> subscriptionService.previewChangeAsOperator(
                account.getId(), actorId, renewal))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> subscriptionService.applyChangeAsOperator(
                account.getId(), actorId,
                new SubscriptionChangeApplyRequest(
                        immediate, reviewedBeforeRestriction.previewToken()),
                "Attempt stale reviewed selection"))
                .isInstanceOf(StaleResourceVersionException.class);
        assertThat(changeOperationRepository.findAllByAccountId(
                account.getId(), org.springframework.data.domain.Pageable.unpaged())).isEmpty();

        Account other = registerAccount("policy-cross-account");
        assertThat(policyEvaluator.evaluate(other.getId(), Instant.now()).catalogDecisions()).isEmpty();
    }

    @Test
    void blockedAddOnAndPackageRemainExplainableButCannotBeAppliedByClientOrOperator() throws Exception {
        String token = loginAdminAndGetToken();
        UUID actorId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        RegisteredAccount registration = registerAccountWithToken("policy-product-blocks");
        Account account = registration.account();
        var targetPlan = planRepository.findByCode("FLEX").orElseThrow();
        var addOn = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        var quotaPackage = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();

        JsonNode blockDraft = createPolicy(token, policyRequest(
                "Block selected extensions", account.getId(), 100,
                List.of(
                        productEffect(CommercialPolicyEffectType.BLOCK_PRODUCT_SELECTION,
                                CommercialPolicyProductType.ADD_ON, addOn.getId()),
                        productEffect(CommercialPolicyEffectType.BLOCK_PRODUCT_SELECTION,
                                CommercialPolicyProductType.QUOTA_PACKAGE, quotaPackage.getId()))));
        activate(token, UUID.fromString(blockDraft.at("/summary/id").asText()),
                "Block extension selection");

        JsonNode clientCatalog = objectMapper.readTree(mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(registration.accessToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode clientPlan = catalogPlan(clientCatalog, targetPlan.getCode());
        assertThat(catalogProduct(clientPlan.get("addOns"), addOn.getCode())
                .get("selectable").asBoolean()).isFalse();
        assertThat(catalogProduct(clientPlan.get("quotaPackages"), quotaPackage.getCode())
                .get("selectable").asBoolean()).isFalse();

        var operatorCatalog = subscriptionService.catalogAsOperator(account.getId());
        var operatorPlan = operatorCatalog.plans().stream()
                .filter(item -> item.code().equals(targetPlan.getCode())).findFirst().orElseThrow();
        assertThat(operatorPlan.addOns())
                .filteredOn(item -> item.code().equals(addOn.getCode()))
                .singleElement().extracting(item -> item.selectable()).isEqualTo(false);
        assertThat(operatorPlan.quotaPackages())
                .filteredOn(item -> item.code().equals(quotaPackage.getCode()))
                .singleElement().extracting(item -> item.selectable()).isEqualTo(false);

        SubscriptionChangeRequest selection = new SubscriptionChangeRequest(
                targetPlan.getCode(), Set.of(addOn.getCode()),
                List.of(new QuotaPackageSelection(quotaPackage.getCode(), 1)),
                SubscriptionChangeTiming.IMMEDIATE, null);
        JsonNode clientPreview = objectMapper.readTree(mockMvc.perform(post("/api/v1/subscriptions/preview")
                        .header("Authorization", bearer(registration.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selection)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(clientPreview.get("immediateAllowed").asBoolean()).isFalse();
        assertThat(java.util.stream.StreamSupport.stream(
                        clientPreview.at("/commercialPolicyEvaluation/conflicts").spliterator(), false)
                .map(item -> item.get("code").asText()).toList())
                .containsOnly("POLICY_PRODUCT_BLOCKED");
        assertClientPolicyPrivacy(clientPreview);

        var operatorPreview = subscriptionService.previewChangeAsOperator(
                account.getId(), actorId, selection);
        assertThat(operatorPreview.immediateAllowed()).isFalse();
        assertThat(operatorPreview.commercialPolicyEvaluation().conflicts())
                .extracting(conflict -> conflict.code())
                .containsOnly("POLICY_PRODUCT_BLOCKED");

        mockMvc.perform(post("/api/v1/subscriptions/apply")
                        .header("Authorization", bearer(registration.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubscriptionChangeApplyRequest(
                                selection, clientPreview.get("previewToken").asText()))))
                .andExpect(status().isConflict());
        assertThatThrownBy(() -> subscriptionService.applyChangeAsOperator(
                account.getId(), actorId,
                new SubscriptionChangeApplyRequest(selection, operatorPreview.previewToken()),
                "Attempt blocked extension selection"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("commercial-policy conflicts");
        assertThat(changeOperationRepository.findAllByAccountId(
                account.getId(), org.springframework.data.domain.Pageable.unpaged())).isEmpty();
    }

    @Test
    void policyChangeInvalidatesSignedSubscriptionReview() throws Exception {
        String token = loginAdminAndGetToken();
        UUID actorId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        Account account = registerAccount("policy-stale");
        var subscription = subscriptionRepository.findUsableByAccountId(account.getId()).orElseThrow();
        String currentPlanCode = subscription.getEntitlementSnapshot().planCode();
        JsonNode draft = createPolicy(token, policyRequest(
                "Stale policy terms", account.getId(), 20,
                List.of(fixedPrice(BigDecimal.ZERO,
                        subscription.getCurrentPriceCurrencyCode(),
                        subscription.getEntitlementSnapshot().billingCycle()))));
        UUID policyId = UUID.fromString(draft.at("/summary/id").asText());
        JsonNode active = activate(token, policyId, "Activate reviewed terms");

        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                currentPlanCode, Set.of(), List.of(),
                SubscriptionChangeTiming.IMMEDIATE, null);
        var preview = subscriptionService.previewChangeAsOperator(account.getId(), actorId, request);
        pause(token, policyId, active.at("/summary/version").asLong());

        assertThatThrownBy(() -> subscriptionService.applyChangeAsOperator(
                account.getId(), actorId,
                new SubscriptionChangeApplyRequest(request, preview.previewToken()),
                "Reject stale policy review"))
                .isInstanceOf(StaleResourceVersionException.class);
        assertThat(changeOperationRepository.findAllByAccountId(
                account.getId(), org.springframework.data.domain.Pageable.unpaged())).isEmpty();
    }

    @Test
    void accountLockAllowsOnlyOneConcurrentApplyOfTheSamePolicyEvidence() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("policy-concurrent-apply");
        var subscription = subscriptionRepository.findUsableByAccountId(account.getId()).orElseThrow();
        String currentPlanCode = subscription.getEntitlementSnapshot().planCode();
        JsonNode draft = createPolicy(token, policyRequest(
                "Concurrent reviewed terms", account.getId(), 20,
                List.of(fixedPrice(BigDecimal.ZERO,
                        subscription.getCurrentPriceCurrencyCode(),
                        subscription.getEntitlementSnapshot().billingCycle()))));
        activate(token, UUID.fromString(draft.at("/summary/id").asText()), "Enable reviewed terms");

        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                currentPlanCode, Set.of(), List.of(),
                SubscriptionChangeTiming.IMMEDIATE, null);
        JsonNode preview = objectMapper.readTree(mockMvc.perform(post(
                                "/api/admin/subscriptions/account/{accountId}/changes/preview",
                                account.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String body = objectMapper.writeValueAsString(new com.hiveapp.platform.admin.dto
                .AdminSubscriptionChangeApplyRequest(
                request, preview.get("previewToken").asText(), "Concurrent apply"));
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                () -> concurrentApplyStatus(token, account.getId(), body, start));
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                () -> concurrentApplyStatus(token, account.getId(), body, start));
        start.countDown();

        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(201, 409);
        assertThat(changeOperationRepository.findAllByAccountId(
                account.getId(), org.springframework.data.domain.Pageable.unpaged()))
                .hasSize(1);
    }

    @Test
    void evaluatorQueryCountIsConstantAsApplicablePolicyCountGrows() throws Exception {
        String token = loginAdminAndGetToken();
        Account account = registerAccount("policy-query-count");
        JsonNode baselineDraft = createPolicy(token, policyRequest(
                "Policy query baseline", account.getId(), 1,
                List.of(fixedDiscount("1.00", "MAD"))));
        activate(token, UUID.fromString(baselineDraft.at("/summary/id").asText()), "Baseline");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        policyEvaluator.evaluate(account.getId(), Instant.now());
        long baseline = statistics.getPrepareStatementCount();

        for (int index = 0; index < 5; index++) {
            JsonNode draft = createPolicy(token, policyRequest(
                    "Policy query growth " + index, account.getId(), index + 2,
                    List.of(fixedDiscount(index + ".50", "MAD"))));
            activate(token, UUID.fromString(draft.at("/summary/id").asText()), "Grow policy set");
        }
        statistics.clear();
        policyEvaluator.evaluate(account.getId(), Instant.now());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(baseline);
    }

    private CommercialPolicyRequests.Create policyRequest(
            String name,
            UUID accountId,
            int priority,
            List<CommercialPolicyRequests.Effect> effects
    ) {
        return new CommercialPolicyRequests.Create(
                name, "Subscription policy integration fixture",
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(3600),
                CommercialPolicySource.CONTRACT, priority, "Reviewed operational terms",
                null, null,
                new CommercialPolicyRequests.Target(
                        CommercialPolicyTargetKind.ACCOUNT_SET, null,
                        Set.of(accountId), null, null),
                effects);
    }

    private CommercialPolicyRequests.Effect fixedPrice(
            BigDecimal amount,
            String currency,
            BillingCycle cycle
    ) {
        return new CommercialPolicyRequests.Effect(
                CommercialPolicyEffectType.FIXED_SUBSCRIPTION_PRICE,
                null, null, null, null, null,
                amount, currency, cycle, null, null, null);
    }

    private CommercialPolicyRequests.Effect fixedDiscount(String amount, String currency) {
        return new CommercialPolicyRequests.Effect(
                CommercialPolicyEffectType.FIXED_DISCOUNT,
                null, null, null, null, null,
                new BigDecimal(amount), currency, null, null, null, null);
    }

    private CommercialPolicyRequests.Effect productEffect(
            CommercialPolicyEffectType type,
            CommercialPolicyProductType productType,
            UUID productId
    ) {
        return new CommercialPolicyRequests.Effect(
                type, productType, productId, null, null, null,
                null, null, null, null, null, null);
    }

    private CommercialPolicyRequests.Effect additiveQuotaBonus(
            String featureCode,
            String resource,
            long quantity
    ) {
        return new CommercialPolicyRequests.Effect(
                CommercialPolicyEffectType.ADDITIVE_QUOTA_BONUS,
                null, null, featureCode, resource, quantity,
                null, null, null, null, null, null);
    }

    private JsonNode createPolicy(String token, CommercialPolicyRequests.Create request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/admin/commercial-policies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode activate(String token, UUID policyId, String reason) throws Exception {
        JsonNode preview = objectMapper.readTree(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/admin/commercial-policies/{id}/activation-preview", policyId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return objectMapper.readTree(mockMvc.perform(post(
                                "/api/admin/commercial-policies/{id}/activate", policyId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommercialPolicyRequests.Activation(
                                preview.get("expectedVersion").asLong(), reason,
                                preview.get("previewToken").asText()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void pause(String token, UUID policyId, long version) throws Exception {
        mockMvc.perform(post("/api/admin/commercial-policies/{id}/pause", policyId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CommercialPolicyRequests.VersionReason(
                                        version, "Pause accepted policy"))))
                .andExpect(status().isOk());
    }

    private int concurrentApplyStatus(
            String token,
            UUID accountId,
            String body,
            CountDownLatch start
    ) {
        try {
            start.await();
            return mockMvc.perform(post(
                                    "/api/admin/subscriptions/account/{accountId}/changes/apply",
                                    accountId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private void assertClientPolicyPrivacy(JsonNode response) {
        assertThat(response.findValues("outcome").stream().map(JsonNode::asText).toList())
                .doesNotContain("REJECTED_LOWER_PRECEDENCE");
        assertThat(response.findValue("policyId")).isNull();
        assertThat(response.findValue("activationId")).isNull();
        assertThat(response.findValue("lineageId")).isNull();
        assertThat(response.findValue("policyCode")).isNull();
        assertThat(response.findValue("policyName")).isNull();
        assertThat(response.findValue("targetKind")).isNull();
        assertThat(response.findValue("priority")).isNull();
        assertThat(response.findValue("effectId")).isNull();
        assertThat(response.findValue("effectOrder")).isNull();
        assertThat(response.findValue("configuredAmount")).isNull();
        assertThat(response.findValue("configuredCurrencyCode")).isNull();
        assertThat(response.findValue("maximumAmount")).isNull();
        assertThat(response.findValue("maximumCurrencyCode")).isNull();
        assertThat(response.findValue("ownerAdminUserId")).isNull();
        assertThat(response.findValue("contractReference")).isNull();
        assertThat(response.findValue("approvalReference")).isNull();
        assertThat(response.findValue("reason")).isNull();
    }

    private JsonNode catalogPlan(JsonNode catalog, String code) {
        return java.util.stream.StreamSupport.stream(catalog.get("plans").spliterator(), false)
                .filter(item -> code.equals(item.get("code").asText()))
                .findFirst().orElseThrow();
    }

    private JsonNode catalogProduct(JsonNode products, String code) {
        return java.util.stream.StreamSupport.stream(products.spliterator(), false)
                .filter(item -> code.equals(item.get("code").asText()))
                .findFirst().orElseThrow();
    }

    private Account registerAccount(String marker) throws Exception {
        return registerAccountWithToken(marker).account();
    }

    private RegisteredAccount registerAccountWithToken(String marker) throws Exception {
        String email = marker + "-" + UUID.randomUUID() + "@example.com";
        JsonNode registration = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, CLIENT_PASSWORD, "Policy", "Subscription", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        return new RegisteredAccount(
                accountRepository.findByOwner_Id(userId).orElseThrow(),
                registration.get("accessToken").asText());
    }

    private record RegisteredAccount(Account account, String accessToken) {}
}
