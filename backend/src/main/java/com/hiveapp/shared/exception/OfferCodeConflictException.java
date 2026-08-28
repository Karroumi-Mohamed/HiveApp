package com.hiveapp.shared.exception;

/** A normalized customer-facing code is permanently reserved by another Offer lineage. */
public class OfferCodeConflictException extends RuntimeException {

    public OfferCodeConflictException() {
        super("Customer code is permanently reserved by another Offer.");
    }
}
