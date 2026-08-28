package com.hiveapp.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.shared.quota.QuotaLimitMode;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class CommercialOfferControlPlaneIntegrationTest extends PlatformShellIntegrationTestSupport {

  @Autowired private AdminUserRepository adminUsers;
  @Autowired private PlanFeatureRepository planFeatures;
  @Autowired private ProductPriceRepository prices;
  @Autowired private CommercialCampaignRepository campaigns;
  @Autowired private CommercialCampaignAudienceSnapshotRepository audiences;
  @Autowired private CommercialOfferLineageRepository lineages;
  @Autowired private CommercialOfferRepository offers;
  @Autowired private CommercialOfferCapacityRepository capacities;
  @Autowired private CommercialOfferRedemptionRepository redemptions;
  @Autowired private com.hiveapp.platform.client.plan.service.CommercialOfferCodeHasher codeHasher;

  @Test
  void publishRetireAndRestoreRequireFreshEvidenceAndReturnMinimalAcknowledgements()
      throws Exception {
    String token = loginAdminAndGetToken();
    OfferFixture fixture = offerFixture(false, null);

    JsonNode reviewed =
        response(
            mockMvc
                .perform(
                    get("/api/admin/offers/{id}/publication-preview", fixture.offer().getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers").isEmpty()));
    JsonNode published =
        response(
            mockMvc
                .perform(
                    post("/api/admin/offers/{id}/publish", fixture.offer().getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                new CommercialOfferRequests.Publish(
                                    fixture.offer().getVersion(),
                                    "Publish reviewed commercial terms",
                                    reviewed.get("previewToken").asText()))))
                .andExpect(status().isOk()));
    assertMinimalMutation(published, "PUBLISHED");
    assertThat(
            lineages.findById(fixture.offer().getLineageId()).orElseThrow().getFirstPublishedAt())
        .isNotNull();
    mockMvc
        .perform(
            get("/api/admin/offers/{id}/editable-definition", fixture.offer().getId())
                .header("Authorization", bearer(token)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE"));

    JsonNode retired =
        response(
            mockMvc
                .perform(
                    post("/api/admin/offers/{id}/retire", fixture.offer().getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                new CommercialOfferRequests.VersionReason(
                                    published.get("version").asLong(), "Pause new reservations"))))
                .andExpect(status().isOk()));
    assertMinimalMutation(retired, "RETIRED");

    mockMvc
        .perform(
            post("/api/admin/offers/{id}/restore", fixture.offer().getId())
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CommercialOfferRequests.Publish(
                            retired.get("version").asLong(),
                            "Do not trust stale publication evidence",
                            reviewed.get("previewToken").asText()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STALE_OFFER_PREVIEW"));

    JsonNode restoreReview =
        response(
            mockMvc
                .perform(
                    get("/api/admin/offers/{id}/publication-preview", fixture.offer().getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers").isEmpty()));
    JsonNode restored =
        response(
            mockMvc
                .perform(
                    post("/api/admin/offers/{id}/restore", fixture.offer().getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                new CommercialOfferRequests.Publish(
                                    retired.get("version").asLong(),
                                    "Restore after a fresh review",
                                    restoreReview.get("previewToken").asText()))))
                .andExpect(status().isOk()));
    assertMinimalMutation(restored, "PUBLISHED");

    mockMvc
        .perform(
            get("/api/admin/offers/{id}/history", fixture.offer().getId())
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id").isNotEmpty())
        .andExpect(jsonPath("$.content[*].actorEmail", hasItem(ADMIN_EMAIL)))
        .andExpect(jsonPath("$.content[*].reason", hasItem("Publish reviewed commercial terms")))
        .andExpect(jsonPath("$.content[*].reason", hasItem("Pause new reservations")))
        .andExpect(jsonPath("$.content[*].reason", hasItem("Restore after a fresh review")));
  }

  @Test
  void clientAcceptanceIsIdempotentAndOwnHistoryRetainsExactAcceptedTerms() throws Exception {
    String clientToken = registerClientAndGetToken();
    UUID accountId = currentAccountId(clientToken);
    OfferFixture fixture = offerFixture(true, null);

    JsonNode preview =
        response(
            mockMvc
                .perform(
                    post("/api/v1/subscriptions/offers/{id}/preview", fixture.offer().getId())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk()));
    String key = "accept-" + UUID.randomUUID();
    String body =
        objectMapper.writeValueAsString(
            new CommercialOfferRequests.Accept(
                preview.get("previewToken").asText(), "Accept exact terms"));
    JsonNode accepted =
        response(
            mockMvc
                .perform(
                    post("/api/v1/subscriptions/offers/{id}/accept", fixture.offer().getId())
                        .header("Authorization", bearer(clientToken))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.acceptedTerms.finalPrice").isString())
                .andExpect(jsonPath("$.acceptedTerms.currencyCode").value("USD")));

    mockMvc
        .perform(
            post("/api/v1/subscriptions/offers/{id}/accept", fixture.offer().getId())
                .header("Authorization", bearer(clientToken))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(true))
        .andExpect(jsonPath("$.redemptionId").value(accepted.get("redemptionId").asText()));

    mockMvc
        .perform(
            post("/api/v1/subscriptions/offers/{id}/accept", fixture.offer().getId())
                .header("Authorization", bearer(clientToken))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CommercialOfferRequests.Accept(
                            preview.get("previewToken").asText(), "Changed request fingerprint"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE"));

    mockMvc
        .perform(
            get(
                    "/api/v1/subscriptions/offers/redemptions/{id}",
                    UUID.fromString(accepted.get("redemptionId").asText()))
                .header("Authorization", bearer(clientToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offerRevisionNumber").value(1))
        .andExpect(
            jsonPath("$.acceptedTerms.finalPrice")
                .value(accepted.at("/acceptedTerms/finalPrice").asText()))
        .andExpect(jsonPath("$.selection.plan.code").value(fixture.planPrice().getPlan().getCode()))
        .andExpect(jsonPath("$.campaignId").doesNotExist())
        .andExpect(jsonPath("$.offerLineageId").doesNotExist());
    assertThat(
            redemptions.findAllByAccount_Id(
                accountId, org.springframework.data.domain.Pageable.unpaged()))
        .hasSize(1);
  }

  @Test
  void concurrentAccountsCannotBothConsumeTheLastGlobalSlot() throws Exception {
    String firstToken = registerClientAndGetToken();
    String secondToken = registerClientAndGetToken();
    OfferFixture fixture = offerFixture(true, 1L);
    String firstPreview = previewToken(firstToken, fixture.offer().getId());
    String secondPreview = previewToken(secondToken, fixture.offer().getId());
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<Integer> first =
          CompletableFuture.supplyAsync(
              () ->
                  acceptStatus(
                      firstToken,
                      fixture.offer().getId(),
                      firstPreview,
                      "last-slot-" + UUID.randomUUID(),
                      start),
              pool);
      CompletableFuture<Integer> second =
          CompletableFuture.supplyAsync(
              () ->
                  acceptStatus(
                      secondToken,
                      fixture.offer().getId(),
                      secondPreview,
                      "last-slot-" + UUID.randomUUID(),
                      start),
              pool);
      start.countDown();
      assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(201, 409);
    } finally {
      pool.shutdownNow();
    }
    CommercialOfferCapacity capacity =
        capacities.findByLineageId(fixture.offer().getLineageId()).orElseThrow();
    assertThat(capacity.getReservedCount() + capacity.getAppliedCount()).isEqualTo(1);
    mockMvc
        .perform(
            get("/api/v1/subscriptions/offers")
                .header("Authorization", bearer(secondToken))
                .param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[?(@.id == '%s')]".formatted(fixture.offer().getId().toString()))
                .isEmpty());
    mockMvc
        .perform(
            get("/api/v1/subscriptions/offers/{id}", fixture.offer().getId())
                .header("Authorization", bearer(secondToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("OFFER_NOT_AVAILABLE"));
  }

  @Test
  void concurrentIdenticalAcceptanceCreatesOneRedemptionAndReplaysTheOther() throws Exception {
    String token = registerClientAndGetToken();
    UUID accountId = currentAccountId(token);
    OfferFixture fixture = offerFixture(true, null);
    String preview = previewToken(token, fixture.offer().getId());
    String key = "identical-" + UUID.randomUUID();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<Integer> first =
          CompletableFuture.supplyAsync(
              () -> acceptStatus(token, fixture.offer().getId(), preview, key, start), pool);
      CompletableFuture<Integer> second =
          CompletableFuture.supplyAsync(
              () -> acceptStatus(token, fixture.offer().getId(), preview, key, start), pool);
      start.countDown();
      assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(201, 200);
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            redemptions.findAllByAccount_Id(
                accountId, org.springframework.data.domain.Pageable.unpaged()))
        .hasSize(1);
  }

  @Test
  void signedClientPreviewCannotBeSubstitutedAcrossAccounts() throws Exception {
    String firstToken = registerClientAndGetToken();
    String secondToken = registerClientAndGetToken();
    UUID secondAccountId = currentAccountId(secondToken);
    OfferFixture fixture = offerFixture(true, null);
    String firstPreview = previewToken(firstToken, fixture.offer().getId());

    mockMvc
        .perform(
            post("/api/v1/subscriptions/offers/{id}/accept", fixture.offer().getId())
                .header("Authorization", bearer(secondToken))
                .header("Idempotency-Key", "substitution-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CommercialOfferRequests.Accept(firstPreview, null))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STALE_OFFER_PREVIEW"));

    assertThat(
            redemptions.findAllByAccount_Id(
                secondAccountId, org.springframework.data.domain.Pageable.unpaged()))
        .isEmpty();
  }

  @Test
  void chooserComparisonAndOwnerContractsContainTheStateNeededByOperators() throws Exception {
    String token = loginAdminAndGetToken();
    OfferFixture fixture = offerFixture(false, null);

    mockMvc
        .perform(
            get("/api/admin/offers/product-price-choices")
                .header("Authorization", bearer(token))
                .param("ownerType", "PLAN")
                .param("search", fixture.planPrice().getPlan().getCode()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].productRevisionNumber").isNumber())
        .andExpect(jsonPath("$.content[0].priceRevisionNumber").isNumber())
        .andExpect(jsonPath("$.content[0].effectiveFrom").isNotEmpty())
        .andExpect(jsonPath("$.content[0].compatibility.salesVisibility").isNotEmpty());
    mockMvc
        .perform(
            get("/api/admin/offers/campaign-choices")
                .header("Authorization", bearer(token))
                .param("search", fixture.campaign().getCode()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].revisionNumber").value(1))
        .andExpect(jsonPath("$.content[0].audienceMode").value("PUBLIC"))
        .andExpect(jsonPath("$.content[0].startsAt").isNotEmpty())
        .andExpect(jsonPath("$.content[0].endsAt").isNotEmpty());
    mockMvc
        .perform(
            get("/api/admin/offers/quota-resource-choices")
                .header("Authorization", bearer(token))
                .param("selectedPriceIds", fixture.planPrice().getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].featureCode").isNotEmpty());

    fixture.offer().publish(Instant.now());
    fixture.offer().getLineage().markFirstPublished(Instant.now());
    offers.saveAndFlush(fixture.offer());
    CommercialOffer successor = fixture.offer().revise(2);
    successor.edit(
        "A clearer successor",
        fixture.offer().getDescription(),
        fixture.offer().getStartsAt(),
        fixture.offer().getEndsAt(),
        fixture.offer().getSelection(),
        fixture.offer().getEffects());
    successor = offers.saveAndFlush(successor);

    mockMvc
        .perform(
            get(
                    "/api/admin/offers/{id}/compare/{other}",
                    fixture.offer().getId(),
                    successor.getId())
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.changedFields", hasItem("name")))
        .andExpect(jsonPath("$.left.name").value(fixture.offer().getName()))
        .andExpect(jsonPath("$.left.businessCode").isNotEmpty())
        .andExpect(jsonPath("$.left.campaignCode").value(fixture.campaign().getCode()))
        .andExpect(jsonPath("$.left.discovery").value("CATALOG"))
        .andExpect(jsonPath("$.left.acceptance").value("CLIENT_OR_OPERATOR"))
        .andExpect(jsonPath("$.right.name").value("A clearer successor"));
    mockMvc
        .perform(
            get("/api/admin/offers/{id}/owner", fixture.offer().getId())
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offerId").value(fixture.offer().getId().toString()))
        .andExpect(jsonPath("$.offerStatus").value("PUBLISHED"))
        .andExpect(jsonPath("$.offerVersion").isNumber())
        .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
  }

  @Test
  void publishedCustomerCodeRemainsReservedAfterRetirementAndArchive() throws Exception {
    String token = loginAdminAndGetToken();
    String code = "PERMANENT_" + UUID.randomUUID().toString().substring(0, 8);
    String hash = codeHasher.hash(code);
    OfferFixture first = offerFixture(false, null, CommercialOfferDiscovery.CODE_ONLY, hash);
    OfferFixture second = offerFixture(false, null, CommercialOfferDiscovery.CODE_ONLY, hash);

    JsonNode firstReview = publicationPreview(token, first.offer().getId());
    JsonNode published =
        publish(token, first.offer().getId(), firstReview, first.offer().getVersion());
    JsonNode retired =
        response(
            mockMvc
                .perform(
                    post("/api/admin/offers/{id}/retire", first.offer().getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                new CommercialOfferRequests.VersionReason(
                                    published.get("version").asLong(),
                                    "End the first coded Offer"))))
                .andExpect(status().isOk()));
    mockMvc
        .perform(
            post("/api/admin/offers/{id}/archive", first.offer().getId())
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CommercialOfferRequests.VersionReason(
                            retired.get("version").asLong(), "Archive the completed Offer"))))
        .andExpect(status().isOk());

    JsonNode secondReview = publicationPreview(token, second.offer().getId());
    mockMvc
        .perform(
            post("/api/admin/offers/{id}/publish", second.offer().getId())
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CommercialOfferRequests.Publish(
                            second.offer().getVersion(),
                            "Attempt to reuse a permanently reserved code",
                            secondReview.get("previewToken").asText()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OFFER_CODE_CONFLICT"));
  }

  private OfferFixture offerFixture(boolean activeCampaign, Long globalLimit) {
    return offerFixture(activeCampaign, globalLimit, CommercialOfferDiscovery.CATALOG, null);
  }

  private OfferFixture offerFixture(
      boolean activeCampaign,
      Long globalLimit,
      CommercialOfferDiscovery discovery,
      String customerCodeHash) {
    Instant now = Instant.now();
    var owner = adminUsers.findByUser_Email(ADMIN_EMAIL).orElseThrow();
    ProductPrice planPrice =
        prices.findAllApplicable(now).stream()
            .filter(price -> price.getOwnerType() == ProductPriceOwnerType.PLAN)
            .filter(price -> price.getPlan().getStatus() == PlanStatus.ACTIVE)
            .filter(price -> !"FREE".equals(price.getPlan().getCode()))
            .filter(
                price ->
                    planFeatures.findAllByPlanId(price.getPlan().getId()).stream()
                        .filter(feature -> feature.getMode() == PlanFeatureMode.INCLUDED)
                        .flatMap(feature -> feature.getQuotaConfigs().stream())
                        .anyMatch(limit -> limit.mode() == QuotaLimitMode.FINITE))
            .findFirst()
            .orElseThrow();
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    CommercialCampaign campaign =
        CommercialCampaign.draft(
            "OFFER_CAMPAIGN_" + suffix,
            "Offer Campaign " + suffix,
            null,
            now.minusSeconds(120),
            now.plusSeconds(7200),
            CommercialCampaignSource.MARKETING,
            "Offer integration fixture",
            owner);
    campaign.configurePublicAudience();
    campaign.schedule(now.minusSeconds(90));
    if (activeCampaign) campaign.start(now.minusSeconds(60));
    campaign = campaigns.saveAndFlush(campaign);
    audiences.saveAndFlush(
        CommercialCampaignAudienceSnapshot.record(
            campaign,
            owner.getUser().getId(),
            now.minusSeconds(100),
            now.plusSeconds(100),
            campaign.getVersion(),
            0,
            registryCatalogVersionService.currentVersion(),
            "a".repeat(64),
            null,
            null,
            "Freeze public audience",
            Set.of()));
    CommercialOfferLineage lineage =
        lineages.saveAndFlush(
            CommercialOfferLineage.create(
                "OFFER_" + suffix,
                campaign,
                discovery,
                CommercialOfferAcceptance.CLIENT_OR_OPERATOR,
                customerCodeHash,
                globalLimit,
                null,
                owner));
    CommercialOfferSelection selection =
        new CommercialOfferSelection(
            planPrice.getPlan().getId(),
            planPrice.getId(),
            List.of(),
            List.of(),
            SubscriptionChangeTiming.IMMEDIATE);
    CommercialOffer offer =
        CommercialOffer.draft(
            lineage,
            "Offer " + suffix,
            "Exact commercial terms",
            now.minusSeconds(30),
            now.plusSeconds(3600),
            selection,
            new CommercialOfferEffectSnapshot(
                CommercialOfferDiscountType.FIXED,
                new BigDecimal("1.0000"),
                null,
                null,
                List.of()));
    if (activeCampaign) {
      lineage.markFirstPublished(now);
      offer.publish(now);
    }
    offer = offers.saveAndFlush(offer);
    lineages.flush();
    capacities.saveAndFlush(CommercialOfferCapacity.create(lineage.getId()));
    return new OfferFixture(offer, campaign, planPrice);
  }

  private JsonNode publicationPreview(String token, UUID offerId) throws Exception {
    return response(
        mockMvc
            .perform(
                get("/api/admin/offers/{id}/publication-preview", offerId)
                    .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blockers").isEmpty()));
  }

  private JsonNode publish(String token, UUID offerId, JsonNode review, long version)
      throws Exception {
    return response(
        mockMvc
            .perform(
                post("/api/admin/offers/{id}/publish", offerId)
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CommercialOfferRequests.Publish(
                                version,
                                "Publish exact coded terms",
                                review.get("previewToken").asText()))))
            .andExpect(status().isOk()));
  }

  private UUID currentAccountId(String token) throws Exception {
    return UUID.fromString(
        response(
                mockMvc
                    .perform(get("/api/v1/accounts/me").header("Authorization", bearer(token)))
                    .andExpect(status().isOk()))
            .get("id")
            .asText());
  }

  private String previewToken(String token, UUID offerId) {
    try {
      return response(
              mockMvc
                  .perform(
                      post("/api/v1/subscriptions/offers/{id}/preview", offerId)
                          .header("Authorization", bearer(token))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{}"))
                  .andExpect(status().isOk()))
          .get("previewToken")
          .asText();
    } catch (Exception exception) {
      throw new CompletionException(exception);
    }
  }

  private int acceptStatus(
      String token,
      UUID offerId,
      String previewToken,
      String idempotencyKey,
      CountDownLatch start) {
    try {
      start.await();
      return mockMvc
          .perform(
              post("/api/v1/subscriptions/offers/{id}/accept", offerId)
                  .header("Authorization", bearer(token))
                  .header("Idempotency-Key", idempotencyKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new CommercialOfferRequests.Accept(previewToken, null))))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception exception) {
      throw new CompletionException(exception);
    }
  }

  private JsonNode response(org.springframework.test.web.servlet.ResultActions result)
      throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private void assertMinimalMutation(JsonNode response, String status) {
    Set<String> names = new LinkedHashSet<>();
    response.fieldNames().forEachRemaining(names::add);
    assertThat(names).containsExactlyInAnyOrder("offerId", "status", "version");
    assertThat(response.get("status").asText()).isEqualTo(status);
  }

  private record OfferFixture(
      CommercialOffer offer, CommercialCampaign campaign, ProductPrice planPrice) {}
}
