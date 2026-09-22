package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountBillingProfileModels;
import com.hiveapp.platform.client.account.service.AccountBillingProfileService;
import com.hiveapp.platform.client.plan.domain.constant.BillingTimelineEntryType;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeOrigin;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferRedemptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferSurface;
import com.hiveapp.platform.client.plan.domain.constant.RetainedEntitlementState;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyDecisionOutcome;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionAvailabilityReason;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SpecialCommercialAgreementRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialOfferRedemptionRepository;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.ClientCommercialPolicyDecision;
import com.hiveapp.platform.client.plan.dto.CommercialOfferEffectSnapshot;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyDecisionSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeConflict;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.SpecialAgreementModels;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionCommercialPolicyEvaluation;
import com.hiveapp.platform.client.plan.dto.ClientSubscriptionEntitlementState;
import com.hiveapp.platform.client.plan.dto.EffectiveQuotaLimit;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.platform.client.plan.service.BillingCalculator;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.platform.client.plan.service.CommercialOfferService;
import com.hiveapp.platform.client.plan.dto.CommercialOfferRequests;
import com.hiveapp.platform.client.plan.dto.CommercialOfferViews;
import com.hiveapp.platform.client.plan.dto.SubscriptionOfferEvaluation;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotFactory;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.client.plan.service.SubscriptionImpactAnalyzer;
import com.hiveapp.platform.client.plan.service.SubscriptionPeriodCalculator;
import com.hiveapp.platform.client.plan.service.SpecialAgreementSelectionAssessment;
import com.hiveapp.platform.client.plan.service.SubscriptionCheckoutService;
import com.hiveapp.platform.client.plan.service.BillingReadService;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeOperationProjectionMapper;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeActivationService;
import com.hiveapp.platform.client.plan.service.CommercialOfferRedemptionTransitionService;
import com.hiveapp.platform.client.plan.service.ProductPriceResolver;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.client.plan.service.CommercialSelectionFinalizer;
import com.hiveapp.platform.client.plan.service.CommercialPolicyEvaluator;
import com.hiveapp.platform.client.plan.service.CommercialPolicySelectionPlanner;
import com.hiveapp.platform.client.plan.service.CommercialPolicySelectionRules;
import com.hiveapp.platform.client.plan.service.CommercialPolicySubscriptionTermsService;
import com.hiveapp.platform.registry.definition.ClientSubscriptionFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import com.hiveapp.shared.exception.OfferRedemptionBlockedException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.security.EffectivePermissionService;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaLimitMode;
import dev.karroumi.permissionizer.PermissionNode;
import dev.karroumi.permissionizer.PermissionDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = ClientSubscriptionFeature.KEY, description = "Subscription Management", guard = PermissionNode.Guard.ON)
public class SubscriptionServiceImpl extends ClientWorkspaceFeatureService implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final com.hiveapp.platform.client.plan.service.SubscriptionRepricingService repricing;

  private final com.hiveapp.platform.client.plan.service.PlanContentNoticeService contentNotices;

  @Override
  @PermissionNode(
      key = "read_content_notices",
      description = "Read own Account Plan content-change notices")
  public Page<com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Notice>
      listMyContentNotices(UUID accountId, UUID userId, Pageable pageable) {
    return contentNotices.list(accountId, userId, pageable);
  }

  @Override
  @PermissionNode(
      key = "mark_content_notice_read",
      description = "Mark an own Account Plan content notice as read, not as consent")
  public void markContentNoticeRead(UUID accountId, UUID userId, UUID noticeId) {
    contentNotices.markRead(accountId, userId, noticeId);
  }


    @Override @PermissionNode(key = "read_price_notices", description = "Read own Account price-change notices")
    public Page<com.hiveapp.platform.client.plan.dto.RepricingModels.Notice> listMyPriceNotices(UUID accountId, UUID userId, Pageable pageable) {
        return repricing.notices(accountId, userId, pageable);
    }
    @Override @PermissionNode(key = "mark_price_notice_read", description = "Mark an own Account price-change notice as read")
    public void markPriceNoticeRead(UUID accountId, UUID userId, UUID noticeId) { repricing.markRead(accountId, userId, noticeId); }
    private final SpecialCommercialAgreementRepository specialAgreementRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final PlanRepository planRepository;
    private final ProductPriceRepository productPriceRepository;
    private final AccountRepository accountRepository;
    private final BillingCalculator billingCalculator;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotFactory subscriptionSnapshotFactory;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;
    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;
    private final SubscriptionImpactAnalyzer subscriptionImpactAnalyzer;
    private final SubscriptionPeriodCalculator subscriptionPeriodCalculator;
    private final SubscriptionChangeOperationRepository subscriptionChangeOperationRepository;
    private final CommercialOfferRedemptionRepository commercialOfferRedemptionRepository;
    private final CommercialOfferRedemptionTransitionService commercialOfferRedemptionTransitions;
    private final SubscriptionCheckoutService subscriptionCheckoutService;
    private final SubscriptionChangeOperationProjectionMapper operationProjectionMapper;
    private final SubscriptionChangeActivationService subscriptionChangeActivationService;
    private final ProductPriceResolver productPriceResolver;
    private final CommercialCatalogResolver commercialCatalogResolver;
    private final com.hiveapp.platform.client.plan.service.PlanPublicSelection planPublicSelection;
    private final CommercialSelectionFinalizer commercialSelectionFinalizer;
    private final CommercialPolicyEvaluator commercialPolicyEvaluator;
    private final CommercialPolicySelectionPlanner commercialPolicySelectionPlanner;
    private final CommercialPolicySubscriptionTermsService commercialPolicyTermsService;
    private final CommercialCatalogVersionService commercialCatalogVersionService;
    private final RegistryCatalogVersionService registryCatalogVersionService;
    private final CommercialPreviewTokenService previewTokenService;
    private final ObjectProvider<CommercialOfferService> commercialOfferServiceProvider;
    private final EffectivePermissionService effectivePermissionService;
    private final BillingReadService billingReadService;
    private final AccountBillingProfileService billingProfileService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list_invoices", description = "List own Account Invoices")
    public Page<com.hiveapp.platform.client.plan.dto.BillingModels.InvoiceRow> invoiceHistory(
            UUID accountId,
            Pageable pageable
    ) {
        return billingReadService.listClient(accountId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_invoice", description = "Read an own Account Invoice")
    public com.hiveapp.platform.client.plan.dto.BillingModels.ClientInvoiceDetail invoice(
            UUID accountId,
            UUID invoiceId
    ) {
        return billingReadService.clientDetail(accountId, invoiceId);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_invoice_document",
            description = "Read own Account commercial Invoice document data")
    public com.hiveapp.platform.client.plan.dto.BillingModels.InvoiceDocument invoiceDocument(
            UUID accountId,
            UUID invoiceId
    ) {
        return billingReadService.clientDocument(accountId, invoiceId);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_financial_timeline",
            description = "Read own Account financial timeline")
    public Page<com.hiveapp.platform.client.plan.dto.BillingModels.FinancialTimelineEntry> financialTimeline(
            UUID accountId,
            BillingTimelineEntryType type,
            String currencyCode,
            Instant occurredFrom,
            Instant occurredUntil,
            Pageable pageable
    ) {
        return billingReadService.financialTimeline(
                accountId, type, currencyCode, occurredFrom, occurredUntil, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_billing_profile", description = "Read own Account billing profile")
    public AccountBillingProfileModels.Profile billingProfile(UUID accountId) {
        return billingProfileService.get(accountId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_billing_profile",
            description = "Update own Account billing profile for future Invoices")
    public AccountBillingProfileModels.Profile updateBillingProfile(
            UUID accountId,
            AccountBillingProfileModels.UpdateRequest request
    ) {
        return billingProfileService.update(accountId, request);
    }

    @Override @Transactional(readOnly = true)
    @PermissionNode(key = "offer_catalog", description = "List eligible commercial Offers")
    public Page<CommercialOfferViews.ClientOffer> offerCatalogue(UUID accountId, Pageable pageable) {
        return commercialOfferServiceProvider.getObject().catalogue(accountId, pageable);
    }

    @Override @Transactional(readOnly = true)
    @PermissionNode(key = "offer_detail", description = "Read an eligible commercial Offer")
    public CommercialOfferViews.ClientOffer offerDetail(UUID accountId, UUID offerId) {
        return commercialOfferServiceProvider.getObject().detail(accountId, offerId);
    }

    @Override @Transactional(readOnly = true)
    @PermissionNode(key = "offer_code", description = "Resolve a private commercial Offer code")
    public CommercialOfferViews.CodeResolution resolveOfferCode(UUID accountId, UUID actorUserId, CommercialOfferRequests.ResolveCode request) {
        return commercialOfferServiceProvider.getObject().resolveCode(accountId, actorUserId, request);
    }

    @Override @Transactional(readOnly = true)
    @PermissionNode(key = "offer_preview", description = "Preview a commercial Offer")
    public CommercialOfferViews.ClientEligibilityPreview previewOffer(UUID accountId, UUID actorUserId, UUID offerId, CommercialOfferRequests.Preview request) {
        return commercialOfferServiceProvider.getObject().preview(accountId, actorUserId, offerId, request.discoveryToken());
    }

    @Override
    @PermissionNode(key = "offer_accept", description = "Accept a commercial Offer")
    public CommercialOfferViews.ClientAcceptance acceptOffer(UUID accountId, UUID actorUserId, UUID offerId,
                                                        String idempotencyKey, CommercialOfferRequests.ClientAccept request) {
        return commercialOfferServiceProvider.getObject().acceptClient(
                accountId, actorUserId, offerId, idempotencyKey, request);
    }

    @Override @Transactional(readOnly = true)
    @PermissionNode(key = "offer_history", description = "Read own Offer history")
    public Page<CommercialOfferViews.ClientRedemption> offerHistory(UUID accountId, Pageable pageable) {
        return commercialOfferServiceProvider.getObject().history(accountId, pageable);
    }

    @Override @Transactional(readOnly = true)
    @PermissionNode(key = "offer_history_detail", description = "Read own Offer redemption")
    public CommercialOfferViews.ClientRedemption offerRedemption(UUID accountId, UUID redemptionId) {
        return commercialOfferServiceProvider.getObject().redemption(accountId, redemptionId);
    }

    @Override
    protected FeatureDefinition featureDefinition() {
        return ClientSubscriptionFeature.definition();
    }

    /**
     * Internal cross-service read lookup returning the latest subscription, including terminal
     * history. Unguarded on purpose: its only caller is the admin subscription surface, which
     * carries its own guard. Mutation paths use their stricter current-subscription lookups.
     */
    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "internal_subscription", guard = PermissionNode.Guard.OFF)
    public Subscription getSubscription(UUID accountId) {
        return requireLatestSubscription(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "View my subscription")
    public SubscriptionDto getMySubscription(UUID accountId) {
        return subscriptionMapper.toDto(requireLatestSubscription(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(
            key = "read_special_agreements",
            description = "View own Account special agreement terms")
    public Page<SpecialAgreementModels.ClientView> listMySpecialAgreements(
            UUID accountId, Pageable pageable) {
        return specialAgreementRepository.findAllByAccountId(accountId, pageable)
                .map(agreement -> new SpecialAgreementModels.ClientView(
                        agreement.getId(), agreement.getStatus(),
                        agreement.getTargetPlan().getCode(), agreement.getTargetPlan().getName(),
                        agreement.getRequestedSelection(),
                        agreement.getTermSnapshot().addOns().stream()
                                .map(item -> new SpecialAgreementModels.SelectedAddOn(
                                        item.code(), item.name()))
                                .toList(),
                        agreement.getTermSnapshot().quotaPackages().stream()
                                .map(item -> new SpecialAgreementModels.SelectedQuotaPackage(
                                        item.code(), item.name(), item.resource(),
                                        item.capacityPerUnit(), item.quantity()))
                                .toList(),
                        clientEntitlementState(agreement.getTermSnapshot()),
                        agreement.getStartsAt(),
                        agreement.getEndsAt(), agreement.agreedMoney().amount(),
                        agreement.getCurrencyCode(), agreement.getPricingMode(),
                        agreement.getEndInstruction(), agreement.getAttentionStage(),
                        agreement.getActivatedAt(), agreement.getCompletedAt()));
    }

    /**
     * The single definition of "the Account's usable subscription" — ACTIVE, else TRIALING.
     * Shared so the entity and read-model surfaces cannot drift apart.
     */
    private Subscription requireUsableSubscription(UUID accountId) {
        return subscriptionRepository.findActiveByAccountId(accountId)
                .or(() -> subscriptionRepository.findByAccountIdAndStatus(
                        accountId, SubscriptionStatus.TRIALING))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));
    }

    private Subscription requireCurrentSubscription(UUID accountId) {
        return subscriptionRepository.findCurrentByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));
    }

    private Subscription requireLatestSubscription(UUID accountId) {
        return subscriptionRepository.findTopByAccountIdOrderByCreatedAtDesc(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "catalog", description = "View subscription plan catalog")
    public ClientPlanCatalogResponse catalog(UUID accountId) {
        return catalogInternal(accountId, CommercialCatalogResolver.Audience.CLIENT_CATALOG);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "internal_operator_catalog", guard = PermissionNode.Guard.OFF)
    public ClientPlanCatalogResponse catalogAsOperator(UUID accountId) {
        return catalogInternal(accountId, CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
    }

    private ClientPlanCatalogResponse catalogInternal(
            UUID accountId,
            CommercialCatalogResolver.Audience audience
    ) {
        return commercialCatalogVersionService.readConsistently(ignored ->
                catalogAtCurrentRevision(accountId, audience));
    }

    private ClientPlanCatalogResponse catalogAtCurrentRevision(
            UUID accountId,
            CommercialCatalogResolver.Audience audience
    ) {
        Subscription current = requireUsableSubscription(accountId);
        CommercialPolicyEvaluator.Evaluation policyEvaluation =
                commercialPolicyEvaluator.evaluate(accountId, clock.instant());
        SubscriptionOverrides currentOverrides = subscriptionOverrideReader.read(current.getCustomOverrides());
        Map<String, FeatureDefinition> definitions = featureDefinitionCollectorProvider.getObject().collectByCode();
        Map<String, Long> usage = usageByQuotaSlot(accountId, definitions);
        UUID currentPlanId = current.getPlan().getId();
        CommercialCatalogResolver.CatalogResolution catalog = commercialCatalogResolver
                .resolveCatalog(audience);
        var publicChoices = planPublicSelection.choices(catalog.plans().stream()
                .map(CommercialCatalogResolver.PlanResolution::plan).toList());
        CommercialCatalogResolver.PlanResolution currentResolution = catalog.plans().stream()
                .filter(result -> result.plan().getId().equals(currentPlanId))
                .findFirst().orElseThrow(() -> new InvalidStateException(
                        "The current Plan revision is missing from the commercial catalogue."));
        var plans = catalog.plans().stream()
                .filter(result -> audience == CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR
                        || result.plan().getId().equals(currentPlanId)
                        || policyEvaluation.allowsDirectSelection(CommercialPolicyProductType.PLAN, result.plan().getId())
                        || (publicChoices.containsKey(result.plan().getLineageId())
                            && publicChoices.get(result.plan().getLineageId()).getId().equals(result.plan().getId())))
                // A held direct-only or retired exact revision remains readable as the
                // Account's current product, but is never exposed to another Account and
                // cannot be selected again through self service.
                .filter(result -> CommercialPolicySelectionRules.planVisible(
                                result, audience, policyEvaluation)
                        || result.plan().getId().equals(currentPlanId))
                .sorted(Comparator.comparing(
                                (CommercialCatalogResolver.PlanResolution result) -> result.plan().getPrice(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(result -> result.plan().getCode()))
                .map(result -> toCatalogPlan(
                        result, current, definitions, usage, audience,
                        CommercialPolicySelectionRules.planSelectable(
                                result, audience, policyEvaluation)
                                && !planContainsBlockedFeature(result, policyEvaluation),
                        policyEvaluation))
                .toList();
        SubscriptionEntitlementSnapshot currentSnapshot = current.getEntitlementSnapshot();

        return new ClientPlanCatalogResponse(
                new ClientPlanCatalogResponse.CurrentSubscription(
                        current.getId(),
                        current.getPlan().getCode(),
                        current.getStatus(),
                        current.getCurrentPrice(),
                        current.getCurrentPriceCurrencyCode(),
                        currentSnapshot.planPriceEntryId(),
                        currentSnapshot.billingCycle(),
                        current.getCurrentPeriodStart(),
                        current.getCurrentPeriodEnd(),
                        current.isCancelAtPeriodEnd(),
                        currentOverrides.addOnCodes(),
                        currentOverrides.quotaPackages(),
                        retainedAddOns(
                                currentSnapshot, currentResolution, audience, policyEvaluation),
                        retainedQuotaPackages(
                                currentSnapshot, currentResolution, audience, policyEvaluation)),
                plans,
                clientPolicyDecisions(policyEvaluation.catalogDecisions()));
    }

    private boolean planContainsBlockedFeature(
            CommercialCatalogResolver.PlanResolution result,
            CommercialPolicyEvaluator.Evaluation evaluation
    ) {
        return result.planFeatures().stream()
                .filter(feature -> feature.getMode()
                        == com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode.INCLUDED)
                .map(feature -> feature.getFeature().getCode())
                .anyMatch(evaluation.blockedFeatures()::containsKey);
    }

    private List<ClientPlanCatalogResponse.RetainedAddOn> retainedAddOns(
            SubscriptionEntitlementSnapshot snapshot,
            CommercialCatalogResolver.PlanResolution currentResolution,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation policyEvaluation
    ) {
        Map<String, CommercialCatalogResolver.AddOnResolution> currentByCode =
                currentResolution.addOns().stream().collect(Collectors.toMap(
                        CommercialCatalogResolver.AddOnResolution::code, Function.identity()));
        return snapshot.addOns().stream()
                .sorted(Comparator.comparing(com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot::code))
                .map(held -> {
                    CommercialCatalogResolver.AddOnResolution current = currentByCode.get(held.code());
                    boolean selectable = CommercialPolicySelectionRules.planSelectable(
                            currentResolution, audience, policyEvaluation)
                            && !planContainsBlockedFeature(currentResolution, policyEvaluation)
                            && current != null
                            && CommercialPolicySelectionRules.addOnSelectable(
                                    current, audience, policyEvaluation)
                            && current.addOn().getFeatures().stream()
                                    .map(feature -> feature.getFeature().getCode())
                                    .noneMatch(policyEvaluation.blockedFeatures()::containsKey);
                    RetainedEntitlementState state = selectable
                            ? RetainedEntitlementState.SELECTABLE
                            : current == null
                                    ? RetainedEntitlementState.HISTORICAL_ONLY
                                    : RetainedEntitlementState.RETAINED_ONLY;
                    return new ClientPlanCatalogResponse.RetainedAddOn(
                            held.code(), held.name(), held.definitionVersion(), held.price(),
                            held.currencyCode(), held.billingCycle(), List.copyOf(held.featureCodes()),
                            held.priceEntryId(), state, true, selectable);
                })
                .toList();
    }

    private List<ClientPlanCatalogResponse.RetainedQuotaPackage> retainedQuotaPackages(
            SubscriptionEntitlementSnapshot snapshot,
            CommercialCatalogResolver.PlanResolution currentResolution,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation policyEvaluation
    ) {
        Map<String, CommercialCatalogResolver.QuotaPackageResolution> currentByCode =
                currentResolution.quotaPackages().stream().collect(Collectors.toMap(
                        CommercialCatalogResolver.QuotaPackageResolution::code, Function.identity()));
        return snapshot.quotaPackages().stream()
                .sorted(Comparator.comparing(
                        com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::code))
                .map(held -> {
                    CommercialCatalogResolver.QuotaPackageResolution current = currentByCode.get(held.code());
                    boolean selectable = CommercialPolicySelectionRules.planSelectable(
                            currentResolution, audience, policyEvaluation)
                            && !planContainsBlockedFeature(currentResolution, policyEvaluation)
                            && current != null
                            && CommercialPolicySelectionRules.quotaPackageSelectable(
                                    current, audience, policyEvaluation)
                            && !policyEvaluation.blockedFeatures().containsKey(
                                    current.quotaPackage().getFeature().getCode());
                    RetainedEntitlementState state = selectable
                            ? RetainedEntitlementState.SELECTABLE
                            : current == null
                                    ? RetainedEntitlementState.HISTORICAL_ONLY
                                    : RetainedEntitlementState.RETAINED_ONLY;
                    return new ClientPlanCatalogResponse.RetainedQuotaPackage(
                            held.code(), held.name(), held.definitionVersion(), held.featureCode(),
                            held.resource(), held.capacityPerUnit(), held.quantity(), held.unitPrice(),
                            held.currencyCode(), held.billingCycle(), held.priceEntryId(), state, true,
                            selectable, selectable ? current.quotaPackage().getMaximumQuantity() : null);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview", description = "Preview a subscription change")
    public SubscriptionChangePreviewResponse previewChange(
            UUID accountId,
            UUID actorUserId,
            SubscriptionChangeRequest request
    ) {
        return previewChangeInternal(
                accountId, actorUserId, request,
                CommercialCatalogResolver.Audience.CLIENT_CATALOG,
                CommercialPreviewKind.SUBSCRIPTION_CHANGE,
                List.of());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "internal_operator_preview", guard = PermissionNode.Guard.OFF)
    public SubscriptionChangePreviewResponse previewChangeAsOperator(
            UUID accountId,
            UUID actorUserId,
            SubscriptionChangeRequest request
    ) {
        return previewChangeInternal(
                accountId, actorUserId, request,
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                CommercialPreviewKind.ADMIN_SUBSCRIPTION_CHANGE,
                List.of());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "internal_client_offer_preview", guard = PermissionNode.Guard.OFF)
    public SubscriptionChangePreviewResponse previewOfferChange(
            UUID accountId,
            UUID actorUserId,
            SubscriptionChangeRequest request,
            List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses
    ) {
        return previewChangeInternal(
                accountId, actorUserId, request,
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                CommercialPreviewKind.SUBSCRIPTION_CHANGE,
                quotaBonuses);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "internal_operator_offer_preview", guard = PermissionNode.Guard.OFF)
    public SubscriptionChangePreviewResponse previewOfferChangeAsOperator(
            UUID accountId,
            UUID actorUserId,
            SubscriptionChangeRequest request,
            List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses
    ) {
        return previewChangeInternal(
                accountId, actorUserId, request,
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                CommercialPreviewKind.ADMIN_SUBSCRIPTION_CHANGE,
                quotaBonuses);
    }

    private SubscriptionChangePreviewResponse previewChangeInternal(
            UUID accountId,
            UUID actorUserId,
            SubscriptionChangeRequest request,
            CommercialCatalogResolver.Audience audience,
            CommercialPreviewKind previewKind,
            List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses
    ) {
        String registryVersion = registryCatalogVersionService.currentVersion();
        return commercialCatalogVersionService.readConsistently(catalogRevision -> {
            Instant evaluatedAt = clock.instant();
            SubscriptionChangeAssessment assessment = assessSubscriptionChangePreview(
                    accountId, request, audience, evaluatedAt, quotaBonuses);
            var evidence = previewTokenService.issue(
                    previewKind,
                    assessment.current().getId(), assessment.current().getVersion(), actorUserId,
                    catalogRevision, registryVersion, assessment.fingerprint(), evaluatedAt);
            return assessment.toResponse(
                    catalogRevision, registryVersion, evidence.evaluatedAt(),
                    evidence.expiresAt(), evidence.token());
        });
    }

    @Override
    @Transactional
    @PermissionNode(key = "apply", description = "Apply a subscription change")
    public SubscriptionChangeApplyResponse applyChange(
            UUID accountId,
            UUID actorUserId,
            SubscriptionChangeApplyRequest applyRequest
    ) {
        return applyChangeInternal(
                accountId, actorUserId, applyRequest,
                CommercialCatalogResolver.Audience.CLIENT_CATALOG,
                CommercialPreviewKind.SUBSCRIPTION_CHANGE,
                null, null, null);
    }

    @Override
    @PermissionNode(key = "internal_offer_apply_authority", guard = PermissionNode.Guard.OFF)
    public void requireOfferApplyAuthority(UUID accountId, UUID actorUserId) {
        if (!effectivePermissionService.getEffectivePermissions(actorUserId, accountId)
                .permissions().contains("platform.subscription.apply")) {
            throw new PermissionDeniedException("Subscription apply permission is required.");
        }
    }

    @Override
    @Transactional
    @PermissionNode(key = "internal_operator_apply", guard = PermissionNode.Guard.OFF)
    public SubscriptionChangeApplyResponse applyChangeAsOperator(
            UUID accountId,
            UUID actorUserId,
            SubscriptionChangeApplyRequest applyRequest,
            String reason
    ) {
        return applyChangeInternal(
                accountId, actorUserId, applyRequest,
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                CommercialPreviewKind.ADMIN_SUBSCRIPTION_CHANGE,
                reason, null, null);
    }

    @Override
    @Transactional
    @PermissionNode(key = "internal_client_offer_apply", guard = PermissionNode.Guard.OFF)
    public SubscriptionChangeApplyResponse applyOfferChange(UUID accountId, UUID actorUserId,
            SubscriptionChangeApplyRequest request, String reason,
            UUID redemptionId, UUID applicationToken) {
        return applyChangeInternal(accountId, actorUserId, request,
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                CommercialPreviewKind.SUBSCRIPTION_CHANGE,
                reason, redemptionId, applicationToken);
    }

    @Override
    @Transactional
    @PermissionNode(key = "internal_operator_offer_apply", guard = PermissionNode.Guard.OFF)
    public SubscriptionChangeApplyResponse applyOfferChangeAsOperator(
            UUID accountId, UUID actorUserId,
            SubscriptionChangeApplyRequest request, String reason,
            UUID redemptionId, UUID applicationToken) {
        return applyChangeInternal(accountId, actorUserId, request,
                CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                CommercialPreviewKind.ADMIN_SUBSCRIPTION_CHANGE,
                reason, redemptionId, applicationToken);
    }

    private SubscriptionChangeApplyResponse applyChangeInternal(
            UUID accountId,
            UUID actorUserId,
            SubscriptionChangeApplyRequest applyRequest,
            CommercialCatalogResolver.Audience audience,
            CommercialPreviewKind previewKind,
            String requestReason,
            UUID offerRedemptionId,
            UUID applicationClaimId
    ) {
        accountRepository.findByIdForSubscriptionUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption offerRedemption = null;
        if (offerRedemptionId != null) {
            offerRedemption = requireOfferApplicationClaim(
                    accountId, actorUserId, offerRedemptionId, applicationClaimId, previewKind);
            if (!Objects.equals(offerRedemption.getAcceptedSelection(), applyRequest.selection())) {
                throw new OfferRedemptionBlockedException();
            }
        }
        SubscriptionOfferEvaluation offerEvaluation = offerRedemption == null
                ? null : offerRedemption.getCommercialEvaluation();
        SubscriptionChangeRequest request = applyRequest.selection();
        long catalogRevision = commercialCatalogVersionService.currentRevision();
        String registryVersion = registryCatalogVersionService.currentVersion();
        SubscriptionChangeAssessment assessment;
        try {
            assessment = assessSubscriptionChangeForApply(
                    accountId, request, audience,
                    offerEvaluation == null ? List.of() : offerEvaluation.quotaBonuses());
        } catch (InvalidRequestException | InvalidStateException | ResourceNotFoundException
                 | StaleResourceVersionException exception) {
            // Once evidence is supplied, a changed or no-longer-selectable commercial input is
            // a stale review—not a new validation outcome that may leak catalogue changes.
            throw staleSubscriptionPreview();
        }
        commercialCatalogVersionService.requireCurrent(catalogRevision);
        try {
            // The finalizer now holds the registry singleton. Requiring the version observed
            // before finalization closes the registry-only mutation window (for example,
            // NEW_GRANTS changes that intentionally do not advance the commercial revision).
            registryCatalogVersionService.requireCurrent(registryVersion);
        } catch (InvalidStateException exception) {
            throw staleSubscriptionPreview();
        }
        var verified = previewTokenService.requireValid(
                applyRequest.previewToken(), previewKind,
                assessment.current().getId(), assessment.current().getVersion(), actorUserId,
                catalogRevision, registryVersion, assessment.fingerprint(),
                this::staleSubscriptionPreview);
        Subscription current = assessment.current();
        Plan targetPlan = assessment.targetPlan();
        ChangeSelection selection = assessment.selection();
        SubscriptionEntitlementSnapshot targetSnapshot = offerEvaluation == null
                ? assessment.targetSnapshot() : assessment.targetSnapshot().withOfferProvenance(offerEvaluation);
        SubscriptionChangePreviewResponse preview = assessment.toResponse(
                catalogRevision, registryVersion, verified.evaluatedAt(), verified.expiresAt(),
                applyRequest.previewToken());
        if (preview.commercialPolicyEvaluation() != null
                && preview.commercialPolicyEvaluation().blocked()) {
            throw new InvalidStateException(
                    "Subscription change cannot be applied until commercial-policy conflicts are resolved.");
        }
        if (request.effectiveTiming() == SubscriptionChangeTiming.IMMEDIATE
                && !preview.immediateAllowed()) {
            throw new InvalidStateException("Subscription change cannot be applied until conflicts are resolved.");
        }
        if (isNoOp(current, targetSnapshot, selection)) {
            throw new InvalidStateException("Requested subscription change does not modify the current subscription.");
        }
        SubscriptionOverrides requestedSelection = new SubscriptionOverrides(
                selection.addOnCodes(), selection.quotaPackages());
        SubscriptionChangeOperation operation = newChangeOperation(
                current, targetPlan, requestedSelection, targetSnapshot, request.effectiveTiming(),
                previewKind == CommercialPreviewKind.ADMIN_SUBSCRIPTION_CHANGE
                        ? SubscriptionChangeOrigin.PLATFORM_ADMIN
                        : SubscriptionChangeOrigin.CLIENT_SELF_SERVICE,
                actorUserId, requestReason, offerRedemptionId, applicationClaimId, offerEvaluation);

        subscriptionChangeOperationRepository.findTopByAccountIdAndStatusIn(
                        accountId,
                        List.of(SubscriptionChangeStatus.PENDING,
                                SubscriptionChangeStatus.AWAITING_CONFIRMATION))
                .ifPresent(existing -> {
                    throw new InvalidStateException(
                            "Account already has an outstanding subscription change. Cancel it before creating another.");
                });

        Money targetPrice = Money.of(offerEvaluation == null
                ? preview.previewPrice() : offerEvaluation.finalPrice(), preview.currencyCode());
        if (offerRedemption != null && targetPrice.amount().signum() > 0) {
            throw new OfferRedemptionBlockedException();
        }
        if (targetPrice.amount().signum() > 0) {
            operation.setStatus(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
            SubscriptionChangeOperation savedOperation = subscriptionChangeOperationRepository.saveAndFlush(operation);
            subscriptionCheckoutService.initiate(savedOperation, targetPrice, actorUserId);
            return new SubscriptionChangeApplyResponse(
                    toDto(current), preview, operationProjectionMapper.internal(savedOperation));
        }

        if (request.effectiveTiming() == SubscriptionChangeTiming.AT_RENEWAL) {
            SubscriptionChangeOperation savedOperation = subscriptionChangeOperationRepository.saveAndFlush(operation);
            completeOfferApplication(offerRedemption, savedOperation);
            return new SubscriptionChangeApplyResponse(
                    toDto(current), preview, operationProjectionMapper.internal(savedOperation));
        }

        SubscriptionChangeOperation savedOperation = subscriptionChangeOperationRepository.saveAndFlush(operation);
        savedOperation = subscriptionChangeActivationService.activate(
                savedOperation, operation.getTargetSnapshot().effectiveFrom());
        completeOfferApplication(offerRedemption, savedOperation);
        return new SubscriptionChangeApplyResponse(
                toDto(savedOperation.getResultSubscription()), preview,
                operationProjectionMapper.internal(savedOperation));
    }

    private com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption requireOfferApplicationClaim(
            UUID accountId,
            UUID actorUserId,
            UUID redemptionId,
            UUID applicationClaimId,
            CommercialPreviewKind previewKind
    ) {
        var redemption = commercialOfferRedemptionRepository.lockById(redemptionId)
                .orElseThrow(OfferRedemptionBlockedException::new);
        CommercialOfferSurface expectedSurface =
                previewKind == CommercialPreviewKind.ADMIN_SUBSCRIPTION_CHANGE
                        ? CommercialOfferSurface.OPERATOR : CommercialOfferSurface.CLIENT;
        if (redemption.getStatus() != CommercialOfferRedemptionStatus.RESERVED
                || !redemption.getAccount().getId().equals(accountId)
                || !redemption.getActorUserId().equals(actorUserId)
                || redemption.getSurface() != expectedSurface
                || applicationClaimId == null
                || !redemption.hasApplicationClaim(applicationClaimId)) {
            throw new OfferRedemptionBlockedException();
        }
        return redemption;
    }

    private void completeOfferApplication(
            com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption redemption,
            SubscriptionChangeOperation operation
    ) {
        if (redemption != null) {
            commercialOfferRedemptionTransitions.applyOperation(redemption, operation);
            commercialOfferRedemptionRepository.saveAndFlush(redemption);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_changes", description = "View subscription change operations")
    public Page<SubscriptionChangeOperationDto> listChangeOperations(
            UUID accountId,
            Pageable pageable
    ) {
        return subscriptionChangeOperationRepository.findAllByAccountId(accountId, pageable)
                .map(operationProjectionMapper::internal);
    }

    @Override
    @Transactional
    @PermissionNode(key = "cancel_change", description = "Cancel a pending renewal subscription change")
    public SubscriptionChangeOperationDto cancelPendingChange(
            UUID accountId,
            UUID operationId,
            UUID actorUserId
    ) {
        return cancelPendingChangeInternal(
                accountId, operationId, SubscriptionChangeOrigin.CLIENT_SELF_SERVICE,
                actorUserId, null);
    }

    @Override
    @Transactional
    @PermissionNode(key = "internal_operator_cancel", guard = PermissionNode.Guard.OFF)
    public SubscriptionChangeOperationDto cancelPendingChangeAsOperator(
            UUID accountId,
            UUID operationId,
            UUID actorUserId,
            String reason
    ) {
        return cancelPendingChangeInternal(
                accountId, operationId, SubscriptionChangeOrigin.PLATFORM_ADMIN,
                actorUserId, reason);
    }

    private SubscriptionChangeOperationDto cancelPendingChangeInternal(
            UUID accountId,
            UUID operationId,
            SubscriptionChangeOrigin origin,
            UUID actorUserId,
            String reason
    ) {
        SubscriptionChangeOperation operation = subscriptionChangeOperationRepository
                .findByIdAndAccountId(operationId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionChangeOperation", "id", operationId));
        if (operation.getStatus() != SubscriptionChangeStatus.PENDING
                && operation.getStatus() != SubscriptionChangeStatus.AWAITING_CONFIRMATION) {
            throw new InvalidStateException("Only pending or awaiting-confirmation changes can be cancelled.");
        }
        subscriptionCheckoutService.cancelFor(operation);
        operation.setStatus(SubscriptionChangeStatus.CANCELLED);
        operation.setCancellationOrigin(origin);
        operation.setCancelledByUserId(actorUserId);
        operation.setCancellationReason(reason);
        operation.setCancelledAt(clock.instant());
        return operationProjectionMapper.internal(
                subscriptionChangeOperationRepository.saveAndFlush(operation));
    }

    private ClientPlanCatalogResponse.CatalogPlan toCatalogPlan(
            CommercialCatalogResolver.PlanResolution result,
            Subscription current,
            Map<String, FeatureDefinition> definitions,
            Map<String, Long> usage,
            CommercialCatalogResolver.Audience audience,
            boolean selectable,
            CommercialPolicyEvaluator.Evaluation policyEvaluation
    ) {
        Plan plan = result.plan();
        var features = result.planFeatures().stream()
                .filter(planFeature -> isSelfServiceAvailable(planFeature, definitions))
                .sorted(Comparator.comparing(planFeature -> definitions.get(planFeature.getFeature().getCode()).sortOrder()))
                .map(planFeature -> toCatalogFeature(planFeature, definitions.get(planFeature.getFeature().getCode()), usage))
                .toList();
        boolean basePlanVisible = CommercialPolicySelectionRules.planVisible(
                result, audience, policyEvaluation);
        Set<String> visibleAddOnCodes = basePlanVisible ? result.addOns().stream()
                .filter(item -> CommercialPolicySelectionRules.addOnVisible(
                        item, audience, policyEvaluation))
                .map(CommercialCatalogResolver.AddOnResolution::code)
                .collect(Collectors.toUnmodifiableSet()) : Set.of();
        var addOns = (basePlanVisible ? result.addOns().stream() : java.util.stream.Stream
                .<CommercialCatalogResolver.AddOnResolution>empty())
                .filter(item -> CommercialPolicySelectionRules.addOnVisible(
                        item, audience, policyEvaluation))
                .map(addOnResult -> {
                    AddOn addOn = addOnResult.addOn();
                    boolean productSelectable = selectable
                            && CommercialPolicySelectionRules.addOnSelectable(
                                    addOnResult, audience, policyEvaluation)
                            && addOn.getFeatures().stream().map(feature -> feature.getFeature().getCode())
                                    .noneMatch(policyEvaluation.blockedFeatures()::containsKey);
                    boolean granted = policyGrant(
                            policyEvaluation,
                            com.hiveapp.platform.client.plan.domain.constant
                                    .CommercialPolicyProductType.ADD_ON,
                            addOn.getId());
                    return new ClientPlanCatalogResponse.CatalogAddOn(
                        addOn.getCode(), addOn.getName(), addOn.getDescription(),
                        granted ? Money.zero(addOn.getCurrencyCode()).amount() : addOn.getPrice(),
                        addOn.getCurrencyCode(), addOn.getBillingCycle(), addOn.getDefinitionVersion(),
                        addOn.getDependencyCodes().stream().filter(visibleAddOnCodes::contains)
                                .collect(Collectors.toUnmodifiableSet()),
                        addOn.getExclusionCodes().stream().filter(visibleAddOnCodes::contains)
                                .collect(Collectors.toUnmodifiableSet()),
                        addOn.getFeatures().stream()
                                .sorted(Comparator.comparing(feature -> feature.getFeature().getCode()))
                                .map(feature -> toCatalogAddOnFeature(feature, definitions, usage))
                                .toList(),
                        toCatalogPrices(addOnResult.prices(), granted), productSelectable,
                        clientPolicyDecisions(policyEvaluation.product(
                                com.hiveapp.platform.client.plan.domain.constant
                                        .CommercialPolicyProductType.ADD_ON,
                                addOn.getId()).catalogDecisions()));
                })
                .toList();
        var quotaPackages = (basePlanVisible ? result.quotaPackages().stream() : java.util.stream.Stream
                .<CommercialCatalogResolver.QuotaPackageResolution>empty())
                .filter(item -> CommercialPolicySelectionRules.quotaPackageVisible(
                        item, audience, policyEvaluation))
                .map(packageResult -> {
                    QuotaPackage item = packageResult.quotaPackage();
                    boolean productSelectable = selectable
                            && CommercialPolicySelectionRules.quotaPackageSelectable(
                                    packageResult, audience, policyEvaluation)
                            && !policyEvaluation.blockedFeatures().containsKey(
                                    item.getFeature().getCode());
                    boolean granted = policyGrant(
                            policyEvaluation,
                            com.hiveapp.platform.client.plan.domain.constant
                                    .CommercialPolicyProductType.QUOTA_PACKAGE,
                            item.getId());
                    return new ClientPlanCatalogResponse.CatalogQuotaPackage(
                        item.getCode(), item.getName(), item.getDescription(), item.getDefinitionVersion(),
                        item.getFeature().getCode(), item.getResource(), item.getCapacityPerUnit(),
                        granted ? Money.zero(item.getCurrencyCode()).amount() : item.getPrice(),
                        item.getCurrencyCode(), item.getBillingCycle(), item.isRepeatable(),
                        item.getMaximumQuantity(),
                        packageResult.directlySelectable() ? Set.of(plan.getCode()) : Set.of(),
                        packageResult.requiredAddOnCodes(),
                        toCatalogPrices(packageResult.prices(), granted), packageResult.directlySelectable(),
                        packageResult.requiredAddOnCodes(), productSelectable,
                        clientPolicyDecisions(policyEvaluation.product(
                                com.hiveapp.platform.client.plan.domain.constant
                                        .CommercialPolicyProductType.QUOTA_PACKAGE,
                                item.getId()).catalogDecisions()));
                })
                .toList();
        return new ClientPlanCatalogResponse.CatalogPlan(
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getCurrencyCode(),
                plan.getBillingCycle(),
                current.getPlan().getId().equals(plan.getId()),
                selectable,
                features,
                addOns,
                quotaPackages,
                selectable ? toCatalogPrices(result.prices()) : List.of(),
                clientPolicyDecisions(policyEvaluation.product(
                        com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType.PLAN,
                        plan.getId()).catalogDecisions()));
    }

    private List<ClientCommercialPolicyDecision> clientPolicyDecisions(
            List<CommercialPolicyDecisionSnapshot> decisions
    ) {
        return decisions.stream()
                .filter(decision -> decision.outcome()
                        != CommercialPolicyDecisionOutcome.REJECTED_LOWER_PRECEDENCE)
                .map(ClientCommercialPolicyDecision::from).toList();
    }

    private boolean policyGrant(
            CommercialPolicyEvaluator.Evaluation evaluation,
            com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType type,
            UUID id
    ) {
        var winner = evaluation.product(type, id).winner();
        return winner != null && (winner.effect().getType()
                == com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType.GRANT_ADD_ON
                || winner.effect().getType()
                == com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType.GRANT_QUOTA_PACKAGE);
    }

    private ClientPlanCatalogResponse.CatalogFeature toCatalogFeature(
            PlanFeature planFeature,
            FeatureDefinition definition,
            Map<String, Long> usage
    ) {
        Map<String, QuotaLimitEntry> quotasByResource = safeQuotaConfigs(planFeature).stream()
                .collect(Collectors.toMap(QuotaLimitEntry::resource, Function.identity(), (left, right) -> left));
        var quotas = definition.quotaSlots().stream()
                .filter(slot -> quotasByResource.containsKey(slot.resource()))
                .map(slot -> {
                    QuotaLimitEntry limit = quotasByResource.get(slot.resource());
                    return new ClientPlanCatalogResponse.CatalogQuota(
                            definition.code(),
                            slot,
                            limit.mode(),
                            limit.limit(),
                            limit.mode() == QuotaLimitMode.UNLIMITED,
                            usage.get(definition.code() + ":" + slot.resource()));
                })
                .toList();
        return new ClientPlanCatalogResponse.CatalogFeature(
                definition.code(),
                definition.displayName(),
                definition.description(),
                planFeature.getMode(),
                quotas);
    }

    private ClientPlanCatalogResponse.CatalogAddOnFeature toCatalogAddOnFeature(
            AddOnFeature addOnFeature,
            Map<String, FeatureDefinition> definitions,
            Map<String, Long> usage
    ) {
        FeatureDefinition definition = definitions.get(addOnFeature.getFeature().getCode());
        Map<String, QuotaLimitEntry> quotasByResource = addOnFeature.getQuotaConfigs().stream()
                .collect(Collectors.toMap(QuotaLimitEntry::resource, Function.identity(), (left, right) -> left));
        var quotas = definition.quotaSlots().stream()
                .filter(slot -> quotasByResource.containsKey(slot.resource()))
                .map(slot -> {
                    QuotaLimitEntry limit = quotasByResource.get(slot.resource());
                    return new ClientPlanCatalogResponse.CatalogQuota(
                            definition.code(), slot,
                            limit.mode(), limit.limit(), limit.mode() == QuotaLimitMode.UNLIMITED,
                            usage.get(definition.code() + ":" + slot.resource()));
                })
                .toList();
        return new ClientPlanCatalogResponse.CatalogAddOnFeature(
                definition.code(), definition.displayName(), definition.description(), quotas);
    }

    private ChangeSelection validateSelection(Plan targetPlan, SubscriptionChangeRequest request,
                                              CommercialCatalogResolver.PriceTuple priceTuple,
                                              CommercialCatalogResolver.Audience audience,
                                              CommercialCatalogResolver.RetainedSelection retained) {
        Set<String> addOnCodes = normalizeAddOnCodes(request.addOnCodes());
        List<QuotaPackageSelection> packageSelections = normalizeQuotaPackages(request.quotaPackages());
        Set<String> packageCodes = new LinkedHashSet<>();
        for (QuotaPackageSelection selection : packageSelections) {
            if (selection.packageCode() == null || selection.packageCode().isBlank()) {
                throw new InvalidRequestException("Quota package code is required.");
            }
            if (!packageCodes.add(selection.packageCode())) {
                throw new InvalidRequestException("Duplicate quota package selection: " + selection.packageCode());
            }
        }

        CommercialCatalogResolver.SelectionResolution resolved = commercialCatalogResolver.resolveSelection(
                targetPlan, priceTuple, addOnCodes, packageSelections, audience, retained);
        requireResolvedSelection(resolved, audience);

        return new ChangeSelection(addOnCodes, packageSelections);
    }

    private SubscriptionEntitlementSnapshot targetSnapshot(
            Subscription current,
            Plan targetPlan,
            ChangeSelection selection,
            ProductPrice selectedPlanPrice,
            ProductPriceSelectionRequest requestedPrice
    ) {
        SubscriptionEntitlementSnapshot currentSnapshot = current.getEntitlementSnapshot();
        boolean sameExactPlan = Objects.equals(current.getPlan().getId(), targetPlan.getId());
        boolean sameExactPrice = requestedPrice == null
                || (requestedPrice.priceEntryId() != null
                && requestedPrice.priceEntryId().equals(currentSnapshot.planPriceEntryId()));
        if (sameExactPlan && sameExactPrice) {
            return subscriptionSnapshotFactory.fromPlanPreservingPrices(
                    targetPlan, selection.addOnCodes(), selection.quotaPackages(), currentSnapshot);
        }
        return subscriptionSnapshotFactory.fromPlan(
                targetPlan, selection.addOnCodes(), selection.quotaPackages(), selectedPlanPrice);
    }

    private CommercialCatalogResolver.RetainedSelection retainedSelection(
            SubscriptionEntitlementSnapshot current,
            Plan targetPlan,
            SubscriptionChangeRequest request
    ) {
        if (current == null || !targetPlan.getCode().equals(current.planCode())) {
            return CommercialCatalogResolver.RetainedSelection.none();
        }
        ProductPriceSelectionRequest requestedPlanPrice = request.planPriceSelection();
        if (requestedPlanPrice != null) {
            boolean exactHeldIdentity = requestedPlanPrice.priceEntryId() != null
                    && requestedPlanPrice.priceEntryId().equals(current.planPriceEntryId());
            boolean heldTuple = requestedPlanPrice.priceEntryId() == null
                    && requestedPlanPrice.currencyCode() != null
                    && requestedPlanPrice.billingCycle() != null
                    && com.hiveapp.shared.money.Money.normalizeCurrencyCode(
                            requestedPlanPrice.currencyCode()).equals(current.currencyCode())
                    && requestedPlanPrice.billingCycle() == current.billingCycle();
            if (!exactHeldIdentity && !heldTuple) {
                return CommercialCatalogResolver.RetainedSelection.none();
            }
        }
        Set<String> requestedAddOns = request.addOnCodes() == null
                ? Set.of() : request.addOnCodes();
        Set<String> requestedPackages = (request.quotaPackages() == null
                ? List.<QuotaPackageSelection>of() : request.quotaPackages()).stream()
                .filter(Objects::nonNull)
                .map(QuotaPackageSelection::packageCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> retainedAddOns = current.addOns().stream()
                .map(com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot::code)
                .filter(requestedAddOns::contains)
                .collect(Collectors.toSet());
        Map<String, Integer> retainedPackages = current.quotaPackages().stream()
                .filter(item -> requestedPackages.contains(item.code()))
                .collect(Collectors.toMap(
                        com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::code,
                        com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::quantity));
        return new CommercialCatalogResolver.RetainedSelection(
                retainedAddOns, retainedPackages, current.planPriceEntryId());
    }

    private Set<String> normalizeAddOnCodes(Set<String> requested) {
        if (requested == null) return Set.of();
        if (requested.size() > 100) {
            throw new InvalidRequestException("Subscription overrides support at most 100 AddOns.");
        }
        if (requested.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new InvalidRequestException("AddOn codes cannot be null or blank.");
        }
        return Set.copyOf(requested);
    }

    private List<QuotaPackageSelection> normalizeQuotaPackages(List<QuotaPackageSelection> requested) {
        if (requested == null) return List.of();
        if (requested.size() > 100) {
            throw new InvalidRequestException(
                    "Subscription overrides support at most 100 capacity packages.");
        }
        if (requested.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRequestException("Quota package selection cannot be null.");
        }
        List<QuotaPackageSelection> normalized = List.copyOf(requested);
        Set<String> packageCodes = new LinkedHashSet<>();
        for (QuotaPackageSelection selection : normalized) {
            if (selection.packageCode() == null || selection.packageCode().isBlank()) {
                throw new InvalidRequestException("Quota package code is required.");
            }
            if (!packageCodes.add(selection.packageCode())) {
                throw new InvalidRequestException(
                        "Duplicate quota package selection: " + selection.packageCode());
            }
        }
        return normalized;
    }

    private void requireResolvedSelection(
            CommercialCatalogResolver.SelectionResolution resolved,
            CommercialCatalogResolver.Audience audience
    ) {
        CommercialCatalogResolver.requireSelectable(resolved, audience);
    }

    private Money previewPrice(
            Subscription current,
            Plan targetPlan,
            SubscriptionEntitlementSnapshot targetSnapshot,
            ChangeSelection selection
    ) {
        Subscription preview = new Subscription();
        preview.setAccount(current.getAccount());
        preview.setPlan(targetPlan);
        preview.setStatus(SubscriptionStatus.ACTIVE);
        preview.setEntitlementSnapshot(subscriptionSnapshotReader.write(targetSnapshot));
        preview.setCustomOverrides(subscriptionOverrideReader.write(
                new SubscriptionOverrides(selection.addOnCodes(), selection.quotaPackages())));
        return billingCalculator.calculateMoney(preview);
    }

    private SubscriptionChangeAssessment assessSubscriptionChangePreview(
            UUID accountId,
            SubscriptionChangeRequest request,
            CommercialCatalogResolver.Audience audience,
            Instant evaluatedAt,
            List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses
    ) {
        Subscription current = requireUsableSubscription(accountId);
        CommercialPolicyEvaluator.Evaluation policyEvaluation =
                commercialPolicyEvaluator.evaluate(accountId, evaluatedAt);
        ClientPlanSelection target = requirePlanSelection(
                current, request.targetPlanCode(), request.planPriceSelection(),
                audience, policyEvaluation);
        requireSameSubscriptionCurrency(current, target.price());
        Set<String> requestedAddOns = normalizeAddOnCodes(request.addOnCodes());
        List<QuotaPackageSelection> requestedPackages = normalizeQuotaPackages(request.quotaPackages());
        var planned = commercialPolicySelectionPlanner.plan(
                target.plan(), CommercialCatalogResolver.PriceTuple.from(target.price()),
                requestedAddOns, requestedPackages, audience,
                retainedSelection(current.getEntitlementSnapshot(), target.plan(), request),
                policyEvaluation);
        requireResolvedSelection(planned.resolution(), audience);
        ChangeSelection selection = new ChangeSelection(planned.addOnCodes(), planned.quotaPackages());
        SubscriptionEntitlementSnapshot catalogueSnapshot = subscriptionSnapshotFactory
                .fromResolvedSelection(target.plan(), target.price(), planned.resolution(),
                        current.getEntitlementSnapshot());
        requireExactRequestedPrices(catalogueSnapshot, request);
        var terms = commercialPolicyTermsService.apply(
                target.plan(), catalogueSnapshot, policyEvaluation, planned);
        SubscriptionEntitlementSnapshot targetSnapshot =
                terms.snapshot().withOfferQuotaBonuses(quotaBonuses);
        return assessResolvedChange(
                accountId, current, target.plan(), targetSnapshot, selection,
                request.effectiveTiming(), audience, terms.evaluation());
    }

    SpecialAgreementSelectionAssessment assessSpecialAgreementPreview(
            UUID accountId,
            SubscriptionChangeRequest request,
            List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses,
            Instant evaluatedAt
    ) {
        SubscriptionChangeAssessment assessment = assessSubscriptionChangePreview(
                accountId, immediate(request), CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                evaluatedAt, quotaBonuses);
        return specialAgreementAssessment(assessment);
    }

    SpecialAgreementSelectionAssessment assessSpecialAgreementForApply(
            UUID accountId,
            SubscriptionChangeRequest request,
            List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses
    ) {
        return specialAgreementAssessment(assessSubscriptionChangeForApply(
                accountId, immediate(request), CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR,
                quotaBonuses));
    }

    private SpecialAgreementSelectionAssessment specialAgreementAssessment(
            SubscriptionChangeAssessment assessment
    ) {
        return new SpecialAgreementSelectionAssessment(
                assessment.current(), assessment.targetPlan(), assessment.targetSnapshot(),
                new SubscriptionOverrides(
                        assessment.selection().addOnCodes(), assessment.selection().quotaPackages()),
                assessment.previewPrice(), assessment.currencyCode(),
                assessment.currentEntitlements(), assessment.targetEntitlements(),
                assessment.conflicts(), assessment.immediateAllowed(), assessment.fingerprint());
    }

    private SubscriptionChangeRequest immediate(SubscriptionChangeRequest request) {
        return new SubscriptionChangeRequest(
                request.targetPlanCode(), request.addOnCodes(), request.quotaPackages(),
                SubscriptionChangeTiming.IMMEDIATE, request.planPriceSelection(),
                request.addOnPriceEntryIds(), request.quotaPackagePriceEntryIds());
    }

    private void requireExactRequestedPrices(SubscriptionEntitlementSnapshot snapshot,
            SubscriptionChangeRequest request) {
        if (request.addOnPriceEntryIds().isEmpty() && request.quotaPackagePriceEntryIds().isEmpty()) return;
        Map<String, UUID> addOnPrices = snapshot.addOns().stream().collect(Collectors.toMap(
                SubscriptionAddOnSnapshot::code, SubscriptionAddOnSnapshot::priceEntryId));
        Map<String, UUID> packagePrices = snapshot.quotaPackages().stream().collect(Collectors.toMap(
                SubscriptionQuotaPackageSnapshot::code, SubscriptionQuotaPackageSnapshot::priceEntryId));
        request.addOnPriceEntryIds().forEach((code, id) -> {
            if (!Objects.equals(addOnPrices.get(code), id)) {
                throw new StaleResourceVersionException("Exact Offer Add-on price is no longer selectable.");
            }
        });
        request.quotaPackagePriceEntryIds().forEach((code, id) -> {
            if (!Objects.equals(packagePrices.get(code), id)) {
                throw new StaleResourceVersionException("Exact Offer capacity-package price is no longer selectable.");
            }
        });
    }

    private SubscriptionChangeAssessment assessSubscriptionChangeForApply(
            UUID accountId,
            SubscriptionChangeRequest request,
            CommercialCatalogResolver.Audience audience,
            List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses
    ) {
        Subscription current = requireUsableSubscription(accountId);
        CommercialPolicyEvaluator.Evaluation policyEvaluation =
                commercialPolicyEvaluator.evaluate(accountId, clock.instant());
        // Preserve the ordinary client privacy boundary before exact lock-taking selection.
        ClientPlanSelection preliminary = requirePlanSelection(
                current, request.targetPlanCode(), request.planPriceSelection(),
                audience, policyEvaluation);
        requireSameSubscriptionCurrency(current, preliminary.price());
        Set<String> requestedAddOns = normalizeAddOnCodes(request.addOnCodes());
        List<QuotaPackageSelection> requestedPackages = normalizeQuotaPackages(request.quotaPackages());
        CommercialCatalogResolver.RetainedSelection retained = retainedSelection(
                current.getEntitlementSnapshot(), preliminary.plan(), request);
        var planned = commercialPolicySelectionPlanner.plan(
                preliminary.plan(), CommercialCatalogResolver.PriceTuple.from(preliminary.price()),
                requestedAddOns, requestedPackages, audience, retained, policyEvaluation);
        requireResolvedSelection(planned.resolution(), audience);
        var finalized = request.addOnPriceEntryIds().isEmpty() && request.quotaPackagePriceEntryIds().isEmpty()
                ? commercialSelectionFinalizer.finalizeSelection(
                        request.targetPlanCode(), request.planPriceSelection(), planned.addOnCodes(),
                        planned.quotaPackages(), audience, retained,
                        current.getEntitlementSnapshot(), policyEvaluation)
                : commercialSelectionFinalizer.finalizeSelection(
                        request.targetPlanCode(), request.planPriceSelection(), planned.addOnCodes(),
                        planned.quotaPackages(), audience, retained,
                        current.getEntitlementSnapshot(), policyEvaluation,
                        request.addOnPriceEntryIds(), request.quotaPackagePriceEntryIds());
        requireResolvedSelection(finalized.resolution(), audience);

        // The finalizer clears the persistence context; re-read the subscription while the
        // Account lock still prevents a competing subscription operation for this tenant.
        current = requireUsableSubscription(accountId);
        requireSameSubscriptionCurrency(current, finalized.planPrice());
        ChangeSelection selection = new ChangeSelection(planned.addOnCodes(), planned.quotaPackages());
        var finalizedPlan = new CommercialPolicySelectionPlanner.PlannedSelection(
                finalized.resolution(), planned.addOnCodes(), planned.quotaPackages(),
                planned.acceptedGrants(), planned.rejectedGrants());
        var terms = commercialPolicyTermsService.apply(
                finalized.plan(), finalized.snapshot(), policyEvaluation, finalizedPlan);
        SubscriptionEntitlementSnapshot targetSnapshot =
                terms.snapshot().withOfferQuotaBonuses(quotaBonuses);
        return assessResolvedChange(
                accountId, current, finalized.plan(), targetSnapshot, selection,
                request.effectiveTiming(), audience, terms.evaluation());
    }

    private SubscriptionChangeAssessment assessResolvedChange(
            UUID accountId,
            Subscription current,
            Plan targetPlan,
            SubscriptionEntitlementSnapshot targetSnapshot,
            ChangeSelection selection,
            SubscriptionChangeTiming timing,
            CommercialCatalogResolver.Audience audience,
            com.hiveapp.platform.client.plan.dto.SubscriptionCommercialPolicyEvaluation policyEvaluation
    ) {
        List<SubscriptionChangeConflict> conflicts = subscriptionImpactAnalyzer.analyze(
                accountId, current, targetSnapshot);
        Money price = previewPrice(current, targetPlan, targetSnapshot, selection);
        ClientSubscriptionEntitlementState currentEntitlements = clientEntitlementState(
                current.getEntitlementSnapshot());
        ClientSubscriptionEntitlementState targetEntitlements = clientEntitlementState(targetSnapshot);
        Set<String> effectiveFeatureCodes = targetEntitlements.featureCodes();
        List<EffectiveQuotaLimit> effectiveQuotaLimits = targetEntitlements.effectiveQuotaLimits();
        SubscriptionPeriodCalculator.Period effectivePeriod = subscriptionPeriodCalculator.change(
                targetSnapshot.billingCycle(), timing, current.getCurrentPeriodEnd());
        String fingerprint = subscriptionChangeFingerprint(
                current, targetPlan, targetSnapshot, selection, timing,
                price, conflicts, effectiveQuotaLimits,
                policyEvaluation, audience);
        return new SubscriptionChangeAssessment(
                current, targetPlan, targetSnapshot, selection,
                current.getCurrentPrice(), price.amount(), price.currencyCode(),
                conflicts.isEmpty() && !policyEvaluation.blocked(),
                Set.copyOf(effectiveFeatureCodes), List.copyOf(effectiveQuotaLimits),
                List.copyOf(conflicts), policyEvaluation, timing, effectivePeriod,
                currentEntitlements, targetEntitlements, fingerprint);
    }

    private ClientSubscriptionEntitlementState clientEntitlementState(
            SubscriptionEntitlementSnapshot snapshot
    ) {
        Set<String> featureCodes = snapshot.features().stream()
                .map(feature -> feature.featureCode())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> addOnCodes = snapshot.addOns().stream()
                .map(SubscriptionAddOnSnapshot::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<QuotaPackageSelection> quotaPackages = snapshot.quotaPackages().stream()
                .map(item -> new QuotaPackageSelection(item.code(), item.quantity()))
                .toList();
        return new ClientSubscriptionEntitlementState(
                snapshot.planCode(), snapshot.billingCycle(), featureCodes,
                subscriptionImpactAnalyzer.effectiveQuotaLimits(snapshot), addOnCodes, quotaPackages);
    }

    private String subscriptionChangeFingerprint(
            Subscription current,
            Plan targetPlan,
            SubscriptionEntitlementSnapshot targetSnapshot,
            ChangeSelection selection,
            SubscriptionChangeTiming timing,
            Money price,
            List<SubscriptionChangeConflict> conflicts,
            List<EffectiveQuotaLimit> effectiveQuotaLimits,
            SubscriptionCommercialPolicyEvaluation policyEvaluation,
            CommercialCatalogResolver.Audience audience
    ) {
        StringBuilder state = new StringBuilder();
        appendFingerprint(state, "subscription-change");
        appendFingerprint(state, audience);
        appendFingerprint(state, current.getId());
        appendFingerprint(state, current.getVersion());
        appendFingerprint(state, current.getPlan().getId());
        appendFingerprint(state, current.getStatus());
        appendFingerprint(state, current.getCurrentPrice());
        appendFingerprint(state, current.getCurrentPriceCurrencyCode());
        appendFingerprint(state, timing);
        appendSnapshotFingerprint(state, current.getEntitlementSnapshot());
        appendFingerprint(state, targetPlan.getId());
        appendFingerprint(state, targetPlan.getVersion());
        appendSnapshotFingerprint(state, targetSnapshot);
        selection.addOnCodes().stream().sorted()
                .forEach(code -> appendFingerprint(state, "add-on:" + code));
        selection.quotaPackages().stream()
                .sorted(Comparator.comparing(QuotaPackageSelection::packageCode))
                .forEach(item -> {
                    appendFingerprint(state, "package:" + item.packageCode());
                    appendFingerprint(state, item.quantity());
                });
        appendFingerprint(state, price.amount().toPlainString());
        appendFingerprint(state, price.currencyCode());
        conflicts.stream().map(Object::toString).sorted()
                .forEach(item -> appendFingerprint(state, "conflict:" + item));
        effectiveQuotaLimits.stream().map(Object::toString).sorted()
                .forEach(item -> appendFingerprint(state, "quota:" + item));
        appendPolicyFingerprint(state, policyEvaluation);
        return sha256(state.toString());
    }

    private void appendSnapshotFingerprint(
            StringBuilder state,
            SubscriptionEntitlementSnapshot snapshot
    ) {
        appendFingerprint(state, snapshot.schemaVersion());
        appendFingerprint(state, snapshot.planCode());
        appendFingerprint(state, snapshot.planName());
        appendFingerprint(state, snapshot.planDefinitionVersion());
        appendFingerprint(state, snapshot.financialPlanSource());
        appendFingerprint(state, snapshot.basePrice());
        appendFingerprint(state, snapshot.currencyCode());
        appendFingerprint(state, snapshot.billingCycle());
        appendFingerprint(state, snapshot.planPriceEntryId());
        snapshot.features().stream().map(Object::toString).sorted()
                .forEach(item -> appendFingerprint(state, "feature:" + item));
        snapshot.addOns().stream().map(Object::toString).sorted()
                .forEach(item -> appendFingerprint(state, "add-on-snapshot:" + item));
        snapshot.quotaPackages().stream().map(Object::toString).sorted()
                .forEach(item -> appendFingerprint(state, "package-snapshot:" + item));
        appendPolicyFingerprint(state, snapshot.commercialPolicyEvaluation());
    }

    /** Policy time is evidence metadata; exact policy/effect terms are the stale-review contract. */
    private void appendPolicyFingerprint(
            StringBuilder state,
            SubscriptionCommercialPolicyEvaluation evaluation
    ) {
        if (evaluation == null) {
            appendFingerprint(state, "policy:<none>");
            return;
        }
        appendFingerprint(state, evaluation.catalogueRecurringPrice());
        appendFingerprint(state, evaluation.fixedRecurringPrice());
        appendFingerprint(state, evaluation.discountAmount());
        appendFingerprint(state, evaluation.finalRecurringPrice());
        appendFingerprint(state, evaluation.currencyCode());
        evaluation.decisions().stream()
                .sorted(Comparator.comparing(decision -> decision.effectId().toString()))
                .forEach(decision -> {
                    appendFingerprint(state, decision.policyId());
                    appendFingerprint(state, decision.activationId());
                    appendFingerprint(state, decision.policyRevisionNumber());
                    appendFingerprint(state, decision.priority());
                    appendFingerprint(state, decision.effectId());
                    appendFingerprint(state, decision.effectOrder());
                    appendFingerprint(state, decision.effectType());
                    appendFingerprint(state, decision.productType());
                    appendFingerprint(state, decision.productId());
                    appendFingerprint(state, decision.productCode());
                    appendFingerprint(state, decision.featureCode());
                    appendFingerprint(state, decision.quotaResource());
                    appendFingerprint(state, decision.quantityDelta());
                    appendFingerprint(state, decision.configuredAmount());
                    appendFingerprint(state, decision.configuredCurrencyCode());
                    appendFingerprint(state, decision.percentage());
                    appendFingerprint(state, decision.maximumAmount());
                    appendFingerprint(state, decision.maximumCurrencyCode());
                    appendFingerprint(state, decision.outcome());
                    appendFingerprint(state, decision.evaluatedAmount());
                    appendFingerprint(state, decision.evaluatedCurrencyCode());
                });
        evaluation.conflicts().stream().map(Object::toString).sorted()
                .forEach(conflict -> appendFingerprint(state, "policy-conflict:" + conflict));
    }

    private void appendFingerprint(StringBuilder state, Object value) {
        String encoded = value == null ? "<null>" : value.toString();
        state.append(encoded.length()).append(':').append(encoded).append(';');
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private StaleResourceVersionException staleSubscriptionPreview() {
        return new StaleResourceVersionException(
                "Subscription-change preview is stale. Review the selection again and retry.");
    }

    private boolean isNoOp(
            Subscription current,
            SubscriptionEntitlementSnapshot targetSnapshot,
            ChangeSelection selection
    ) {
        SubscriptionOverrides currentOverrides = subscriptionOverrideReader.read(current.getCustomOverrides());
        SubscriptionEntitlementSnapshot currentSnapshot = current.getEntitlementSnapshot();
        return current.getPlan().getCode().equals(targetSnapshot.planCode())
                && Objects.equals(currentOverrides.addOnCodes(), selection.addOnCodes())
                && Objects.equals(currentOverrides.quotaPackages(), selection.quotaPackages())
                && Objects.equals(currentSnapshot.features(), targetSnapshot.features())
                && sameSelectedPrices(currentSnapshot, targetSnapshot)
                && samePolicyTerms(currentSnapshot.commercialPolicyEvaluation(),
                        targetSnapshot.commercialPolicyEvaluation());
    }

    private boolean samePolicyTerms(
            SubscriptionCommercialPolicyEvaluation current,
            SubscriptionCommercialPolicyEvaluation target
    ) {
        StringBuilder currentState = new StringBuilder();
        StringBuilder targetState = new StringBuilder();
        appendPolicyFingerprint(currentState, current);
        appendPolicyFingerprint(targetState, target);
        return currentState.toString().equals(targetState.toString());
    }

    private boolean sameSelectedPrices(
            SubscriptionEntitlementSnapshot current,
            SubscriptionEntitlementSnapshot target
    ) {
        if (!samePrice(
                current.planPriceEntryId(), current.basePrice(), current.currencyCode(), current.billingCycle(),
                target.planPriceEntryId(), target.basePrice(), target.currencyCode(), target.billingCycle())) {
            return false;
        }
        Map<String, com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot> currentAddOns =
                current.addOns().stream().collect(Collectors.toMap(
                        com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot::code,
                        Function.identity()));
        if (currentAddOns.size() != target.addOns().size()
                || !currentAddOns.keySet().equals(target.addOns().stream()
                    .map(com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot::code)
                    .collect(Collectors.toSet()))) {
            return false;
        }
        for (var targetAddOn : target.addOns()) {
            var currentAddOn = currentAddOns.get(targetAddOn.code());
            if (currentAddOn == null || !samePrice(
                    currentAddOn.priceEntryId(), currentAddOn.price(), currentAddOn.currencyCode(),
                    currentAddOn.billingCycle(), targetAddOn.priceEntryId(), targetAddOn.price(),
                    targetAddOn.currencyCode(), targetAddOn.billingCycle())) {
                return false;
            }
        }
        Map<String, com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot> currentPackages =
                current.quotaPackages().stream().collect(Collectors.toMap(
                        com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::code,
                        Function.identity()));
        if (currentPackages.size() != target.quotaPackages().size()
                || !currentPackages.keySet().equals(target.quotaPackages().stream()
                    .map(com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot::code)
                    .collect(Collectors.toSet()))) {
            return false;
        }
        for (var targetPackage : target.quotaPackages()) {
            var currentPackage = currentPackages.get(targetPackage.code());
            if (currentPackage == null || !samePrice(
                    currentPackage.priceEntryId(), currentPackage.unitPrice(), currentPackage.currencyCode(),
                    currentPackage.billingCycle(), targetPackage.priceEntryId(), targetPackage.unitPrice(),
                    targetPackage.currencyCode(), targetPackage.billingCycle())) {
                return false;
            }
        }
        return true;
    }

    private boolean samePrice(
            UUID currentId,
            BigDecimal currentAmount,
            String currentCurrency,
            com.hiveapp.platform.client.plan.domain.constant.BillingCycle currentCycle,
            UUID targetId,
            BigDecimal targetAmount,
            String targetCurrency,
            com.hiveapp.platform.client.plan.domain.constant.BillingCycle targetCycle
    ) {
        if (currentId != null && targetId != null) {
            return currentId.equals(targetId);
        }
        return currentAmount != null
                && targetAmount != null
                && currentAmount.compareTo(targetAmount) == 0
                && Objects.equals(currentCurrency, targetCurrency)
                && currentCycle == targetCycle;
    }

    private Plan requireActivePlan(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            throw new InvalidRequestException("Target plan code is required.");
        }
        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "code", planCode));
        if (!plan.isActive()) {
            throw new InvalidStateException("Inactive plans cannot be selected.");
        }
        return plan;
    }

    private ClientPlanSelection requirePlanSelection(
            Subscription current,
            String planCode,
            ProductPriceSelectionRequest requestedPrice,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation policyEvaluation
    ) {
        if (planCode == null || planCode.isBlank()) {
            throw unavailableSelection(audience);
        }
        CommercialCatalogResolver.PlanResolution resolution = commercialCatalogResolver
                .resolveCatalog(audience)
                .plans().stream()
                .filter(candidate -> candidate.plan().getCode().equals(planCode))
                .findFirst()
                .orElseThrow(() -> unavailableSelection(audience));
        ProductPrice retainedPrice = retainedPlanPrice(current, resolution.plan(), requestedPrice);
        if (audience == CommercialCatalogResolver.Audience.CLIENT_CATALOG
                && !current.getPlan().getId().equals(resolution.plan().getId())
                && !policyEvaluation.allowsDirectSelection(CommercialPolicyProductType.PLAN, resolution.plan().getId())
                && !planPublicSelection.isPublicChoice(resolution.plan())) {
            throw unavailableSelection(audience);
        }
        if (!CommercialPolicySelectionRules.planSelectable(
                        resolution, audience, policyEvaluation)
                && !retainedPlanPriceSelectable(
                        resolution, retainedPrice, audience, policyEvaluation)) {
            throw unavailableSelection(audience);
        }
        // Only after the Plan is known to be selectable for this audience may exact price lookup
        // return its normal owner/tuple errors. Client-hidden and unknown Plans fail identically.
        return new ClientPlanSelection(resolution.plan(), retainedPrice != null
                ? retainedPrice
                : productPriceResolver.resolvePlan(resolution.plan(), requestedPrice));
    }

    private boolean retainedPlanPriceSelectable(
            CommercialCatalogResolver.PlanResolution resolution,
            ProductPrice retainedPrice,
            CommercialCatalogResolver.Audience audience,
            CommercialPolicyEvaluator.Evaluation policyEvaluation
    ) {
        if (retainedPrice == null
                || resolution.issues().isEmpty()
                || resolution.issues().stream().anyMatch(
                        issue -> issue.reason() != ExtensionAvailabilityReason.PRICE_UNAVAILABLE)
                || policyEvaluation.blocksProduct(
                        CommercialPolicyProductType.PLAN, resolution.plan().getId())) {
            return false;
        }
        return audience == CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR
                || resolution.effectiveSalesVisibility()
                == com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility.PUBLIC
                || policyEvaluation.allowsDirectSelection(
                        CommercialPolicyProductType.PLAN, resolution.plan().getId());
    }

    /**
     * A subscriber may change extensions without being forced off an exact historical Plan price
     * that is no longer offered to new buyers. The price is usable only for the same current Plan
     * and only when the request does not choose a different price.
     */
    private ProductPrice retainedPlanPrice(
            Subscription current,
            Plan targetPlan,
            ProductPriceSelectionRequest requestedPrice
    ) {
        SubscriptionEntitlementSnapshot snapshot = current.getEntitlementSnapshot();
        if (snapshot == null
                || snapshot.planPriceEntryId() == null
                || !targetPlan.getCode().equals(snapshot.planCode())) {
            return null;
        }
        boolean preserve = requestedPrice == null
                || (requestedPrice.priceEntryId() != null
                && requestedPrice.priceEntryId().equals(snapshot.planPriceEntryId()));
        if (!preserve) {
            return null;
        }
        ProductPrice retained = productPriceRepository.findOwned(
                        com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN,
                        snapshot.financialPlanSource() == null ? targetPlan.getId()
                                : snapshot.financialPlanSource().planId(), snapshot.planPriceEntryId())
                .orElseThrow(() -> new StaleResourceVersionException(
                        "The current subscription price no longer exists. Reload and retry."));
        if (requestedPrice != null && requestedPrice.currencyCode() != null
                && !Money.normalizeCurrencyCode(requestedPrice.currencyCode())
                .equals(retained.getCurrencyCode())) {
            throw new InvalidRequestException(
                    "Selected price entry does not use the requested currency.");
        }
        if (requestedPrice != null && requestedPrice.billingCycle() != null
                && requestedPrice.billingCycle() != retained.getBillingCycle()) {
            throw new InvalidRequestException(
                    "Selected price entry does not use the requested billing cycle.");
        }
        return retained;
    }

    private InvalidRequestException unavailableSelection(
            CommercialCatalogResolver.Audience audience
    ) {
        return audience == CommercialCatalogResolver.Audience.CLIENT_CATALOG
                ? unavailableClientSelection()
                : new InvalidRequestException(
                        "The requested commercial selection is unavailable for operator assignment.");
    }

    private InvalidRequestException unavailableClientSelection() {
        return new InvalidRequestException("The requested commercial selection is unavailable.");
    }

    private void requireSameSubscriptionCurrency(Subscription current, ProductPrice targetPrice) {
        String currentCurrency = current.getCurrentPriceCurrencyCode() != null
                ? current.getCurrentPriceCurrencyCode()
                : current.getPlan().getCurrencyCode();
        if (!currentCurrency.equals(targetPrice.getCurrencyCode())) {
            throw new InvalidRequestException(
                    "Subscription changes cannot convert " + currentCurrency + " to "
                            + targetPrice.getCurrencyCode() + ". Choose a plan in the current currency.");
        }
    }

    private Map<String, Long> usageByQuotaSlot(UUID accountId, Map<String, FeatureDefinition> definitions) {
        Map<String, Long> usage = new java.util.LinkedHashMap<>();
        definitions.values().forEach(definition -> definition.quotaSlots().forEach(slot -> {
            var measurement = subscriptionImpactAnalyzer.quotaUsage(
                    accountId, definition.code(), slot.resource());
            if (measurement.isPresent()) {
                usage.put(definition.code() + ":" + slot.resource(), measurement.getAsLong());
            }
        }));
        return Map.copyOf(usage);
    }

    private List<QuotaLimitEntry> safeQuotaConfigs(PlanFeature planFeature) {
        return planFeature.getQuotaConfigs() != null ? planFeature.getQuotaConfigs() : List.of();
    }

    private boolean isSelfServiceAvailable(PlanFeature planFeature, Map<String, FeatureDefinition> definitions) {
        FeatureDefinition definition = definitions.get(planFeature.getFeature().getCode());
        if (definition == null || !definition.planAssignable()) {
            return false;
        }
        return planFeature.getFeature().isNewSalesEnabled()
                && (planFeature.getFeature().getStatus() == FeatureStatus.PUBLIC
                || planFeature.getFeature().getStatus() == FeatureStatus.BETA);
    }

    private SubscriptionChangeOperation newChangeOperation(
            Subscription current,
            Plan targetPlan,
            SubscriptionOverrides selection,
            SubscriptionEntitlementSnapshot targetSnapshot,
            SubscriptionChangeTiming timing,
            SubscriptionChangeOrigin origin,
            UUID actorUserId,
            String requestReason,
            UUID offerRedemptionId,
            UUID offerApplicationClaimId,
            SubscriptionOfferEvaluation offerEvaluation
    ) {
        SubscriptionPeriodCalculator.Period targetPeriod = subscriptionPeriodCalculator.change(
                targetSnapshot.billingCycle(), timing, current.getCurrentPeriodEnd());
        SubscriptionChangeOperation operation = new SubscriptionChangeOperation();
        operation.setAccount(current.getAccount());
        operation.setSourceSubscription(current);
        operation.setTargetPlan(targetPlan);
        operation.setTiming(timing);
        operation.setStatus(SubscriptionChangeStatus.PENDING);
        operation.setEffectiveAt(targetPeriod.startsAt());
        operation.setRequestedSelection(selection);
        operation.setBeforeSnapshot(subscriptionSnapshotReader.read(current.getEntitlementSnapshot())
                .orElseThrow(() -> new InvalidStateException(
                        "Current subscription has no entitlement snapshot.")));
        operation.setTargetSnapshot(
                targetSnapshot.withEffectivePeriod(targetPeriod.startsAt(), targetPeriod.endsAt()));
        operation.setCommercialPolicyEvaluation(targetSnapshot.commercialPolicyEvaluation());
        operation.setCommercialOfferEvaluation(offerEvaluation);
        operation.setOfferRedemptionId(offerRedemptionId);
        operation.setOfferApplicationClaimId(offerApplicationClaimId);
        operation.setRequestOrigin(origin);
        operation.setRequestedByUserId(actorUserId);
        operation.setRequestReason(requestReason);
        return operation;
    }

    private SubscriptionDto toDto(Subscription subscription) {
        return new SubscriptionDto(
                subscription.getId(),
                new SubscriptionDto.PlanSummaryDto(
                        subscription.getPlan().getCode(),
                        subscription.getPlan().getName(),
                        subscription.getPlan().getPrice(),
                        subscription.getPlan().getCurrencyCode()),
                subscription.getStatus(),
                subscription.getCurrentPrice(),
                subscription.getCurrentPriceCurrencyCode(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getPastDueAt(),
                subscription.getGraceEndsAt(),
                subscription.getSuspendedAt(),
                subscription.getSuspensionCause());
    }

    private record ChangeSelection(
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages
    ) {}

    private record SubscriptionChangeAssessment(
            Subscription current,
            Plan targetPlan,
            SubscriptionEntitlementSnapshot targetSnapshot,
            ChangeSelection selection,
            BigDecimal currentPrice,
            BigDecimal previewPrice,
            String currencyCode,
            boolean immediateAllowed,
            Set<String> effectiveFeatureCodes,
            List<EffectiveQuotaLimit> effectiveQuotaLimits,
            List<SubscriptionChangeConflict> conflicts,
            SubscriptionCommercialPolicyEvaluation policyEvaluation,
            SubscriptionChangeTiming timing,
            SubscriptionPeriodCalculator.Period effectivePeriod,
            ClientSubscriptionEntitlementState currentEntitlements,
            ClientSubscriptionEntitlementState targetEntitlements,
            String fingerprint
    ) {
        private SubscriptionChangePreviewResponse toResponse(
                long catalogRevision,
                String registryVersion,
                Instant evaluatedAt,
                Instant expiresAt,
                String previewToken
        ) {
            return new SubscriptionChangePreviewResponse(
                    current.getId(), current.getVersion(), catalogRevision, registryVersion,
                    evaluatedAt, expiresAt, previewToken, current.getPlan().getCode(),
                    targetPlan.getCode(), currentPrice, previewPrice, currencyCode,
                    timing, effectivePeriod.startsAt(), effectivePeriod.endsAt(),
                    currentEntitlements, targetEntitlements, immediateAllowed,
                    effectiveFeatureCodes, effectiveQuotaLimits,
                    selection.addOnCodes(), selection.quotaPackages(), conflicts,
                    policyEvaluation);
        }
    }

    private record ClientPlanSelection(Plan plan, ProductPrice price) {}

    private List<ClientPlanCatalogResponse.CatalogPrice> toCatalogPrices(List<ProductPrice> prices) {
        return toCatalogPrices(prices, false);
    }

    private List<ClientPlanCatalogResponse.CatalogPrice> toCatalogPrices(
            List<ProductPrice> prices,
            boolean granted
    ) {
        return (prices == null ? List.<ProductPrice>of() : prices).stream()
                .map(price -> new ClientPlanCatalogResponse.CatalogPrice(
                        price.getId(),
                        granted ? Money.zero(price.getCurrencyCode()).amount() : price.getAmount(),
                        price.getCurrencyCode(), price.getBillingCycle(),
                        price.getEffectiveFrom(), price.getEffectiveUntil()))
                .toList();
    }

}
