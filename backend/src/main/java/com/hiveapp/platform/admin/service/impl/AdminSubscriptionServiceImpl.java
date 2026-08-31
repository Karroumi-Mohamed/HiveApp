package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.dto.AdminSubscriptionDto;
import com.hiveapp.platform.admin.dto.AdminSubscriptionChangeApplyRequest;
import com.hiveapp.platform.admin.dto.AdminSubscriptionChangeOperationDto;
import com.hiveapp.platform.admin.dto.LatestSubscriptionSummary;
import com.hiveapp.platform.admin.dto.SubscriptionAccountOwnerLookupDto;
import com.hiveapp.platform.admin.dto.SubscriptionAccountOperationalListItemDto;
import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.service.AccountDirectoryService;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;
import com.hiveapp.platform.client.plan.dto.AssignablePlanPriceDto;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionLifecycleEventRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import com.hiveapp.platform.client.plan.service.SubscriptionCheckoutService;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeOperationProjectionMapper;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideChoiceService;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrideChoicePage;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnOverrideChoiceDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageOverrideChoiceDto;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeJobService;
import com.hiveapp.platform.client.plan.service.SubscriptionLifecycleAdminService;
import com.hiveapp.platform.client.plan.dto.SubscriptionLifecycleModels;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.SubscriptionsFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@Service
@RequiredArgsConstructor
@PermissionNode(key = SubscriptionsFeature.KEY, description = "Client Subscription Management", guard = PermissionNode.Guard.ON)
public class AdminSubscriptionServiceImpl extends PlatformControlFeatureService implements AdminSubscriptionService {

    private static final int MAX_ACCOUNT_SEARCH_LENGTH = 160;
    private static final int MAX_OWNER_EMAIL_LENGTH = 320;
    private static final int MAX_PLAN_CODE_LENGTH = 100;

