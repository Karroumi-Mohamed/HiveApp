package com.hiveapp.shared.payment;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BillingStartupValidator {

    private final BillingProperties billingProperties;
    private final List<PaymentGateway> paymentGateways;

    @PostConstruct
    public void validate() {
        if (billingProperties.isCollectionEnabled()
                && paymentGateways.stream().noneMatch(PaymentGateway::trustedForSettlement)) {
            throw new IllegalStateException(
                    "Billing collection is enabled but no trusted payment provider is configured");
        }
    }
}
