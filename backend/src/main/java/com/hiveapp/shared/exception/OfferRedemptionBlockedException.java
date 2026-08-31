package com.hiveapp.shared.exception;

/** A reviewed Offer cannot currently create or apply its subscription operation. */
public class OfferRedemptionBlockedException extends RuntimeException {

    public OfferRedemptionBlockedException() {
        super("Offer redemption is currently unavailable.");
    }
}
