package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.shared.quota.QuotaOverride;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AdminSubscriptionService {
    Subscription getSubscription(UUID accountId);
    Subscription createSubscription(UUID accountId, String planCode);
    Subscription updateOverrides(UUID accountId, Set<String> featureCodes, List<QuotaOverride> quotaOverrides);
}
