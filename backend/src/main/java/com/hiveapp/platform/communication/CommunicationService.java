package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.entity.CommercialNoticeRead;
import com.hiveapp.platform.client.plan.domain.repository.CommercialNoticeReadRepository;
import com.hiveapp.shared.exception.*;
import com.hiveapp.platform.generated.PlatformPermissions;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.*;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shared lifecycle and client interactions; never executes the communication's business action. */
@Service
@RequiredArgsConstructor
public class CommunicationService {
  private final CommunicationPublicationRepository publications;
  private final CommunicationEntryRepository entries;
  private final CommunicationInteractionRepository interactions;
  private final CommunicationPreferenceRepository preferences;
  private final CommercialNoticeReadRepository reads;
  private final AccountRepository accounts;
  private final CommunicationSources sources;
  private final NotificationAccess access;
  private final NotificationPreferenceRepository notificationPreferences;
  private final com.hiveapp.platform.client.plan.service.CommercialOfferService offers;
  private final Clock clock;

  public static Pageable page(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new InvalidRequestException("Pages support 1 to 100 entries.");
    return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
  }

  private UUID actor() {
    var c = HiveAppContextHolder.getContext();
    if (c == null || c.actorUserId() == null)
      throw new ForbiddenException("An authenticated actor is required.");
    return c.actorUserId();
  }

  private UUID ownAccount() {
    return access.viewer(false).accountId();
  }

  private void marketing(Purpose purpose) {
    if (purpose == Purpose.MARKETING
        && !PermissionGuard.has(
            PlatformPermissions.Customer_communications.Publish_marketing.permission()))
      throw new ForbiddenException("Marketing publication requires its own permission.");
  }

  @Transactional
  public Publication create(Draft draft) {
    var p = new CommunicationPublication();
    p.setActorId(actor());
    assign(p, draft);
    return view(publications.saveAndFlush(p));
  }

  @Transactional
  public Publication edit(UUID id, Edit edit) {
    var p = locked(id, edit.version());
    if (p.getState() != State.DRAFT)
      throw new InvalidStateException("Published content is immutable.");
    assign(p, edit.draft());
    return view(publications.saveAndFlush(p));
  }

  private void assign(CommunicationPublication p, Draft d) {
    if (!PermissionGuard.has(PlatformPermissions.Customer_communications.Choose_recipients.permission()))
      throw new ForbiddenException("Recipient selection permission is required.");
    if (d.kind() == Kind.WARNING && d.purpose() == Purpose.MARKETING)
      throw new InvalidRequestException("Marketing cannot be a warning.");
    if (d.replies())
      throw new InvalidRequestException("Notifications are one-way; replies are not supported.");
    if (d.kind() == Kind.ACTION)
      throw new InvalidRequestException(
          "Action notifications require their originating business workflow.");
    if ((d.kind() == Kind.OFFER) != (d.offerId() != null)
        || (d.kind() == Kind.OFFER && d.purpose() != Purpose.MARKETING))
      throw new InvalidRequestException(
          "An Offer notification must reference an Offer and use marketing purpose.");
    if (new HashSet<>(d.accountIds()).size() != d.accountIds().size())
      throw new InvalidRequestException("Recipients must be unique.");
    var at = d.availableAt() == null ? clock.instant() : d.availableAt();
    if (d.expiresAt() != null
        && (!d.expiresAt().isAfter(at) || !d.expiresAt().isAfter(clock.instant())))
      throw new InvalidRequestException("Expiry must follow availability and be in the future.");
    if (accounts.findAllById(d.accountIds()).size() != d.accountIds().size())
      throw new InvalidRequestException("Choose existing Accounts.");
    p.setKind(d.kind() == Kind.MESSAGE ? Kind.NOTICE : d.kind());
    p.setPurpose(d.purpose());
    p.setMessageTitle(d.messageTitle().trim());
    p.setMessageBody(d.messageBody().trim());
    p.setAccountIds(List.copyOf(d.accountIds()));
    p.setEmail(d.email());
    p.setReplies(false);
    p.setAvailableAt(at);
    p.setExpiresAt(d.expiresAt());
    p.setOfferId(d.offerId());
    validateOffer(p);
  }

