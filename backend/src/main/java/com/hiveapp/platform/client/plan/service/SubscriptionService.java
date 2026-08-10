package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SubscriptionService {
    Subscription getSubscription(UUID accountId);
    ClientPlanCatalogResponse catalog(UUID accountId);
    SubscriptionChangePreviewResponse previewChange(UUID accountId, SubscriptionChangeRequest request);
    SubscriptionChangeApplyResponse applyChange(
            UUID accountId, UUID actorUserId, SubscriptionChangeRequest request);
    List<SubscriptionChangeOperationDto> listChangeOperations(UUID accountId);
    SubscriptionChangeOperationDto cancelPendingChange(UUID accountId, UUID operationId);
    Subscription createSubscription(UUID accountId, String planCode);
    Subscription createTrial(UUID accountId, String planCode, int trialDays);
    Subscription updateOverrides(UUID accountId, Set<String> addOnCodes, List<QuotaPackageSelection> quotaPackages);
}
