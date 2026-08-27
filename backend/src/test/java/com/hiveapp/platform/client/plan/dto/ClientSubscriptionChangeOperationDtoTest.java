package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CheckoutConfirmationSource;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.shared.payment.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClientSubscriptionChangeOperationDtoTest {

    @Test
    void projectionDoesNotExposeProviderOrManualSettlementProvenance() {
        UUID checkoutId = UUID.randomUUID();
        var source = new SubscriptionChangeOperationDto(
                UUID.randomUUID(), Instant.now(), Instant.now(),
                SubscriptionChangeTiming.IMMEDIATE, SubscriptionChangeStatus.NEEDS_ATTENTION,
                Instant.now(), "FREE", "PRO", "provider refused merchant secret 42",
                new SubscriptionCheckoutDto(
                        checkoutId, SubscriptionCheckoutStatus.FAILED,
                        new BigDecimal("19.99"), "USD", PaymentStatus.FAILED,
                        "gateway-secret-reference", "provider refused merchant secret 42",
                        CheckoutConfirmationSource.MANUAL_OPERATOR, "internal-contract-42",
                        Instant.now()),
                null);

        ClientSubscriptionChangeOperationDto result =
                ClientSubscriptionChangeOperationDto.from(source);

        assertThat(result.attentionCode())
                .isEqualTo(ClientSubscriptionAttentionCode.OPERATOR_ASSISTANCE_REQUIRED);
        assertThat(result.checkout()).satisfies(checkout -> {
            assertThat(checkout.id()).isEqualTo(checkoutId);
            assertThat(checkout.status()).isEqualTo(SubscriptionCheckoutStatus.FAILED);
            assertThat(checkout.gatewayAttemptStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(checkout.amount()).isEqualByComparingTo("19.99");
        });
    }
}
