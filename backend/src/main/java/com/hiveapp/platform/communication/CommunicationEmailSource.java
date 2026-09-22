package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.Purpose;

import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import com.hiveapp.platform.client.plan.service.CommercialNoticeDeliverySource;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunicationEmailSource implements CommercialNoticeDeliverySource {
  private final CommunicationEntryRepository entries;
  private final CommunicationPreferenceRepository preferences;
  private final NotificationAccess access;
  private final NotificationPreferenceRepository notificationPreferences;
  private final NotificationOfferEligibility offers;
  private final com.hiveapp.shared.config.ActivationProperties links;
  private final Clock clock;

  public List<UUID> due(Instant now, int limit) {
    return entries.due(now, now.minusSeconds(300), PageRequest.of(0, Math.min(limit, 100)));
  }

  @Transactional
  public Claim claimNotice(UUID id) {
    var e = entries.lock(id).orElseThrow();
    var d = e.getDelivery();
    var now = clock.instant();
    if (!d.claimable(now)) return null;
    if (e.isCancelled()
        || e.isHidden()
        || (e.getResolvedAt() != null
            && (e.getKind() == CommunicationModels.Kind.WARNING
                || e.getKind() == CommunicationModels.Kind.ACTION))
        || (e.getExpiresAt() != null && !e.getExpiresAt().isAfter(now))) {
      d.cancelAbandonedClaim();
      e.setEmailFailureCode(e.getExpiresAt() != null && !e.getExpiresAt().isAfter(now) ? "EXPIRED" : "WITHDRAWN");
      e.setNextEmailAttemptAt(null);
      return null;
    }
    if (now.isBefore(e.getAvailableAt())) return null;
    if (e.getNextEmailAttemptAt() != null && now.isBefore(e.getNextEmailAttemptAt())) return null;
    if (d.automaticAttemptsExhausted()) {
      d.fail();
      e.setEmailFailureCode("ATTEMPTS_EXHAUSTED");
      return null;
    }
    var recipient = access.emailRecipient(e).orElse(null);
    if (recipient == null
        || (e.isOptional()
            && notificationPreferences
                .findByUserIdAndTopic(recipient.getId(), e.getTopic())
                .filter(p -> !p.isEmailEnabled())
                .isPresent())
        || (e.getPurpose() == Purpose.MARKETING
            && !preferences
                .findById(e.getAccountId())
                .map(CommunicationPreference::isMarketingEmail)
                .orElse(false))) {
      d.suppress();
      e.setEmailFailureCode(recipient == null ? "RECIPIENT_UNAVAILABLE" : "PREFERENCE_DISABLED");
      return null;
    }
    if (e.getKind() == CommunicationModels.Kind.OFFER) {
      try {
        offers.requireAvailable(e.getAccountId(), e.getResourceId());
      } catch (com.hiveapp.shared.exception.OfferNotAvailableException unavailable) {
        d.suppress();
        e.setEmailFailureCode("OFFER_UNAVAILABLE");
        return null;
      }
    }
    var claim = d.claim(recipient.getId(), now);
    e.setEmailFailureCode(null);
    entries.saveAndFlush(e);
    return new Claim(
        id,
        claim,
        recipient.getEmail(),
        "HiveApp — Notification",
        e.getPurpose() == Purpose.MARKETING
            ? e.getMessageBody() + "\n\n" + links.getValidatedOrigin() + (e.getActionPath() == null ? "" : e.getActionPath())
            : "Une notification vous attend dans HiveApp → Notifications. Connectez-vous pour la"
                + " consulter.\n\n" + links.getValidatedOrigin()
                + (e.getAccountId() == null ? "/admin/notifications" : "/app/communications")
                + "?item=" + e.getId()
                + (e.getCompanyId() == null ? "" : "&company=" + e.getCompanyId()));
  }

  @Transactional
  public void completeNotice(Claim claim, Delivery outcome) {
    var e = entries.lock(claim.noticeId()).orElseThrow();
    var d = e.getDelivery();
    if (d.getDelivery() != Delivery.SENDING
        || !java.util.Objects.equals(d.getClaimId(), claim.claimId())) return;
    d.complete(claim.claimId(), outcome);
    e.setEmailFailureCode(outcome == Delivery.FAILED ? "TRANSPORT_FAILED" : outcome == Delivery.SUPPRESSED ? "TRANSPORT_SUPPRESSED" : null);
    if (outcome == Delivery.FAILED && d.getAttempts() < 3 && !e.isCancelled()) {
      d.retry();
      e.setNextEmailAttemptAt(clock.instant().plusSeconds(60L << d.getAttempts()));
    }
  }
}