    private static final Set<SubscriptionStatus> OPERATIONAL_SUBSCRIPTION_STATUSES = Set.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.TRIALING,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.SUSPENDED);

    private final SubscriptionService subscriptionService;
    private final SubscriptionCheckoutService subscriptionCheckoutService;
    private final SubscriptionChangeOperationProjectionMapper operationProjectionMapper;
    private final AccountDirectoryService accountDirectoryService;
    private final AccountRepository accountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionChangeOperationRepository subscriptionChangeOperationRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;
    private final ProductPriceRepository productPriceRepository;
    private final CommercialCatalogResolver commercialCatalogResolver;
    private final SubscriptionOverrideChoiceService subscriptionOverrideChoiceService;
    private final SubscriptionChangeJobService subscriptionChangeJobService;
    private final SubscriptionLifecycleAdminService subscriptionLifecycle;
    private final SubscriptionLifecycleEventRepository subscriptionLifecycleEvents;
    private final AdminUserRepository adminUsers;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_lifecycle_actions",
            description = "Read available subscription lifecycle actions")
    public SubscriptionLifecycleModels.Actions lifecycleActions(UUID accountId) {
        return subscriptionLifecycle.actions(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_lifecycle", description = "Preview a subscription lifecycle command")
    public SubscriptionLifecycleModels.Preview previewLifecycle(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.PreviewRequest request) {
        return subscriptionLifecycle.preview(accountId, actorUserId, request);
    }

    @Override
    @PermissionNode(key = "cancel_at_period_end", description = "Cancel subscription at period end")
    public SubscriptionLifecycleModels.Mutation cancelAtPeriodEnd(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return subscriptionLifecycle.cancelAtPeriodEnd(accountId, actorUserId, request);
    }

    @Override
    @PermissionNode(key = "keep_renewing", description = "Remove scheduled subscription cancellation")
    public SubscriptionLifecycleModels.Mutation keepRenewing(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return subscriptionLifecycle.keepRenewing(accountId, actorUserId, request);
    }

    @Override
    @PermissionNode(key = "cancel_immediately", description = "Cancel subscription immediately")
    public SubscriptionLifecycleModels.Mutation cancelImmediately(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return subscriptionLifecycle.cancelImmediately(accountId, actorUserId, request);
    }

    @Override
    @PermissionNode(key = "suspend", description = "Suspend subscription access")
    public SubscriptionLifecycleModels.Mutation suspend(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return subscriptionLifecycle.suspend(accountId, actorUserId, request);
    }

    @Override
    @PermissionNode(key = "restore", description = "Restore an operator-suspended subscription")
    public SubscriptionLifecycleModels.Mutation restore(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return subscriptionLifecycle.restore(accountId, actorUserId, request);
    }

    @Override
    @PermissionNode(key = "extend_grace", description = "Extend subscription collection grace")
    public SubscriptionLifecycleModels.Mutation extendGrace(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return subscriptionLifecycle.extendGrace(accountId, actorUserId, request);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_lifecycle_history", description = "Read subscription lifecycle history")
    public Page<SubscriptionLifecycleModels.Event> lifecycleHistory(UUID accountId, Pageable pageable) {
        subscriptionRepository.findTopByAccountIdOrderByCreatedAtDesc(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription", "accountId", accountId));
        var page = subscriptionLifecycleEvents.findAllByAccountId(accountId, pageable);
        Set<UUID> actorIds = page.getContent().stream()
                .map(com.hiveapp.platform.client.plan.domain.entity.SubscriptionLifecycleEvent::getActorUserId)
                .collect(Collectors.toSet());
        Map<UUID, String> actorEmails = actorIds.isEmpty()
                ? Map.of()
                : adminUsers.findAllWithUserByUserIdIn(actorIds).stream()
                        .collect(Collectors.toMap(
                                admin -> admin.getUser().getId(),
                                admin -> admin.getUser().getEmail()));
        return page.map(event -> new SubscriptionLifecycleModels.Event(
                event.getId(), event.getSubscription().getId(), event.getAction(),
                event.getBeforeStatus(), event.getAfterStatus(), event.getEffectiveAt(),
                event.getPreviousGraceEndsAt(), event.getNextGraceEndsAt(),
                event.getActorUserId(),
                actorEmails.getOrDefault(event.getActorUserId(), event.getActorUserId().toString()),
                event.getReason(), event.getCreatedAt()));
    }

    @Override
    @PermissionNode(key = "search_accounts", description = "Search accounts for subscription operations")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<SubscriptionAccountOperationalListItemDto> searchAccounts(
            String query,
            Boolean accountActive,
            SubscriptionStatus subscriptionStatus,
            Boolean hasSubscription,
            String planCode,
            Pageable pageable
    ) {
        return findAccountSnapshots(
                normalizeAccountSearch(query), null, accountActive, subscriptionStatus,
                hasSubscription, normalizePlanCode(planCode), pageable)
                .map(this::toAccountOperationsRow);
    }

    @Override
    @PermissionNode(key = "lookup_account_owner_email",
            description = "Find subscription Accounts by owner email")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<SubscriptionAccountOwnerLookupDto> findAccountsByOwnerEmail(
            String ownerEmail,
            Boolean accountActive,
            SubscriptionStatus subscriptionStatus,
            Boolean hasSubscription,
            String planCode,
            Pageable pageable
    ) {
        String normalizedOwnerEmail = normalizeOwnerEmail(ownerEmail);
        return findAccountSnapshots(
                null, normalizedOwnerEmail, accountActive, subscriptionStatus,
                hasSubscription, normalizePlanCode(planCode), pageable)
                .map(snapshot -> new SubscriptionAccountOwnerLookupDto(
                        snapshot.account().getOwner().getEmail(),
                        toAccountOperationsRow(snapshot)));
    }

    private Page<AccountSnapshot> findAccountSnapshots(
            String query,
            String ownerEmail,
            Boolean accountActive,
            SubscriptionStatus subscriptionStatus,
            Boolean hasSubscription,
            String planCode,
            Pageable pageable
    ) {
        if (Boolean.FALSE.equals(hasSubscription) && subscriptionStatus != null) {
            throw new InvalidRequestException(
                    "subscriptionStatus cannot be combined with hasSubscription=false.");
        }
        if (Boolean.FALSE.equals(hasSubscription) && planCode != null) {
            throw new InvalidRequestException(
                    "planCode cannot be combined with hasSubscription=false.");
        }
        Page<Account> accounts = accountRepository.findAll(
                accountOperationsSpecification(
                        query, ownerEmail, accountActive, subscriptionStatus,
                        hasSubscription, planCode),
                pageable);
        if (accounts.isEmpty()) {
            return accounts.map(account -> new AccountSnapshot(account, null));
        }
        Set<UUID> accountIds = accounts.getContent().stream()
                .map(Account::getId)
                .collect(Collectors.toSet());
        Map<UUID, Subscription> latestByAccount = subscriptionRepository.findAll(
                        latestSubscriptionSpecification(accountIds),
                        Sort.by(Sort.Direction.ASC, "account.id")
                                .and(Sort.by(Sort.Direction.ASC, "id"))).stream()
                .collect(Collectors.toMap(
                        subscription -> subscription.getAccount().getId(),
                        subscription -> subscription));
        return accounts.map(account ->
                new AccountSnapshot(account, latestByAccount.get(account.getId())));
    }

    @Override
    @PermissionNode(key = "choose_accounts",
            description = "Choose bounded accounts for subscription operations")
    @Transactional(readOnly = true)
    public Page<AccountDirectoryEntryDto> chooseAccounts(
            String query, Boolean active, Pageable pageable) {
        return accountDirectoryService.search(query, active, pageable);
    }

    @Override
    @PermissionNode(key = "resolve_account_choices",
            description = "Resolve exact selected accounts for subscription operations")
    @Transactional(readOnly = true)
    public List<AccountDirectoryEntryDto> resolveAccountChoices(Collection<UUID> ids) {
        return accountDirectoryService.resolve(ids);
    }

    private Specification<Account> accountOperationsSpecification(
            String query,
            String ownerEmail,
            Boolean accountActive,
            SubscriptionStatus subscriptionStatus,
            Boolean hasSubscription,
            String planCode
    ) {
        return (root, criteriaQuery, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (query != null) {
                String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
                var matches = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                matches.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
                matches.add(cb.like(cb.lower(root.get("slug")), pattern, '\\'));
                parseUuid(query).ifPresent(id -> matches.add(cb.equal(root.get("id"), id)));
                predicates.add(cb.or(matches.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            if (ownerEmail != null) {
                predicates.add(cb.equal(
                        cb.lower(root.get("owner").get("email")),
                        ownerEmail.toLowerCase(Locale.ROOT)));
            }
            if (accountActive != null) {
                predicates.add(cb.equal(root.get("isActive"), accountActive));
            }
            if (hasSubscription != null && subscriptionStatus == null && planCode == null) {
                var anySubscription = criteriaQuery.subquery(Integer.class);
                var subscription = anySubscription.from(Subscription.class);
                anySubscription.select(cb.literal(1))
                        .where(cb.equal(subscription.get("account"), root));
                predicates.add(hasSubscription
                        ? cb.exists(anySubscription)
                        : cb.not(cb.exists(anySubscription)));
            }
            if (subscriptionStatus != null || planCode != null) {
                var matchingLatest = criteriaQuery.subquery(Integer.class);
                var candidate = matchingLatest.from(Subscription.class);
                var newer = matchingLatest.subquery(Integer.class);
                var newerSubscription = newer.from(Subscription.class);
                newer.select(cb.literal(1)).where(
                        cb.equal(newerSubscription.get("account"), root),
                        newerThan(cb, newerSubscription, candidate));
                var latestPredicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                latestPredicates.add(cb.equal(candidate.get("account"), root));
                latestPredicates.add(cb.not(cb.exists(newer)));
                if (subscriptionStatus != null) {
                    latestPredicates.add(cb.equal(candidate.get("status"), subscriptionStatus));
                }
                if (planCode != null) {
                    latestPredicates.add(cb.equal(candidate.get("plan").get("code"), planCode));
                }
                matchingLatest.select(cb.literal(1)).where(
                        latestPredicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                predicates.add(cb.exists(matchingLatest));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Specification<Subscription> latestSubscriptionSpecification(Set<UUID> accountIds) {
        return (candidate, criteriaQuery, cb) -> {
            var newer = criteriaQuery.subquery(Integer.class);
            var newerSubscription = newer.from(Subscription.class);
            newer.select(cb.literal(1)).where(
                    cb.equal(newerSubscription.get("account"), candidate.get("account")),
                    newerThan(cb, newerSubscription, candidate));
            return cb.and(
                    candidate.get("account").get("id").in(accountIds),
                    cb.not(cb.exists(newer)));
        };
    }

    private jakarta.persistence.criteria.Predicate newerThan(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<Subscription> newer,
            jakarta.persistence.criteria.Root<Subscription> candidate
    ) {
        var newerCreatedAt = newer.<Instant>get("createdAt");
        var candidateCreatedAt = candidate.<Instant>get("createdAt");
        return cb.or(
                cb.greaterThan(newerCreatedAt, candidateCreatedAt),
                cb.and(
                        cb.equal(newerCreatedAt, candidateCreatedAt),
                        cb.greaterThan(newer.<UUID>get("id"), candidate.<UUID>get("id"))));
    }

    private SubscriptionAccountOperationalListItemDto toAccountOperationsRow(AccountSnapshot snapshot) {
        Account account = snapshot.account();
        Subscription latest = snapshot.latest();
        return new SubscriptionAccountOperationalListItemDto(
                account.getId(), account.getName(), account.getSlug(),
                account.isActive(), account.getCreatedAt(),
                latest == null ? null : toLatestSubscriptionSummary(latest));
    }

    private LatestSubscriptionSummary toLatestSubscriptionSummary(Subscription subscription) {
        var snapshot = subscription.getEntitlementSnapshot();
        var plan = subscription.getPlan();
        return new LatestSubscriptionSummary(
                subscription.getId(), subscription.getStatus(), plan.getId(), plan.getCode(),
                plan.getName(), plan.getRevisionNumber(), snapshot.billingCycle(),
                subscription.getCurrentPeriodEnd(), subscription.isCancelAtPeriodEnd(),
                subscription.getCurrentPrice(), subscription.getCurrentPriceCurrencyCode());
    }

    private String normalizeAccountSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_ACCOUNT_SEARCH_LENGTH) {
            throw new InvalidRequestException(
                    "Account search must not exceed " + MAX_ACCOUNT_SEARCH_LENGTH + " characters.");
        }
        return normalized;
    }

    private String normalizeOwnerEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Owner email is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_OWNER_EMAIL_LENGTH) {
            throw new InvalidRequestException(
                    "Owner email must not exceed " + MAX_OWNER_EMAIL_LENGTH + " characters.");
        }
        return normalized;
    }

    private String normalizePlanCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_PLAN_CODE_LENGTH
                || !normalized.matches("^[A-Z][A-Z0-9_]*$")) {
            throw new InvalidRequestException(
                    "planCode must use uppercase letters, numbers, and underscores.");
        }
        return normalized;
    }

    private java.util.Optional<UUID> parseUuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record AccountSnapshot(Account account, Subscription latest) {}

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
        Set<String> staticallyEligibleFeatureCodes =
                commercialCatalogResolver.staticallyEligiblePlanFeatureCodes(
                        CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR);
        if (staticallyEligibleFeatureCodes.isEmpty()) {
            staticallyEligibleFeatureCodes = Set.of("__NO_ELIGIBLE_FEATURE__");
        }
        return productPriceRepository.findAssignablePlanPrices(
                        staticallyEligibleFeatureCodes, normalizedSearch, normalizedCurrency,
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
    @Transactional(readOnly = true)
    @PermissionNode(key = "choose_change_options",
            description = "Choose operator-authorized subscription change options")
    public ClientPlanCatalogResponse changeCatalog(UUID accountId) {
        return subscriptionService.catalogAsOperator(accountId);
    }

    @Override
    @PermissionNode(key = "read_changes", description = "View account subscription changes and checkouts")
    @Transactional(readOnly = true)
    public Page<AdminSubscriptionChangeOperationDto> listChangeOperations(
            UUID accountId,
            Pageable pageable
    ) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account", "id", accountId);
        }
        return subscriptionChangeOperationRepository.findAllByAccountId(accountId, pageable)
                .map(operationProjectionMapper::admin);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_change", description = "Preview an Account subscription change")
    public SubscriptionChangePreviewResponse previewChange(
            UUID accountId,
            UUID actorUserId,
            SubscriptionChangeRequest request
    ) {
        return subscriptionService.previewChangeAsOperator(accountId, actorUserId, request);
    }

    @Override
    @Transactional
    @PermissionNode(key = "apply_change", description = "Apply a reviewed Account subscription change")
    public SubscriptionChangeApplyResponse applyChange(
            UUID accountId,
            UUID actorUserId,
            AdminSubscriptionChangeApplyRequest request
    ) {
        return subscriptionService.applyChangeAsOperator(
                accountId, actorUserId, request.reviewedSelection(),
                requireOperatorReason(request.reason()));
    }

    @Override
    @Transactional
    @PermissionNode(key = "cancel_change", description = "Cancel an outstanding Account subscription change")
    public SubscriptionChangeOperationDto cancelChange(
            UUID accountId,
            UUID operationId,
            UUID actorUserId,
            String reason
    ) {
        return subscriptionService.cancelPendingChangeAsOperator(
                accountId, operationId, actorUserId, requireOperatorReason(reason));
    }

    private String requireOperatorReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("Operator reason is required.");
        }
        String normalized = reason.trim();
        if (normalized.length() > 2000) {
            throw new InvalidRequestException("Operator reason must not exceed 2000 characters.");
        }
        return normalized;
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

    @Override
    @PermissionNode(key = "preview_change_job",
            description = "Preview a subscription population change job")
    public SubscriptionChangeJobModels.Preview previewChangeJob(
            UUID actorUserId,
            SubscriptionChangeJobModels.PreviewRequest request
    ) {
        return subscriptionChangeJobService.preview(actorUserId, request);
    }

    @Override
    @PermissionNode(key = "confirm_change_job",
            description = "Confirm a reviewed subscription population change job")
    public SubscriptionChangeJobModels.Detail confirmChangeJob(
            UUID jobId,
            UUID actorUserId,
            SubscriptionChangeJobModels.ConfirmRequest request
    ) {
        return subscriptionChangeJobService.confirm(jobId, actorUserId, request);
    }

    @Override
    @PermissionNode(key = "list_change_jobs",
            description = "List subscription population jobs")
    public Page<SubscriptionChangeJobModels.Summary> listChangeJobs(
            SubscriptionChangeJobStatus status,
            Pageable pageable
    ) {
        return subscriptionChangeJobService.list(status, pageable);
    }

    @Override
    @PermissionNode(key = "read_change_job",
            description = "Read a subscription population job")
    public SubscriptionChangeJobModels.Detail getChangeJob(UUID jobId) {
        return subscriptionChangeJobService.get(jobId);
    }

    @Override
    @PermissionNode(key = "read_change_job_results",
            description = "Read subscription job results")
    public Page<SubscriptionChangeJobModels.Item> listChangeJobResults(
            UUID jobId,
            SubscriptionChangeJobItemStatus status,
            Pageable pageable
    ) {
        return subscriptionChangeJobService.results(jobId, status, pageable);
    }

    @Override
    @PermissionNode(key = "read_change_job_result_identities",
            description = "Reveal Account identities in subscription job results")
    public List<SubscriptionChangeJobModels.Identity> resolveChangeJobResultIdentities(
            UUID jobId,
            Collection<UUID> resultIds
    ) {
        return subscriptionChangeJobService.resolveIdentities(jobId, resultIds);
    }

    @Override
    @PermissionNode(key = "cancel_change_job",
            description = "Cancel an unstarted subscription population job")
    public SubscriptionChangeJobModels.Detail cancelChangeJob(
            UUID jobId,
            UUID actorUserId,
            SubscriptionChangeJobModels.CancelRequest request
    ) {
        return subscriptionChangeJobService.cancel(jobId, actorUserId, request);
    }

    @Override
    @PermissionNode(key = "retry_change_job",
            description = "Retry failed subscription job results")
    public SubscriptionChangeJobModels.Detail retryChangeJob(
            UUID jobId,
            UUID actorUserId,
            SubscriptionChangeJobModels.RetryRequest request
    ) {
        return subscriptionChangeJobService.retry(jobId, actorUserId, request);
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
                subscription.getPastDueAt(),
                subscription.getGraceEndsAt(),
                subscription.getSuspendedAt(),
                subscription.getSuspensionCause(),
                subscription.getSuspensionReason(),
                com.hiveapp.platform.client.plan.service.SubscriptionLifecycleRules.availableActions(
                        subscription, clock.instant()),
                subscriptionOverrideReader.read(subscription.getCustomOverrides()),
                subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()).orElse(null));
    }
}