  @Transactional
  public Publication publish(UUID id, Command command) {
    var p = locked(id, command.version());
    marketing(p.getPurpose());
    if (p.getState() != State.DRAFT)
      throw new InvalidStateException("Only a draft can be published.");
    if (p.getExpiresAt() != null && !p.getExpiresAt().isAfter(clock.instant()))
      throw new InvalidStateException("The publication has expired.");
    var targets = accounts.findAllById(p.getAccountIds());
    if (targets.size() != p.getAccountIds().size() || targets.stream().anyMatch(a -> !a.isActive()))
      throw new InvalidStateException("All recipients must be active Accounts.");
    validateOffer(p);
    for (var account : targets) {
      var e = new CommunicationEntry();
      e.setAccountId(account.getId());
      e.setPublicationId(id);
      e.setSource("ADMIN");
      e.setSourceId(id);
      e.setKind(p.getKind());
      e.setPriority(p.getKind() == Kind.WARNING ? Priority.HIGH : Priority.NORMAL);
      e.setPurpose(p.getPurpose());
      e.setMessageTitle(p.getMessageTitle());
      e.setMessageBody(p.getMessageBody());
      e.setAvailableAt(p.getAvailableAt());
      e.setExpiresAt(p.getExpiresAt());
      e.setReplies(false);
      e.setOptional(p.getKind() != Kind.WARNING);
      if (p.getKind() == Kind.OFFER) {
        e.setTopic(Topic.COMMERCIAL);
        e.setEventType(CoreNotification.OFFER_AVAILABLE.key());
        e.setResourceId(p.getOfferId());
        e.setRequiredPermission(CoreNotification.OFFER_AVAILABLE.requiredPermission().path());
        e.setActionPath(CoreNotification.OFFER_AVAILABLE.actionPath(p.getOfferId()));
      }
      e.getDelivery().publish(clock.instant(), p.isEmail());
      entries.save(e);
    }
    p.setState(State.PUBLISHED);
    p.setReason(command.reason().trim());
    return view(publications.saveAndFlush(p));
  }

  @Transactional
  public Publication cancel(UUID id, Command command) {
    var p = locked(id, command.version());
    if (p.getState() == State.CANCELLED) return view(p);
    for (var e : entries.findAllByPublicationIdOrderById(id)) {
      var locked = entries.lock(e.getId()).orElseThrow();
      locked.setCancelled(true);
      locked.setHidden(clock.instant().isBefore(locked.getAvailableAt()));
      locked.getDelivery().cancelUndispatched();
    }
    p.setState(State.CANCELLED);
    p.setReason(command.reason().trim());
    return view(publications.saveAndFlush(p));
  }

  @Transactional(readOnly = true)
  public Page<Publication> publications(Pageable page) {
    return publications.findAll(page).map(this::view);
  }

  @Transactional(readOnly = true)
  public Publication publication(UUID id) {
    return view(publications.findById(id).orElseThrow(() -> missing(id)));
  }

  @Transactional(readOnly = true)
  public Page<AccountChoice> choices(String search, Pageable page) {
    String q =
        search == null
            ? ""
            : search
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    return accounts
        .findAll(
            (root, query, cb) ->
                cb.and(
                    cb.isTrue(root.get("isActive")),
                    cb.like(cb.lower(root.get("name")), "%" + q + "%", '!')),
            page)
        .map(a -> new AccountChoice(a.getId(), a.getName()));
  }

