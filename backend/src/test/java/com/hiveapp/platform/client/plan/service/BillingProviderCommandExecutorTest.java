package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.shared.payment.PaymentGateway;
import com.hiveapp.shared.payment.PaymentResult;
import com.hiveapp.shared.payment.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BillingProviderCommandExecutorTest {
    @Test
    void externalExecutionContractExplicitlyForbidsAnAmbientTransaction() throws Exception {
        Transactional boundary = BillingProviderCommandExecutor.class
                .getMethod("execute", BillingProviderCommand.class)
                .getAnnotation(Transactional.class);

        assertThat(boundary).isNotNull();
        assertThat(boundary.propagation()).isEqualTo(Propagation.NEVER);
    }

    @Test
    void forwardsStableFinancialIdempotencyKeyToGateway() {
        @SuppressWarnings("unchecked")
        ObjectProvider<PaymentGateway> providers = mock(ObjectProvider.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(providers.orderedStream()).thenReturn(Stream.of(gateway));
        when(gateway.charge(org.mockito.ArgumentMatchers.argThat(request ->
                "charge-key".equals(request.idempotencyKey()))))
                .thenReturn(new PaymentResult("provider-1", PaymentStatus.PENDING, null));
        BillingProviderCommandExecutor executor = new BillingProviderCommandExecutor(providers);

        var result = executor.execute(new BillingProviderCommand(
                UUID.randomUUID(), BillingOutboxOperation.CHARGE, UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("19.00"), "USD", null,
                "Invoice INV-1", "charge-key"));

        assertThat(result.paymentResult().transactionId()).isEqualTo("provider-1");
    }
}
