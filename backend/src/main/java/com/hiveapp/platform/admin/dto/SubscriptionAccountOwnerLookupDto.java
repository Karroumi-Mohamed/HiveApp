package com.hiveapp.platform.admin.dto;

/**
 * Separately authorized Account-owner identity result. Ordinary Account tables and choosers
 * deliberately omit the email address.
 */
public record SubscriptionAccountOwnerLookupDto(
        String ownerEmail,
        SubscriptionAccountOperationalListItemDto account
) {
}