  @Transactional(readOnly = true)
  public Page<Recipient> recipients(UUID id, Pageable page) {
    publication(id);
    var found = entries.findAllByPublicationId(id, page);
    var recipients = accounts.findAllById(found.map(CommunicationEntry::getAccountId));
    var activeAccounts =
        recipients.stream()
            .filter(a -> a.isActive())
            .map(a -> a.getId())
            .collect(Collectors.toSet());
    var names = recipients.stream().collect(Collectors.toMap(a -> a.getId(), a -> a.getName()));
    var ids = found.map(CommunicationEntry::getId).toList();
    var readCounts = counts(reads.counts(found.map(this::receiptId).toList()));
    var ackCounts = counts(interactions.counts(ids));
    var optedIn =
        preferences.findAllById(found.map(CommunicationEntry::getAccountId)).stream()
            .filter(CommunicationPreference::isMarketingInApp)
            .map(CommunicationPreference::getAccountId)
            .collect(Collectors.toSet());
    return found.map(
        e ->
            new Recipient(
                e.getId(),
                e.getAccountId(),
                names.get(e.getAccountId()),
                activeAccounts.contains(e.getAccountId())
                    && visible(e, optedIn.contains(e.getAccountId())),
                e.getDelivery().getDelivery(),
                e.getDelivery().getAttempts(),
                readCounts.getOrDefault(receiptId(e), 0L),
                ackCounts.getOrDefault(e.getId(), 0L)));
  }

  @Transactional(readOnly = true)
  public List<AccountChoice> selectedRecipients(UUID id) {
    return accounts.findAllById(publication(id).accountIds()).stream()
        .map(a -> new AccountChoice(a.getId(), a.getName()))
        .toList();
  }

  @Transactional
  public void retryEmail(UUID id) {
    var e = entries.lock(id).orElseThrow(() -> missing(id));
    requireManual(e);
    marketing(e.getPurpose());
    if (e.isCancelled() || expired(e))
      throw new InvalidStateException("Cancelled or expired communications cannot be resent.");
    if (!e.getDelivery().retry())
      throw new InvalidStateException("Only failed or suppressed email can be retried.");
  }

  @Transactional(readOnly = true)
  public Page<Item> inbox(Kind kind, boolean archived, boolean unread, Pageable page) {
    return inbox(kind, null, archived, unread, page, false);
  }

