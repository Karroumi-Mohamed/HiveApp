package com.hiveapp.platform.client.plan.dto;

import java.time.Instant;

public record ProductPriceReplacementResult(
        ProductPriceDto previousPrice,
        ProductPriceDto successorPrice,
        Instant cutoff,
        boolean existingResult
) {}
