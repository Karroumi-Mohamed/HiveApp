package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.audit.domain.AuditOutcome;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductType;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;

import java.time.Instant;
import java.util.UUID;

/** Safe projection; raw audit payloads remain internal. */
public record CommercialAvailabilityHistoryEntryDto(
        UUID id,
        Instant occurredAt,
        UUID actorUserId,
        String actorEmail,
        String action,
        AuditOutcome outcome,
        String failureType,
        String reason,
        CommercialProductType productType,
        String productCode,
        PlanExtensionPolicy previousExtensionPolicy,
        PlanExtensionPolicy resultingExtensionPolicy,
        ProductSalesVisibility previousSalesVisibility,
        ProductSalesVisibility resultingSalesVisibility
) {}