  @Transactional(readOnly = true)
  public Page<Item> inbox(
      Kind kind, Topic topic, boolean archived, boolean unread, Pageable page, boolean platform) {
    var viewer = access.viewer(platform);
    UUID account = viewer.accountId(), user = viewer.userId();
    var now = clock.instant();
    boolean marketing =
        account != null
            && preferences
                .findById(account)
                .map(CommunicationPreference::isMarketingInApp)
                .orElse(false);
    var allowed = sources.allowedPermissions();
    var muted =
        notificationPreferences.findAllByUserId(user).stream()
            .filter(p -> !p.isInAppEnabled())
            .map(NotificationPreference::getTopic)
            .toList();
    Specification<CommunicationEntry> spec =
        (r, q, cb) -> {
          List<Predicate> terms = new ArrayList<>();
          terms.add(
              platform ? cb.isNull(r.get("accountId")) : cb.equal(r.get("accountId"), account));
          terms.add(
              r.get("audience")
                  .in(
                      platform
                          ? List.of(Audience.PLATFORM, Audience.OPERATOR)
                          : List.of(Audience.ACCOUNT, Audience.MEMBER)));
          terms.add(
              cb.or(cb.isNull(r.get("recipientUserId")), cb.equal(r.get("recipientUserId"), user)));
          terms.add(
              viewer.companyId() == null
                  ? cb.isNull(r.get("companyId"))
                  : cb.or(
                      cb.isNull(r.get("companyId")),
                      cb.equal(r.get("companyId"), viewer.companyId())));
          terms.add(cb.isFalse(r.get("hidden")));
          terms.add(cb.lessThanOrEqualTo(r.get("availableAt"), now));
          terms.add(cb.or(cb.isNull(r.get("expiresAt")), cb.greaterThan(r.get("expiresAt"), now)));
          terms.add(
              cb.or(
                  cb.isNull(r.get("requiredPermission")), r.get("requiredPermission").in(allowed)));
          if (!marketing) terms.add(cb.notEqual(r.get("purpose"), Purpose.MARKETING));
          if (!muted.isEmpty())
            terms.add(cb.or(cb.isFalse(r.get("optional")), cb.not(r.get("topic").in(muted))));
          if (kind != null) terms.add(cb.equal(r.get("kind"), kind));
          if (topic != null) terms.add(cb.equal(r.get("topic"), topic));
          var archivedRows = q.subquery(UUID.class);
          var ar = archivedRows.from(CommunicationInteraction.class);
          archivedRows
              .select(ar.get("entryId"))
              .where(
                  cb.equal(ar.get("entryId"), r.get("id")),
                  cb.equal(ar.get("userId"), user),
                  cb.isNotNull(ar.get("archivedAt")));
          terms.add(archived ? cb.exists(archivedRows) : cb.not(cb.exists(archivedRows)));
          if (unread) {
            var readRows = q.subquery(UUID.class);
            var rr = readRows.from(CommercialNoticeRead.class);
            readRows
                .select(rr.get("noticeId"))
                .where(
                    cb.equal(rr.get("userId"), user),
                    cb.equal(
                        rr.get("noticeId"),
                        cb.<UUID>selectCase()
                            .when(
                                r.get("source").in("PLAN_CONTENT", "REPRICING"), r.get("sourceId"))
                            .otherwise(r.get("id"))));
            terms.add(cb.not(cb.exists(readRows)));
          }
          return cb.and(terms.toArray(Predicate[]::new));
        };
    var found = entries.findAll(spec, page);
    var ids = found.map(CommunicationEntry::getId).toList();
    var marks =
        interactions.findAllByUserIdAndEntryIdIn(user, ids).stream()
            .collect(Collectors.toMap(CommunicationInteraction::getEntryId, m -> m));
    var read =
        reads.findAllByUserIdAndNoticeIdIn(user, found.map(this::receiptId).toList()).stream()
            .collect(
                Collectors.toMap(
                    CommercialNoticeRead::getNoticeId, CommercialNoticeRead::getReadAt));
    var states = sources.states(found.getContent());
    return found.map(
        e ->
            item(
                e, marks.get(e.getId()), isRead(e, read.get(receiptId(e))), states.get(e.getId())));
  }

  @Transactional(readOnly = true)
  public Item detail(UUID id) {
    return detail(id, false);
  }

  @Transactional(readOnly = true)
  public Item detail(UUID id, boolean platform) {
    var e = ownEntry(id, false, platform);
    return item(
        e,
        interactions.findByEntryIdAndUserId(id, actor()).orElse(null),
        isRead(
            e,
            reads
                .findByNoticeIdAndUserId(receiptId(e), actor())
                .map(CommercialNoticeRead::getReadAt)
                .orElse(null)),
        sources.states(List.of(e)).get(id));
  }

  @Transactional
  public void interact(UUID id, Interaction action) {
    interact(id, action, false);
  }

  @Transactional
  public void interact(UUID id, Interaction action, boolean platform) {
    var e = ownEntry(id, true, platform);
    UUID user = actor();
    var mark =
        interactions
            .findByEntryIdAndUserId(id, user)
            .orElseGet(
                () -> {
                  var m = new CommunicationInteraction();
                  m.setEntryId(id);
                  m.setUserId(user);
                  return m;
                });
    if (action == Interaction.ACKNOWLEDGE) {
      if (e.getKind() != Kind.WARNING || e.isCancelled() || sources.inactive(e))
        throw new InvalidStateException("Only a current warning can be acknowledged.");
      if (mark.getAcknowledgedAt() == null) mark.setAcknowledgedAt(clock.instant());
    }
    if (action == Interaction.ARCHIVE) {
      if (e.getKind() == Kind.WARNING && !e.isCancelled() && !sources.inactive(e))
        throw new InvalidStateException(
            "An active warning cannot be archived. Acknowledgement is not resolution.");
      mark.setArchivedAt(clock.instant());
    }
    if (action == Interaction.RESTORE) mark.setArchivedAt(null);
    if (action == Interaction.READ || action == Interaction.ACKNOWLEDGE) markRead(e, user);
    interactions.save(mark);
  }

