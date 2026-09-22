package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanContentNoticeRepository;
import com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.State;
import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanContentNoticeDeliverySource implements CommercialNoticeDeliverySource {
  private final PlanContentNoticeRepository notices;
  private final AccountRepository accounts;
  private final Clock clock;

  @Override
  public List<UUID> due(Instant now, int limit) {
    return notices.due(
        Delivery.PENDING, Delivery.SENDING, now.minusSeconds(300), PageRequest.of(0, limit));
  }

  @Override
  @Transactional
  public Claim claimNotice(UUID id) {
    accounts.findByIdForSubscriptionUpdate(notices.accountId(id).orElseThrow()).orElseThrow();
    var notice = notices.lock(id).orElseThrow();
    var state = notice.getDelivery();
    if (!state.claimable(clock.instant())) return null;
    if (notice.getState() == State.CANCELLED) {
      state.cancelAbandonedClaim();
      return null;
    }
    if (state.automaticAttemptsExhausted()) {
      state.fail();
      return null;
    }
    var owner = notice.getAccount().getOwner();
    if (owner == null || !owner.isEmailVerified()) {
      state.suppress();
      return null;
    }
    UUID claim = state.claim(owner.getId(), clock.instant());
    notices.saveAndFlush(notice);
    return new Claim(
        id,
        claim,
        owner.getEmail(),
        "HiveApp — évolution du contenu de votre forfait",
        "Une évolution du contenu de votre forfait a été préparée. Consultez Abonnement →"
            + " Notifications dans HiveApp pour connaître les fonctionnalités concernées, la date"
            + " et l’état actuel. Ce changement conserve vos conditions financières et ne constitue"
            + " pas une demande de paiement.");
  }

  @Override
  @Transactional
  public void completeNotice(Claim claim, Delivery outcome) {
    var notice = notices.lock(claim.noticeId()).orElseThrow();
    notice.getDelivery().complete(claim.claimId(), outcome);
    notices.saveAndFlush(notice);
  }
}
