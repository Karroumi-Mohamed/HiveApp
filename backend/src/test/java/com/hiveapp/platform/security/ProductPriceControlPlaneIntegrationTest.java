package com.hiveapp.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.PlanLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.client.plan.infrastructure.ProductPriceBackfill;
import com.hiveapp.platform.client.plan.dto.CreateProductPriceRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.PlanLifecycleRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceActivationRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementPreviewRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceVersionRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.UpdateProductPriceRequest;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncLockRepository;
import com.hiveapp.platform.registry.service.RegistrySynchronizationCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductPriceControlPlaneIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private PlanRepository planRepository;
    @Autowired private AddOnRepository addOnRepository;
    @Autowired private QuotaPackageRepository quotaPackageRepository;
    @Autowired private ProductPriceRepository productPriceRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private ProductPriceBackfill productPriceBackfill;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private CommercialPreviewTokenService commercialPreviewTokenService;
    @Autowired private RegistrySyncLockRepository registrySyncLockRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void compatibilityBackfillIsIdempotentForEverySeededProductTuple() {
        long before = productPriceRepository.count();
        productPriceBackfill.backfill();
        assertThat(productPriceRepository.count()).isEqualTo(before);
        var free = planRepository.findByCode("FREE").orElseThrow();
        var addOn = addOnRepository.findByCode("ORGANIZATION_TOOLS").orElseThrow();
        var quotaPackage = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
        // Copied price drafts intentionally retain source dates; only original bootstrap
        // entries claim a start contemporaneous with their own creation.
        var seededOwnerIds = Set.of(free.getId(), addOn.getId(), quotaPackage.getId());
        productPriceRepository.findAll().stream().filter(ProductPrice::isCompatibilityDefault)
                .filter(price -> seededOwnerIds.contains(price.ownerId()))
                .forEach(price -> assertThat(price.getEffectiveFrom())
                        .isBetween(price.getCreatedAt().minusSeconds(5), price.getCreatedAt()));
        assertThat(productPriceRepository.countCompatibilityPrice(
                ProductPriceOwnerType.PLAN, free.getId(), "USD", BillingCycle.MONTHLY)).isEqualTo(1);
        assertThat(productPriceRepository.countCompatibilityPrice(
                ProductPriceOwnerType.ADD_ON, addOn.getId(), "USD", BillingCycle.MONTHLY)).isEqualTo(1);
        assertThat(productPriceRepository.countCompatibilityPrice(
                ProductPriceOwnerType.QUOTA_PACKAGE, quotaPackage.getId(), "USD", BillingCycle.MONTHLY)).isEqualTo(1);
    }

    @Test
    void compatibilityBackfillNeverPublishesPricesForArchivedOwners() throws Exception {
        String token = loginAdminAndGetToken();
        UUID planId = null;
        UUID addOnId = null;
        UUID packageId = null;
        try {
            JsonNode plan = responseJson(mockMvc.perform(post("/api/admin/plans")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                    "Archived backfill Plan " + UUID.randomUUID(), null,
                                    BigDecimal.ONE, "USD", BillingCycle.MONTHLY))))
                    .andExpect(status().isCreated()));
            planId = UUID.fromString(plan.get("id").asText());
            JsonNode addOn = responseJson(mockMvc.perform(post("/api/admin/add-ons")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateAddOnRequest(
                                    "Archived backfill AddOn " + UUID.randomUUID(), null,
                                    BigDecimal.ONE, "USD", BillingCycle.MONTHLY,
                                    Set.of("FLEX"), Set.of(), Set.of(), Set.of(),
                                    ProductSalesVisibility.PUBLIC))))
                    .andExpect(status().isCreated()));
            addOnId = UUID.fromString(addOn.get("id").asText());
            JsonNode quota = responseJson(mockMvc.perform(post("/api/admin/quota-packages")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateQuotaPackageRequest(
                                    "Archived backfill package " + UUID.randomUUID(), null,
                                    "platform.staff", "members", 1, BigDecimal.ONE,
                                    "USD", BillingCycle.MONTHLY, true, 5, Set.of("FLEX"),
                                    Set.of(), ProductSalesVisibility.PUBLIC))))
                    .andExpect(status().isCreated()));
            packageId = UUID.fromString(quota.get("id").asText());

            productPriceRepository.deleteAllInBatch(productPriceRepository.findAllByPlanId(planId));
            productPriceRepository.deleteAllInBatch(productPriceRepository.findAllByAddOnId(addOnId));
            productPriceRepository.deleteAllInBatch(
                    productPriceRepository.findAllByQuotaPackageId(packageId));
            productPriceRepository.flush();
            var archivedPlan = planRepository.findById(planId).orElseThrow();
            archivedPlan.setStatus(PlanStatus.ARCHIVED);
            planRepository.saveAndFlush(archivedPlan);
            var archivedAddOn = addOnRepository.findById(addOnId).orElseThrow();
            archivedAddOn.setStatus(AddOnStatus.ARCHIVED);
            addOnRepository.saveAndFlush(archivedAddOn);
            var archivedPackage = quotaPackageRepository.findById(packageId).orElseThrow();
            archivedPackage.setStatus(QuotaPackageStatus.ARCHIVED);
            quotaPackageRepository.saveAndFlush(archivedPackage);

            productPriceBackfill.backfill();
            assertThat(productPriceRepository.findAllByPlanId(planId)).isEmpty();
            assertThat(productPriceRepository.findAllByAddOnId(addOnId)).isEmpty();
            assertThat(productPriceRepository.findAllByQuotaPackageId(packageId)).isEmpty();
        } finally {
            if (packageId != null) quotaPackageRepository.deleteById(packageId);
            if (addOnId != null) addOnRepository.deleteById(addOnId);
            if (planId != null) planRepository.deleteById(planId);
            quotaPackageRepository.flush();
            addOnRepository.flush();
            planRepository.flush();
        }
    }

    @Test
    void archivedOwnersRejectNewEditedAndRevisedPriceDrafts() throws Exception {
        String token = loginAdminAndGetToken();
        UUID planId = createActivePlan(token);
        Instant starts = Instant.now().minusSeconds(30);
        JsonNode editableDraft = createPrice(
                token, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("120.00"), BillingCycle.YEARLY, starts, null);
        var activeMonthly = productPriceRepository.findAllByPlanId(planId).stream()
                .filter(price -> price.getStatus()
                        == com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE)
                .filter(price -> price.getBillingCycle() == BillingCycle.MONTHLY)
                .findFirst().orElseThrow();
        JsonNode paused = legacyPausedPrice(token, activeMonthly.getId(), activeMonthly.getVersion());

        var owner = planRepository.findById(planId).orElseThrow();
        owner.setStatus(PlanStatus.ARCHIVED);
        planRepository.saveAndFlush(owner);

        mockMvc.perform(post("/api/admin/product-prices")
                        .header("Authorization", bearer(token))
                        .param("ownerType", ProductPriceOwnerType.PLAN.name())
                        .param("ownerId", planId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductPriceRequest(
                                BigDecimal.ONE, "USD", BillingCycle.MONTHLY,
                                starts.plusSeconds(1), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(put("/api/admin/product-prices/{id}", editableDraft.get("id").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductPriceRequest(
                                new BigDecimal("121.00"), "USD", BillingCycle.YEARLY,
                                starts, null, editableDraft.get("version").asLong()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/admin/product-prices/{id}/revisions", activeMonthly.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductPriceVersionRequest(
                                paused.get("version").asLong(), "Attempt archived-owner revision"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void supportsIndependentAnnualPriceLifecycleAndStableConflictCodes() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = createActivePlan(adminToken);
        Instant starts = Instant.now().minusSeconds(30);

        mockMvc.perform(post("/api/admin/product-prices")
                        .header("Authorization", bearer(adminToken))
                        .param("ownerId", planId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductPriceRequest(
                                BigDecimal.ZERO, "USD", BillingCycle.YEARLY, starts, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        JsonNode annualDraft = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("120.00"), BillingCycle.YEARLY, starts, null);
        UUID annualId = UUID.fromString(annualDraft.get("id").asText());
        String productName = planRepository.findById(planId).orElseThrow().getName();
        assertThat(annualDraft.get("productName").asText()).isEqualTo(productName);

        String searchResponse = mockMvc.perform(get("/api/admin/product-prices")
                        .header("Authorization", bearer(adminToken))
                        .param("search", productName))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(searchResponse).get("content").findValuesAsText("id"))
                .contains(annualId.toString());

        mockMvc.perform(get("/api/admin/product-prices/{id}/activation-preview", annualId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activatable").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty());

        mockMvc.perform(post("/api/admin/product-prices/{id}/activate", annualId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + annualDraft.get("version").asLong() + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        JsonNode annual = activate(adminToken, annualId, annualDraft.get("version").asLong());
        assertThat(annual.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(annual.get("amount").isTextual()).isTrue();
        assertThat(new BigDecimal(annual.get("amount").asText())).isEqualByComparingTo("120.00");

        mockMvc.perform(get("/api/admin/product-prices/{id}/history", annualId)
                        .header("Authorization", bearer(adminToken))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content[0].actorUserId").isNotEmpty())
                .andExpect(jsonPath("$.content[0].actorEmail").isNotEmpty())
                .andExpect(jsonPath("$.content[0].action").value("platform.price_books.activate"))
                .andExpect(jsonPath("$.content[0].outcome").value("SUCCEEDED"))
                .andExpect(jsonPath("$.content[0].reason").value("Activate price"))
                .andExpect(jsonPath("$.content[0].occurredAt").isNotEmpty());

        JsonNode overlap = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("99.00"), BillingCycle.YEARLY, starts.plusSeconds(1), null);
        mockMvc.perform(put("/api/admin/product-prices/{id}", overlap.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":99,"currencyCode":"USD","billingCycle":"YEARLY",
                                 "effectiveFrom":"2026-01-01T00:00:00Z","version":99}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        mockMvc.perform(post("/api/admin/product-prices/{id}/activate", overlap.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceActivationRequest(
                                        overlap.get("version").asLong(), "Publish overlapping annual price",
                                        fetchProductPriceActivationToken(
                                                adminToken, UUID.fromString(overlap.get("id").asText()))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_ENTRY_OVERLAP"));
        mockMvc.perform(delete("/api/admin/product-prices/{id}", overlap.get("id").asText())
                        .param("version", String.valueOf(overlap.get("version").asLong()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/admin/product-prices/{id}", annualId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":121,"currencyCode":"USD","billingCycle":"YEARLY",
                                 "effectiveFrom":"2026-01-01T00:00:00Z","version":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        JsonNode paused = legacyPausedPrice(adminToken, annualId, annual.get("version").asLong());
        JsonNode revision = revise(adminToken, annualId, paused.get("version").asLong());
        assertThat(revision.get("status").asText()).isEqualTo("DRAFT");
        assertThat(revision.get("sourcePriceId").asText()).isEqualTo(annualId.toString());
        assertThat(revision.get("revisionNumber").asInt()).isEqualTo(2);

        mockMvc.perform(get("/api/admin/product-prices/{id}", annualId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableActions", hasItem("REACTIVATE")))
                .andExpect(jsonPath("$.availableActions", not(hasItem("REVISE"))))
                .andExpect(jsonPath("$.blockers", hasItem("SUCCESSOR_ALREADY_EXISTS")));

        JsonNode reactivated = lifecycle(adminToken, annualId, paused.get("version").asLong(), "reactivate");
        JsonNode pausedAgain = legacyPausedPrice(adminToken, annualId, reactivated.get("version").asLong());
        JsonNode archived = lifecycle(adminToken, annualId, pausedAgain.get("version").asLong(), "archive");
        assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
        mockMvc.perform(post("/api/admin/product-prices/{id}/reactivate", annualId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceActivationRequest(
                                        archived.get("version").asLong(), "Attempt archived reactivation",
                                        fetchProductPriceActivationToken(adminToken, annualId)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mockMvc.perform(delete("/api/admin/product-prices/{id}", revision.get("id").asText())
                        .param("version", String.valueOf(revision.get("version").asLong()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        var monthly = productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, planId, "USD", BillingCycle.MONTHLY, Instant.now());
        assertThat(monthly).singleElement().satisfies(price -> {
            assertThat(price.isCompatibilityDefault()).isTrue();
            assertThat(price.getAmount()).isZero();
        });
    }

    @Test
    void activationEvidenceRejectsMissingTamperedExpiredAndMismatchedClaimsWithoutWriting()
            throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = createActivePlan(adminToken);
        JsonNode draft = createPrice(
                adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("47.00"), BillingCycle.YEARLY,
                Instant.now().minusSeconds(30), null);
        UUID priceId = UUID.fromString(draft.get("id").asText());
        long version = draft.get("version").asLong();
        JsonNode preview = priceActivationPreview(adminToken, priceId);
        String reviewedToken = preview.get("previewToken").asText();
        PreviewClaims claims = previewClaims(reviewedToken);

        assertThat(preview.get("expectedVersion").asLong()).isEqualTo(version);
        assertThat(preview.get("catalogRevision").isIntegralNumber()).isTrue();
        assertThat(preview.get("registryVersion").asText()).isNotBlank();
        assertThat(preview.get("evaluatedAt").asText()).isNotBlank();
        assertThat(preview.get("expiresAt").asText()).isNotBlank();

        assertRejectedActivation(adminToken, priceId,
                new ProductPriceActivationRequest(version, "Missing evidence", null));

        int signatureOffset = reviewedToken.indexOf('.') + 3;
        char replacement = reviewedToken.charAt(signatureOffset) == 'A' ? 'B' : 'A';
        String tampered = reviewedToken.substring(0, signatureOffset) + replacement
                + reviewedToken.substring(signatureOffset + 1);
        assertRejectedActivation(adminToken, priceId,
                new ProductPriceActivationRequest(version, "Tampered evidence", tampered));

        assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Wrong operation kind", issueEvidence(
                        CommercialPreviewKind.PLAN_ACTIVATION,
                        claims.resourceId(), claims.version(), claims.actorUserId(),
                        claims.catalogRevision(), claims.registryVersion(), claims.fingerprint(),
                        claims.issuedAt())));
        assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Wrong resource", issueEvidence(
                        CommercialPreviewKind.PRODUCT_PRICE_ACTIVATION,
                        UUID.randomUUID(), claims.version(), claims.actorUserId(),
                        claims.catalogRevision(), claims.registryVersion(), claims.fingerprint(),
                        claims.issuedAt())));
        assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Wrong actor", issueEvidence(
                        CommercialPreviewKind.PRODUCT_PRICE_ACTIVATION,
                        claims.resourceId(), claims.version(), UUID.randomUUID(),
                        claims.catalogRevision(), claims.registryVersion(), claims.fingerprint(),
                        claims.issuedAt())));
        assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Wrong signed version", issueEvidence(
                        CommercialPreviewKind.PRODUCT_PRICE_ACTIVATION,
                        claims.resourceId(), claims.version() + 1, claims.actorUserId(),
                        claims.catalogRevision(), claims.registryVersion(), claims.fingerprint(),
                        claims.issuedAt())));
        assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version + 1, "Wrong request version", reviewedToken));
        assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Wrong catalogue", issueEvidence(
                        CommercialPreviewKind.PRODUCT_PRICE_ACTIVATION,
                        claims.resourceId(), claims.version(), claims.actorUserId(),
                        claims.catalogRevision() + 1, claims.registryVersion(), claims.fingerprint(),
                        claims.issuedAt())));
        assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Wrong registry", issueEvidence(
                        CommercialPreviewKind.PRODUCT_PRICE_ACTIVATION,
                        claims.resourceId(), claims.version(), claims.actorUserId(),
                        claims.catalogRevision(), claims.registryVersion() + "-stale",
                        claims.fingerprint(), claims.issuedAt())));
        assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Wrong assessment", issueEvidence(
                        CommercialPreviewKind.PRODUCT_PRICE_ACTIVATION,
                        claims.resourceId(), claims.version(), claims.actorUserId(),
                        claims.catalogRevision(), claims.registryVersion(),
                        claims.fingerprint() + "-stale", claims.issuedAt())));
        assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Expired evidence", issueEvidence(
                        CommercialPreviewKind.PRODUCT_PRICE_ACTIVATION,
                        claims.resourceId(), claims.version(), claims.actorUserId(),
                        claims.catalogRevision(), claims.registryVersion(), claims.fingerprint(),
                        Instant.now().minusSeconds(360))));

        JsonNode activated = applyActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Apply exact reviewed price", reviewedToken));
        assertThat(activated.get("status").asText()).isEqualTo("ACTIVE");

        JsonNode paused = legacyPausedPrice(adminToken, priceId, activated.get("version").asLong());
        assertRejectedReactivation(adminToken, priceId, new ProductPriceActivationRequest(
                paused.get("version").asLong(), "Missing reactivation evidence", null));
        JsonNode reactivationPreview = priceActivationPreview(adminToken, priceId);
        JsonNode reactivated = applyReactivation(adminToken, priceId,
                new ProductPriceActivationRequest(
                        reactivationPreview.get("expectedVersion").asLong(),
                        "Apply exact reviewed reactivation",
                        reactivationPreview.get("previewToken").asText()));
        assertThat(reactivated.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void activationEvidenceIsInvalidatedByCatalogAndRegistryInterleaving() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = createActivePlan(adminToken);
        JsonNode draft = createPrice(
                adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("58.00"), BillingCycle.YEARLY,
                Instant.now().minusSeconds(30), null);
        UUID priceId = UUID.fromString(draft.get("id").asText());
        long version = draft.get("version").asLong();

        JsonNode beforeCatalogChange = priceActivationPreview(adminToken, priceId);
        createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("59.00"), BillingCycle.MONTHLY,
                Instant.now().plusSeconds(3600), null);
        assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Reject stale catalogue review",
                beforeCatalogChange.get("previewToken").asText()));

        JsonNode beforeRegistryChange = priceActivationPreview(adminToken, priceId);
        long originalRegistryRevision = transactionTemplate.execute(status -> {
            var lock = registrySyncLockRepository.findByLockNameForUpdate(
                    RegistrySynchronizationCoordinator.LOCK_NAME).orElseThrow();
            long original = lock.getCatalogRevision();
            lock.setCatalogRevision(original + 1);
            registrySyncLockRepository.saveAndFlush(lock);
            return original;
        });
        try {
            assertRejectedActivation(adminToken, priceId, new ProductPriceActivationRequest(
                    version, "Reject stale registry review",
                    beforeRegistryChange.get("previewToken").asText()));
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                var lock = registrySyncLockRepository.findByLockNameForUpdate(
                        RegistrySynchronizationCoordinator.LOCK_NAME).orElseThrow();
                lock.setCatalogRevision(originalRegistryRevision);
                registrySyncLockRepository.saveAndFlush(lock);
            });
        }

        JsonNode current = priceActivationPreview(adminToken, priceId);
        applyActivation(adminToken, priceId, new ProductPriceActivationRequest(
                version, "Apply after fresh catalogue and registry review",
                current.get("previewToken").asText()));
        assertThat(productPriceRepository.findById(priceId).orElseThrow().getStatus().name())
                .isEqualTo("ACTIVE");
    }

    @Test
    void exactPlanSelectionResolvesMatchingItemsAndWritesImmutableCurrentSnapshot() throws Exception {
        String adminToken = loginAdminAndGetToken();
        var plan = planRepository.findByCode("FLEX").orElseThrow();
        var addOn = addOnRepository.findByCode("ORGANIZATION_TOOLS").orElseThrow();
        var quotaPackage = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
        Instant starts = Instant.now().minusSeconds(30);

        var planPrice = ensureActivePrice(adminToken, ProductPriceOwnerType.PLAN, plan.getId(),
                BigDecimal.ZERO, BillingCycle.YEARLY, starts);
        var addOnPrice = ensureActivePrice(adminToken, ProductPriceOwnerType.ADD_ON, addOn.getId(),
                BigDecimal.ZERO, BillingCycle.YEARLY, starts);
        var packagePrice = ensureActivePrice(adminToken, ProductPriceOwnerType.QUOTA_PACKAGE,
                quotaPackage.getId(), BigDecimal.ZERO, BillingCycle.YEARLY, starts);

        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                plan.getCode(), Set.of(addOn.getCode()),
                List.of(new QuotaPackageSelection(quotaPackage.getCode(), 1)),
                SubscriptionChangeTiming.IMMEDIATE,
                new ProductPriceSelectionRequest(
                        planPrice.getId(), "USD", BillingCycle.YEARLY));

        applySubscriptionChange(clientToken, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operation.status").value("APPLIED"))
                .andExpect(jsonPath("$.preview.previewPrice").value("0.00"));

        var subscription = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        var snapshot = subscription.getEntitlementSnapshot();
        assertThat(snapshot.schemaVersion())
                .isEqualTo(com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot
                        .CURRENT_SCHEMA_VERSION);
        assertThat(snapshot.billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(snapshot.planPriceEntryId()).isEqualTo(planPrice.getId());
        assertThat(snapshot.addOns()).singleElement()
                .extracting(item -> item.priceEntryId())
                .isEqualTo(addOnPrice.getId());
        assertThat(snapshot.quotaPackages()).singleElement()
                .extracting(item -> item.priceEntryId())
                .isEqualTo(packagePrice.getId());

        legacyPausedPrice(adminToken, planPrice.getId(), planPrice.getVersion());
        legacyPausedPrice(adminToken, addOnPrice.getId(), addOnPrice.getVersion());
        legacyPausedPrice(adminToken, packagePrice.getId(), packagePrice.getVersion());

        mockMvc.perform(patch("/api/admin/subscriptions/account/{accountId}/overrides", accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "addOnCodes", Set.of(addOn.getCode()),
                                "quotaPackages", List.of(new QuotaPackageSelection(
                                        quotaPackage.getCode(), 1))))))
                .andExpect(status().isNotFound());

        var unchanged = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow()
                .getEntitlementSnapshot();
        assertThat(unchanged).isEqualTo(snapshot);
        String catalog = mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode catalogJson = objectMapper.readTree(catalog);
        assertThat(catalogJson.path("currentSubscription").path("planPriceEntryId").asText())
                .isEqualTo(planPrice.getId().toString());
        String selectableProducts = catalogJson.path("plans").toString();
        assertThat(selectableProducts).doesNotContain(planPrice.getId().toString());
        assertThat(selectableProducts).doesNotContain(addOnPrice.getId().toString());
        assertThat(selectableProducts).doesNotContain(packagePrice.getId().toString());
    }

    @Test
    void samePlanMayChangeToAnotherExactBillingCyclePrice() throws Exception {
        String adminToken = loginAdminAndGetToken();
        var free = planRepository.findByCode("FLEX").orElseThrow();
        var addOn = addOnRepository.findByCode("CUSTOM_ROLES").orElseThrow();
        var quotaPackage = quotaPackageRepository.findByCode("MEMBERS_5").orElseThrow();
        var annualPrice = ensureActivePrice(
                adminToken,
                ProductPriceOwnerType.PLAN,
                free.getId(),
                BigDecimal.ZERO,
                BillingCycle.YEARLY,
                Instant.now().minusSeconds(30));
        var annualAddOnPrice = ensureActivePrice(
                adminToken, ProductPriceOwnerType.ADD_ON, addOn.getId(),
                BigDecimal.ZERO, BillingCycle.YEARLY, Instant.now().minusSeconds(30));
        var annualPackagePrice = ensureActivePrice(
                adminToken, ProductPriceOwnerType.QUOTA_PACKAGE, quotaPackage.getId(),
                BigDecimal.ZERO, BillingCycle.YEARLY, Instant.now().minusSeconds(30));

        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        var monthlyPlanPrice = productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, free.getId(), "USD", BillingCycle.MONTHLY, Instant.now())
                .getFirst();
        applyReviewedAdminSubscriptionChange(adminToken, accountId, new SubscriptionChangeRequest(
                free.getCode(), Set.of(addOn.getCode()),
                List.of(new QuotaPackageSelection(quotaPackage.getCode(), 1)),
                SubscriptionChangeTiming.IMMEDIATE,
                new ProductPriceSelectionRequest(
                        monthlyPlanPrice.getId(), "USD", BillingCycle.MONTHLY)));
        var before = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        var monthlySnapshot = before.getEntitlementSnapshot();
        assertThat(monthlySnapshot.billingCycle()).isEqualTo(BillingCycle.MONTHLY);

        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                free.getCode(), Set.of(addOn.getCode()),
                List.of(new QuotaPackageSelection(quotaPackage.getCode(), 2)),
                SubscriptionChangeTiming.IMMEDIATE,
                new ProductPriceSelectionRequest(
                        annualPrice.getId(), "USD", BillingCycle.YEARLY));

        applySubscriptionChange(clientToken, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operation.status").value("APPLIED"));

        var changed = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        assertThat(changed.getId()).isNotEqualTo(before.getId());
        assertThat(changed.getEntitlementSnapshot().billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(changed.getEntitlementSnapshot().planPriceEntryId())
                .isEqualTo(annualPrice.getId());
        assertThat(changed.getEntitlementSnapshot().addOns()).singleElement()
                .satisfies(item -> {
                    assertThat(item.billingCycle()).isEqualTo(BillingCycle.YEARLY);
                    assertThat(item.priceEntryId()).isEqualTo(annualAddOnPrice.getId());
                    assertThat(item.priceEntryId()).isNotEqualTo(
                            monthlySnapshot.addOns().getFirst().priceEntryId());
                });
        assertThat(changed.getEntitlementSnapshot().quotaPackages()).singleElement()
                .satisfies(item -> {
                    assertThat(item.quantity()).isEqualTo(2);
                    assertThat(item.billingCycle()).isEqualTo(BillingCycle.YEARLY);
                    assertThat(item.priceEntryId()).isEqualTo(annualPackagePrice.getId());
                    assertThat(item.priceEntryId()).isNotEqualTo(
                            monthlySnapshot.quotaPackages().getFirst().priceEntryId());
                });

        mockMvc.perform(get("/api/v1/subscriptions/catalog")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSubscription.planPriceEntryId")
                        .value(annualPrice.getId().toString()))
                .andExpect(jsonPath("$.currentSubscription.billingCycle").value("YEARLY"));
    }

    @Test
    void legacyAdminCreateAndTrialRoutesAreRetiredAndReviewedChangeKeepsExactPrice() throws Exception {
        String adminToken = loginAdminAndGetToken();
        var pro = planRepository.findByCode("PRO").orElseThrow();
        Instant starts = Instant.now().minusSeconds(30);
        JsonNode proAnnual = activateNewPrice(adminToken, ProductPriceOwnerType.PLAN, pro.getId(),
                new BigDecimal("240.00"), "USD", BillingCycle.YEARLY, starts);
        ProductPriceSelectionRequest proSelection = new ProductPriceSelectionRequest(
                UUID.fromString(proAnnual.get("id").asText()), "USD", BillingCycle.YEARLY);

        String subscriberToken = registerClientAndGetToken();
        UUID subscriberAccountId = currentAccountId(subscriberToken);
        var before = subscriptionRepository.findActiveByAccountId(subscriberAccountId).orElseThrow();
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}", subscriberAccountId)
                        .param("planCode", pro.getCode())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proSelection)))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/admin/subscriptions/account/{accountId}/trial", subscriberAccountId)
                        .param("planCode", pro.getCode())
                        .param("trialDays", "14")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proSelection)))
                .andExpect(status().isNotFound());
        assertThat(subscriptionRepository.findActiveByAccountId(subscriberAccountId).orElseThrow().getId())
                .isEqualTo(before.getId());

        applyReviewedAdminSubscriptionChange(
                adminToken,
                subscriberAccountId,
                new SubscriptionChangeRequest(
                        pro.getCode(), Set.of(), List.of(), SubscriptionChangeTiming.IMMEDIATE,
                        proSelection));

        var annualSubscription = subscriptionRepository.findActiveByAccountId(subscriberAccountId).orElseThrow();
        assertThat(annualSubscription.getId()).isNotEqualTo(before.getId());
        assertThat(annualSubscription.getEntitlementSnapshot().planPriceEntryId())
                .isEqualTo(proSelection.priceEntryId());
        assertThat(annualSubscription.getEntitlementSnapshot().billingCycle())
                .isEqualTo(BillingCycle.YEARLY);
        assertThat(annualSubscription.getCurrentPriceCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void concurrentOverlappingActivationsPublishExactlyOneEntry() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = createActivePlan(adminToken);
        Instant starts = Instant.now().minusSeconds(30);
        JsonNode first = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("10.00"), BillingCycle.YEARLY, starts, null);
        JsonNode second = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("11.00"), BillingCycle.YEARLY, starts, null);
        String firstPreviewToken = fetchProductPriceActivationToken(
                adminToken, UUID.fromString(first.get("id").asText()));
        String secondPreviewToken = fetchProductPriceActivationToken(
                adminToken, UUID.fromString(second.get("id").asText()));
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<HttpResult> left = CompletableFuture.supplyAsync(
                () -> activateAfter(start, adminToken, first, firstPreviewToken));
        CompletableFuture<HttpResult> right = CompletableFuture.supplyAsync(
                () -> activateAfter(start, adminToken, second, secondPreviewToken));
        start.countDown();

        List<HttpResult> results = List.of(left.join(), right.join());
        assertThat(results).extracting(HttpResult::status).containsExactlyInAnyOrder(200, 409);
        assertThat(results.stream().filter(result -> result.status() == 409).findFirst().orElseThrow().body())
                .contains("STALE_ACTIVATION_PREVIEW");
        assertThat(productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, planId, "USD", BillingCycle.YEARLY, Instant.now()))
                .hasSize(1);
    }

    @Test
    void standaloneFiniteWindowsCannotPublishAndExpiredWindowsStayInvalid() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID planId = createActivePlan(adminToken);
        Instant now = Instant.now();
        Instant boundary = now.plusSeconds(3600);

        JsonNode current = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("20.00"), BillingCycle.YEARLY, now.minusSeconds(60), boundary);
        mockMvc.perform(get("/api/admin/product-prices/{id}/activation-preview", current.get("id").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers", hasItem("CONTINUOUS_PRICE_REQUIRED")));
        JsonNode successor = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("25.00"), BillingCycle.YEARLY, boundary, boundary.plusSeconds(3600));
        mockMvc.perform(get("/api/admin/product-prices/{id}/activation-preview", successor.get("id").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers", hasItem("CONTINUOUS_PRICE_REQUIRED")));

        JsonNode expired = createPrice(adminToken, ProductPriceOwnerType.PLAN, planId,
                new BigDecimal("15.00"), BillingCycle.YEARLY,
                now.minusSeconds(7200), now.minusSeconds(3600));
        mockMvc.perform(get("/api/admin/product-prices/{id}/activation-preview", expired.get("id").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activatable").value(false))
                .andExpect(jsonPath("$.blockers[0]").value("EFFECTIVE_WINDOW_EXPIRED"));
        mockMvc.perform(post("/api/admin/product-prices/{id}/activate", expired.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceActivationRequest(
                                        expired.get("version").asLong(), "Attempt expired activation",
                                        fetchProductPriceActivationToken(adminToken,
                                                UUID.fromString(expired.get("id").asText()))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void continuousPriceChangeLeavesExistingSubscriberSnapshotUntouched() throws Exception {
        String token = loginAdminAndGetToken();
        var free = planRepository.findByCode("FREE").orElseThrow();
        var current = productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, free.getId(), "USD", BillingCycle.MONTHLY, Instant.now()).getFirst();
        String clientToken = registerClientAndGetToken();
        UUID accountId = currentAccountId(clientToken);
        var before = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        var snapshot = before.getEntitlementSnapshot();
        assertThat(snapshot.planPriceEntryId()).isEqualTo(current.getId());

        var change = new com.hiveapp.platform.client.plan.dto.ProductPriceChangeRequest(
                com.hiveapp.platform.client.plan.dto.ProductPriceChangeRequest.Operation.CHANGE,
                current.getVersion(), null, null,
                com.hiveapp.platform.client.plan.dto.ProductPriceChangeRequest.Timing.SCHEDULED,
                BigDecimal.ONE, Instant.now().plusSeconds(7200), "Schedule new subscriber price");
        JsonNode review = responseJson(mockMvc.perform(post("/api/admin/product-prices/{id}/change-preview", current.getId())
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(change))).andExpect(status().isOk()));
        mockMvc.perform(post("/api/admin/product-prices/{id}/change", current.getId())
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "change", review.get("change"), "previewToken", review.get("previewToken").asText(),
                        "idempotencyKey", UUID.randomUUID())))).andExpect(status().isOk());
        var after = subscriptionRepository.findActiveByAccountId(accountId).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getEntitlementSnapshot()).isEqualTo(snapshot);
        current = productPriceRepository.findById(current.getId()).orElseThrow();
        var cancel = new com.hiveapp.platform.client.plan.dto.ProductPriceChangeRequest(
                com.hiveapp.platform.client.plan.dto.ProductPriceChangeRequest.Operation.CANCEL,
                current.getVersion(), null, null, null, null, null, "Restore shared demo fixture");
        JsonNode cancellation = responseJson(mockMvc.perform(post("/api/admin/product-prices/{id}/change-preview", current.getId())
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancel))).andExpect(status().isOk()));
        mockMvc.perform(post("/api/admin/product-prices/{id}/cancel-change", current.getId())
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "change", cancellation.get("change"), "previewToken", cancellation.get("previewToken").asText(),
                        "idempotencyKey", UUID.randomUUID())))).andExpect(status().isOk());
    }

    @Test
    void priceIdentityIsBoundToTheExactProductRevision() throws Exception {
        String adminToken = loginAdminAndGetToken();
        UUID sourceId = createActivePlan(adminToken);
        var source = planRepository.findById(sourceId).orElseThrow();
        UUID sourcePriceId = productPriceRepository.findApplicable(
                        ProductPriceOwnerType.PLAN, sourceId, "USD", BillingCycle.MONTHLY, Instant.now())
                .getFirst().getId();

        PlanBranchRequest branch = new PlanBranchRequest(
                "Exact revision " + UUID.randomUUID(), null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY);
        String revisionResponse = mockMvc.perform(post("/api/admin/plans/{id}/revisions", sourceId)
                        .param("expectedVersion", String.valueOf(source.getVersion()))
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branch)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode revisionJson = objectMapper.readTree(revisionResponse);
        UUID revisionId = UUID.fromString(revisionJson.get("id").asText());
        publishPlanPriceDrafts(adminToken, revisionId);
        mockMvc.perform(post("/api/admin/plans/{id}/lifecycle", revisionId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlanLifecycleRequest(
                                PlanLifecycleAction.ACTIVATE, revisionJson.get("version").asLong(), null,
                                fetchPlanActivationToken(adminToken, revisionId)))))
                .andExpect(status().isOk());
        var revision = planRepository.findById(revisionId).orElseThrow();
        assertThat(revision.getLineageId()).isEqualTo(source.getLineageId());
        assertThat(productPriceRepository.findApplicable(
                ProductPriceOwnerType.PLAN, revisionId, "USD", BillingCycle.MONTHLY, Instant.now()))
                .singleElement().satisfies(price -> assertThat(price.getId()).isNotEqualTo(sourcePriceId));

        String clientToken = registerClientAndGetToken();
        SubscriptionChangeRequest request = new SubscriptionChangeRequest(
                revision.getCode(), Set.of(), List.of(), SubscriptionChangeTiming.IMMEDIATE,
                new ProductPriceSelectionRequest(sourcePriceId, "USD", BillingCycle.MONTHLY));
        mockMvc.perform(post("/api/v1/subscriptions/preview")
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private HttpResult activateAfter(
            CountDownLatch start,
            String token,
            JsonNode draft,
            String previewToken
    ) {
        try {
            start.await();
            var response = mockMvc.perform(post("/api/admin/product-prices/{id}/activate", draft.get("id").asText())
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ProductPriceActivationRequest(
                                            draft.get("version").asLong(), "Concurrent price activation",
                                            previewToken))))
                    .andReturn().getResponse();
            return new HttpResult(response.getStatus(), response.getContentAsString());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private UUID createActivePlan(String token) throws Exception {
        var source = planRepository.findByCode("FREE").orElseThrow();
        PlanBranchRequest request = new PlanBranchRequest(
                "Price fixture " + UUID.randomUUID(), null, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY);
        String response = mockMvc.perform(post("/api/admin/plans/{id}/duplicate", source.getId())
                        .param("expectedVersion", String.valueOf(source.getVersion()))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(response);
        UUID id = UUID.fromString(created.get("id").asText());
        publishPlanPriceDrafts(token, id);
        mockMvc.perform(post("/api/admin/plans/{id}/lifecycle", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlanLifecycleRequest(
                                PlanLifecycleAction.ACTIVATE, created.get("version").asLong(), null,
                                fetchPlanActivationToken(token, id)))))
                .andExpect(status().isOk());
        return id;
    }

    private void publishPlanPriceDrafts(String token, UUID planId) throws Exception {
        for (var price : productPriceRepository.findAllByPlanId(planId)) {
            if (price.getStatus()
                    == com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.DRAFT) {
                activate(token, price.getId(), price.getVersion());
            }
        }
    }

    private JsonNode activateNewPrice(String token, ProductPriceOwnerType type, UUID ownerId,
                                      BigDecimal amount, BillingCycle cycle, Instant starts) throws Exception {
        return activateNewPrice(token, type, ownerId, amount, "USD", cycle, starts);
    }

    private com.hiveapp.platform.client.plan.domain.entity.ProductPrice ensureActivePrice(
            String token,
            ProductPriceOwnerType type,
            UUID ownerId,
            BigDecimal amount,
            BillingCycle cycle,
            Instant starts
    ) throws Exception {
        var existing = productPriceRepository.findApplicable(
                type, ownerId, "USD", cycle, Instant.now());
        if (!existing.isEmpty()) return existing.getFirst();
        JsonNode activated = activateNewPrice(token, type, ownerId, amount, cycle, starts);
        return productPriceRepository.findById(
                UUID.fromString(activated.get("id").asText())).orElseThrow();
    }

    private JsonNode activateNewPrice(String token, ProductPriceOwnerType type, UUID ownerId,
                                      BigDecimal amount, String currencyCode,
                                      BillingCycle cycle, Instant starts) throws Exception {
        JsonNode draft = createPrice(token, type, ownerId, amount, currencyCode, cycle, starts, null);
        return activate(token, UUID.fromString(draft.get("id").asText()), draft.get("version").asLong());
    }

    private JsonNode createPrice(String token, ProductPriceOwnerType type, UUID ownerId,
                                 BigDecimal amount, BillingCycle cycle, Instant starts,
                                 Instant until) throws Exception {
        return createPrice(token, type, ownerId, amount, "USD", cycle, starts, until);
    }

    private JsonNode createPrice(String token, ProductPriceOwnerType type, UUID ownerId,
                                 BigDecimal amount, String currencyCode, BillingCycle cycle,
                                 Instant starts, Instant until) throws Exception {
        String response = mockMvc.perform(post("/api/admin/product-prices")
                        .header("Authorization", bearer(token))
                        .param("ownerType", type.name())
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateProductPriceRequest(amount, currencyCode, cycle, starts, until))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode activate(String token, UUID priceId, long version) throws Exception {
        String previewToken = fetchProductPriceActivationToken(token, priceId);
        return applyActivation(token, priceId, new ProductPriceActivationRequest(
                version, "Activate price", previewToken));
    }

    private JsonNode priceActivationPreview(String token, UUID priceId) throws Exception {
        String response = mockMvc.perform(
                        get("/api/admin/product-prices/{id}/activation-preview", priceId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode applyActivation(
            String token,
            UUID priceId,
            ProductPriceActivationRequest request
    ) throws Exception {
        String response = mockMvc.perform(post("/api/admin/product-prices/{id}/activate", priceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode applyReactivation(
            String token,
            UUID priceId,
            ProductPriceActivationRequest request
    ) throws Exception {
        String response = mockMvc.perform(post("/api/admin/product-prices/{id}/reactivate", priceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void assertRejectedActivation(
            String token,
            UUID priceId,
            ProductPriceActivationRequest request
    ) throws Exception {
        mockMvc.perform(post("/api/admin/product-prices/{id}/activate", priceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_ACTIVATION_PREVIEW"));
        assertThat(productPriceRepository.findById(priceId).orElseThrow().getStatus().name())
                .isEqualTo("DRAFT");
    }

    private void assertRejectedReactivation(
            String token,
            UUID priceId,
            ProductPriceActivationRequest request
    ) throws Exception {
        mockMvc.perform(post("/api/admin/product-prices/{id}/reactivate", priceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_ACTIVATION_PREVIEW"));
        assertThat(productPriceRepository.findById(priceId).orElseThrow().getStatus().name())
                .isEqualTo("INACTIVE");
    }

    private String issueEvidence(
            CommercialPreviewKind kind,
            UUID resourceId,
            long version,
            UUID actorUserId,
            long catalogRevision,
            String registryVersion,
            String fingerprint,
            Instant evaluatedAt
    ) {
        return commercialPreviewTokenService.issue(
                kind, resourceId, version, actorUserId, catalogRevision,
                registryVersion, fingerprint, evaluatedAt).token();
    }

    private PreviewClaims previewClaims(String token) {
        String encodedPayload = token.substring(0, token.indexOf('.'));
        String[] claims = new String(
                Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8)
                .split("\\n", -1);
        return new PreviewClaims(
                CommercialPreviewKind.valueOf(claims[1]),
                UUID.fromString(claims[2]),
                Long.parseLong(claims[3]),
                UUID.fromString(claims[4]),
                Long.parseLong(claims[5]),
                claims[6],
                Instant.ofEpochMilli(Long.parseLong(claims[7])),
                claims[9]);
    }

    // Preserve coverage for historical INACTIVE records; the public pause command is retired.
    private JsonNode legacyPausedPrice(String token, UUID priceId, long version) throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            var price = productPriceRepository.findById(priceId).orElseThrow();
            assertThat(price.getVersion()).isEqualTo(version);
            price.pause();
            productPriceRepository.saveAndFlush(price);
        });
        return responseJson(mockMvc.perform(get("/api/admin/product-prices/{id}", priceId)
                .header("Authorization", bearer(token))).andExpect(status().isOk()));
    }

    private JsonNode revise(String token, UUID priceId, long version) throws Exception {
        String response = mockMvc.perform(post("/api/admin/product-prices/{id}/revisions", priceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductPriceVersionRequest(version, "Create successor price revision"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode lifecycle(String token, UUID priceId, long version, String action) throws Exception {
        Object request = "reactivate".equals(action)
                ? new ProductPriceActivationRequest(
                        version, "Price lifecycle " + action,
                        fetchProductPriceActivationToken(token, priceId))
                : new ProductPriceVersionRequest(version, "Price lifecycle " + action);
        String response = mockMvc.perform(post("/api/admin/product-prices/{id}/{action}", priceId, action)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private org.springframework.test.web.servlet.ResultActions applySubscriptionChange(
            String token,
            SubscriptionChangeRequest request
    ) throws Exception {
        String previewResponse = mockMvc.perform(post("/api/v1/subscriptions/preview")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String previewToken = objectMapper.readTree(previewResponse).get("previewToken").asText();
        return mockMvc.perform(post("/api/v1/subscriptions/apply")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "selection", request,
                        "previewToken", previewToken))));
    }

    private UUID currentAccountId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.ResultActions result)
            throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private record HttpResult(int status, String body) {}

    private record PreviewClaims(
            CommercialPreviewKind kind,
            UUID resourceId,
            long version,
            UUID actorUserId,
            long catalogRevision,
            String registryVersion,
            Instant issuedAt,
            String fingerprint
    ) {}
}
