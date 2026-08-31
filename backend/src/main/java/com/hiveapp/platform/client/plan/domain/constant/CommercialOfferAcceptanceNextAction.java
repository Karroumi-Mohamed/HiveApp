package com.hiveapp.platform.client.plan.domain.constant;

/** Server-owned next step for an idempotent Offer acceptance response. */
public enum CommercialOfferAcceptanceNextAction {
  RETRY_LATER,
  TRACK_OPERATION,
  NONE
}
