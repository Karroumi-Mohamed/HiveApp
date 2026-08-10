package com.hiveapp.shared.email;

import com.hiveapp.shared.email.delivery.EmailDeliveryFailureCode;

public class EmailDeliveryException extends RuntimeException {

    private final EmailDeliveryFailureCode failureCode;

    public EmailDeliveryException(EmailDeliveryFailureCode failureCode, Throwable cause) {
        super("Email delivery failed", cause);
        this.failureCode = failureCode;
    }

    public EmailDeliveryFailureCode failureCode() {
        return failureCode;
    }
}
