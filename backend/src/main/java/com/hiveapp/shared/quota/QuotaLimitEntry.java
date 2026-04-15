package com.hiveapp.shared.quota;

/**
 * One limit value for a quota slot, stored per plan in PlanFeature.quota_configs JSONB.
 * resource must match a resource name declared in the Feature's QuotaSlot list.
 * null limit = explicitly unlimited (admin chose no cap for this plan tier).
 */
public record QuotaLimitEntry(
        String resource,
        Long limit
) {}
