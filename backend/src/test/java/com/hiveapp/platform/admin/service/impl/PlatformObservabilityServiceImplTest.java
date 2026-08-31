package com.hiveapp.platform.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingProviderEventRepository;
import com.hiveapp.shared.email.delivery.EmailDeliveryRepository;
import com.hiveapp.shared.observability.ObservabilityProperties;
import com.hiveapp.shared.payment.BillingProperties;
import java.time.Clock;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class PlatformObservabilityServiceImplTest {

    @Test
    void logAccessNeverExposesCredentialsQuerySecretsOrFragments() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getLogs().setProvider("Loki");
        properties.getLogs().setUrl(
                "https://operator:password@logs.example.test/explore?api_key=secret#private");

        var service = new PlatformObservabilityServiceImpl(
                mock(DataSource.class),
                mock(Environment.class),
                mock(BillingProperties.class),
                List.of(),
                mock(BillingOutboxCommandRepository.class),
                mock(BillingProviderEventRepository.class),
                mock(EmailDeliveryRepository.class),
                properties,
                Clock.systemUTC());

        var result = service.logAccess();

        assertThat(result.configured()).isTrue();
        assertThat(result.provider()).isEqualTo("Loki");
        assertThat(result.destination()).isEqualTo("https://logs.example.test/explore");
        assertThat(result.destination()).doesNotContain("operator", "password", "api_key", "secret", "private");
    }
}
