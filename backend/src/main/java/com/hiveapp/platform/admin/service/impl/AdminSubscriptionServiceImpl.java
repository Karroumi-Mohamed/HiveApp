package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.shared.quota.QuotaOverride;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = "subscriptions", description = "Client Subscription Management")
public class AdminSubscriptionServiceImpl implements AdminSubscriptionService {

    private final SubscriptionService subscriptionService;

    @Override
    @PermissionNode(key = "read", description = "View account subscription")
    public Subscription getSubscription(UUID accountId) {
        return subscriptionService.getSubscription(accountId);
    }

    @Override
    @PermissionNode(key = "create", description = "Manually assign a plan to account")
    public Subscription createSubscription(UUID accountId, String planCode) {
        return subscriptionService.createSubscription(accountId, planCode);
    }

    @Override
    @PermissionNode(key = "update_overrides", description = "Apply custom feature/quota overrides to subscription")
    public Subscription updateOverrides(UUID accountId, Set<String> featureCodes, List<QuotaOverride> quotaOverrides) {
        return subscriptionService.updateOverrides(accountId, featureCodes, quotaOverrides);
    }
}
