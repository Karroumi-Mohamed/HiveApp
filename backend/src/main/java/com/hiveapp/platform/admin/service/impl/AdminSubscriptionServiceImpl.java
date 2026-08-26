package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.dto.AdminSubscriptionDto;
import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.account.service.AccountDirectoryService;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.platform.client.plan.dto.AssignablePlanPriceDto;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import com.hiveapp.platform.client.plan.service.SubscriptionCheckoutService;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideChoiceService;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrideChoicePage;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnOverrideChoiceDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageOverrideChoiceDto;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.money.Money;
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
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@PermissionNode(key = SubscriptionsFeature.KEY, description = "Client Subscription Management", guard = PermissionNode.Guard.ON)
public class AdminSubscriptionServiceImpl extends PlatformControlFeatureService implements AdminSubscriptionService {

    private static final Set<SubscriptionStatus> OPERATIONAL_SUBSCRIPTION_STATUSES = Set.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.TRIALING,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.SUSPENDED);

    private final SubscriptionService subscriptionService;
    private final SubscriptionCheckoutService subscriptionCheckoutService;
    private final AccountDirectoryService accountDirectoryService;
    private final AccountRepository accountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;
    private final ProductPriceRepository productPriceRepository;
    private final CommercialCatalogResolver commercialCatalogResolver;
    private final SubscriptionOverrideChoiceService subscriptionOverrideChoiceService;
    private final Clock clock;

    @Override
    @PermissionNode(key = "search_accounts", description = "Search accounts for subscription operations")
    @Transactional(readOnly = true)
    public Page<AccountDirectoryEntryDto> searchAccounts(String query, Pageable pageable) {
        return accountDirectoryService.search(query, pageable);
    }

    @Override
    @PermissionNode(key = "list_assignable_prices", description = "List exact Plan prices assignable to subscriptions")
    @Transactional(readOnly = true)
    public Page<AssignablePlanPriceDto> listAssignablePlanPrices(
            String search,
            String currencyCode,
            BillingCycle billingCycle,
            Pageable pageable
    ) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        String normalizedCurrency = normalizeOptionalCurrency(currencyCode);
        if (billingCycle != null && billingCycle != BillingCycle.MONTHLY && billingCycle != BillingCycle.YEARLY) {
            throw new InvalidRequestException(
                    "Assignable prices support MONTHLY and YEARLY billing cycles only.");
        }
        Set<UUID> eligiblePlanIds = commercialCatalogResolver.resolveCatalog(
                        CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR)
                .plans().stream()
                .filter(CommercialCatalogResolver.PlanResolution::selectable)
                .map(result -> result.plan().getId())
                .collect(java.util.stream.Collectors.toSet());
        if (eligiblePlanIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return productPriceRepository.findAssignablePlanPrices(
                        eligiblePlanIds, normalizedSearch, normalizedCurrency,
                        billingCycle, clock.instant(), pageable)
                .map(price -> new AssignablePlanPriceDto(
                        price.getPlan().getId(), price.getPlan().getCode(), price.getPlan().getName(),
                        price.getPlan().getRevisionNumber(), price.getId(), price.getAmount(),
                        price.getCurrencyCode(), price.getBillingCycle(),
                        price.getEffectiveFrom(), price.getEffectiveUntil()));
    }

    @Override
    @PermissionNode(key = "choose_add_on_overrides",
            description = "Choose bounded AddOn overrides compatible with one account subscription")
    @Transactional(readOnly = true)
    public SubscriptionOverrideChoicePage<SubscriptionAddOnOverrideChoiceDto> chooseAddOnOverrides(
            UUID accountId,
            String search,
            java.util.Collection<String> selectedAddOnCodes,
            Pageable pageable
    ) {
        return subscriptionOverrideChoiceService.chooseAddOns(
                requireOperationalSubscription(accountId), search, selectedAddOnCodes, pageable);
    }

    @Override
    @PermissionNode(key = "choose_quota_package_overrides",
            description = "Choose bounded capacity overrides compatible with one account subscription")
    @Transactional(readOnly = true)
    public SubscriptionOverrideChoicePage<SubscriptionQuotaPackageOverrideChoiceDto>
            chooseQuotaPackageOverrides(
                    UUID accountId,
                    String search,
                    String featureCode,
                    String resource,
                    java.util.Collection<String> selectedAddOnCodes,
                    Pageable pageable
            ) {
        return subscriptionOverrideChoiceService.chooseQuotaPackages(
                requireOperationalSubscription(accountId), search, featureCode, resource,
                selectedAddOnCodes, pageable);
    }

    private Subscription requireOperationalSubscription(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account", "id", accountId);
        }
        return subscriptionRepository
                .findTopByAccountIdAndStatusInOrderByCreatedAtDesc(
                        accountId, OPERATIONAL_SUBSCRIPTION_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription", "accountId", accountId));
    }

    private String normalizeOptionalCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return null;
        }
        try {
            return Money.normalizeCurrencyCode(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(exception.getMessage(), exception);
        }
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
    @PermissionNode(key = "create", description = "Manually assign an exact priced plan to account")
    public SubscriptionDto createSubscription(
            UUID accountId,
            String planCode,
            ProductPriceSelectionRequest priceSelection
    ) {
        return subscriptionMapper.toDto(
                subscriptionService.createSubscription(accountId, planCode, priceSelection));
    }

    @Override
    @Transactional
    @PermissionNode(key = "create_trial", description = "Start a trial on an exact priced plan")
    public SubscriptionDto createTrial(
            UUID accountId,
            String planCode,
            int trialDays,
            ProductPriceSelectionRequest priceSelection
    ) {
        return subscriptionMapper.toDto(
                subscriptionService.createTrial(accountId, planCode, trialDays, priceSelection));
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
