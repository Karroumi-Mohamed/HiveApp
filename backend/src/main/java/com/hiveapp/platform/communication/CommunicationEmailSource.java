package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.Purpose;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
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
  private final AccountRepository accounts;
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
        || (e.getExpiresAt() != null && !e.getExpiresAt().isAfter(now))) {
      d.cancelAbandonedClaim();
      return null;
    }
    if (now.isBefore(e.getAvailableAt())) return null;
    if (d.automaticAttemptsExhausted()) {
      d.fail();
      return null;
    }
    var a = accounts.findById(e.getAccountId()).orElse(null);
    if (a == null
        || !a.isActive()
        || a.getOwner() == null
        || !a.getOwner().isActive()
        || !a.getOwner().isEmailVerified()
        || (e.getPurpose() == Purpose.MARKETING
            && !preferences
                .findById(e.getAccountId())
                .map(CommunicationPreference::isMarketingEmail)
                .orElse(false))) {
      d.suppress();
      return null;
    }
    var claim = d.claim(a.getOwner().getId(), now);
    entries.saveAndFlush(e);
    return new Claim(
        id,
        claim,
        a.getOwner().getEmail(),
        "HiveApp — " + e.getMessageTitle(),
        e.getPurpose() == Purpose.MARKETING
            ? e.getMessageBody()
            : "Une communication vous attend dans HiveApp → Communications. Connectez-vous pour la"
                + " consulter.");
  }

  @Transactional
  public void completeNotice(Claim claim, Delivery outcome) {
    entries.lock(claim.noticeId()).orElseThrow().getDelivery().complete(claim.claimId(), outcome);
  }
}
