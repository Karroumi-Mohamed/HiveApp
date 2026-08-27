package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.shared.config.JwtProperties;
import com.hiveapp.shared.exception.StaleActivationPreviewException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Signs tamper-evident, actor-bound evidence for reviewed commercial operations. The protocol is
 * operation-agnostic: the typed kind separates activation, availability, deletion, and future
 * commercial review domains without creating parallel signing implementations.
 */
@Service
@RequiredArgsConstructor
public class CommercialPreviewTokenService {

    static final Duration VALIDITY = Duration.ofMinutes(5);
    private static final String VERSION = "v1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN =
            "hiveapp:commercial-preview:v1".getBytes(StandardCharsets.UTF_8);

    private final JwtProperties jwtProperties;
    private final Clock clock;

    public IssuedEvidence issue(
            CommercialPreviewKind kind,
            UUID resourceId,
            long expectedVersion,
            UUID actorUserId,
            long catalogRevision,
            String assessmentFingerprint,
            Instant evaluatedAt
    ) {
        Instant issuedAt = evaluatedAt;
        if (issuedAt == null || issuedAt.isAfter(clock.instant().plusSeconds(30))) {
            throw new IllegalArgumentException("Commercial preview evaluation time is invalid.");
        }
        Instant expiresAt = issuedAt.plus(VALIDITY);
        String payload = payload(kind, resourceId, expectedVersion, actorUserId,
                catalogRevision, issuedAt, expiresAt, assessmentFingerprint);
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        String signature = encode(sign(encodedPayload.getBytes(StandardCharsets.US_ASCII)));
        return new IssuedEvidence(encodedPayload + "." + signature, issuedAt, expiresAt);
    }

    public void requireValid(
            String token,
            CommercialPreviewKind expectedKind,
            UUID expectedResourceId,
            long expectedVersion,
            UUID expectedActorUserId,
            long expectedCatalogRevision,
            String expectedAssessmentFingerprint
    ) {
        requireValid(token, expectedKind, expectedResourceId, expectedVersion,
                expectedActorUserId, expectedCatalogRevision, expectedAssessmentFingerprint,
                StaleActivationPreviewException::new);
    }

    /**
     * Verifies evidence while allowing each reviewed operation to expose its own stable rejection
     * contract. Activation uses {@link StaleActivationPreviewException}; future commercial
     * preview domains can reuse the signer without inheriting activation terminology.
     */
    public void requireValid(
            String token,
            CommercialPreviewKind expectedKind,
            UUID expectedResourceId,
            long expectedVersion,
            UUID expectedActorUserId,
            long expectedCatalogRevision,
            String expectedAssessmentFingerprint,
            Supplier<? extends RuntimeException> rejection
    ) {
        if (rejection == null) {
            throw new IllegalArgumentException("Commercial preview rejection is required.");
        }
        try {
            validate(token, expectedKind, expectedResourceId, expectedVersion,
                    expectedActorUserId, expectedCatalogRevision, expectedAssessmentFingerprint);
        } catch (InvalidEvidenceException exception) {
            throw rejection.get();
        }
    }

    private void validate(
            String token,
            CommercialPreviewKind expectedKind,
            UUID expectedResourceId,
            long expectedVersion,
            UUID expectedActorUserId,
            long expectedCatalogRevision,
            String expectedAssessmentFingerprint
    ) {
        if (token == null || token.isBlank()) throw invalid();
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) throw invalid();
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[1]);
            byte[] expectedSignature = sign(parts[0].getBytes(StandardCharsets.US_ASCII));
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) throw invalid();

            String[] claims = new String(Base64.getUrlDecoder().decode(parts[0]),
                    StandardCharsets.UTF_8).split("\\n", -1);
            if (claims.length != 9 || !VERSION.equals(claims[0])) throw invalid();
            CommercialPreviewKind kind = CommercialPreviewKind.valueOf(claims[1]);
            UUID resourceId = UUID.fromString(claims[2]);
            long version = Long.parseLong(claims[3]);
            UUID actorUserId = UUID.fromString(claims[4]);
            long catalogRevision = Long.parseLong(claims[5]);
            Instant issuedAt = Instant.ofEpochMilli(Long.parseLong(claims[6]));
            Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(claims[7]));
            String fingerprint = claims[8];
            Instant now = clock.instant();

            if (kind != expectedKind
                    || !resourceId.equals(expectedResourceId)
                    || version != expectedVersion
                    || !actorUserId.equals(expectedActorUserId)
                    || catalogRevision != expectedCatalogRevision
                    || !fingerprint.equals(expectedAssessmentFingerprint)
                    || expiresAt.toEpochMilli() - issuedAt.toEpochMilli() != VALIDITY.toMillis()
                    || issuedAt.isAfter(now.plusSeconds(30))
                    || !now.isBefore(expiresAt)) {
                throw invalid();
            }
        } catch (InvalidEvidenceException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private String payload(
            CommercialPreviewKind kind,
            UUID resourceId,
            long expectedVersion,
            UUID actorUserId,
            long catalogRevision,
            Instant issuedAt,
            Instant expiresAt,
            String fingerprint
    ) {
        if (kind == null || resourceId == null || actorUserId == null
                || expectedVersion < 0 || catalogRevision < 0
                || fingerprint == null || fingerprint.isBlank()
                || fingerprint.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Commercial preview evidence is incomplete.");
        }
        return String.join("\n",
                VERSION,
                kind.name(),
                resourceId.toString(),
                Long.toString(expectedVersion),
                actorUserId.toString(),
                Long.toString(catalogRevision),
                Long.toString(issuedAt.toEpochMilli()),
                Long.toString(expiresAt.toEpochMilli()),
                fingerprint);
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac derivation = Mac.getInstance(ALGORITHM);
            derivation.init(new SecretKeySpec(requireSecret(), ALGORITHM));
            byte[] derivedKey = derivation.doFinal(DOMAIN);

            Mac signer = Mac.getInstance(ALGORITHM);
            signer.init(new SecretKeySpec(derivedKey, ALGORITHM));
            return signer.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Commercial preview signing is unavailable.", exception);
        }
    }

    private byte[] requireSecret() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT signing secret is required for commercial previews.");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private InvalidEvidenceException invalid() {
        return new InvalidEvidenceException();
    }

    public record IssuedEvidence(String token, Instant evaluatedAt, Instant expiresAt) {}

    private static final class InvalidEvidenceException extends RuntimeException {
        private InvalidEvidenceException() {
            super(null, null, false, false);
        }
    }
}
