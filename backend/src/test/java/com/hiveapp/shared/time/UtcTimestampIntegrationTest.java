package com.hiveapp.shared.time;

import com.hiveapp.platform.registry.domain.entity.RegistrySyncLock;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncLockRepository;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UtcTimestampIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private RegistrySyncLockRepository lockRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @Transactional
    void instantRoundTripsWithoutChangingWhenTheJvmDefaultZoneIsNotUtc() {
        // This mutates process-wide state and must not run concurrently with other tests.
        TimeZone original = TimeZone.getDefault();
        Instant expected = Instant.parse("2026-08-10T21:22:23.123456Z");

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            RegistrySyncLock probe = new RegistrySyncLock();
            probe.setLockName("utc-probe-" + UUID.randomUUID());
            probe.setCatalogRevision(0);
            probe.setLastCompletedAt(expected);
            UUID id = lockRepository.saveAndFlush(probe).getId();
            entityManager.clear();

            assertThat(lockRepository.findById(id).orElseThrow().getLastCompletedAt())
                    .isEqualTo(expected);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void instantJsonUsesAnExplicitUtcOffsetAndRoundTripsExactly() throws Exception {
        Instant expected = Instant.parse("2026-08-10T21:22:23.123456Z");

        String json = objectMapper.writeValueAsString(new TimestampPayload(expected));

        assertThat(json).isEqualTo("{\"occurredAt\":\"2026-08-10T21:22:23.123456Z\"}");
        assertThat(objectMapper.readValue(json, TimestampPayload.class).occurredAt())
                .isEqualTo(expected);
    }

    private record TimestampPayload(Instant occurredAt) {
    }
}
