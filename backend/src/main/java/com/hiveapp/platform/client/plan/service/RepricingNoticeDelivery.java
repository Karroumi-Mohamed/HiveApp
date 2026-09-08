package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepricingItemRepository;
import com.hiveapp.platform.client.plan.dto.RepricingModels.*;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short transactions claim/complete an email; the transport is deliberately outside them. */
@Service
@RequiredArgsConstructor
public class RepricingNoticeDelivery {
  private final SubscriptionRepricingItemRepository items;
  private final AccountRepository accounts;
  private final Clock clock;

  public record Dispatch(UUID itemId, UUID claimId, String email) {}

  @Transactional
  public Dispatch claim(UUID id) {
    accounts.findByIdForSubscriptionUpdate(items.accountId(id).orElseThrow());
    var item = items.lock(id).orElseThrow();
    if (item.getDelivery() != Delivery.PENDING
        && !(item.getDelivery() == Delivery.SENDING
            && item.getEmailClaimedAt().isBefore(clock.instant().minus(Duration.ofMinutes(5)))))
      return null;
    if (item.getStatus() != State.PENDING || "CANCELLED".equals(item.getJob().getStatus())) {
      item.setDelivery(Delivery.CANCELLED);
      return null;
    }
    // Automatic abandoned-claim recovery is bounded. An explicitly authorized manual
    // retry may try again without erasing the cumulative attempt history.
    if (item.getDelivery() == Delivery.SENDING && item.getEmailAttempts() >= 3) {
      item.setDelivery(Delivery.FAILED);
      return null;
    }
    var owner = item.getAccount().getOwner();
    if (owner == null || !owner.isEmailVerified()) {
      item.setDelivery(Delivery.SUPPRESSED);
      return null;
    }
    item.setDelivery(Delivery.SENDING);
    item.setEmailClaimId(UUID.randomUUID());
    item.setEmailClaimedAt(clock.instant());
    item.setEmailAttempts(item.getEmailAttempts() + 1);
    item.setEmailRecipientId(owner.getId());
    items.saveAndFlush(item);
    return new Dispatch(id, item.getEmailClaimId(), owner.getEmail());
  }

  @Transactional
  public void complete(Dispatch dispatch, Delivery outcome) {
    var item = items.lock(dispatch.itemId()).orElseThrow();
    if (item.getDelivery() != Delivery.SENDING
        || !dispatch.claimId().equals(item.getEmailClaimId())) return;
    item.setDelivery(outcome);
    items.saveAndFlush(item);
  }
}
