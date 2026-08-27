package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.shared.config.JwtProperties;
import com.hiveapp.shared.exception.StaleActivationPreviewException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommercialPreviewTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

    private JwtProperties properties;
    private CommercialPreviewTokenService service;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("test-only-preview-secret-long-enough-for-hmac-derivation");
        service = at(NOW);
    }

    @Test
    void signedEvidenceBindsEveryCommercialClaim() {
        UUID resourceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var evidence = service.issue(
                CommercialPreviewKind.ADD_ON_ACTIVATION,
                resourceId, 7, actorId, 41, "abc123", NOW);

        assertThat(evidence.evaluatedAt()).isEqualTo(NOW);
        assertThat(evidence.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(evidence.token()).contains(".");
        assertThatCode(() -> service.requireValid(
                evidence.token(), CommercialPreviewKind.ADD_ON_ACTIVATION,
                resourceId, 7, actorId, 41, "abc123"))
                .doesNotThrowAnyException();

        assertStale(evidence.token(), CommercialPreviewKind.PLAN_ACTIVATION,
                resourceId, 7, actorId, 41, "abc123");
        assertStale(evidence.token(), CommercialPreviewKind.ADD_ON_ACTIVATION,
                UUID.randomUUID(), 7, actorId, 41, "abc123");
        assertStale(evidence.token(), CommercialPreviewKind.ADD_ON_ACTIVATION,
                resourceId, 8, actorId, 41, "abc123");
        assertStale(evidence.token(), CommercialPreviewKind.ADD_ON_ACTIVATION,
                resourceId, 7, UUID.randomUUID(), 41, "abc123");
        assertStale(evidence.token(), CommercialPreviewKind.ADD_ON_ACTIVATION,
                resourceId, 7, actorId, 42, "abc123");
        assertStale(evidence.token(), CommercialPreviewKind.ADD_ON_ACTIVATION,
                resourceId, 7, actorId, 41, "changed");
    }

    @Test
    void expiryTamperingAndMalformedTokensFailClosed() {
        UUID resourceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String token = service.issue(
                CommercialPreviewKind.QUOTA_PACKAGE_ACTIVATION,
                resourceId, 2, actorId, 3, "fingerprint", NOW).token();

        CommercialPreviewTokenService expired = at(NOW.plusSeconds(300));
        assertThatThrownBy(() -> expired.requireValid(
                token, CommercialPreviewKind.QUOTA_PACKAGE_ACTIVATION,
                resourceId, 2, actorId, 3, "fingerprint"))
                .isInstanceOf(StaleActivationPreviewException.class);

        int signatureOffset = token.indexOf('.') + 5;
        char replacement = token.charAt(signatureOffset) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureOffset)
                + replacement + token.substring(signatureOffset + 1);
        assertStale(tampered, CommercialPreviewKind.QUOTA_PACKAGE_ACTIVATION,
                resourceId, 2, actorId, 3, "fingerprint");
        assertStale("not-a-token", CommercialPreviewKind.QUOTA_PACKAGE_ACTIVATION,
                resourceId, 2, actorId, 3, "fingerprint");
        assertStale("", CommercialPreviewKind.QUOTA_PACKAGE_ACTIVATION,
                resourceId, 2, actorId, 3, "fingerprint");
    }

    @Test
    void reusableVerifierUsesTheCallingPreviewDomainsRejectionContract() {
        UUID resourceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String token = service.issue(
                CommercialPreviewKind.PLAN_ACTIVATION,
                resourceId, 2, actorId, 3, "fingerprint", NOW).token();

        assertThatThrownBy(() -> service.requireValid(
                token, CommercialPreviewKind.PLAN_ACTIVATION,
                resourceId, 2, actorId, 4, "fingerprint",
                () -> new IllegalStateException("domain-specific stale evidence")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("domain-specific stale evidence");
    }

    private CommercialPreviewTokenService at(Instant instant) {
        return new CommercialPreviewTokenService(
                properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private void assertStale(
            String token,
            CommercialPreviewKind kind,
            UUID resourceId,
            long version,
            UUID actorId,
            long catalogRevision,
            String fingerprint
    ) {
        assertThatThrownBy(() -> service.requireValid(
                token, kind, resourceId, version, actorId, catalogRevision, fingerprint))
                .isInstanceOf(StaleActivationPreviewException.class)
                .hasMessageContaining("fresh preview");
    }
}