  @Transactional(readOnly = true)
  public Preference preference() {
    var p = preferences.findById(ownAccount()).orElse(new CommunicationPreference());
    return new Preference(p.isMarketingInApp(), p.isMarketingEmail());
  }

  @Transactional
  public Preference preference(Preference request) {
    UUID id = ownAccount();
    var account = accounts.findByIdForSubscriptionUpdate(id).orElseThrow();
    if (account.getOwner() == null || !account.getOwner().getId().equals(actor()))
      throw new ForbiddenException("Only the Account owner may change marketing preferences.");
    var p =
        preferences
            .findById(id)
            .orElseGet(
                () -> {
                  var v = new CommunicationPreference();
                  v.setAccountId(id);
                  return v;
                });
    p.setMarketingInApp(request.marketingInApp());
    p.setMarketingEmail(request.marketingEmail());
    preferences.save(p);
    return request;
  }

  private CommunicationEntry ownEntry(UUID id, boolean lock, boolean platform) {
    var viewer = access.viewer(platform);
    UUID account = viewer.accountId();
    // Account lock shares receipt serialization with legacy notices.
    if (lock && account != null) accounts.findByIdForSubscriptionUpdate(account).orElseThrow();
    var candidate =
        entries.findById(id).filter(e -> access.matches(e, viewer)).orElseThrow(() -> missing(id));
    var e = lock ? entries.lock(candidate.getId()).orElseThrow(() -> missing(id)) : candidate;
    if (!access.matches(e, viewer)
        || !visible(e)
        || (e.getRequiredPermission() != null
            && !PermissionGuard.has(new Permission(e.getRequiredPermission())))) throw missing(id);
    return e;
  }

  boolean visible(CommunicationEntry e) {
    return visible(
        e,
        e.getPurpose() != Purpose.MARKETING
            || preferences
                .findById(e.getAccountId())
                .map(CommunicationPreference::isMarketingInApp)
                .orElse(false));
  }

  private boolean visible(CommunicationEntry e, boolean optedIn) {
    return !e.isHidden()
        && !clock.instant().isBefore(e.getAvailableAt())
        && !expired(e)
        && (e.getPurpose() != Purpose.MARKETING || optedIn);
  }

  boolean expired(CommunicationEntry e) {
    return e.getExpiresAt() != null && !clock.instant().isBefore(e.getExpiresAt());
  }

  @Transactional(readOnly = true)
  public List<NotificationSetting> settings(boolean platform) {
    var v = access.viewer(platform);
    var found =
        notificationPreferences.findAllByUserId(v.userId()).stream()
            .collect(Collectors.toMap(NotificationPreference::getTopic, p -> p));
    return Arrays.stream(Topic.values())
        .map(
            topic -> {
              var p = found.get(topic);
              return new NotificationSetting(
                  topic, p == null || p.isInAppEnabled(), p == null || p.isEmailEnabled());
            })
        .toList();
  }

  @Transactional
  public NotificationSetting setting(NotificationSetting setting, boolean platform) {
    var v = access.viewer(platform);
    access.lockIdentity(v);
    var p =
        notificationPreferences
            .findByUserIdAndTopic(v.userId(), setting.topic())
            .orElseGet(
                () -> {
                  var value = new NotificationPreference();
                  value.setUserId(v.userId());
                  value.setTopic(setting.topic());
                  return value;
                });
    p.setInAppEnabled(setting.inAppEnabled());
    p.setEmailEnabled(setting.emailEnabled());
    notificationPreferences.save(p);
    return setting;
  }

