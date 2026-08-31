package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.dto.ProductActivationPriceDto;
import com.hiveapp.shared.quota.QuotaLimitEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Deterministic serialization shared by the product-specific activation assessors. */
final class ActivationAssessmentFingerprint {

    private ActivationAssessmentFingerprint() {}

    static void appendQuotaEntries(StringBuilder state, List<QuotaLimitEntry> entries) {
        List<QuotaLimitEntry> sorted = entries == null ? List.of()
                : entries.stream()
                        .sorted(Comparator.comparing(QuotaLimitEntry::resource)
                                .thenComparing(entry -> entry.mode().name())
                                .thenComparing(entry -> entry.limit() == null
                                        ? Long.MIN_VALUE : entry.limit()))
                        .toList();
        for (QuotaLimitEntry entry : sorted) {
            append(state, entry.resource());
            append(state, entry.mode());
            append(state, entry.limit());
        }
    }

    static void appendActivationPrices(
            StringBuilder state,
            List<ProductPrice> prices,
            Instant evaluatedAt
    ) {
        for (ProductPrice price : prices.stream()
                .sorted(Comparator.comparing(ProductPrice::getId)).toList()) {
            append(state, "activation-price");
            append(state, price.getId());
            append(state, price.getVersion());
            append(state, price.getStatus());
            append(state, price.getAmount().toPlainString());
            append(state, price.getCurrencyCode());
            append(state, price.getBillingCycle());
            append(state, price.getEffectiveFrom());
            append(state, price.getEffectiveUntil());
            append(state, price.getLineageId());
            append(state, price.getRevisionNumber());
            append(state, price.isCompatibilityDefault());
            append(state, priceWindowState(price, evaluatedAt));
        }
    }

    static void appendValues(StringBuilder state, String label, Collection<?> values) {
        append(state, label);
        append(state, values.size());
        values.forEach(value -> append(state, value));
    }

    static void append(StringBuilder state, Object value) {
        String encoded = value == null ? "<null>" : value.toString();
        state.append(encoded.length()).append(':').append(encoded).append(';');
    }

    static String priceWindowState(ProductPrice price, Instant evaluatedAt) {
        if (price.getEffectiveFrom().isAfter(evaluatedAt)) return "FUTURE";
        if (price.getEffectiveUntil() != null
                && !price.getEffectiveUntil().isAfter(evaluatedAt)) {
            return "EXPIRED";
        }
        return "CURRENT";
    }

    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static ProductActivationPriceDto toDto(ProductPrice price) {
        return new ProductActivationPriceDto(
                price.getId(), price.getAmount(), price.getCurrencyCode(), price.getBillingCycle(),
                price.getStatus(), price.getEffectiveFrom(), price.getEffectiveUntil(),
                price.getLineageId(), price.getRevisionNumber(), price.getVersion(),
                price.isCompatibilityDefault());
    }
}
