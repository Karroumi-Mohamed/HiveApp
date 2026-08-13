package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.dto.AdminSubscriptionDto;
import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.account.service.AccountDirectoryService;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import com.hiveapp.platform.client.plan.service.SubscriptionCheckoutService;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.SubscriptionsFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@PermissionNode(key = SubscriptionsFeature.KEY, description = "Client Subscription Management", guard = PermissionNode.Guard.ON)
public class AdminSubscriptionServiceImpl extends PlatformControlFeatureService implements AdminSubscriptionService {

    private final SubscriptionService subscriptionService;
    private final SubscriptionCheckoutService subscriptionCheckoutService;
    private final AccountDirectoryService accountDirectoryService;
    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;

    @Override
    @PermissionNode(key = "search_accounts", description = "Search accounts for subscription operations")
    @Transactional(readOnly = true)
    public Page<AccountDirectoryEntryDto> searchAccounts(String query, Pageable pageable) {
        return accountDirectoryService.search(query, pageable);
    }

    @Override
    protected FeatureDefinition featureDefinition() {
        return SubscriptionsFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "View account subscription")
    public AdminSubscriptionDto getSubscription(UUID accountId) {
        return toAdminDto(subscriptionService.getSubscription(accountId));
    }

    @Override
    @Transactional
    @PermissionNode(key = "create", description = "Manually assign a plan to account")
    public SubscriptionDto createSubscription(UUID accountId, String planCode) {
        return subscriptionMapper.toDto(subscriptionService.createSubscription(accountId, planCode));
    }

    @Override
    @Transactional
    @PermissionNode(key = "create_trial", description = "Start a trial subscription for an account")
    public SubscriptionDto createTrial(UUID accountId, String planCode, int trialDays) {
        return subscriptionMapper.toDto(subscriptionService.createTrial(accountId, planCode, trialDays));
    }

    @Override
    @Transactional
    @PermissionNode(key = "update_overrides", description = "Apply AddOn and quota package selections to subscription")
    public SubscriptionDto updateOverrides(
            UUID accountId,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages
    ) {
        return subscriptionMapper.toDto(
                subscriptionService.updateOverrides(accountId, addOnCodes, quotaPackages));
    }

    @Override
    @PermissionNode(key = "read_changes", description = "View account subscription changes and checkouts")
    public List<SubscriptionChangeOperationDto> listChangeOperations(UUID accountId) {
        return subscriptionService.listChangeOperations(accountId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "confirm_checkout", description = "Confirm a subscription checkout manually")
    public SubscriptionCheckoutDto confirmCheckoutManually(
            UUID checkoutId,
            UUID actorUserId,
            String reference,
            String reason
    ) {
        return subscriptionCheckoutService.toDto(subscriptionCheckoutService.confirmManual(
                checkoutId, actorUserId, reference, reason));
    }

    /**
     * Assembled here rather than in the controller so the account and plan relationships are
     * resolved inside this service's transaction instead of during response rendering.
     */
    private AdminSubscriptionDto toAdminDto(Subscription subscription) {
        return new AdminSubscriptionDto(
                subscription.getId(),
                subscription.getAccount().getId(),
                subscription.getAccount().getName(),
                subscription.getPlan().getCode(),
                subscription.getPlan().getName(),
                subscription.getStatus(),
                subscription.getCurrentPrice(),
                subscription.getCurrentPriceCurrencyCode(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                subscriptionOverrideReader.read(subscription.getCustomOverrides()),
                subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()).orElse(null));
    }
}
