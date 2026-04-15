package com.hiveapp.shared.quota;

/**
 * A client-specific quota limit that overrides the plan's default for one slot.
 * Stored in Subscription.custom_overrides JSONB alongside addedFeatures.
 *
 * featureCode — the AppFeature.code() that owns this slot (e.g. "platform.workspace").
 * resource    — the slot name (e.g. "members").
 * limit       — the new effective limit for this subscription. null = overridden to unlimited.
 */
public record QuotaOverride(
        String featureCode,
        String resource,
        Long limit
) {}
