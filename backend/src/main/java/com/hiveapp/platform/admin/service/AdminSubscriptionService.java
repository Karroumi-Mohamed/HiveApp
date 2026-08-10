package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AdminSubscriptionService {
    Subscription getSubscription(UUID accountId);
    Subscription createSubscription(UUID accountId, String planCode);
    Subscription createTrial(UUID accountId, String planCode, int trialDays);
    Subscription updateOverrides(UUID accountId, Set<String> addOnCodes, List<QuotaPackageSelection> quotaPackages);
    List<SubscriptionChangeOperationDto> listChangeOperations(UUID accountId);
    SubscriptionCheckoutDto confirmCheckoutManually(
            UUID checkoutId, UUID actorUserId, String reference, String reason);
}
