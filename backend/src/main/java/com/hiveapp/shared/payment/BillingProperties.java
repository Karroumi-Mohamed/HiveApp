package com.hiveapp.shared.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "hiveapp.billing")
public class BillingProperties {

    /** A real provider must be installed before production collection can be enabled. */
    private boolean collectionEnabled;

    private Simulator simulator = new Simulator();

    @Getter
    @Setter
    public static class Simulator {
        private PaymentStatus outcome = PaymentStatus.PENDING;
        private String failureReason = "Configured simulated payment failure";
    }
}
