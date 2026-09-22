package com.hiveapp.platform.client.plan.service;

import static com.hiveapp.platform.client.plan.dto.RepricingModels.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.audit.AuditedMutation;
import com.hiveapp.shared.exception.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionRepricingService {
  public static final int MAX_ACCOUNTS = 500;
  private final SubscriptionRepricingRepository jobs;
  private final SubscriptionRepricingItemRepository items;
  private final SubscriptionRepository subscriptions;
  private final ProductPriceRepository prices;
  private final AccountRepository accounts;
  private final SpecialCommercialAgreementRepository agreements;
  private final SubscriptionChangeOperationRepository operations;
  private final CommercialNoticeReadRepository noticeReads;
  private final com.hiveapp.platform.communication.CommunicationSources communicationSources;
  private final CommercialSegmentAudienceResolver segments;
  private final SubscriptionRepricingRules rules;
  private final BillingCalculator billing;
  private final SubscriptionPeriodCalculator periods;
  private final CommercialPreviewTokenService evidence;
  private final CommercialCatalogVersionService catalog;
  private final RegistryCatalogVersionService registry;
  private final ObjectMapper json;
  private final Clock clock;
  private final jakarta.persistence.EntityManager entityManager;

  @Transactional
  public Preview preview(UUID actor, Request request) {
    validateAudience(request);
    ProductPrice source = price(request.sourcePriceId()), target = price(request.targetPriceId());
    rules.requirePair(source, target);
    if (target.getStatus() != ProductPriceStatus.ACTIVE)
      throw new InvalidRequestException("Publish the target tariff first.");
    SubscriptionRepricing job = new SubscriptionRepricing();
    job.setActorUserId(actor);
    job.setRequest(request);
    job.setSourcePrice(source);
    job.setTargetPrice(target);
    job.setSourcePriceVersion(source.getVersion());
    job.setTargetPriceVersion(target.getVersion());
    job.setCatalogVersion(catalog.currentRevision());
    job.setRegistryVersion(registry.currentVersion());
    List<UUID> audience = resolveAudience(job);
    if (audience.isEmpty())
      throw new InvalidRequestException("No Accounts match this tariff and audience.");
    job.setTargetCount(audience.size());
    job.setFingerprint("pending");
    jobs.saveAndFlush(job);
    List<SubscriptionRepricingItem> assessed = new ArrayList<>();
    for (UUID id : audience) {
      SubscriptionRepricingItem item = new SubscriptionRepricingItem();
      item.setJob(job);
      item.setAccount(accounts.getReferenceById(id));
      Subscription current = subscriptions.findCurrentByAccountId(id).orElse(null);
      assess(item, current);
      assessed.add(item);
    }
    items.saveAllAndFlush(assessed);
    job.setFingerprint(
        hash(
            assessed.stream()
                .map(
                    i ->
                        List.of(
                            i.getAccount().getId(),
                            Objects.toString(i.getTermsIdentity(), ""),
                            Objects.toString(i.getTermsFingerprint(), ""),
                            i.getStatus(),
                            Objects.toString(i.getEffectiveAt(), "")))
                .toList()));
    jobs.saveAndFlush(job);
    var token =
        evidence.issue(
            CommercialPreviewKind.SUBSCRIPTION_REPRICING,
            job.getId(),
            job.getVersion(),
            actor,
            job.getCatalogVersion(),
            job.getRegistryVersion(),
            job.getFingerprint(),
            clock.instant());
    return new Preview(
        summary(job, counts(assessed)),
        token.token(),
        token.expiresAt(),
        assessed.stream().limit(25).map(this::itemView).toList());
  }

  @Transactional
  @AuditedMutation(
      action = "platform.subscriptions.repricing.confirm",
      resourceType = "SUBSCRIPTION_REPRICING")
  public Detail confirm(UUID id, UUID actor, Confirm request) {
    var job = lock(id);
    if (!job.getActorUserId().equals(actor)) throw stale();
    if (!"PREVIEWED".equals(job.getStatus()))
      throw new InvalidStateException("This review was already confirmed or cancelled.");
    evidence.requireValid(
        request.previewToken(),
        CommercialPreviewKind.SUBSCRIPTION_REPRICING,
        id,
        job.getVersion(),
        actor,
        job.getCatalogVersion(),
        job.getRegistryVersion(),
        job.getFingerprint(),
        this::stale);
    catalog.requireCurrent(job.getCatalogVersion());
    registry.requireCurrent(job.getRegistryVersion());
    if (job.getTargetPrice().getVersion() != job.getTargetPriceVersion()
        || job.getSourcePrice().getVersion() != job.getSourcePriceVersion()) throw stale();
    var targets = items.findAllByJobIdOrderById(id);
    if (targets.stream().noneMatch(i -> i.getStatus() == State.READY))
      throw new InvalidStateException("No eligible Accounts in this review.");
    // Stable Account lock order is shared with other commercial mutations.
    for (var item :
        targets.stream().sorted(Comparator.comparing(i -> i.getAccount().getId())).toList()) {
      if (item.getStatus() != State.READY) continue;
      UUID accountId = item.getAccount().getId();
      accounts.findByIdForSubscriptionUpdate(accountId).orElseThrow(this::stale);
      Subscription current = subscriptions.findCurrentByAccountId(accountId).orElse(null);
      if (current == null
          || !current.termsIdentity().equals(item.getTermsIdentity())
          || !termsFingerprint(current).equals(item.getTermsFingerprint())
          || blocker(current, job, item.getEffectiveAt(), true) != null
          || !current.getCurrentPeriodEnd().isAfter(clock.instant())) throw stale();
      item.setStatus(State.PENDING);
      item.setNoticeCreatedAt(clock.instant());
      item.setDelivery(job.getRequest().email() ? Delivery.PENDING : Delivery.NOT_REQUESTED);
    }
    items.saveAllAndFlush(targets);
    targets.stream().filter(item -> item.getNoticeCreatedAt() != null).forEach(communicationSources::index);
    job.setStatus("CONFIRMED");
    job.setConfirmedAt(clock.instant());
    jobs.saveAndFlush(job);
    return detail(job);
  }

  @Transactional(readOnly = true)
  public Page<Summary> list(Pageable page) {
    Page<SubscriptionRepricing> result = jobs.findAll(page);
    Map<UUID, Map<State, Long>> counts = new HashMap<>();
    if (!result.isEmpty())
      for (Object[] row :
          items.counts(result.getContent().stream().map(SubscriptionRepricing::getId).toList()))
        counts
            .computeIfAbsent((UUID) row[0], key -> new EnumMap<>(State.class))
            .put((State) row[1], (Long) row[2]);
    return result.map(job -> summary(job, counts.getOrDefault(job.getId(), Map.of())));
  }

  @Transactional(readOnly = true)
  public Detail get(UUID id) {
    return detail(find(id));
  }

  @Transactional(readOnly = true)
  public Page<Item> results(UUID id, State state, Pageable page) {
    find(id);
    return (state == null
            ? items.findAllByJobId(id, page)
            : items.findAllByJobIdAndStatus(id, state, page))
        .map(this::itemView);
  }

  @Transactional(readOnly = true)
  public List<Identity> identities(UUID id, Collection<UUID> ids) {
    find(id);
    if (ids.isEmpty() || ids.size() > 100 || new HashSet<>(ids).size() != ids.size())
      throw new InvalidRequestException("Choose 1–100 unique results.");
    var found = items.findAllByJobIdAndIdIn(id, ids);
    if (found.size() != ids.size()) throw new InvalidRequestException("Unknown results.");
    return found.stream()
        .map(i -> new Identity(i.getId(), i.getAccount().getId(), i.getAccount().getName()))
        .toList();
  }

  @Transactional
  @AuditedMutation(
      action = "platform.subscriptions.repricing.cancel",
      resourceType = "SUBSCRIPTION_REPRICING")
  public Detail cancel(UUID id, UUID actor, String reason, UUID onlyItem) {
    var job = lock(id);
    var targets = items.findAllByJobIdOrderById(id);
    if (onlyItem != null && targets.stream().noneMatch(i -> i.getId().equals(onlyItem)))
      throw new ResourceNotFoundException("Repricing result", "id", onlyItem);
    for (var snapshot :
        targets.stream().sorted(Comparator.comparing(i -> i.getAccount().getId())).toList()) {
      if (onlyItem != null && !onlyItem.equals(snapshot.getId())) continue;
      accounts.findByIdForSubscriptionUpdate(snapshot.getAccount().getId());
      var item = items.lock(snapshot.getId()).orElseThrow(this::stale);
      entityManager.refresh(item);
      if (item.getStatus() != State.PENDING && item.getStatus() != State.READY) continue;
      item.setStatus(State.CANCELLED);
      item.setCancelledBy(actor);
      item.setCancellationReason(reason);
      if (item.getDelivery() == Delivery.PENDING) item.setDelivery(Delivery.CANCELLED);
      items.saveAndFlush(item);
    }
    if (onlyItem == null) {
      job.setStatus("CANCELLED");
      job.setCancelledBy(actor);
      job.setCancellationReason(reason);
    }
    return detail(job);
  }

  /** Only delivery is retried in-place. Changed commercial terms always require a new review. */
  @Transactional
  @AuditedMutation(
      action = "platform.subscriptions.repricing.retry_notice",
      resourceType = "SUBSCRIPTION_REPRICING")
  public Detail retryNotice(UUID id, String reason) {
    var job = lock(id);
    if ("CANCELLED".equals(job.getStatus()))
      throw new InvalidStateException("Cancelled changes cannot be notified again.");
    for (var snapshot :
        items.findAllByJobIdOrderById(id).stream()
            .sorted(Comparator.comparing(i -> i.getAccount().getId()))
            .toList()) {
      accounts.findByIdForSubscriptionUpdate(snapshot.getAccount().getId());
      var item = items.lock(snapshot.getId()).orElseThrow(this::stale);
      entityManager.refresh(item);
      if (item.getDelivery() == Delivery.FAILED && item.getStatus() == State.PENDING)
        item.setDelivery(Delivery.PENDING);
    }
    return detail(job);
  }

  @Transactional
  @AuditedMutation(
      action = "platform.subscriptions.repricing.retry_execution",
      resourceType = "SUBSCRIPTION_REPRICING")
  public Detail retryExecution(UUID id, UUID itemId, String reason) {
    var job = lock(id);
    if (!"CONFIRMED".equals(job.getStatus()))
      throw new InvalidStateException("Only confirmed changes may be retried.");
    UUID accountId =
        items
            .accountId(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Result", "id", itemId));
    accounts.findByIdForSubscriptionUpdate(accountId);
    var item = items.lock(itemId).orElseThrow(this::stale);
    if (!item.getJob().getId().equals(id))
      throw new ResourceNotFoundException("Result", "id", itemId);
    if (item.getStatus() != State.CONFLICT
        || !"EXECUTION_FAILED".equals(item.getBlocker())
        || item.getOperation() != null)
      throw new InvalidStateException(
          "This outcome requires a new review, not an execution retry.");
    var current = subscriptions.findCurrentByAccountId(accountId).orElseThrow(this::stale);
    if (!current.termsIdentity().equals(item.getTermsIdentity())
        || !termsFingerprint(current).equals(item.getTermsFingerprint())
        || !current.getCurrentPeriodEnd().equals(item.getEffectiveAt())
        || blocker(current, job, item.getEffectiveAt(), true) != null
        || job.getTargetPrice().getVersion() != job.getTargetPriceVersion()) throw stale();
    item.setStatus(State.PENDING);
    item.setBlocker(null);
    items.saveAndFlush(item);
    return detail(job);
  }

  @Transactional(readOnly = true)
  public Page<Notice> notices(UUID accountId, UUID userId, Pageable page) {
    var found = items.findAllByAccountIdAndNoticeCreatedAtIsNotNull(accountId, page);
    Set<UUID> read =
        found.isEmpty()
            ? Set.of()
            : noticeReads
                .findAllByUserIdAndNoticeIdIn(
                    userId,
                    found.getContent().stream().map(SubscriptionRepricingItem::getId).toList())
                .stream()
                .map(CommercialNoticeRead::getNoticeId)
                .collect(Collectors.toSet());
    return found.map(
        i ->
            new Notice(
                i.getId(),
                i.getJob().getSourcePrice().ownerName(),
                itemView(i),
                read.contains(i.getId()),
                i.getNoticeCreatedAt()));
  }

  @Transactional
  public void markRead(UUID accountId, UUID userId, UUID id) {
    accounts
        .findByIdForSubscriptionUpdate(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
    items
        .findByIdAndAccountIdAndNoticeCreatedAtIsNotNull(id, accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Notice", "id", id));
    if (!noticeReads.existsByNoticeIdAndUserId(id, userId)) {
      var read = new CommercialNoticeRead();
      read.setNoticeId(id);
      read.setUserId(userId);
      read.setReadAt(clock.instant());
      noticeReads.save(read);
    }
  }

  void assess(SubscriptionRepricingItem item, Subscription current) {
    var job = item.getJob();
    if (current != null) {
      item.setTermsIdentity(current.termsIdentity());
      item.setTermsFingerprint(termsFingerprint(current));
      item.setBeforeSnapshot(current.getEntitlementSnapshot());
      item.setOldTotal(current.getCurrentPrice());
      item.setQuantity(rules.quantity(current.getEntitlementSnapshot(), job.getSourcePrice()));
      item.setEffectiveAt(
          rules.effectiveAt(
              current.getCurrentPeriodEnd(),
              job.getRequest().notBefore(),
              current.getEntitlementSnapshot().billingCycle(),
              periods));
    }
    String blocker = blocker(current, job, item.getEffectiveAt(), true);
    item.setBlocker(blocker);
    item.setStatus(blocker == null ? State.READY : State.CONFLICT);
    if (blocker == null) {
      item.setTargetSnapshot(
          rules.replace(
              current.getEntitlementSnapshot(), job.getSourcePrice(), job.getTargetPrice()));
      item.setNewTotal(billing.catalogueMoney(item.getTargetSnapshot()).amount());
    }
  }

  String blocker(
      Subscription current, SubscriptionRepricing job, Instant at, boolean checkPendingItem) {
    if (current == null) return "NO_CURRENT_SUBSCRIPTION";
    if (!current.getAccount().isActive()) return "ACCOUNT_INACTIVE";
    if (current.getStatus() != SubscriptionStatus.ACTIVE || current.isCancelAtPeriodEnd())
      return "NOT_RENEWING";
    if (rules.quantity(current.getEntitlementSnapshot(), job.getSourcePrice()) == 0)
      return "SOURCE_TARIFF_CHANGED";
    if (!job.getTargetPrice().isApplicableAt(at)) return "TARGET_TARIFF_UNAVAILABLE";
    if (current.getEntitlementSnapshot().commercialPolicyEvaluation() != null
        || current.getEntitlementSnapshot().offerEvaluation() != null
        || current.currentMoney() == null
        || !current.currentMoney().equals(billing.catalogueMoney(current.getEntitlementSnapshot()))
        || agreements.existsByAccountIdAndStatusIn(
            current.getAccount().getId(),
            List.of(
                SpecialAgreementStatus.ACTIVE,
                SpecialAgreementStatus.SCHEDULED,
                SpecialAgreementStatus.AWAITING_SETTLEMENT,
                SpecialAgreementStatus.NEEDS_ATTENTION))) return "PROTECTED_TERMS";
    if (operations.existsByAccountIdAndStatusIn(
        current.getAccount().getId(),
        List.of(
            SubscriptionChangeStatus.PENDING,
            SubscriptionChangeStatus.AWAITING_CONFIRMATION,
            SubscriptionChangeStatus.NEEDS_ATTENTION))) return "PENDING_CHANGE";
    if (checkPendingItem && items.existsByPendingAccountId(current.getAccount().getId()))
      return "PENDING_REPRICING";
    return null;
  }

  String termsFingerprint(Subscription subscription) {
    return hash(
        List.of(
            subscription.getEntitlementSnapshot().withEffectivePeriod(null, null),
            subscription.getCustomOverrides(),
            subscription.getCurrentPrice() == null
                ? ""
                : subscription.getCurrentPrice().stripTrailingZeros().toPlainString()));
  }

  private List<UUID> resolveAudience(SubscriptionRepricing job) {
    Request r = job.getRequest();
    Set<UUID> selected = new HashSet<>(r.accountIds());
    if (r.audience() == Audience.SELECTED) {
      selected.removeAll(r.excludedAccountIds());
      if (accounts.findAllById(selected).size() != selected.size())
        throw new InvalidRequestException("One or more selected Accounts are unavailable.");
      return selected.stream().sorted().toList();
    }
    if (r.audience() == Audience.SEGMENT) {
      var frozen = segments.resolveFrozenReference(r.segmentId().toString());
      if (!frozen.available()) throw new InvalidRequestException("Select an active Segment.");
      job.setSegmentActivationId(frozen.activationId());
      selected = new HashSet<>(frozen.accountIds());
    }
    Set<UUID> acceptedIds = selected;
    Specification<Subscription> spec =
        (root, query, cb) -> {
          var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
          conditions.add(cb.isNotNull(root.get("currentAccountId")));
          conditions.add(
              cb.equal(root.join("currentHoldings").get("priceEntryId"), r.sourcePriceId()));
          if (r.audience() == Audience.SELECTED || r.audience() == Audience.SEGMENT)
            conditions.add(root.get("account").get("id").in(acceptedIds));
          if (!r.excludedAccountIds().isEmpty())
            conditions.add(cb.not(root.get("account").get("id").in(r.excludedAccountIds())));
          if (r.planId() != null) conditions.add(cb.equal(root.get("plan").get("id"), r.planId()));
          if (r.subscriptionStatus() != null)
            conditions.add(cb.equal(root.get("status"), r.subscriptionStatus()));
          return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var found =
        subscriptions.findAll(spec, PageRequest.of(0, MAX_ACCOUNTS + 1, Sort.by("account.id")));
    if (found.getTotalElements() > MAX_ACCOUNTS)
      throw new InvalidRequestException(
          "Narrow the audience to at most 500 Accounts; none were silently omitted.");
    return found.getContent().stream()
        .map(s -> s.getAccount().getId())
        .distinct()
        .sorted()
        .toList();
  }

  private void validateAudience(Request r) {
    if (r.audience() == Audience.SELECTED != !r.accountIds().isEmpty()
        || (r.audience() == Audience.SEGMENT) != (r.segmentId() != null)
        || (r.audience() != Audience.FILTERED
            && (r.planId() != null || r.subscriptionStatus() != null))
        || (r.audience() == Audience.FILTERED
            && r.planId() == null
            && r.subscriptionStatus() == null)
        || new HashSet<>(r.accountIds()).size() != r.accountIds().size()
        || new HashSet<>(r.excludedAccountIds()).size() != r.excludedAccountIds().size())
      throw new InvalidRequestException("Choose one audience and valid non-duplicated exclusions.");
    if (r.notBefore() != null && r.notBefore().isBefore(clock.instant()))
      throw new InvalidRequestException("Choose a future effective threshold.");
  }

  Item itemView(SubscriptionRepricingItem i) {
    var job = i.getJob();
    String blocker = i.getBlocker();
    if (i.getStatus() == State.AWAITING_PAYMENT
        && i.getOperation() != null
        && i.getOperation().getCheckout() != null
        && i.getOperation().getCheckout().getStatus() == SubscriptionCheckoutStatus.FAILED) {
      blocker = "PAYMENT_FAILED";
    }
    return new Item(
        i.getId(),
        i.getStatus(),
        blocker,
        i.getQuantity(),
        job.getSourcePrice().getAmount(),
        job.getTargetPrice().getAmount(),
        i.getOldTotal(),
        i.getNewTotal(),
        job.getTargetPrice().getCurrencyCode(),
        job.getTargetPrice().getBillingCycle().name(),
        i.getEffectiveAt(),
        i.getOperation() == null ? null : i.getOperation().getId(),
        i.getDelivery(),
        i.getEmailAttempts());
  }

  private Detail detail(SubscriptionRepricing job) {
    var counts = new EnumMap<State, Long>(State.class);
    for (Object[] row : items.counts(List.of(job.getId())))
      counts.put((State) row[1], (Long) row[2]);
    Request r = job.getRequest();
    // Account membership is revealed only through the separately authorized identity endpoint.
    Request safe =
        new Request(
            r.sourcePriceId(),
            r.targetPriceId(),
            r.audience(),
            List.of(),
            List.of(),
            r.planId(),
            r.subscriptionStatus(),
            r.segmentId(),
            r.notBefore(),
            r.email(),
            r.reason());
    return new Detail(summary(job, counts), safe, job.getConfirmedAt());
  }

  private Map<State, Long> counts(List<SubscriptionRepricingItem> list) {
    return list.stream()
        .collect(
            Collectors.groupingBy(
                SubscriptionRepricingItem::getStatus,
                () -> new EnumMap<>(State.class),
                Collectors.counting()));
  }

  private Summary summary(SubscriptionRepricing j, Map<State, Long> c) {
    return new Summary(
        j.getId(),
        j.getStatus(),
        j.getSourcePrice().ownerName(),
        j.getTargetPrice().getCurrencyCode(),
        j.getTargetPrice().getBillingCycle().name(),
        j.getSourcePrice().getAmount(),
        j.getTargetPrice().getAmount(),
        j.getTargetCount(),
        c.getOrDefault(State.READY, 0L),
        c.getOrDefault(State.PENDING, 0L),
        c.getOrDefault(State.APPLIED, 0L),
        c.getOrDefault(State.CONFLICT, 0L),
        c.getOrDefault(State.CANCELLED, 0L),
        c.getOrDefault(State.AWAITING_PAYMENT, 0L),
        j.getCreatedAt());
  }

  private ProductPrice price(UUID id) {
    return prices.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tariff", "id", id));
  }

  private SubscriptionRepricing find(UUID id) {
    return jobs.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Repricing", "id", id));
  }

  private SubscriptionRepricing lock(UUID id) {
    return jobs.lock(id).orElseThrow(() -> new ResourceNotFoundException("Repricing", "id", id));
  }

  private StaleResourceVersionException stale() {
    return new StaleResourceVersionException(
        "Review expired or terms changed. Review this operation again.");
  }

  private String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(json.writeValueAsBytes(value)));
    } catch (Exception failure) {
      throw new IllegalStateException("Cannot fingerprint reviewed terms", failure);
    }
  }
}
