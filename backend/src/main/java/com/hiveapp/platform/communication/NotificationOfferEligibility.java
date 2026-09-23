package com.hiveapp.platform.communication;

import com.hiveapp.platform.client.plan.service.CommercialOfferService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Non-throwing domain availability keeps inbox and email checks on the caller's connection. */
@Service
@RequiredArgsConstructor
public class NotificationOfferEligibility {
  private final CommercialOfferService offers;

  public boolean available(UUID accountId, UUID offerId) {
    return offers.isCatalogueAvailable(accountId, offerId);
  }
}
