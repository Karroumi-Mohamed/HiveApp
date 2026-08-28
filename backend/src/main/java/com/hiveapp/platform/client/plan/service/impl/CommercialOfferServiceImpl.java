package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.service.*;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.*;
import java.math.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class CommercialOfferServiceImpl implements CommercialOfferService {
  private static final int CLIENT_CATALOGUE_CANDIDATE_LIMIT = 200;
  private static final List<CommercialOfferRedemptionStatus> USED =
      List.of(CommercialOfferRedemptionStatus.RESERVED, CommercialOfferRedemptionStatus.APPLIED);

  private final CommercialOfferRepository offers;
  private final CommercialOfferRedemptionRepository redemptions;
  private final CommercialOfferCapacityRepository capacities;
  private final CommercialCampaignRepository campaigns;
  private final AccountRepository accounts;
  private final SubscriptionRepository subscriptions;
  private final SubscriptionChangeOperationRepository subscriptionOperations;
  private final PlanRepository plans;
  private final AddOnRepository addOns;
  private final QuotaPackageRepository packages;
  private final ProductPriceRepository prices;
  private final SubscriptionService subscriptionsService;
  private final CommercialCatalogVersionService catalogVersions;
  private final RegistryCatalogVersionService registryVersions;
  private final CommercialPreviewTokenService previewTokens;
  private final Clock clock;
  private final CommercialOfferCodeHasher codeHasher;
  private final PlatformTransactionManager transactionManager;
  private final CommercialOfferClientProjectionMapper projections;
  private final CommercialOfferEligibilityService eligibility;
  private final CommercialOfferRedemptionTransitionService transitions;
  private final CommercialCatalogResolver catalogResolver;
  private final CommercialPolicyEvaluator policyEvaluator;

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<CommercialOfferViews.ClientOffer> catalogue(UUID accountId, Pageable p) {
    eligibility.requireActiveAccount(accountId);
    return catalogVersions.readConsistently(ignored -> catalogueFromStableSnapshot(accountId, p));
  }

  private Page<CommercialOfferViews.ClientOffer> catalogueFromStableSnapshot(
      UUID accountId, Pageable requestedPage) {
    Page<CommercialOffer> candidates =
        offers.findEligibleClientCatalogue(
            accountId,
            clock.instant(),
            PageRequest.of(0, CLIENT_CATALOGUE_CANDIDATE_LIMIT, requestedPage.getSort()));
    if (candidates.getTotalElements() > CLIENT_CATALOGUE_CANDIDATE_LIMIT) {
      throw new OfferNotAvailableException();
    }
    Map<UUID, ProductPrice> exactPrices = projections.exactPrices(candidates.getContent());
    Set<UUID> availableIds =
        availableClientSelectionIds(candidates.getContent(), accountId, exactPrices);
    List<CommercialOfferViews.ClientOffer> eligible =
        candidates.getContent().stream()
            .filter(offer -> availableIds.contains(offer.getId()))
            .map(offer -> projections.client(offer, exactPrices))
            .toList();
    int from = Math.toIntExact(Math.min(requestedPage.getOffset(), eligible.size()));
    int to = Math.min(from + requestedPage.getPageSize(), eligible.size());
    return new PageImpl<>(eligible.subList(from, to), requestedPage, eligible.size());
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CommercialOfferViews.ClientOffer detail(UUID accountId, UUID id) {
    return catalogVersions.readConsistently(
        ignored -> {
          var o = eligibility.requireAvailable(id, accountId, false, false);
          if (o.getDiscovery() != CommercialOfferDiscovery.CATALOG)
            throw new OfferNotAvailableException();
          Map<UUID, ProductPrice> exactPrices = projections.exactPrices(List.of(o));
          if (!availableClientSelectionIds(List.of(o), accountId, exactPrices).contains(o.getId())) {
            throw new OfferNotAvailableException();
          }
          return projections.client(o, exactPrices);
        },
        OfferNotAvailableException::new);
  }

  @Override
  @Transactional(readOnly = true)
  public CommercialOfferViews.CodeResolution resolveCode(
      UUID accountId, UUID actor, CommercialOfferRequests.ResolveCode r) {
    String hash;
    try {
      hash = codeHasher.hash(r.code());
    } catch (IllegalArgumentException invalidCode) {
      throw new OfferNotAvailableException();
    }
    var found = offers.findPublishedByReservedCodeHash(hash, PageRequest.of(0, 2));
    if (found.size() != 1) throw new OfferNotAvailableException();
    var offer = found.getFirst();
    try {
      if (!eligibility.eligible(offer, accountId, false, true)
          || !eligibility.hasAvailableCapacity(offer, accountId)) {
        throw new OfferNotAvailableException();
      }
      // Resolution must not distinguish a valid code whose exact selection is now unusable.
      buildPreview(offer, accountId, actor, false);
    } catch (OfferNotAvailableException
        | InvalidRequestException
        | InvalidStateException
        | OperationBlockedException
        | ResourceNotFoundException
        | OfferRedemptionBlockedException expectedUnavailability) {
      throw new OfferNotAvailableException();
    }
    long catalog = catalogVersions.currentRevision();
    String registry = registryVersions.currentVersion();
    String fp = discoveryFingerprint(offer, accountId);
    var evidence =
        previewTokens.issue(
            CommercialPreviewKind.COMMERCIAL_OFFER_REDEMPTION,
            offer.getId(),
            offer.getVersion(),
            actor,
            catalog,
            registry,
            fp,
            clock.instant());
    return new CommercialOfferViews.CodeResolution(
        projections.client(offer, projections.exactPrices(List.of(offer))),
        evidence.expiresAt(),
        evidence.token());
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CommercialOfferViews.EligibilityPreview preview(
      UUID accountId, UUID actor, UUID id, String discoveryToken, boolean operator) {
    var o = eligibility.requireAvailable(id, accountId, operator, true);
    if (!operator && o.getDiscovery() == CommercialOfferDiscovery.CODE_ONLY) {
      previewTokens.requireValid(
          discoveryToken,
          CommercialPreviewKind.COMMERCIAL_OFFER_REDEMPTION,
          o.getId(),
          o.getVersion(),
          actor,
          catalogVersions.currentRevision(),
          registryVersions.currentVersion(),
          discoveryFingerprint(o, accountId),
          OfferNotAvailableException::new);
    }
    try {
      return buildPreview(o, accountId, actor, operator);
    } catch (RuntimeException failure) {
      throw clientSafeFailure(failure, operator, OfferNotAvailableException::new);
    }
  }

  @Override
  public CommercialOfferViews.Acceptance accept(
      UUID accountId,
      UUID actor,
      UUID id,
      String key,
      CommercialOfferRequests.Accept r,
      boolean operator) {
    if (key == null || key.isBlank() || key.length() > 200)
      throw new InvalidRequestException(
          "Idempotency-Key is required and must not exceed 200 characters.");
    if (!operator) subscriptionsService.requireOfferApplyAuthority(accountId, actor);
    String applicationReason =
        operator ? requiredOperatorReason(r.reason()) : clientReason(r.reason());
    String keyHash = CommercialOfferAdminServiceImpl.sha(accountId + "|" + key);
    String requestFingerprint = acceptanceRequestFingerprint(id, operator, r);
    try {
      Reservation reservation;
      try {
        reservation =
            reserveWithConcurrencyRetry(
                accountId, actor, id, keyHash, requestFingerprint, r.previewToken(), operator);
      } catch (DataIntegrityViolationException concurrentReplay) {
        reservation =
            inNewTransaction(
                TransactionDefinition.ISOLATION_READ_COMMITTED,
                () -> replay(accountId, keyHash, requestFingerprint));
      }
      if (reservation.replay() != null) return reservation.replay();
      return applyReservation(accountId, actor, applicationReason, reservation, operator);
    } catch (RuntimeException failure) {
      throw clientSafeFailure(failure, operator, OfferNotAvailableException::new);
    }
  }

  private CommercialOfferViews.Acceptance applyReservation(
      UUID accountId,
      UUID actor,
      String applicationReason,
      Reservation acceptedReservation,
      boolean operator) {
    try {
      var changePreview =
          operator
              ? subscriptionsService.previewOfferChangeAsOperator(
                  accountId, actor, acceptedReservation.selection())
              : subscriptionsService.previewOfferChange(
                  accountId, actor, acceptedReservation.selection());
      var applied =
          operator
              ? subscriptionsService.applyOfferChangeAsOperator(
                  accountId,
                  actor,
                  new SubscriptionChangeApplyRequest(
                      acceptedReservation.selection(), changePreview.previewToken()),
                  applicationReason,
                  acceptedReservation.redemptionId(),
                  acceptedReservation.evaluation())
              : subscriptionsService.applyOfferChange(
                  accountId,
                  actor,
                  new SubscriptionChangeApplyRequest(
                      acceptedReservation.selection(), changePreview.previewToken()),
                  applicationReason,
                  acceptedReservation.redemptionId(),
                  acceptedReservation.evaluation());
      return inNewTransaction(
          TransactionDefinition.ISOLATION_READ_COMMITTED,
          () -> complete(acceptedReservation.redemptionId(), applied.operation()));
    } catch (RuntimeException applicationFailure) {
      try {
        inNewTransaction(
            TransactionDefinition.ISOLATION_READ_COMMITTED,
            () -> {
              failOrRecover(acceptedReservation.redemptionId());
              return Boolean.TRUE;
            });
      } catch (RuntimeException reconciliationFailure) {
        applicationFailure.addSuppressed(reconciliationFailure);
      }
      throw clientSafeFailure(
          applicationFailure, operator, OfferRedemptionBlockedException::new);
    }
  }

  private Reservation reserveWithConcurrencyRetry(
      UUID accountId,
      UUID actor,
      UUID offerId,
      String keyHash,
      String requestFingerprint,
      String previewToken,
      boolean operator) {
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        return inNewTransaction(
            TransactionDefinition.ISOLATION_SERIALIZABLE,
            () ->
                reserve(
                    accountId,
                    actor,
                    offerId,
                    keyHash,
                    requestFingerprint,
                    previewToken,
                    operator));
      } catch (PessimisticLockingFailureException transientRace) {
        if (attempt == 2) throw new OfferRedemptionBlockedException();
      }
    }
    throw new OfferRedemptionBlockedException();
  }

  private Reservation reserve(
      UUID accountId,
      UUID actor,
      UUID offerId,
      String keyHash,
      String requestFingerprint,
      String previewToken,
      boolean operator) {
    var account =
        accounts
            .findByIdForSubscriptionUpdate(accountId)
            .orElseThrow(OfferNotAvailableException::new);
    if (!operator && !account.getOwner().getId().equals(actor)) {
      throw new ForbiddenException("Only the Account owner can accept a commercial Offer.");
    }
    var existing = redemptions.findByAccount_IdAndIdempotencyKeyHash(accountId, keyHash);
    if (existing.isPresent()) return replay(existing.get(), requestFingerprint);
    if (!account.isActive()) throw new OfferNotAvailableException();

    catalogVersions.lockForMutation();
    CommercialOffer coordinate =
        offers.findOperationsById(offerId).orElseThrow(OfferNotAvailableException::new);
    campaigns
        .findByIdForUpdate(coordinate.getCampaign().getId())
        .orElseThrow(OfferNotAvailableException::new);
    offers.lockLineage(coordinate.getLineageId());
    CommercialOffer offer =
        offers.findForUpdate(offerId).orElseThrow(OfferNotAvailableException::new);
    registryVersions.lockForMutation();
    if (!eligibility.eligible(offer, accountId, operator, true)) {
      throw new OfferNotAvailableException();
    }

    CommercialOfferViews.EligibilityPreview currentPreview =
        buildPreview(offer, accountId, actor, operator);
    requireNonNoOp(offer, accountId, currentPreview);
    previewTokens.requireValid(
        previewToken,
        CommercialPreviewKind.COMMERCIAL_OFFER_REDEMPTION,
        offer.getId(),
        offer.getVersion(),
        actor,
        catalogVersions.currentRevision(),
        registryVersions.currentVersion(),
        fingerprint(offer, accountId, currentPreview),
        StaleOfferPreviewException::new);

    var capacity =
        capacities
            .lockByLineage(offer.getLineageId())
            .orElseThrow(OfferRedemptionBlockedException::new);
    long used =
        redemptions.countByOfferLineageIdAndAccount_IdAndStatusIn(
            offer.getLineageId(), accountId, USED);
    capacity.reserve(
        offer.getGlobalLimit() == null ? -1 : offer.getGlobalLimit(),
        used,
        offer.getPerAccountLimit() == null ? -1 : offer.getPerAccountLimit());
    SubscriptionOfferEvaluation evaluation = offerEvaluation(offer, currentPreview);
    CommercialOfferRedemption redemption =
        CommercialOfferRedemption.reserve(
            account,
            offer,
            operator ? CommercialOfferSurface.OPERATOR : CommercialOfferSurface.CLIENT,
            actor,
            keyHash,
            requestFingerprint,
            evaluation,
            clock.instant());
    redemptions.saveAndFlush(redemption);
    return new Reservation(
        redemption.getId(), changeRequest(offer), evaluation, currentPreview, null);
  }

  private Reservation replay(UUID accountId, String keyHash, String requestFingerprint) {
    accounts.findByIdForSubscriptionUpdate(accountId).orElseThrow(OfferNotAvailableException::new);
    CommercialOfferRedemption redemption =
        redemptions
            .findByAccount_IdAndIdempotencyKeyHash(accountId, keyHash)
            .orElseThrow(
                () -> new InvalidStateException("Concurrent Offer acceptance was not committed."));
    return replay(redemption, requestFingerprint);
  }

  private Reservation replay(CommercialOfferRedemption redemption, String requestFingerprint) {
    if (!MessageDigest.isEqual(
        redemption.getRequestFingerprint().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
        requestFingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
      throw new IdempotencyConflictException();
    }
    return new Reservation(
        redemption.getId(),
        null,
        redemption.getCommercialEvaluation(),
        null,
        new CommercialOfferViews.Acceptance(
            redemption.getId(),
            redemption.getSubscriptionOperationId(),
            redemption.getStatus(),
            true,
            projections.accepted(redemption)));
  }

  private CommercialOfferViews.Acceptance complete(
      UUID redemptionId, SubscriptionChangeOperationDto operation) {
    CommercialOfferRedemption redemption =
        redemptions.lockById(redemptionId).orElseThrow(OfferRedemptionBlockedException::new);
    transitions.applyOperation(redemption, operation.id(), operation.status());
    return new CommercialOfferViews.Acceptance(
        redemption.getId(),
        redemption.getSubscriptionOperationId(),
        redemption.getStatus(),
        false,
        projections.accepted(redemption));
  }

  private void failOrRecover(UUID redemptionId) {
    CommercialOfferRedemption redemption =
        redemptions.lockById(redemptionId).orElseThrow(OfferRedemptionBlockedException::new);
    if (redemption.getStatus() != CommercialOfferRedemptionStatus.RESERVED) return;
    var operation = subscriptionOperations.findByOfferRedemptionId(redemptionId);
    if (operation.isPresent()) {
      var found = operation.orElseThrow();
      transitions.applyOperation(redemption, found.getId(), found.getStatus());
      return;
    }
    transitions.failReservation(redemption, "SUBSCRIPTION_APPLICATION_FAILED");
  }

  private <T> T inNewTransaction(int isolation, Supplier<T> work) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.setIsolationLevel(isolation);
    T result = template.execute(status -> work.get());
    if (result == null) throw new IllegalStateException("Offer transaction returned no result.");
    return result;
  }

  private String requiredOperatorReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new InvalidRequestException("Operator Offer application requires a reason.");
    }
    return reason.trim();
  }

  private String clientReason(String reason) {
    return reason == null || reason.isBlank() ? "Accepted commercial Offer" : reason.trim();
  }

  private String acceptanceRequestFingerprint(
      UUID offerId, boolean operator, CommercialOfferRequests.Accept request) {
    String normalizedReason = request.reason() == null ? "" : request.reason().trim();
    return CommercialOfferAdminServiceImpl.sha(
        "offer-accept:v1|"
            + offerId
            + "|"
            + (operator ? "OPERATOR" : "CLIENT")
            + "|"
            + CommercialOfferAdminServiceImpl.sha(request.previewToken())
            + "|"
            + normalizedReason);
  }

  private record Reservation(
      UUID redemptionId,
      SubscriptionChangeRequest selection,
      SubscriptionOfferEvaluation evaluation,
      CommercialOfferViews.EligibilityPreview preview,
      CommercialOfferViews.Acceptance replay) {}

  @Override
  @Transactional(readOnly = true)
  public Page<CommercialOfferViews.ClientRedemption> history(UUID accountId, Pageable p) {
    Page<CommercialOfferRedemption> page = redemptions.findAllByAccount_Id(accountId, p);
    Map<UUID, ProductPrice> exactPrices =
        projections.exactPrices(
            page.getContent().stream().map(CommercialOfferRedemption::getOffer).toList());
    return page.map(redemption -> projections.clientRedemption(redemption, exactPrices));
  }

  @Override
  @Transactional(readOnly = true)
  public CommercialOfferViews.ClientRedemption redemption(UUID accountId, UUID id) {
    CommercialOfferRedemption redemption =
        redemptions
            .findByIdAndAccount_Id(id, accountId)
            .orElseThrow(OfferNotAvailableException::new);
    return projections.clientRedemption(
        redemption, projections.exactPrices(List.of(redemption.getOffer())));
  }

  private CommercialOfferViews.EligibilityPreview buildPreview(
      CommercialOffer o, UUID accountId, UUID actor, boolean operator) {
    SubscriptionChangeRequest request = changeRequest(o);
    var p =
        operator
            ? subscriptionsService.previewOfferChangeAsOperator(accountId, actor, request)
            : subscriptionsService.previewOfferChange(accountId, actor, request);
    var policy = p.commercialPolicyEvaluation();
    if (policy != null && policy.blocked()) {
      if (operator) {
        throw new InvalidStateException(
            "Offer cannot be applied until commercial-policy conflicts are resolved.");
      }
      throw new OfferNotAvailableException();
    }
    BigDecimal catalogue = policy == null ? p.previewPrice() : policy.catalogueRecurringPrice();
    BigDecimal fixedBase = policy == null ? catalogue : policy.fixedRecurringPrice();
    BigDecimal policyPrice = policy == null ? p.previewPrice() : policy.finalRecurringPrice();
    BigDecimal freeReduction = freeProductReduction(o);
    BigDecimal offerBase = fixedBase.subtract(freeReduction).max(BigDecimal.ZERO);
    BigDecimal offerPrice = discount(offerBase, o.getEffects());
    int comparison = offerPrice.compareTo(policyPrice);
    String winner = comparison < 0 ? "OFFER" : "POLICY";
    String winnerReason = winnerReason(winner);
    BigDecimal finalPrice = comparison < 0 ? offerPrice : policyPrice;
    long catalog = catalogVersions.currentRevision();
    String registry = registryVersions.currentVersion();
    String fp =
        pricingFingerprint(
            o,
            accountId,
            p.subscriptionId(),
            p.expectedSubscriptionVersion(),
            catalogue,
            policyPrice,
            offerPrice,
            finalPrice,
            winner);
    var evidence =
        previewTokens.issue(
            CommercialPreviewKind.COMMERCIAL_OFFER_REDEMPTION,
            o.getId(),
            o.getVersion(),
            actor,
            catalog,
            registry,
            fp,
            clock.instant());
    return new CommercialOfferViews.EligibilityPreview(
        o.getId(),
        p.subscriptionId(),
        p.expectedSubscriptionVersion(),
        catalogue,
        policyPrice,
        offerPrice,
        finalPrice,
        p.currencyCode(),
        fixedBase,
        freeReduction,
        winner,
        winnerReason,
        evidence.evaluatedAt(),
        evidence.expiresAt(),
        evidence.token());
  }

  private Set<UUID> availableClientSelectionIds(
      List<CommercialOffer> candidates,
      UUID accountId,
      Map<UUID, ProductPrice> exactPrices) {
    if (candidates.isEmpty()) return Set.of();
    Map<UUID, CommercialCatalogResolver.ExactSelectionCandidate> requests = new LinkedHashMap<>();
    for (CommercialOffer offer : candidates) {
      exactSelectionCandidate(offer, exactPrices).ifPresent(candidate -> requests.put(offer.getId(), candidate));
    }
    Map<UUID, CommercialCatalogResolver.SelectionResolution> resolutions =
        catalogResolver.resolveExactSelectionCandidates(
            requests.values(), CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
    CommercialPolicyEvaluator.Evaluation policy = policyEvaluator.evaluate(accountId, clock.instant());
    Set<UUID> result = new LinkedHashSet<>();
    for (CommercialOffer offer : candidates) {
      var resolution = resolutions.get(offer.getId());
      if (resolution != null && exactSelectionAvailable(offer, resolution, exactPrices, policy)) {
        result.add(offer.getId());
      }
    }
    return Set.copyOf(result);
  }

  private Optional<CommercialCatalogResolver.ExactSelectionCandidate> exactSelectionCandidate(
      CommercialOffer offer, Map<UUID, ProductPrice> exactPrices) {
    var selection = offer.getSelection();
    ProductPrice planPrice = exactPrices.get(selection.planPriceId());
    if (!ownedBy(planPrice, ProductPriceOwnerType.PLAN, selection.planId())) return Optional.empty();
    Set<String> addOnCodes = new LinkedHashSet<>();
    for (var item : selection.addOns()) {
      ProductPrice price = exactPrices.get(item.priceId());
      if (!ownedBy(price, ProductPriceOwnerType.ADD_ON, item.addOnId())
          || !sameTuple(planPrice, price)
          || !addOnCodes.add(price.getAddOn().getCode())) return Optional.empty();
    }
    List<QuotaPackageSelection> packages = new ArrayList<>();
    Set<String> packageCodes = new LinkedHashSet<>();
    for (var item : selection.quotaPackages()) {
      ProductPrice price = exactPrices.get(item.priceId());
      if (!ownedBy(price, ProductPriceOwnerType.QUOTA_PACKAGE, item.quotaPackageId())
          || !sameTuple(planPrice, price)
          || !packageCodes.add(price.getQuotaPackage().getCode())) return Optional.empty();
      packages.add(new QuotaPackageSelection(price.getQuotaPackage().getCode(), item.quantity()));
    }
    return Optional.of(new CommercialCatalogResolver.ExactSelectionCandidate(
        offer.getId(), selection.planId(), CommercialCatalogResolver.PriceTuple.from(planPrice),
        addOnCodes, packages));
  }

  private boolean exactSelectionAvailable(
      CommercialOffer offer,
      CommercialCatalogResolver.SelectionResolution resolution,
      Map<UUID, ProductPrice> exactPrices,
      CommercialPolicyEvaluator.Evaluation policy) {
    if (offer.getCampaign().getAudienceMode() == CommercialCampaignAudienceMode.PUBLIC
        && selectionContainsDirectOnlyProduct(offer, resolution, exactPrices)) {
      return false;
    }
    if (!resolution.selectable()
        || !CommercialPolicySelectionRules.planSelectable(
            resolution.plan(), CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR, policy)
        || containsBlockedFeature(resolution.plan().planFeatures(), policy)
        || resolution.plan().prices().stream()
            .noneMatch(price -> price.getId().equals(offer.getSelection().planPriceId()))) {
      return false;
    }
    Map<String, CommercialCatalogResolver.AddOnResolution> addOns = resolution.plan().addOns().stream()
        .collect(java.util.stream.Collectors.toMap(
            CommercialCatalogResolver.AddOnResolution::code, java.util.function.Function.identity()));
    for (var item : offer.getSelection().addOns()) {
      ProductPrice exact = exactPrices.get(item.priceId());
      var product = exact == null ? null : addOns.get(exact.getAddOn().getCode());
      if (product == null
          || !CommercialPolicySelectionRules.addOnSelectable(
              product, CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR, policy)
          || product.prices().stream().noneMatch(price -> price.getId().equals(item.priceId()))
          || product.addOn().getFeatures().stream()
              .map(feature -> feature.getFeature().getCode())
              .anyMatch(policy.blockedFeatures()::containsKey)) return false;
    }
    for (var item : offer.getSelection().quotaPackages()) {
      ProductPrice exact = exactPrices.get(item.priceId());
      var product = exact == null
          ? null : resolution.packageResolutions().get(exact.getQuotaPackage().getCode());
      if (product == null
          || !CommercialPolicySelectionRules.quotaPackageSelectable(
              product, CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR, policy)
          || product.prices().stream().noneMatch(price -> price.getId().equals(item.priceId()))
          || policy.blockedFeatures().containsKey(product.quotaPackage().getFeature().getCode())) {
        return false;
      }
    }
    return true;
  }

  private boolean selectionContainsDirectOnlyProduct(
      CommercialOffer offer,
      CommercialCatalogResolver.SelectionResolution resolution,
      Map<UUID, ProductPrice> exactPrices) {
    if (resolution.plan().effectiveSalesVisibility() == ProductSalesVisibility.DIRECT_ONLY) {
      return true;
    }
    for (var item : offer.getSelection().addOns()) {
      ProductPrice price = exactPrices.get(item.priceId());
      if (price != null
          && price.getAddOn().getSalesVisibility() == ProductSalesVisibility.DIRECT_ONLY) {
        return true;
      }
    }
    for (var item : offer.getSelection().quotaPackages()) {
      ProductPrice price = exactPrices.get(item.priceId());
      if (price != null
          && price.getQuotaPackage().getSalesVisibility() == ProductSalesVisibility.DIRECT_ONLY) {
        return true;
      }
    }
    return false;
  }

  private boolean containsBlockedFeature(
      List<PlanFeature> features, CommercialPolicyEvaluator.Evaluation policy) {
    return features.stream()
        .filter(feature -> feature.getMode() == PlanFeatureMode.INCLUDED)
        .map(feature -> feature.getFeature().getCode())
        .anyMatch(policy.blockedFeatures()::containsKey);
  }

  private boolean ownedBy(ProductPrice price, ProductPriceOwnerType type, UUID ownerId) {
    return price != null && price.getOwnerType() == type && price.ownerId().equals(ownerId);
  }

  private boolean sameTuple(ProductPrice left, ProductPrice right) {
    return left.getCurrencyCode().equals(right.getCurrencyCode())
        && left.getBillingCycle() == right.getBillingCycle();
  }

  private RuntimeException clientSafeFailure(
      RuntimeException failure,
      boolean operator,
      Supplier<? extends RuntimeException> privacySafeFailure) {
    if (!operator && isExpectedAvailabilityFailure(failure)) return privacySafeFailure.get();
    return failure;
  }

  private boolean isExpectedAvailabilityFailure(RuntimeException failure) {
    return failure instanceof OfferNotAvailableException
        || failure instanceof InvalidRequestException
        || failure instanceof InvalidStateException
        || failure instanceof OperationBlockedException
        || failure instanceof ResourceNotFoundException
        || failure instanceof StaleResourceVersionException;
  }

  private BigDecimal freeProductReduction(CommercialOffer offer) {
    BigDecimal total = BigDecimal.ZERO;
    for (var item : offer.getSelection().addOns())
      if (item.pricingMode() == CommercialOfferSelection.PricingMode.FREE)
        total =
            total.add(
                prices
                    .findById(item.priceId())
                    .orElseThrow(OfferNotAvailableException::new)
                    .getAmount());
    for (var item : offer.getSelection().quotaPackages())
      if (item.pricingMode() == CommercialOfferSelection.PricingMode.FREE)
        total =
            total.add(
                prices
                    .findById(item.priceId())
                    .orElseThrow(OfferNotAvailableException::new)
                    .getAmount()
                    .multiply(BigDecimal.valueOf(item.quantity())));
    return total;
  }

  private SubscriptionOfferEvaluation offerEvaluation(
      CommercialOffer offer, CommercialOfferViews.EligibilityPreview preview) {
    return new SubscriptionOfferEvaluation(
        offer.getCampaign().getId(),
        offer.getLineageId(),
        offer.getId(),
        offer.getRevisionNumber(),
        preview.catalogueSubtotal(),
        preview.policyPrice(),
        preview.offerPrice(),
        preview.finalPrice(),
        preview.currencyCode(),
        preview.discountWinner(),
        preview.winnerReason(),
        offer.getEffects().finiteQuotaBonuses());
  }

  private String winnerReason(String winner) {
    return "OFFER".equals(winner)
        ? "Offer produces the lower payable recurring price."
        : "Commercial Policy produces an equal or lower payable recurring price; exact ties prefer"
            + " Policy.";
  }

  private BigDecimal discount(BigDecimal base, CommercialOfferEffectSnapshot e) {
    return switch (e.discountType()) {
      case NONE -> base;
      case FIXED -> base.subtract(e.discountAmount()).max(BigDecimal.ZERO);
      case PERCENTAGE_WITH_CAP -> {
        BigDecimal reduction =
            base.multiply(e.percentage())
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                .min(e.percentageCap());
        yield base.subtract(reduction).max(BigDecimal.ZERO);
      }
    };
  }

  private SubscriptionChangeRequest changeRequest(CommercialOffer o) {
    var s = o.getSelection();
    var plan = plans.findById(s.planId()).orElseThrow(OfferNotAvailableException::new);
    Set<String> addonCodes = new LinkedHashSet<>();
    Map<String, UUID> addOnPrices = new LinkedHashMap<>();
    for (var item : s.addOns()) {
      String code =
          addOns.findById(item.addOnId()).orElseThrow(OfferNotAvailableException::new).getCode();
      addonCodes.add(code);
      addOnPrices.put(code, item.priceId());
    }
    Map<String, UUID> packagePrices = new LinkedHashMap<>();
    List<QuotaPackageSelection> qs =
        s.quotaPackages().stream()
            .map(
                x -> {
                  String code =
                      packages
                          .findById(x.quotaPackageId())
                          .orElseThrow(OfferNotAvailableException::new)
                          .getCode();
                  packagePrices.put(code, x.priceId());
                  return new QuotaPackageSelection(code, x.quantity());
                })
            .toList();
    return new SubscriptionChangeRequest(
        plan.getCode(),
        addonCodes,
        qs,
        s.timing(),
        new ProductPriceSelectionRequest(s.planPriceId(), null, null),
        addOnPrices,
        packagePrices);
  }

  private void requireNonNoOp(
      CommercialOffer offer, UUID accountId, CommercialOfferViews.EligibilityPreview preview) {
    Subscription current =
        subscriptions
            .findTopByAccountIdAndStatusInOrderByCreatedAtDesc(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
            .orElseThrow(OfferNotAvailableException::new);
    SubscriptionOfferEvaluation existing = current.getEntitlementSnapshot().offerEvaluation();
    if (existing != null && offer.getId().equals(existing.offerRevisionId())) {
      throw new OfferRedemptionBlockedException();
    }
    boolean sameSelection =
        Objects.equals(
                current.getEntitlementSnapshot().planPriceEntryId(),
                offer.getSelection().planPriceId())
            && sameAddOnPrices(current.getEntitlementSnapshot(), offer.getSelection())
            && samePackagePrices(current.getEntitlementSnapshot(), offer.getSelection());
    if (sameSelection
        && offer.getEffects().finiteQuotaBonuses().isEmpty()
        && current.getCurrentPrice() != null
        && current.getCurrentPrice().compareTo(preview.finalPrice()) == 0) {
      throw new OfferRedemptionBlockedException();
    }
  }

  private boolean sameAddOnPrices(
      SubscriptionEntitlementSnapshot current, CommercialOfferSelection offered) {
    if (current.addOns().stream().anyMatch(item -> item.priceEntryId() == null)) return false;
    return current.addOns().stream()
        .map(SubscriptionAddOnSnapshot::priceEntryId)
        .collect(java.util.stream.Collectors.toSet())
        .equals(
            offered.addOns().stream()
                .map(CommercialOfferSelection.AddOnSelection::priceId)
                .collect(java.util.stream.Collectors.toSet()));
  }

  private boolean samePackagePrices(
      SubscriptionEntitlementSnapshot current, CommercialOfferSelection offered) {
    if (current.quotaPackages().stream().anyMatch(item -> item.priceEntryId() == null))
      return false;
    return current.quotaPackages().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                SubscriptionQuotaPackageSnapshot::priceEntryId,
                SubscriptionQuotaPackageSnapshot::quantity,
                Integer::sum))
        .equals(
            offered.quotaPackages().stream()
                .collect(
                    java.util.stream.Collectors.toMap(
                        CommercialOfferSelection.PackageSelection::priceId,
                        CommercialOfferSelection.PackageSelection::quantity,
                        Integer::sum)));
  }

  private CommercialOfferViews.ClientOffer unavailable() {
    throw new OfferNotAvailableException();
  }

  private String fingerprint(CommercialOffer o, UUID a, CommercialOfferViews.EligibilityPreview p) {
    return pricingFingerprint(
        o,
        a,
        p.subscriptionId(),
        p.expectedSubscriptionVersion(),
        p.catalogueSubtotal(),
        p.policyPrice(),
        p.offerPrice(),
        p.finalPrice(),
        p.discountWinner());
  }

  private String pricingFingerprint(
      CommercialOffer o,
      UUID a,
      UUID s,
      long v,
      BigDecimal catalogue,
      BigDecimal policy,
      BigDecimal offer,
      BigDecimal result,
      String winner) {
    return CommercialOfferAdminServiceImpl.sha(
        fingerprint(o, a, s, v, catalogue, result)
            + "|"
            + policy.toPlainString()
            + "|"
            + offer.toPlainString()
            + "|"
            + winner);
  }

  private String discoveryFingerprint(CommercialOffer o, UUID accountId) {
    return CommercialOfferAdminServiceImpl.sha(
        "DISCOVERY|" + o.getId() + "|" + o.getVersion() + "|" + accountId);
  }

  private String fingerprint(
      CommercialOffer o, UUID a, UUID s, long v, BigDecimal b, BigDecimal f) {
    return CommercialOfferAdminServiceImpl.sha(
        o.getId()
            + "|"
            + o.getVersion()
            + "|"
            + a
            + "|"
            + s
            + "|"
            + v
            + "|"
            + b.toPlainString()
            + "|"
            + f.toPlainString()
            + "|"
            + o.getSelection()
            + "|"
            + o.getEffects());
  }
}
