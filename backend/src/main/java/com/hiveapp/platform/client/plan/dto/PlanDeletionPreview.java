package com.hiveapp.platform.client.plan.dto;

import java.util.List;
import java.util.UUID;

public record PlanDeletionPreview(
        UUID planId,
        String planName,
        long expectedVersion,
        String previewToken,
        boolean deletable,
        int ownedFeatureCount,
        long subscriptionHistoryCount,
        long changeOperationReferenceCount,
        long addOnReferenceCount,
        long quotaPackageReferenceCount,
        long lineageReferenceCount,
        List<String> blockers
) {}
