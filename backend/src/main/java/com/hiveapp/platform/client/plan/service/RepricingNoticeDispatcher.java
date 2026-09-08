package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepricingItemRepository;
import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import com.hiveapp.shared.email.*;
import java.time.Clock;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RepricingNoticeDispatcher {
  private final SubscriptionRepricingItemRepository items;
  private final RepricingNoticeDelivery delivery;
  private final EmailService email;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${hiveapp.subscriptions.notice-delay-ms:30000}")
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NEVER)
  public void dispatch() {
    var ids = new LinkedHashSet<>(items.emailDue(Delivery.PENDING, PageRequest.of(0, 20)));
    ids.addAll(
        items.abandonedEmailClaims(
            Delivery.SENDING, clock.instant().minusSeconds(300), PageRequest.of(0, 20)));
    for (var id : ids) {
      var claim = delivery.claim(id);
      if (claim == null) continue;
      Delivery outcome;
      try {
        // Financial details stay behind current Account authorization in the portal.
        var result =
            email.sendCommercialNotice(
                claim.email(),
                "HiveApp — changement de tarif programmé",
                "Un changement de tarif a été programmé pour votre abonnement. Connectez-vous à"
                    + " HiveApp, puis ouvrez Abonnement → Notifications pour consulter le montant,"
                    + " la date et l’état actuel. Ce message n’est ni une facture ni une demande de"
                    + " paiement.");
        outcome = result == EmailDispatchOutcome.SENT ? Delivery.SENT : Delivery.SUPPRESSED;
      } catch (RuntimeException failure) {
        outcome = Delivery.FAILED;
      }
      delivery.complete(claim, outcome);
    }
  }
}
