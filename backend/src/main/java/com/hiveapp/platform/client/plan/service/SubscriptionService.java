package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import java.util.List;
import java.util.UUID;
import java.util.Set;

public interface SubscriptionService {
    Subscription getSubscription(UUID accountId);
    Subscription createSubscription(UUID accountId, String planCode);
    void updateOverridesWithExpansion(UUID accountId, Set<String> featureCodes);
}
