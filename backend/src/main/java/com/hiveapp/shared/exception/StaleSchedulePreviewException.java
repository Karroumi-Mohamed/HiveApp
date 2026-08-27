package com.hiveapp.shared.exception;

/** The reviewed Campaign schedule evidence no longer matches the operation being applied. */
public class StaleSchedulePreviewException extends RuntimeException {
    public StaleSchedulePreviewException() {
        super("Campaign schedule preview is stale, expired, or belongs to another operation.");
    }
}
