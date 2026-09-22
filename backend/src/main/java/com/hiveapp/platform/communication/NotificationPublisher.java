package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Trusted domain API only. No HTTP endpoint accepts arbitrary event definitions or action URLs. */
@Service
@RequiredArgsConstructor
public class NotificationPublisher {
  public record Target(Audience audience, UUID accountId, UUID userId, UUID companyId) {
    public Target {
      Objects.requireNonNull(audience);
      boolean platform = audience == Audience.PLATFORM || audience == Audience.OPERATOR;
      if (platform != (accountId == null)
          || (platform && companyId != null)
          || ((audience == Audience.MEMBER || audience == Audience.OPERATOR) != (userId != null)))
        throw new IllegalArgumentException("Invalid notification audience.");
    }

    public static Target account(UUID id) {
      return new Target(Audience.ACCOUNT, Objects.requireNonNull(id), null, null);
    }

    public static Target member(UUID account, UUID user) {
      return new Target(
          Audience.MEMBER, Objects.requireNonNull(account), Objects.requireNonNull(user), null);
    }

    public static Target platform() {
      return new Target(Audience.PLATFORM, null, null, null);
    }
  }

  private final NotificationCatalog catalog;
  private final NotificationEventRepository events;
  private final CommunicationEntryRepository entries;
  private final NotificationOccurrenceLock locks;
  private final NotificationAccess access;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID publish(
      NotificationDefinition definition,
      String occurrence,
      Target target,
      UUID resourceId,
      String title,
      String body,
      boolean email,
      Instant availableAt,
      Instant expiresAt) {
    if (catalog.require(definition.key()) != definition)
      throw new IllegalArgumentException("Use the registered typed notification definition.");
    if (definition.platform() != (target.accountId() == null)
        || (definition.platform() && email && target.userId() == null))
      throw new IllegalArgumentException("Notification audience does not match its definition.");
    if (occurrence == null
        || occurrence.isBlank()
        || occurrence.length() > 200
        || title == null
        || title.isBlank()
        || title.length() > 160
        || body == null
        || body.isBlank()
        || body.length() > 10000)
      throw new IllegalArgumentException("Invalid notification content or occurrence.");
    var at = availableAt == null ? clock.instant() : availableAt;
    if (expiresAt != null && !expiresAt.isAfter(at))
      throw new IllegalArgumentException("Invalid notification expiry.");
    String path = definition.actionPath(resourceId);
    if (path != null && !(path.startsWith(definition.platform() ? "/admin/" : "/app/"))
        || (path != null && (path.contains("//") || path.contains("\\"))))
      throw new IllegalArgumentException(
          "Notification actions must be internal typed destinations.");
    String key = hash(definition.key() + "|" + occurrence + "|" + target);
    locks.acquire(key);
    access.validateTarget(target);
    var previous = events.findByDedupeKey(key);
    if (previous.isPresent()) {
      var old = previous.get();
      if (!Objects.equals(old.getResourceId(), resourceId)
          || !old.getMessageTitle().equals(title.trim())
          || !old.getMessageBody().equals(body.trim())
          || old.isEmail() != email
          || !Objects.equals(old.getExpiresAt(), expiresAt)
          || (availableAt != null && !old.getAvailableAt().equals(availableAt)))
        throw new IllegalArgumentException(
            "Notification occurrence was reused for different content.");
      return old.getId();
    }
    var e = new NotificationEvent();
    e.setDedupeKey(key);
    e.setDefinitionKey(definition.key());
    e.setAudience(target.audience());
    e.setAccountId(target.accountId());
    e.setRecipientUserId(target.userId());
    e.setCompanyId(target.companyId());
    e.setResourceId(resourceId);
    e.setMessageTitle(title.trim());
    e.setMessageBody(body.trim());
    e.setEmail(email);
    e.setAvailableAt(at);
    e.setExpiresAt(expiresAt);
    e.setNextAttemptAt(clock.instant());
    return events.saveAndFlush(e).getId();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void resolve(NotificationDefinition definition, UUID resourceId) {
    catalog.require(definition.key());
    Objects.requireNonNull(resourceId);
    events.resolve(definition.key(), resourceId, clock.instant());
    entries.resolveEvent(definition.key(), resourceId, clock.instant());
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
