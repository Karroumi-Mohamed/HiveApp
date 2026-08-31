package com.hiveapp.shared.exception;

/** A reviewed Offer publication/redemption no longer matches current authoritative state. */
public class StaleOfferPreviewException extends RuntimeException {

    public StaleOfferPreviewException() {
        super("Offer review is stale. Refresh the preview and try again.");
    }
}
