package com.hiveapp.platform.communication;

import com.hiveapp.platform.client.plan.service.CommercialOfferService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolate a rejected Offer read so it cannot roll back the caller's durable suppression result. */
@Service
@RequiredArgsConstructor
public class NotificationOfferEligibility {
  private final CommercialOfferService offers;

  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void requireAvailable(UUID accountId, UUID offerId) {
    offers.detail(accountId, offerId);
  }
}
