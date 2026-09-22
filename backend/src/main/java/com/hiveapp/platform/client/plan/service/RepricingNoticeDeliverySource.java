package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepricingItemRepository;
import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Compatibility adapter: existing price notice rows, leases and receipt IDs are unchanged. */
@Component
@RequiredArgsConstructor
public class RepricingNoticeDeliverySource implements CommercialNoticeDeliverySource {
  private final SubscriptionRepricingItemRepository items;
  private final RepricingNoticeDelivery delivery;

  @Override
  public List<UUID> due(Instant now, int limit) {
    var ids = new LinkedHashSet<>(items.emailDue(Delivery.PENDING, PageRequest.of(0, limit)));
    ids.addAll(
        items.abandonedEmailClaims(
            Delivery.SENDING, now.minusSeconds(300), PageRequest.of(0, limit)));
    return List.copyOf(ids);
  }

  @Override
  public Claim claimNotice(UUID id) {
    var claim = delivery.claim(id);
    if (claim == null) return null;
    return new Claim(
        claim.itemId(),
        claim.claimId(),
        claim.email(),
        "HiveApp — changement de tarif programmé",
        "Un changement de tarif a été programmé pour votre abonnement. Connectez-vous à HiveApp,"
            + " puis ouvrez Abonnement → Notifications pour consulter le montant, la date et l’état"
            + " actuel. Ce message n’est ni une facture ni une demande de paiement.");
  }

  @Override
  public void completeNotice(Claim claim, Delivery outcome) {
    delivery.complete(
        new RepricingNoticeDelivery.Dispatch(claim.noticeId(), claim.claimId(), claim.email()),
        outcome);
  }
}
