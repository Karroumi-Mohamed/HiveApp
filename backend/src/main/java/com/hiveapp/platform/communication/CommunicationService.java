package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.entity.CommercialNoticeRead;
import com.hiveapp.platform.client.plan.domain.repository.CommercialNoticeReadRepository;
import com.hiveapp.shared.exception.*;
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
  private final CommunicationReplyRepository replies;
  private final CommunicationPreferenceRepository preferences;
  private final CommercialNoticeReadRepository reads;
  private final AccountRepository accounts;
  private final CommunicationSources sources;
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
    var c = HiveAppContextHolder.getContext();
    if (c == null
        || c.isB2B()
        || c.currentAccountId() == null
        || !c.currentAccountId().equals(c.clientAccountId())
        || !accounts.existsActiveById(c.currentAccountId()))
      throw new ForbiddenException("Communications are private to the active own Account.");
    return c.currentAccountId();
  }

  private void marketing(Purpose purpose) {
    if (purpose == Purpose.MARKETING
        && !PermissionGuard.has(
            new Permission("platform.customer_communications.publish_marketing")))
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
    if (!PermissionGuard.has(new Permission("platform.customer_communications.choose_recipients")))
      throw new ForbiddenException("Recipient selection permission is required.");
    if (d.kind() == Kind.WARNING && d.purpose() == Purpose.MARKETING)
      throw new InvalidRequestException("Marketing cannot be a warning.");
    if (d.replies() && d.kind() != Kind.MESSAGE)
      throw new InvalidRequestException("Only messages allow replies.");
    if (new HashSet<>(d.accountIds()).size() != d.accountIds().size())
      throw new InvalidRequestException("Recipients must be unique.");
    var at = d.availableAt() == null ? clock.instant() : d.availableAt();
    if (d.expiresAt() != null
        && (!d.expiresAt().isAfter(at) || !d.expiresAt().isAfter(clock.instant())))
      throw new InvalidRequestException("Expiry must follow availability and be in the future.");
    if (accounts.findAllById(d.accountIds()).size() != d.accountIds().size())
      throw new InvalidRequestException("Choose existing Accounts.");
    p.setKind(d.kind());
    p.setPurpose(d.purpose());
    p.setMessageTitle(d.messageTitle().trim());
    p.setMessageBody(d.messageBody().trim());
    p.setAccountIds(List.copyOf(d.accountIds()));
    p.setEmail(d.email());
    p.setReplies(d.replies());
    p.setAvailableAt(at);
    p.setExpiresAt(d.expiresAt());
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
    for (var account : targets) {
      var e = new CommunicationEntry();
      e.setAccountId(account.getId());
      e.setPublicationId(id);
      e.setSource("ADMIN");
      e.setSourceId(id);
      e.setKind(p.getKind());
      e.setPurpose(p.getPurpose());
      e.setMessageTitle(p.getMessageTitle());
      e.setMessageBody(p.getMessageBody());
      e.setAvailableAt(p.getAvailableAt());
      e.setExpiresAt(p.getExpiresAt());
      e.setReplies(p.isReplies());
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
    var replyCounts = counts(replies.counts(ids));
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
                ackCounts.getOrDefault(e.getId(), 0L),
                replyCounts.getOrDefault(e.getId(), 0L),
                e.isClosed()));
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

  @Transactional
  public void close(UUID id, boolean closed) {
    var e = entries.lock(id).orElseThrow(() -> missing(id));
    requireManual(e);
    if (e.getKind() != Kind.MESSAGE || !e.isReplies())
      throw new InvalidStateException("This communication has no reply thread.");
    e.setClosed(closed);
  }

  @Transactional(readOnly = true)
  public Page<Item> inbox(Kind kind, boolean archived, boolean unread, Pageable page) {
    UUID account = ownAccount(), user = actor();
    var now = clock.instant();
    boolean marketing =
        preferences.findById(account).map(CommunicationPreference::isMarketingInApp).orElse(false);
    var allowed = sources.allowedPermissions();
    Specification<CommunicationEntry> spec =
        (r, q, cb) -> {
          List<Predicate> terms = new ArrayList<>();
          terms.add(cb.equal(r.get("accountId"), account));
          terms.add(cb.isFalse(r.get("hidden")));
          terms.add(cb.lessThanOrEqualTo(r.get("availableAt"), now));
          terms.add(cb.or(cb.isNull(r.get("expiresAt")), cb.greaterThan(r.get("expiresAt"), now)));
          terms.add(
              cb.or(
                  cb.isNull(r.get("requiredPermission")), r.get("requiredPermission").in(allowed)));
          if (!marketing) terms.add(cb.notEqual(r.get("purpose"), Purpose.MARKETING));
          if (kind != null) terms.add(cb.equal(r.get("kind"), kind));
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
                            .when(cb.equal(r.get("source"), "ADMIN"), r.get("id"))
                            .otherwise(r.get("sourceId"))),
                    cb.or(
                        cb.isNull(r.get("lastReplyAt")),
                        cb.greaterThanOrEqualTo(rr.get("readAt"), r.get("lastReplyAt"))));
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
    var e = ownEntry(id, false);
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
    var e = ownEntry(id, true);
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

  @Transactional
  public Reply reply(UUID id, ReplyRequest request, boolean admin) {
    var e = admin ? entries.lock(id).orElseThrow(() -> missing(id)) : ownEntry(id, true);
    if (admin) requireManual(e);
    UUID actor = actor();
    var previous = replies.findByEntryIdAndActorIdAndCommandId(id, actor, request.commandId());
    if (previous.isPresent()) {
      if (!previous.get().getReplyBody().equals(request.replyBody().trim()))
        throw new InvalidRequestException("This command identifies a different reply.");
      return replyView(previous.get());
    }
    if (e.getKind() != Kind.MESSAGE
        || !e.isReplies()
        || e.isClosed()
        || e.isCancelled()
        || !visible(e)) throw new InvalidStateException("This thread is not open for replies.");
    var reply = new CommunicationReply();
    reply.setEntryId(id);
    reply.setActorId(actor);
    reply.setCommandId(request.commandId());
    reply.setFromAdmin(admin);
    reply.setReplyBody(request.replyBody().trim());
    e.setLastReplyAt(clock.instant());
    interactions.unarchiveThread(id);
    if (!admin) markRead(e, actor);
    return replyView(replies.saveAndFlush(reply));
  }

  @Transactional(readOnly = true)
  public Page<Reply> thread(UUID id, boolean admin, Pageable page) {
    if (admin) requireManual(entries.findById(id).orElseThrow(() -> missing(id)));
    else ownEntry(id, false);
    return replies.findAllByEntryId(id, page).map(this::replyView);
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

  private CommunicationEntry ownEntry(UUID id, boolean lock) {
    UUID account = ownAccount();
    // Account lock shares receipt serialization with legacy notices.
    if (lock) accounts.findByIdForSubscriptionUpdate(account).orElseThrow();
    var e =
        (lock ? entries.lockOwn(id, account) : entries.findById(id)).orElseThrow(() -> missing(id));
    if (!e.getAccountId().equals(account)
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

  private Item item(CommunicationEntry e, CommunicationInteraction m, boolean read, String state) {
    boolean withdrawn = e.isCancelled() || sources.isInactiveState(state);
    return new Item(
        e.getId(),
        e.getKind(),
        e.getPurpose(),
        e.getMessageTitle(),
        e.getMessageBody(),
        e.getSource(),
        e.isCancelled() ? "CANCELLED" : state == null ? "PUBLISHED" : state,
        e.getActionPath(),
        e.getAvailableAt(),
        e.getExpiresAt(),
        read,
        m != null && m.getAcknowledgedAt() != null,
        m != null && m.getArchivedAt() != null,
        e.getKind() == Kind.WARNING && !withdrawn,
        e.getKind() != Kind.WARNING || withdrawn,
        e.getKind() == Kind.MESSAGE && e.isReplies() && !e.isClosed() && !withdrawn,
        e.isClosed());
  }

  private UUID receiptId(CommunicationEntry e) {
    return "ADMIN".equals(e.getSource()) ? e.getId() : e.getSourceId();
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
    return readAt != null && (e.getLastReplyAt() == null || !readAt.isBefore(e.getLastReplyAt()));
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

  private Reply replyView(CommunicationReply r) {
    return new Reply(
        r.getId(), r.getCommandId(), r.isFromAdmin(), r.getReplyBody(), r.getCreatedAt());
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
        p.isReplies(),
        p.getAvailableAt(),
        p.getExpiresAt(),
        p.getState(),
        p.getCreatedAt());
  }
}
