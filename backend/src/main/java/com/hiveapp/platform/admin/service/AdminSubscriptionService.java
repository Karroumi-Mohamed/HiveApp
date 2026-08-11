package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.dto.AdminSubscriptionDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Operator-facing subscription contract. Every method returns a read model rather than a
 * persistence entity, so response composition and its transaction boundary stay inside the
 * service instead of being completed by the controller.
 */
public interface AdminSubscriptionService {
    AdminSubscriptionDto getSubscription(UUID accountId);
    SubscriptionDto createSubscription(UUID accountId, String planCode);
    SubscriptionDto createTrial(UUID accountId, String planCode, int trialDays);
    SubscriptionDto updateOverrides(UUID accountId, Set<String> addOnCodes, List<QuotaPackageSelection> quotaPackages);
    List<SubscriptionChangeOperationDto> listChangeOperations(UUID accountId);
    SubscriptionCheckoutDto confirmCheckoutManually(
            UUID checkoutId, UUID actorUserId, String reference, String reason);
}
