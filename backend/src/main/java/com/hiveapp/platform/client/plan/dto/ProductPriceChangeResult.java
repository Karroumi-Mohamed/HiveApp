package com.hiveapp.platform.client.plan.dto;

import java.time.Instant;

public record ProductPriceChangeResult(
        ProductPriceDto previousPrice,
        ProductPriceDto successorPrice,
        Instant cutoff,
        boolean existingResult) {}
