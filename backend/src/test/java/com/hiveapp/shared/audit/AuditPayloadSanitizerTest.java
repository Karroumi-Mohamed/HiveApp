package com.hiveapp.shared.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPayloadSanitizerTest {
    @Test
    void communicationContentIsNotCopiedIntoAuditPayloads() {
        assertThat(sanitizer.value(Map.of("messageTitle", "Private title", "messageBody", "Private notice", "nested", Map.of("replyBody", "Private reply"), "kind", "MESSAGE")))
                .contains("MESSAGE", AuditPayloadSanitizer.REDACTED)
                .doesNotContain("Private title", "Private notice", "Private reply");
    }

    private final AuditPayloadSanitizer sanitizer =
            new AuditPayloadSanitizer(new ObjectMapper());

    @Test
    void offerCodesAndIdempotencyKeysAreRedactedAtEveryPayloadDepth() {
        String payload = sanitizer.value(Map.of(
                "redemptionCode", "NEVER-LOG-ME",
                "nested", Map.of(
                        "customer_code", "NOR-ME",
                        "idempotencyKey", "OR-ME"),
                "safeCode", "INTERNAL-OFFER-IDENTITY"));

        assertThat(payload)
                .contains("safeCode", "INTERNAL-OFFER-IDENTITY")
                .contains(AuditPayloadSanitizer.REDACTED)
                .doesNotContain("NEVER-LOG-ME", "NOR-ME", "OR-ME");
    }
}
