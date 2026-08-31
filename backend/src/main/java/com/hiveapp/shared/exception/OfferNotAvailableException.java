package com.hiveapp.shared.exception;

/** Privacy-safe response for every failed customer Offer/code resolution path. */
public class OfferNotAvailableException extends RuntimeException {

    public OfferNotAvailableException() {
        super("Offer is not available for this Account.");
    }
}
