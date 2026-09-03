package com.hiveapp.shared.exception;

public class StaleSpecialAgreementPreviewException extends RuntimeException {
    public StaleSpecialAgreementPreviewException() {
        super("The special-agreement review is stale. Review the agreement again.");
    }
}
