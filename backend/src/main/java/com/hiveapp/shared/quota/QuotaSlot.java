package com.hiveapp.shared.quota;

/**
 * Compile-time declaration of one measurable resource on a Feature.
 * Stored in Feature.quota_schema JSONB — represents the SHAPE, not the limit value.
 * The limit value lives in PlanFeature.quota_configs.
 */
public record QuotaSlot(
        String resource, // what we are measuring: "members", "companies", "documents"
        QuotaType type,  // how we measure it
        String unit      // display label only: "persons", "MB" — no effect on enforcement
) {
    public static QuotaSlot count(String resource, String unit) {
        return new QuotaSlot(resource, QuotaType.COUNT, unit);
    }

    public static QuotaSlot storage(String resource, String unit) {
        return new QuotaSlot(resource, QuotaType.STORAGE, unit);
    }

    public static QuotaSlot rate(String resource, String unit) {
        return new QuotaSlot(resource, QuotaType.RATE, unit);
    }
}
