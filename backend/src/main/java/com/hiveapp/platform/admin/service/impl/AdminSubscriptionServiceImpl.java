package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.SubscriptionsFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = SubscriptionsFeature.KEY, description = "Client Subscription Management", guard = PermissionNode.Guard.ON)
public class AdminSubscriptionServiceImpl extends PlatformControlFeatureService implements AdminSubscriptionService {

    private final SubscriptionService subscriptionService;

    @Override
    protected FeatureDefinition featureDefinition() {
        return SubscriptionsFeature.definition();
    }

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
    @PermissionNode(key = "create_trial", description = "Start a trial subscription for an account")
    public Subscription createTrial(UUID accountId, String planCode, int trialDays) {
        return subscriptionService.createTrial(accountId, planCode, trialDays);
    }

    @Override
    @PermissionNode(key = "update_overrides", description = "Apply AddOn and quota package selections to subscription")
    public Subscription updateOverrides(
            UUID accountId,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages
    ) {
        return subscriptionService.updateOverrides(accountId, addOnCodes, quotaPackages);
    }
}
