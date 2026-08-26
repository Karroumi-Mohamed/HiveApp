package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.dto.AdminSubscriptionDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.platform.client.plan.dto.AssignablePlanPriceDto;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;

/**
 * Operator-facing subscription contract. Every method returns a read model rather than a
 * persistence entity, so response composition and its transaction boundary stay inside the
 * service instead of being completed by the controller.
 */
public interface AdminSubscriptionService {
    Page<AccountDirectoryEntryDto> searchAccounts(String query, Pageable pageable);
    Page<AssignablePlanPriceDto> listAssignablePlanPrices(
            String search, String currencyCode, BillingCycle billingCycle, Pageable pageable);
    AdminSubscriptionDto getSubscription(UUID accountId);
    SubscriptionDto createSubscription(
            UUID accountId, String planCode, ProductPriceSelectionRequest priceSelection);
    SubscriptionDto createTrial(
            UUID accountId, String planCode, int trialDays, ProductPriceSelectionRequest priceSelection);
    SubscriptionDto updateOverrides(UUID accountId, Set<String> addOnCodes, List<QuotaPackageSelection> quotaPackages);
    List<SubscriptionChangeOperationDto> listChangeOperations(UUID accountId);
    SubscriptionCheckoutDto confirmCheckoutManually(
            UUID checkoutId, UUID actorUserId, String reference, String reason);
}
