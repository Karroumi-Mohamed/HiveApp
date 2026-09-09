package com.hiveapp.shared.exception;

public class StalePriceChangePreviewException extends RuntimeException {
    public StalePriceChangePreviewException() {
        super(
                "Price change preview is stale, expired, or belongs to another operation. Review"
                    + " again.");
    }
}