  private Item item(CommunicationEntry e, CommunicationInteraction m, boolean read, String state) {
    boolean withdrawn =
        e.isCancelled() || sources.isInactiveState(state) || e.getResolvedAt() != null;
    return new Item(
        e.getId(),
        e.getKind() == Kind.MESSAGE ? Kind.NOTICE : e.getKind(),
        e.getPurpose(),
        e.getMessageTitle(),
        e.getMessageBody(),
        e.getSource(),
        e.isCancelled()
            ? "CANCELLED"
            : e.getResolvedAt() != null ? "RESOLVED" : state == null ? "PUBLISHED" : state,
        withdrawn ? null : e.getActionPath(),
        e.getAvailableAt(),
        e.getExpiresAt(),
        read,
        m != null && m.getAcknowledgedAt() != null,
        m != null && m.getArchivedAt() != null,
        e.getKind() == Kind.WARNING && !withdrawn,
        e.getKind() != Kind.WARNING || withdrawn,
        e.getTopic(),
        e.getEventType(),
        e.getResourceId(),
        e.getAudience(),
        withdrawn,
        e.getSenderName(), e.getPriority());
  }

  private UUID receiptId(CommunicationEntry e) {
    return Set.of("PLAN_CONTENT", "REPRICING").contains(e.getSource())
        ? e.getSourceId()
        : e.getId();
  }

  private void markRead(CommunicationEntry e, UUID user) {
    UUID id = receiptId(e);
    var r =
        reads
            .findByNoticeIdAndUserId(id, user)
            .orElseGet(
                () -> {
                  var mark = new CommercialNoticeRead();
                  mark.setNoticeId(id);
                  mark.setUserId(user);
                  return mark;
                });
    if (isRead(e, r.getReadAt())) return;
    r.setReadAt(clock.instant());
    reads.save(r);
  }

  private boolean isRead(CommunicationEntry e, Instant readAt) {
    return readAt != null;
  }

  private Map<UUID, Long> counts(List<Object[]> rows) {
    return rows.stream()
        .collect(Collectors.toMap(r -> (UUID) r[0], r -> ((Number) r[1]).longValue()));
  }

  private CommunicationPublication locked(UUID id, long version) {
    var p = publications.lock(id).orElseThrow(() -> missing(id));
    if (p.getVersion() != version)
      throw new StaleResourceVersionException(
          "This communication changed. Reload before continuing.");
    return p;
  }

  private void requireManual(CommunicationEntry e) {
    if (!"ADMIN".equals(e.getSource()))
      throw new InvalidStateException(
          "Use the originating business workflow for this communication.");
  }

  private ResourceNotFoundException missing(UUID id) {
    return new ResourceNotFoundException("Communication", "id", id);
  }

  private Publication view(CommunicationPublication p) {
    return new Publication(
        p.getId(),
        p.getVersion(),
        p.getKind(),
        p.getPurpose(),
        p.getMessageTitle(),
        p.getMessageBody(),
        p.getAccountIds(),
        p.isEmail(),
        false,
        p.getAvailableAt(),
        p.getExpiresAt(),
        p.getState(),
        p.getCreatedAt(),
        p.getOfferId());
  }

  private void validateOffer(CommunicationPublication p) {
    if (p.getOfferId() == null) return;
    if (!PermissionGuard.has(PlatformPermissions.Offers.Read.permission()))
      throw new ForbiddenException("Offer read permission is required.");
    if (p.getAccountIds().size() > 100)
      throw new InvalidRequestException(
          "Offer announcements are limited to 100 reviewed Accounts.");
    for (var id : p.getAccountIds()) {
      try {
        var offer = offers.detail(id, p.getOfferId());
        if (p.getExpiresAt() == null || p.getExpiresAt().isAfter(offer.endsAt()))
          p.setExpiresAt(offer.endsAt());
      } catch (com.hiveapp.shared.exception.OfferNotAvailableException unavailable) {
        throw new InvalidRequestException(
            "The Offer must be currently available in the catalogue to every selected Account.");
      }
    }
    if (!p.getExpiresAt().isAfter(p.getAvailableAt()))
      throw new InvalidRequestException("The announcement must start before the Offer expires.");
  }
}
