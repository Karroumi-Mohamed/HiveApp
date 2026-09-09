package com.hiveapp.platform.client.plan.dto;

import java.time.Instant;
import java.util.List;

public record ProductPriceChangePreview(
        ProductPriceChangeRequest change,
        ProductPriceDto currentPrice,
        ProductPriceDto scheduledPrice,
        Instant evaluatedAt,
        Instant cutoff,
        Instant expiresAt,
        String previewToken,
        long blockingOfferCount,
        List<String> blockers,
        boolean allowed) {}
