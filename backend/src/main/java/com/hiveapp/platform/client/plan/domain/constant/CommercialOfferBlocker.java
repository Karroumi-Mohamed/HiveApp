package com.hiveapp.platform.client.plan.domain.constant;

/** Definition-free reasons why an otherwise authorized Offer operation cannot run. */
public enum CommercialOfferBlocker {
  NOT_DRAFT,
  NOT_PUBLISHED,
  NOT_RETIRED,
  NOT_LATEST_REVISION,
  DRAFT_SUCCESSOR_EXISTS,
  PUBLISHED_SUCCESSOR_EXISTS,
  HAS_DERIVED_OFFERS,
  CAMPAIGN_NOT_LIVE,
  WINDOW_ENDED,
  WINDOW_OUTSIDE_CAMPAIGN,
  CODE_REQUIRED,
  INVALID_SELECTION
}
