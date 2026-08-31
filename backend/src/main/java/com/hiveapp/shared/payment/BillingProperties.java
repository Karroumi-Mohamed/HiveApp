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

    /** Identity frozen onto newly issued commercial billing documents. */
    private Issuer issuer = new Issuer();

    @Getter
    @Setter
    public static class Simulator {
        private PaymentStatus outcome = PaymentStatus.PENDING;
        private String failureReason = "Configured simulated payment failure";
    }

    @Getter
    @Setter
    public static class Issuer {
        private String name = "HiveApp";
        private String address;
        private String countryCode;
        private String taxId;
    }
}
