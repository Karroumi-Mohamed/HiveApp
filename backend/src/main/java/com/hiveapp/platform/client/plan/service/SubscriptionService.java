package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.shared.quota.QuotaOverride;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SubscriptionService {
    Subscription getSubscription(UUID accountId);
    ClientPlanCatalogResponse catalog(UUID accountId);
    SubscriptionChangePreviewResponse previewChange(UUID accountId, SubscriptionChangeRequest request);
    SubscriptionChangeApplyResponse applyChange(UUID accountId, SubscriptionChangeRequest request);
    Subscription createSubscription(UUID accountId, String planCode);
    Subscription updateOverrides(UUID accountId, Set<String> featureCodes, List<QuotaOverride> quotaOverrides);
}
