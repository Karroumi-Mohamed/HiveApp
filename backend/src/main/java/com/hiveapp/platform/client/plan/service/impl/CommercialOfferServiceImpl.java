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
import org.springframework.beans.factory.annotation.Value;
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
  private final SubscriptionService subscriptionsService;
  private final CommercialCatalogVersionService catalogVersions;
  private final RegistryCatalogVersionService registryVersions;
  private final CommercialPreviewTokenService previewTokens;
  private final Clock clock;
  private final CommercialOfferCodeHasher codeHasher;
  private final PlatformTransactionManager transactionManager;
  private final CommercialOfferClientProjectionMapper projections;
  private final SubscriptionChangeOperationProjectionMapper operationProjections;
  private final CommercialOfferEligibilityService eligibility;
  private final CommercialOfferRedemptionTransitionService transitions;
  private final CommercialCatalogResolver catalogResolver;
  private final CommercialPolicyEvaluator policyEvaluator;

  @Value("${hiveapp.offers.application-lease:PT5M}")
  private Duration applicationLeaseDuration = Duration.ofMinutes(5);

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
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public boolean isCatalogueAvailable(UUID accountId, UUID offerId) {
    // An expected eligibility rejection is a value, not a transaction failure.
    // The self-call shares this boundary and never needs a second pooled connection.
    try {
      detail(accountId, offerId);
      return true;
    } catch (OfferNotAvailableException unavailable) {
      return false;
    }
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
  public CommercialOfferViews.ClientEligibilityPreview preview(
      UUID accountId, UUID actor, UUID id, String discoveryToken) {
    var o = eligibility.requireAvailable(id, accountId, false, true);
    if (o.getDiscovery() == CommercialOfferDiscovery.CODE_ONLY) {
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
      return buildPreview(o, accountId, actor, false);
    } catch (RuntimeException failure) {
      throw clientSafeFailure(failure, false, OfferNotAvailableException::new);
    }
  }

  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CommercialOfferViews.AccountEligibilityAssessment assessForOperator(
      UUID accountId, UUID actor, UUID offerId) {
    CommercialOffer offer =
        offers
            .findDetailById(offerId)
            .orElseThrow(() -> new ResourceNotFoundException("CommercialOffer", "id", offerId));
    List<CommercialOfferEligibilityBlocker> blockers =
        new ArrayList<>(eligibility.blockers(offer, accountId, true, true));
    if (blockers.isEmpty()
        && subscriptionOperations
            .findTopByAccountIdAndStatusIn(
                accountId,
                List.of(
                    SubscriptionChangeStatus.PENDING,
                    SubscriptionChangeStatus.AWAITING_CONFIRMATION))
            .isPresent()) {
      blockers.add(CommercialOfferEligibilityBlocker.OUTSTANDING_SUBSCRIPTION_OPERATION);
    }
    if (!blockers.isEmpty()) {
      return assessment(offerId, accountId, blockers, null, clock.instant());
    }
    try {
      PreparedPreview prepared = preparePreview(offer, accountId, actor, true);
      blockers.addAll(changeBlockers(offer, accountId, prepared));
      if (!blockers.isEmpty()) {
        return assessment(
            offerId, accountId, blockers, null, prepared.changePreview().evaluatedAt());
      }
      CommercialOfferViews.ClientEligibilityPreview preview =
          issuePreview(offer, accountId, actor, prepared);
      return assessment(offerId, accountId, List.of(), preview, preview.evaluatedAt());
    } catch (InvalidRequestException
        | InvalidStateException
        | OperationBlockedException
        | OfferNotAvailableException
        | OfferRedemptionBlockedException
        | ResourceNotFoundException
        | StaleResourceVersionException unavailableSelection) {
      return assessment(
          offerId,
          accountId,
          List.of(CommercialOfferEligibilityBlocker.SELECTION_UNAVAILABLE),
          null,
          clock.instant());
    }
  }

  @Override
  public CommercialOfferViews.ClientAcceptance acceptClient(
      UUID accountId,
      UUID actor,
      UUID id,
      String key,
      CommercialOfferRequests.ClientAccept request) {
    return accept(
        accountId,
        actor,
        id,
        key,
        request.previewToken(),
        false,
        "Accepted commercial Offer",
        this::clientAcceptance);
  }

  @Override
  public CommercialOfferViews.AdminAcceptance acceptAsOperator(
      UUID accountId,
      UUID actor,
      UUID id,
      String key,
      CommercialOfferRequests.OperatorAccept request) {
    return accept(
        accountId,
        actor,
        id,
        key,
        request.previewToken(),
        true,
        requiredOperatorReason(request.reason()),
        this::adminAcceptance);
  }

  private <T> T accept(
      UUID accountId,
      UUID actor,
      UUID id,
      String key,
      String previewToken,
      boolean operator,
      String applicationReason,
      AcceptanceProjector<T> projector) {
    if (key == null || key.isBlank() || key.length() > 200)
      throw new InvalidRequestException(
          "Idempotency-Key is required and must not exceed 200 characters.");
    if (!operator) subscriptionsService.requireOfferApplyAuthority(accountId, actor);
    String keyHash = CommercialOfferAdminServiceImpl.sha(accountId + "|" + key);
    String requestFingerprint =
        acceptanceRequestFingerprint(id, actor, operator, previewToken, applicationReason);
    try {
      Reservation<T> reservation;
      try {
        reservation =
            reserveWithConcurrencyRetry(
                accountId,
                actor,
                id,
                keyHash,
                requestFingerprint,
                previewToken,
                operator,
                projector);
      } catch (DataIntegrityViolationException concurrentReplay) {
        reservation =
            inNewTransaction(
                TransactionDefinition.ISOLATION_READ_COMMITTED,
                () -> replay(accountId, keyHash, requestFingerprint, projector));
      }
      if (reservation.replay() != null) return reservation.replay();
      return applyReservation(
          accountId, actor, applicationReason, reservation, operator, projector);
    } catch (RuntimeException failure) {
      throw clientSafeFailure(failure, operator, OfferNotAvailableException::new);
    }
  }

  private <T> T applyReservation(
      UUID accountId,
      UUID actor,
      String applicationReason,
      Reservation<T> acceptedReservation,
      boolean operator,
      AcceptanceProjector<T> projector) {
    try {
      var changePreview =
          operator
              ? subscriptionsService.previewOfferChangeAsOperator(
                  accountId,
                  actor,
                  acceptedReservation.selection(),
                  acceptedReservation.evaluation().quotaBonuses())
              : subscriptionsService.previewOfferChange(
                  accountId,
                  actor,
                  acceptedReservation.selection(),
                  acceptedReservation.evaluation().quotaBonuses());
      var applied =
          operator
              ? subscriptionsService.applyOfferChangeAsOperator(
                  accountId,
                  actor,
                  new SubscriptionChangeApplyRequest(
                      acceptedReservation.selection(), changePreview.previewToken()),
                  applicationReason,
                  acceptedReservation.redemptionId(),
                  acceptedReservation.applicationClaimId())
              : subscriptionsService.applyOfferChange(
                  accountId,
                  actor,
                  new SubscriptionChangeApplyRequest(
                      acceptedReservation.selection(), changePreview.previewToken()),
                  applicationReason,
                  acceptedReservation.redemptionId(),
                  acceptedReservation.applicationClaimId());
      return inNewTransaction(
          TransactionDefinition.ISOLATION_READ_COMMITTED,
          () ->
              complete(
                  accountId,
                  acceptedReservation.redemptionId(),
                  applied.operation(),
                  acceptedReservation.replaying(),
                  projector));
    } catch (RuntimeException applicationFailure) {
      try {
        inNewTransaction(
            TransactionDefinition.ISOLATION_READ_COMMITTED,
            () -> {
              failOrRecover(
                  accountId,
                  acceptedReservation.redemptionId(),
                  acceptedReservation.applicationClaimId());
              return Boolean.TRUE;
            });
      } catch (RuntimeException reconciliationFailure) {
        applicationFailure.addSuppressed(reconciliationFailure);
      }
      throw clientSafeFailure(
          applicationFailure, operator, OfferRedemptionBlockedException::new);
    }
  }

  private <T> Reservation<T> reserveWithConcurrencyRetry(
      UUID accountId,
      UUID actor,
      UUID offerId,
      String keyHash,
      String requestFingerprint,
      String previewToken,
      boolean operator,
      AcceptanceProjector<T> projector) {
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
                    operator,
                    projector));
      } catch (PessimisticLockingFailureException transientRace) {
        if (attempt == 2) throw new OfferRedemptionBlockedException();
      }
    }
    throw new OfferRedemptionBlockedException();
  }

  private <T> Reservation<T> reserve(
      UUID accountId,
      UUID actor,
      UUID offerId,
      String keyHash,
      String requestFingerprint,
      String previewToken,
      boolean operator,
      AcceptanceProjector<T> projector) {
    var account =
        accounts
            .findByIdForSubscriptionUpdate(accountId)
            .orElseThrow(OfferNotAvailableException::new);
    if (!operator && !account.getOwner().getId().equals(actor)) {
      throw new ForbiddenException("Only the Account owner can accept a commercial Offer.");
    }
    var existing = redemptions.lockByAccountIdAndIdempotencyKeyHash(accountId, keyHash);
    if (existing.isPresent()) return replay(existing.get(), requestFingerprint, projector);
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

    CommercialOfferViews.ClientEligibilityPreview currentPreview =
        buildPreview(offer, accountId, actor, operator);
    if (isNoOp(offer, accountId, currentPreview.finalPrice())) {
      throw new OfferRedemptionBlockedException();
    }
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
    SubscriptionChangeRequest acceptedSelection =
        changeRequest(offer, projections.exactPrices(List.of(offer)));
    UUID applicationClaimId = UUID.randomUUID();
    Instant reservedAt = clock.instant();
    CommercialOfferRedemption redemption =
        CommercialOfferRedemption.reserve(
            account,
            offer,
            operator ? CommercialOfferSurface.OPERATOR : CommercialOfferSurface.CLIENT,
            actor,
            keyHash,
            requestFingerprint,
            acceptedSelection,
            evaluation,
            reservedAt,
            applicationClaimId,
            reservedAt.plus(applicationLeaseDuration));
    redemptions.saveAndFlush(redemption);
    return new Reservation<>(
        redemption.getId(),
        acceptedSelection,
        evaluation,
        currentPreview,
        null,
        applicationClaimId,
        false);
  }

  private <T> Reservation<T> replay(
      UUID accountId,
      String keyHash,
      String requestFingerprint,
      AcceptanceProjector<T> projector) {
    accounts.findByIdForSubscriptionUpdate(accountId).orElseThrow(OfferNotAvailableException::new);
    CommercialOfferRedemption redemption =
        redemptions
            .lockByAccountIdAndIdempotencyKeyHash(accountId, keyHash)
            .orElseThrow(
                () -> new InvalidStateException("Concurrent Offer acceptance was not committed."));
    return replay(redemption, requestFingerprint, projector);
  }

  private <T> Reservation<T> replay(
      CommercialOfferRedemption redemption,
      String requestFingerprint,
      AcceptanceProjector<T> projector) {
    if (!MessageDigest.isEqual(
        redemption.getRequestFingerprint().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
        requestFingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
      throw new IdempotencyConflictException();
    }
    SubscriptionChangeOperation operation = recoverOperation(redemption);
    Instant replayedAt = clock.instant();
    if (operation == null
        && redemption.getStatus() == CommercialOfferRedemptionStatus.RESERVED
        && redemption.applicationLeaseExpired(replayedAt)) {
      UUID applicationClaimId = UUID.randomUUID();
      redemption.reclaimApplication(
          applicationClaimId, replayedAt.plus(applicationLeaseDuration), replayedAt);
      redemptions.saveAndFlush(redemption);
      return new Reservation<>(
          redemption.getId(),
          redemption.getAcceptedSelection(),
          redemption.getCommercialEvaluation(),
          null,
          null,
          applicationClaimId,
          true);
    }
    return new Reservation<>(
        redemption.getId(),
        null,
        redemption.getCommercialEvaluation(),
        null,
        projector.project(redemption, operation, true),
        redemption.getApplicationClaimId(),
        true);
  }

  private <T> T complete(
      UUID accountId,
      UUID redemptionId,
      SubscriptionChangeOperationDto appliedOperation,
      boolean replayed,
      AcceptanceProjector<T> projector) {
    accounts.findByIdForSubscriptionUpdate(accountId).orElseThrow(OfferNotAvailableException::new);
    CommercialOfferRedemption redemption =
        redemptions.lockById(redemptionId).orElseThrow(OfferRedemptionBlockedException::new);
    if (!redemption.getAccount().getId().equals(accountId)) {
      throw new OfferRedemptionBlockedException();
    }
    SubscriptionChangeOperation operation =
        subscriptionOperations
            .findByOfferRedemptionIdAndAccountId(
                redemptionId, redemption.getAccount().getId())
            .orElseThrow(OfferRedemptionBlockedException::new);
    if (!operation.getId().equals(appliedOperation.id())) {
      throw new OfferRedemptionBlockedException();
    }
    requireMatchingOperation(redemption, operation);
    return projector.project(redemption, operation, replayed);
  }

  private SubscriptionChangeOperation recoverOperation(CommercialOfferRedemption redemption) {
    Optional<SubscriptionChangeOperation> found =
        subscriptionOperations.findByOfferRedemptionIdAndAccountId(
            redemption.getId(), redemption.getAccount().getId());
    if (found.isEmpty() && redemption.getSubscriptionOperationId() != null) {
      found =
          subscriptionOperations.findByIdAndAccountId(
              redemption.getSubscriptionOperationId(), redemption.getAccount().getId());
    }
    found.ifPresent(
        operation -> {
          transitions.applyOperation(redemption, operation);
          redemptions.saveAndFlush(redemption);
        });
    return found.orElse(null);
  }

  private void failOrRecover(UUID accountId, UUID redemptionId, UUID applicationClaimId) {
    accounts.findByIdForSubscriptionUpdate(accountId).orElseThrow(OfferNotAvailableException::new);
    CommercialOfferRedemption redemption =
        redemptions.lockById(redemptionId).orElseThrow(OfferRedemptionBlockedException::new);
    if (!redemption.getAccount().getId().equals(accountId)) {
      throw new OfferRedemptionBlockedException();
    }
    if (redemption.getStatus() != CommercialOfferRedemptionStatus.RESERVED) return;
    if (!redemption.hasApplicationClaim(applicationClaimId)) return;
    var operation =
        subscriptionOperations.findByOfferRedemptionIdAndAccountId(
            redemptionId, redemption.getAccount().getId());
    if (operation.isPresent()) {
      var found = operation.orElseThrow();
      transitions.applyOperation(redemption, found);
      redemptions.saveAndFlush(redemption);
      return;
    }
    transitions.failReservation(redemption, "SUBSCRIPTION_APPLICATION_FAILED");
    redemptions.saveAndFlush(redemption);
  }

  private void requireMatchingOperation(
      CommercialOfferRedemption redemption, SubscriptionChangeOperation operation) {
    if (!redemption.getId().equals(operation.getOfferRedemptionId())
        || !redemption.getAccount().getId().equals(operation.getAccount().getId())
        || !redemption.hasApplicationClaim(operation.getOfferApplicationClaimId())) {
      throw new OfferRedemptionBlockedException();
    }
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

  private String acceptanceRequestFingerprint(
      UUID offerId,
      UUID actor,
      boolean operator,
      String previewToken,
      String normalizedApplicationReason) {
    return CommercialOfferAdminServiceImpl.sha(
        "offer-accept:v2|"
            + offerId
            + "|"
            + actor
            + "|"
            + (operator ? "OPERATOR" : "CLIENT")
            + "|"
            + CommercialOfferAdminServiceImpl.sha(previewToken)
            + (operator
                ? "|" + CommercialOfferAdminServiceImpl.sha(normalizedApplicationReason)
                : ""));
  }

  private CommercialOfferViews.ClientAcceptance clientAcceptance(
      CommercialOfferRedemption redemption,
      SubscriptionChangeOperation operation,
      boolean replayed) {
    return new CommercialOfferViews.ClientAcceptance(
        redemption.getId(),
        operation == null ? redemption.getSubscriptionOperationId() : operation.getId(),
        redemption.getStatus(),
        replayed,
        acceptanceProgress(redemption, operation),
        acceptanceNextAction(redemption, operation),
        retryAfter(redemption, operation),
        operation == null ? null : operationProjections.client(operation),
        projections.accepted(redemption));
  }

  private CommercialOfferViews.AdminAcceptance adminAcceptance(
      CommercialOfferRedemption redemption,
      SubscriptionChangeOperation operation,
      boolean replayed) {
    return new CommercialOfferViews.AdminAcceptance(
        redemption.getId(),
        operation == null ? redemption.getSubscriptionOperationId() : operation.getId(),
        redemption.getStatus(),
        replayed,
        acceptanceProgress(redemption, operation),
        acceptanceNextAction(redemption, operation),
        retryAfter(redemption, operation),
        operation == null ? null : operationProjections.admin(operation),
        projections.accepted(redemption));
  }

  private CommercialOfferAcceptanceProgress acceptanceProgress(
      CommercialOfferRedemption redemption, SubscriptionChangeOperation operation) {
    if (operation != null) return CommercialOfferAcceptanceProgress.OPERATION_AVAILABLE;
    return redemption.getStatus() == CommercialOfferRedemptionStatus.RESERVED
        ? CommercialOfferAcceptanceProgress.IN_PROGRESS
        : CommercialOfferAcceptanceProgress.TERMINAL_WITHOUT_OPERATION;
  }

  private CommercialOfferAcceptanceNextAction acceptanceNextAction(
      CommercialOfferRedemption redemption, SubscriptionChangeOperation operation) {
    if (operation == null) {
      return redemption.getStatus() == CommercialOfferRedemptionStatus.RESERVED
          ? CommercialOfferAcceptanceNextAction.RETRY_LATER
          : CommercialOfferAcceptanceNextAction.NONE;
    }
    return operation.getStatus() == SubscriptionChangeStatus.PENDING
            || operation.getStatus() == SubscriptionChangeStatus.AWAITING_CONFIRMATION
        ? CommercialOfferAcceptanceNextAction.TRACK_OPERATION
        : CommercialOfferAcceptanceNextAction.NONE;
  }

  private Instant retryAfter(
      CommercialOfferRedemption redemption, SubscriptionChangeOperation operation) {
    return operation == null && redemption.getStatus() == CommercialOfferRedemptionStatus.RESERVED
        ? redemption.getApplicationLeaseExpiresAt()
        : null;
  }

  private record Reservation<T>(
      UUID redemptionId,
      SubscriptionChangeRequest selection,
      SubscriptionOfferEvaluation evaluation,
      CommercialOfferViews.ClientEligibilityPreview preview,
      T replay,
      UUID applicationClaimId,
      boolean replaying) {}

  @FunctionalInterface
  private interface AcceptanceProjector<T> {
    T project(
        CommercialOfferRedemption redemption,
        SubscriptionChangeOperation operation,
        boolean replayed);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<CommercialOfferViews.ClientRedemption> history(UUID accountId, Pageable p) {
    Page<CommercialOfferRedemption> page = redemptions.findAllByAccount_Id(accountId, p);
    Map<UUID, ProductPrice> exactPrices =
        projections.exactPrices(
            page.getContent().stream().map(CommercialOfferRedemption::getOffer).toList());
    Map<UUID, SubscriptionChangeOperation> operations = operationsByRedemption(page.getContent());
    return page.map(
        redemption -> {
          SubscriptionChangeOperation operation = operations.get(redemption.getId());
          return projections.clientRedemption(
              redemption,
              exactPrices,
              operation == null ? null : operationProjections.client(operation));
        });
  }

  @Override
  @Transactional(readOnly = true)
  public CommercialOfferViews.ClientRedemption redemption(UUID accountId, UUID id) {
    CommercialOfferRedemption redemption =
        redemptions
            .findByIdAndAccount_Id(id, accountId)
            .orElseThrow(OfferNotAvailableException::new);
    var operation =
        subscriptionOperations.findByOfferRedemptionIdAndAccountId(id, accountId).orElse(null);
    return projections.clientRedemption(
        redemption,
        projections.exactPrices(List.of(redemption.getOffer())),
        operation == null ? null : operationProjections.client(operation));
  }

  private CommercialOfferViews.ClientEligibilityPreview buildPreview(
      CommercialOffer o, UUID accountId, UUID actor, boolean operator) {
    PreparedPreview prepared = preparePreview(o, accountId, actor, operator);
    List<CommercialOfferEligibilityBlocker> blockers = changeBlockers(o, accountId, prepared);
    if (!blockers.isEmpty()) {
      if (!operator) throw new OfferNotAvailableException();
      throw new InvalidStateException("Offer cannot be applied in the current Account state.");
    }
    return issuePreview(o, accountId, actor, prepared);
  }

  private PreparedPreview preparePreview(
      CommercialOffer offer, UUID accountId, UUID actor, boolean operator) {
    Map<UUID, ProductPrice> exactPrices = projections.exactPrices(List.of(offer));
    SubscriptionChangeRequest request = changeRequest(offer, exactPrices);
    var p =
        operator
            ? subscriptionsService.previewOfferChangeAsOperator(
                accountId, actor, request, offer.getEffects().finiteQuotaBonuses())
            : subscriptionsService.previewOfferChange(
                accountId, actor, request, offer.getEffects().finiteQuotaBonuses());
    var policy = p.commercialPolicyEvaluation();
    BigDecimal catalogue = policy == null ? p.previewPrice() : policy.catalogueRecurringPrice();
    BigDecimal fixedBase = policy == null ? catalogue : policy.fixedRecurringPrice();
    BigDecimal policyPrice = policy == null ? p.previewPrice() : policy.finalRecurringPrice();
    BigDecimal freeReduction = freeProductReduction(offer, exactPrices);
    BigDecimal offerBase = fixedBase.subtract(freeReduction).max(BigDecimal.ZERO);
    BigDecimal offerPrice = discount(offerBase, offer.getEffects());
    int comparison = offerPrice.compareTo(policyPrice);
    CommercialOfferDiscountWinner winner =
        comparison < 0
            ? CommercialOfferDiscountWinner.OFFER
            : CommercialOfferDiscountWinner.POLICY;
    CommercialOfferDiscountDecisionCode decision =
        winner == CommercialOfferDiscountWinner.OFFER
            ? CommercialOfferDiscountDecisionCode.OFFER_LOWER_FINAL_PRICE
            : CommercialOfferDiscountDecisionCode.POLICY_LOWER_OR_EQUAL_FINAL_PRICE;
    String winnerReason = winnerReason(winner);
    BigDecimal finalPrice = comparison < 0 ? offerPrice : policyPrice;
    return new PreparedPreview(
        request,
        p,
        projections.selection(offer, exactPrices),
        catalogue,
        policyPrice,
        offerPrice,
        finalPrice,
        fixedBase,
        freeReduction,
        winner,
        decision,
        winnerReason);
  }

  private CommercialOfferViews.ClientEligibilityPreview issuePreview(
      CommercialOffer offer, UUID accountId, UUID actor, PreparedPreview prepared) {
    var p = prepared.changePreview();
    long catalog = catalogVersions.currentRevision();
    String registry = registryVersions.currentVersion();
    String fp =
        pricingFingerprint(
            offer,
            accountId,
            p.subscriptionId(),
            p.expectedSubscriptionVersion(),
            prepared.catalogue(),
            prepared.policyPrice(),
            prepared.offerPrice(),
            prepared.finalPrice(),
            prepared.winner());
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
    boolean checkoutRequired = prepared.finalPrice().signum() > 0;
    SubscriptionChangeStatus expectedStatus =
        checkoutRequired
            ? SubscriptionChangeStatus.AWAITING_CONFIRMATION
            : p.timing() == SubscriptionChangeTiming.AT_RENEWAL
                ? SubscriptionChangeStatus.PENDING
                : SubscriptionChangeStatus.APPLIED;
    var change =
        new CommercialOfferViews.ChangeReview(
            p.currentPrice(),
            p.timing(),
            p.effectiveAt(),
            p.effectiveUntil(),
            p.immediateAllowed(),
            p.currentEntitlements(),
            p.targetEntitlements(),
            p.conflicts(),
            ClientCommercialPolicyEvaluation.from(p.commercialPolicyEvaluation()),
            checkoutRequired,
            prepared.finalPrice(),
            expectedStatus);
    return new CommercialOfferViews.ClientEligibilityPreview(
        offer.getId(),
        p.subscriptionId(),
        p.expectedSubscriptionVersion(),
        prepared.selection(),
        change,
        prepared.catalogue(),
        prepared.policyPrice(),
        prepared.offerPrice(),
        prepared.finalPrice(),
        p.currencyCode(),
        prepared.fixedBase(),
        prepared.freeReduction(),
        prepared.winner(),
        prepared.decision(),
        prepared.winnerReason(),
        evidence.evaluatedAt(),
        evidence.expiresAt(),
        evidence.token());
  }

  private List<CommercialOfferEligibilityBlocker> changeBlockers(
      CommercialOffer offer, UUID accountId, PreparedPreview prepared) {
    List<CommercialOfferEligibilityBlocker> blockers = new ArrayList<>();
    var preview = prepared.changePreview();
    if (preview.commercialPolicyEvaluation() != null
        && preview.commercialPolicyEvaluation().blocked()) {
      blockers.add(CommercialOfferEligibilityBlocker.POLICY_CONFLICT);
    }
    if (preview.timing() == SubscriptionChangeTiming.IMMEDIATE
        && !preview.immediateAllowed()) {
      blockers.add(CommercialOfferEligibilityBlocker.IMMEDIATE_CHANGE_CONFLICT);
    }
    if (prepared.finalPrice().signum() > 0) {
      blockers.add(CommercialOfferEligibilityBlocker.PAID_CHECKOUT_UNAVAILABLE);
    }
    if (isNoOp(offer, accountId, prepared.finalPrice())) {
      blockers.add(CommercialOfferEligibilityBlocker.NO_CHANGE);
    }
    return List.copyOf(new LinkedHashSet<>(blockers));
  }

  private CommercialOfferViews.AccountEligibilityAssessment assessment(
      UUID offerId,
      UUID accountId,
      Collection<CommercialOfferEligibilityBlocker> blockers,
      CommercialOfferViews.ClientEligibilityPreview preview,
      Instant evaluatedAt) {
    List<CommercialOfferEligibilityBlocker> stable =
        List.copyOf(new LinkedHashSet<>(blockers));
    return new CommercialOfferViews.AccountEligibilityAssessment(
        offerId, accountId, stable.isEmpty(), stable, evaluatedAt, preview);
  }

  private Map<UUID, SubscriptionChangeOperation> operationsByRedemption(
      Collection<CommercialOfferRedemption> redemptionPage) {
    if (redemptionPage.isEmpty()) return Map.of();
    Map<UUID, UUID> redemptionAccounts =
        redemptionPage.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    CommercialOfferRedemption::getId,
                    redemption -> redemption.getAccount().getId()));
    return subscriptionOperations.findAllByOfferRedemptionIdIn(redemptionAccounts.keySet()).stream()
        .filter(
            operation ->
                Objects.equals(
                    redemptionAccounts.get(operation.getOfferRedemptionId()),
                    operation.getAccount().getId()))
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                SubscriptionChangeOperation::getOfferRedemptionId,
                java.util.function.Function.identity()));
  }

  private record PreparedPreview(
      SubscriptionChangeRequest request,
      SubscriptionChangePreviewResponse changePreview,
      CommercialOfferViews.ClientSelection selection,
      BigDecimal catalogue,
      BigDecimal policyPrice,
      BigDecimal offerPrice,
      BigDecimal finalPrice,
      BigDecimal fixedBase,
      BigDecimal freeReduction,
      CommercialOfferDiscountWinner winner,
      CommercialOfferDiscountDecisionCode decision,
      String winnerReason) {}

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

  private ProductPrice exactPrice(Map<UUID, ProductPrice> exactPrices, UUID id) {
    ProductPrice price = exactPrices.get(id);
    if (price == null) throw new OfferNotAvailableException();
    return price;
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

  private BigDecimal freeProductReduction(
      CommercialOffer offer, Map<UUID, ProductPrice> exactPrices) {
    BigDecimal total = BigDecimal.ZERO;
    for (var item : offer.getSelection().addOns())
      if (item.pricingMode() == CommercialOfferSelection.PricingMode.FREE)
        total = total.add(exactPrice(exactPrices, item.priceId()).getAmount());
    for (var item : offer.getSelection().quotaPackages())
      if (item.pricingMode() == CommercialOfferSelection.PricingMode.FREE)
        total =
            total.add(
                exactPrice(exactPrices, item.priceId())
                    .getAmount()
                    .multiply(BigDecimal.valueOf(item.quantity())));
    return total;
  }

  private SubscriptionOfferEvaluation offerEvaluation(
      CommercialOffer offer, CommercialOfferViews.ClientEligibilityPreview preview) {
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
        preview.discountWinner().name(),
        preview.winnerReason(),
        offer.getEffects().finiteQuotaBonuses());
  }

  private String winnerReason(CommercialOfferDiscountWinner winner) {
    return winner == CommercialOfferDiscountWinner.OFFER
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

  private SubscriptionChangeRequest changeRequest(
      CommercialOffer o, Map<UUID, ProductPrice> exactPrices) {
    var s = o.getSelection();
    ProductPrice planPrice = exactPrice(exactPrices, s.planPriceId());
    if (planPrice.getOwnerType() != ProductPriceOwnerType.PLAN
        || !planPrice.getPlan().getId().equals(s.planId())) {
      throw new OfferNotAvailableException();
    }
    Set<String> addonCodes = new LinkedHashSet<>();
    Map<String, UUID> addOnPrices = new LinkedHashMap<>();
    for (var item : s.addOns()) {
      ProductPrice price = exactPrice(exactPrices, item.priceId());
      if (price.getOwnerType() != ProductPriceOwnerType.ADD_ON
          || !price.getAddOn().getId().equals(item.addOnId())) {
        throw new OfferNotAvailableException();
      }
      String code = price.getAddOn().getCode();
      addonCodes.add(code);
      addOnPrices.put(code, item.priceId());
    }
    Map<String, UUID> packagePrices = new LinkedHashMap<>();
    List<QuotaPackageSelection> qs =
        s.quotaPackages().stream()
            .map(
                x -> {
                  ProductPrice price = exactPrice(exactPrices, x.priceId());
                  if (price.getOwnerType() != ProductPriceOwnerType.QUOTA_PACKAGE
                      || !price.getQuotaPackage().getId().equals(x.quotaPackageId())) {
                    throw new OfferNotAvailableException();
                  }
                  String code = price.getQuotaPackage().getCode();
                  packagePrices.put(code, x.priceId());
                  return new QuotaPackageSelection(code, x.quantity());
                })
            .toList();
    return new SubscriptionChangeRequest(
        planPrice.getPlan().getCode(),
        addonCodes,
        qs,
        s.timing(),
        new ProductPriceSelectionRequest(s.planPriceId(), null, null),
        addOnPrices,
        packagePrices);
  }

  private boolean isNoOp(
      CommercialOffer offer, UUID accountId, BigDecimal finalPrice) {
    Subscription current =
        subscriptions
            .findTopByAccountIdAndStatusInOrderByCreatedAtDesc(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
            .orElseThrow(OfferNotAvailableException::new);
    SubscriptionOfferEvaluation existing = current.getEntitlementSnapshot().offerEvaluation();
    if (existing != null && offer.getId().equals(existing.offerRevisionId())) {
      return true;
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
        && current.getCurrentPrice().compareTo(finalPrice) == 0) return true;
    return false;
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

  private String fingerprint(
      CommercialOffer o, UUID a, CommercialOfferViews.ClientEligibilityPreview p) {
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
      CommercialOfferDiscountWinner winner) {
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
