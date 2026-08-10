package com.hiveapp.shared.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingStartupValidatorTest {

    @Test
    void productionCollectionCannotStartWithOnlyASimulator() {
        BillingProperties properties = new BillingProperties();
        properties.setCollectionEnabled(true);
        PaymentGateway simulator = new DevPaymentGateway(properties);

        assertThatThrownBy(() -> new BillingStartupValidator(properties, List.of(simulator)).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no trusted payment provider");
    }

    @Test
    void disabledCollectionCanStartWithoutAProvider() {
        BillingProperties properties = new BillingProperties();

        assertThatCode(() -> new BillingStartupValidator(properties, List.of()).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void enabledCollectionAcceptsAnExplicitlyTrustedProviderContract() {
        BillingProperties properties = new BillingProperties();
        properties.setCollectionEnabled(true);

        assertThatCode(() -> new BillingStartupValidator(
                properties, List.of(new TrustedGateway())).validate()).doesNotThrowAnyException();
    }

    private static final class TrustedGateway implements PaymentGateway {
        @Override
        public boolean trustedForSettlement() {
            return true;
        }

        @Override
        public PaymentResult charge(PaymentRequest request) {
            return new PaymentResult("provider-attempt", PaymentStatus.PENDING, null);
        }

        @Override
        public PaymentResult refund(String transactionId, BigDecimal amount) {
            return new PaymentResult("provider-refund", PaymentStatus.PENDING, null);
        }
    }
}
