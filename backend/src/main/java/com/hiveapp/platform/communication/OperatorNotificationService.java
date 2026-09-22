package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.exception.*;
import dev.karroumi.permissionizer.*;
import java.time.*;
import java.util.UUID;
import com.hiveapp.platform.generated.PlatformPermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PermissionNode(
    key = "notifications",
    description = "Operator notifications",
    guard = PermissionNode.Guard.ON)
public class OperatorNotificationService extends PlatformControlFeatureService {
  public record EventResult(
      UUID id,
      String type,
      NotificationEvent.State state,
      int attempts,
      Instant nextAttemptAt,
      String failureCode,
      long version) {}

  public record EmailResult(
      UUID id,
      String type,
      Delivery state,
      int attempts,
      Instant nextAttemptAt,
      long version,
      boolean canRetry) {}

  private final CommunicationService communications;
  private final NotificationEventRepository events;
  private final CommunicationEntryRepository entries;
  private final Clock clock;

  protected FeatureDefinition featureDefinition() {
    return FeatureDefinition.platformControl("platform.notifications")
        .displayName("Operator notifications")
        .description("Scoped operational notifications, not customer publications")
        .sortOrder(58)
        .build();
  }

  @PermissionNode(key = "read", description = "Read authorized operator notifications")
  public Page<Item> inbox(Kind kind, Topic topic, boolean archived, boolean unread, Pageable p) {
    return communications.inbox(kind, topic, archived, unread, p, true);
  }

  @PermissionNode(key = "internal_detail", guard = PermissionNode.Guard.OFF)
  public Item detail(UUID id) {
    requireRead();
    return communications.detail(id, true);
  }

  @PermissionNode(key = "internal_settings", guard = PermissionNode.Guard.OFF)
  public java.util.List<NotificationSetting> settings() {
    requireRead();
    return communications.settings(true);
  }

  @PermissionNode(key = "preferences", description = "Manage own optional notification preferences")
  @Transactional
  public NotificationSetting setting(NotificationSetting s) {
    requireRead();
    return communications.setting(s, true);
  }

  @PermissionNode(key = "mark_read", description = "Mark own operator notification as read")
  @Transactional
  public void read(UUID id) {
    requireRead();
    communications.interact(id, Interaction.READ, true);
  }

  @PermissionNode(key = "acknowledge", description = "Acknowledge seeing an operational warning")
  @Transactional
  public void acknowledge(UUID id) {
    requireRead();
    communications.interact(id, Interaction.ACKNOWLEDGE, true);
  }

  @PermissionNode(key = "archive", description = "Archive or restore own operator notifications")
  @Transactional
  public void archive(UUID id, boolean archived) {
    requireRead();
    communications.interact(id, archived ? Interaction.ARCHIVE : Interaction.RESTORE, true);
  }

  @PermissionNode(
      key = "read_delivery",
      description = "Inspect notification outbox delivery results")
  @Transactional(readOnly = true)
  public Page<EventResult> events(NotificationEvent.State state, Pageable page) {
    return events
        .findAll(
            (r, q, b) -> state == null ? b.conjunction() : b.equal(r.get("state"), state), page)
        .map(
            e ->
                new EventResult(
                    e.getId(),
                    e.getDefinitionKey(),
                    e.getState(),
                    e.getAttempts(),
                    e.getNextAttemptAt(),
                    e.getFailureCode(),
                    e.getVersion()));
  }

  @PermissionNode(key = "retry_delivery", description = "Retry a failed notification event")
  @Transactional
  public void retry(UUID id, Command command) {
    var e =
        events
            .lock(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification event", "id", id));
    if (e.getVersion() != command.version())
      throw new StaleResourceVersionException("Reload the notification event.");
    if (e.getState() != NotificationEvent.State.FAILED
        || (e.getExpiresAt() != null && !e.getExpiresAt().isAfter(clock.instant())))
      throw new InvalidStateException("Only unexpired failed events can retry.");
    e.setState(NotificationEvent.State.PENDING);
    e.setAttempts(0);
    e.setNextAttemptAt(clock.instant());
    e.setFailureCode(null);
  }

  @PermissionNode(key = "internal_email_results", guard = PermissionNode.Guard.OFF)
  @Transactional(readOnly = true)
  public Page<EmailResult> emailResults(Delivery state, Pageable page) {
    requireDelivery(PlatformPermissions.Notifications.Read_delivery.permission());
    return entries
        .findAll(
            (r, q, b) ->
                b.and(
                    b.equal(r.get("source"), "EVENT"),
                    b.notEqual(r.get("delivery").get("delivery"), Delivery.NOT_REQUESTED),
                    state == null
                        ? b.conjunction()
                        : b.equal(r.get("delivery").get("delivery"), state)),
            page)
        .map(
            e ->
                new EmailResult(
                    e.getId(),
                    e.getEventType(),
                    e.getDelivery().getDelivery(),
                    e.getDelivery().getAttempts(),
                    e.getNextEmailAttemptAt(),
                    e.getVersion(),
                    emailRetryable(e)));
  }

  @PermissionNode(key = "internal_retry_email", guard = PermissionNode.Guard.OFF)
  @Transactional
  public void retryEmail(UUID id, Command command) {
    requireDelivery(PlatformPermissions.Notifications.Retry_delivery.permission());
    var e =
        entries
            .lock(id)
            .filter(v -> "EVENT".equals(v.getSource()))
            .orElseThrow(() -> new ResourceNotFoundException("Notification email", "id", id));
    if (e.getVersion() != command.version())
      throw new StaleResourceVersionException("Reload the notification email.");
    if (!emailRetryable(e))
      throw new InvalidStateException("This notification email cannot retry.");
    if (e.getPurpose() == Purpose.MARKETING
        && !PermissionGuard.has(
            PlatformPermissions.Customer_communications.Publish_marketing.permission()))
      throw new ForbiddenException("Marketing publication requires its own permission.");
    e.getDelivery().retry();
    e.setNextEmailAttemptAt(clock.instant());
  }

  private boolean emailRetryable(CommunicationEntry e) {
    var state = e.getDelivery().getDelivery();
    return (state == Delivery.FAILED || state == Delivery.SUPPRESSED)
        && !e.isCancelled()
        && !e.isHidden()
        && (e.getExpiresAt() == null || e.getExpiresAt().isAfter(clock.instant()))
        && !(e.getResolvedAt() != null
            && (e.getKind() == Kind.WARNING || e.getKind() == Kind.ACTION));
  }

  private void requireDelivery(Permission permission) {
    if (!PermissionGuard.has(permission))
      throw new ForbiddenException("Notification delivery permission required.");
  }

  private void requireRead() {
    if (!PermissionGuard.has(PlatformPermissions.Notifications.Read.permission()))
      throw new ForbiddenException("Notification read permission required.");
  }
}
