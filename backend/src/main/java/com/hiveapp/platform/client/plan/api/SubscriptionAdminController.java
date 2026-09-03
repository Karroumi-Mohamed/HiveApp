package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.admin.dto.AdminSubscriptionDto;
import com.hiveapp.platform.admin.dto.AdminSubscriptionChangeApplyRequest;
import com.hiveapp.platform.admin.dto.AdminSubscriptionChangeCancelRequest;
import com.hiveapp.platform.admin.dto.AdminSubscriptionChangeOperationDto;
import com.hiveapp.platform.admin.dto.SubscriptionAccountOwnerLookupDto;
import com.hiveapp.platform.admin.dto.SubscriptionAccountOperationalListItemDto;
import com.hiveapp.platform.admin.dto.ManualCheckoutConfirmationRequest;
import com.hiveapp.platform.admin.dto.OwnerEmailLookupRequest;
import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;
import com.hiveapp.platform.client.plan.dto.AssignablePlanPriceDto;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrideChoicePage;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnOverrideChoiceDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageOverrideChoiceDto;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionLifecycleModels;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus;
import com.hiveapp.platform.client.plan.dto.SpecialAgreementModels;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.security.HiveAppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private static final Map<String, String> ACCOUNT_TABLE_SORTS = Map.of(
            "name", "name",
            "slug", "slug",
            "active", "isActive",
            "createdAt", "createdAt");
    private static final Map<String, String> ACCOUNT_CHOOSER_SORTS = Map.of(
            "name", "name",
            "slug", "slug");
    private static final Map<String, String> CHANGE_OPERATION_SORTS = Map.of(
            "createdAt", "createdAt",
            "effectiveAt", "effectiveAt",
            "status", "status",
            "timing", "timing");
    private static final Map<String, String> LIFECYCLE_HISTORY_SORTS = Map.of(
            "createdAt", "createdAt",
            "effectiveAt", "effectiveAt",
            "action", "action");
    private static final Map<String, String> SPECIAL_AGREEMENT_SORTS = Map.of(
            "createdAt", "createdAt",
            "startsAt", "startsAt",
            "endsAt", "endsAt",
            "status", "status",
            "amount", "agreedTermAmount");

    private final AdminSubscriptionService adminSubscriptionService;

    @GetMapping("/accounts/search")
    public PageResponse<SubscriptionAccountOperationalListItemDto> searchAccounts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean accountActive,
            @RequestParam(required = false) SubscriptionStatus subscriptionStatus,
            @RequestParam(required = false) Boolean hasSubscription,
            @RequestParam(required = false) String planCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(adminSubscriptionService.searchAccounts(
                query, accountActive, subscriptionStatus, hasSubscription, planCode,
                CommercialProductPageRequest.of(page, size, sort, direction,
                        ACCOUNT_TABLE_SORTS, "name", Sort.Direction.ASC)));
    }

    @PostMapping("/accounts/by-owner-email")
    public PageResponse<SubscriptionAccountOwnerLookupDto> findAccountsByOwnerEmail(
            @Valid @RequestBody OwnerEmailLookupRequest request,
            @RequestParam(required = false) Boolean accountActive,
            @RequestParam(required = false) SubscriptionStatus subscriptionStatus,
            @RequestParam(required = false) Boolean hasSubscription,
            @RequestParam(required = false) String planCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(adminSubscriptionService.findAccountsByOwnerEmail(
                request.ownerEmail(), accountActive, subscriptionStatus, hasSubscription, planCode,
                CommercialProductPageRequest.of(page, size, sort, direction,
                        ACCOUNT_TABLE_SORTS, "name", Sort.Direction.ASC)));
    }

    @GetMapping("/accounts/chooser")
    public PageResponse<AccountDirectoryEntryDto> chooseAccounts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(adminSubscriptionService.chooseAccounts(
                query, active,
                CommercialProductPageRequest.of(page, size, sort, direction,
                        ACCOUNT_CHOOSER_SORTS, "name", Sort.Direction.ASC)));
    }

    @GetMapping("/accounts/chooser/selected")
    public List<AccountDirectoryEntryDto> resolveAccountChoices(@RequestParam List<UUID> ids) {
        return adminSubscriptionService.resolveAccountChoices(ids);
    }

    @GetMapping("/assignable-plan-prices")
    public PageResponse<AssignablePlanPriceDto> assignablePlanPrices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) BillingCycle billingCycle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "planCode") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Page must be non-negative and size must be between 1 and 100");
        }
        String property = switch (sort) {
            case "planCode" -> "plan.code";
            case "planName" -> "plan.name";
            case "amount", "currencyCode", "billingCycle", "effectiveFrom" -> sort;
            default -> throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Unsupported assignable-price sort field: " + sort);
        };
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException exception) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Sort direction must be asc or desc.");
        }
        var pageable = PageRequest.of(
                page, size, Sort.by(sortDirection, property).and(Sort.by(Sort.Direction.ASC, "id")));
        return PageResponse.from(adminSubscriptionService.listAssignablePlanPrices(
                search, currencyCode, billingCycle, pageable));
    }

    @GetMapping("/account/{accountId}")
    public AdminSubscriptionDto get(@PathVariable UUID accountId) {
        return adminSubscriptionService.getSubscription(accountId);
    }

    @GetMapping("/account/{accountId}/change-catalog")
    public ClientPlanCatalogResponse changeCatalog(@PathVariable UUID accountId) {
        return adminSubscriptionService.changeCatalog(accountId);
    }

    @GetMapping("/account/{accountId}/override-choices/add-ons")
    public SubscriptionOverrideChoicePage<SubscriptionAddOnOverrideChoiceDto> addOnOverrideChoices(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> selectedAddOnCodes,
            @RequestParam(defaultValue = "true") boolean useCurrentAddOnSelections,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return adminSubscriptionService.chooseAddOnOverrides(
                accountId, search,
                selectedAddOnCodes != null
                        ? selectedAddOnCodes
                        : useCurrentAddOnSelections ? null : List.of(),
                overrideChoicePage(page, size, sort, direction, false));
    }

    @GetMapping("/account/{accountId}/override-choices/quota-packages")
    public SubscriptionOverrideChoicePage<SubscriptionQuotaPackageOverrideChoiceDto>
            quotaPackageOverrideChoices(
                    @PathVariable UUID accountId,
                    @RequestParam(required = false) String search,
                    @RequestParam(required = false) String featureCode,
                    @RequestParam(required = false) String resource,
                    @RequestParam(required = false) List<String> selectedAddOnCodes,
                    @RequestParam(defaultValue = "true") boolean useCurrentAddOnSelections,
                    @RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "20") int size,
                    @RequestParam(defaultValue = "name") String sort,
                    @RequestParam(defaultValue = "asc") String direction
            ) {
        return adminSubscriptionService.chooseQuotaPackageOverrides(
                accountId, search, featureCode, resource,
                selectedAddOnCodes != null
                        ? selectedAddOnCodes
                        : useCurrentAddOnSelections ? null : List.of(),
                overrideChoicePage(page, size, sort, direction, true));
    }

    @GetMapping("/account/{accountId}/changes")
    public PageResponse<AdminSubscriptionChangeOperationDto> changes(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return PageResponse.from(adminSubscriptionService.listChangeOperations(
                accountId,
                CommercialProductPageRequest.of(
                        page, size, sort, direction, CHANGE_OPERATION_SORTS,
                        "createdAt", Sort.Direction.DESC)));
    }

    @PostMapping("/account/{accountId}/changes/preview")
    public SubscriptionChangePreviewResponse previewChange(
            @PathVariable UUID accountId,
            @Valid @RequestBody SubscriptionChangeRequest request,
            Authentication authentication
    ) {
        return adminSubscriptionService.previewChange(
                accountId, actorUserId(authentication), request);
    }

    @PostMapping("/account/{accountId}/changes/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionChangeApplyResponse applyChange(
            @PathVariable UUID accountId,
            @Valid @RequestBody AdminSubscriptionChangeApplyRequest request,
            Authentication authentication
    ) {
        return adminSubscriptionService.applyChange(
                accountId, actorUserId(authentication), request);
    }

    @PostMapping("/account/{accountId}/changes/{operationId}/cancel")
    public SubscriptionChangeOperationDto cancelChange(
            @PathVariable UUID accountId,
            @PathVariable UUID operationId,
            @Valid @RequestBody AdminSubscriptionChangeCancelRequest request,
            Authentication authentication
    ) {
        return adminSubscriptionService.cancelChange(
                accountId, operationId, actorUserId(authentication), request.reason());
    }

    @PostMapping("/checkouts/{checkoutId}/confirm-manual")
    public SubscriptionCheckoutDto confirmCheckoutManually(
            @PathVariable UUID checkoutId,
            @Valid @RequestBody ManualCheckoutConfirmationRequest request,
            Authentication authentication
    ) {
        UUID actorUserId = actorUserId(authentication);
        return adminSubscriptionService.confirmCheckoutManually(
                checkoutId, actorUserId, request.reference(), request.reason());
    }

    @GetMapping("/account/{accountId}/lifecycle/actions")
    public SubscriptionLifecycleModels.Actions lifecycleActions(@PathVariable UUID accountId) {
        return adminSubscriptionService.lifecycleActions(accountId);
    }

    @PostMapping("/account/{accountId}/lifecycle/preview")
    public SubscriptionLifecycleModels.Preview previewLifecycle(
            @PathVariable UUID accountId,
            @Valid @RequestBody SubscriptionLifecycleModels.PreviewRequest request,
            Authentication authentication) {
        return adminSubscriptionService.previewLifecycle(
                accountId, actorUserId(authentication), request);
    }

    @PostMapping("/account/{accountId}/lifecycle/{action}")
    public SubscriptionLifecycleModels.Mutation applyLifecycle(
            @PathVariable UUID accountId,
            @PathVariable SubscriptionLifecycleAction action,
            @Valid @RequestBody SubscriptionLifecycleModels.ApplyRequest request,
            Authentication authentication) {
        UUID actor = actorUserId(authentication);
        return switch (action) {
            case CANCEL_AT_PERIOD_END -> adminSubscriptionService.cancelAtPeriodEnd(accountId, actor, request);
            case KEEP_RENEWING -> adminSubscriptionService.keepRenewing(accountId, actor, request);
            case CANCEL_IMMEDIATELY -> adminSubscriptionService.cancelImmediately(accountId, actor, request);
            case SUSPEND -> adminSubscriptionService.suspend(accountId, actor, request);
            case RESTORE -> adminSubscriptionService.restore(accountId, actor, request);
            case EXTEND_GRACE -> adminSubscriptionService.extendGrace(accountId, actor, request);
        };
    }

    @GetMapping("/account/{accountId}/lifecycle-history")
    public PageResponse<SubscriptionLifecycleModels.Event> lifecycleHistory(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(adminSubscriptionService.lifecycleHistory(
                accountId,
                CommercialProductPageRequest.of(
                        page, size, sort, direction, LIFECYCLE_HISTORY_SORTS,
                        "createdAt", Sort.Direction.DESC)));
    }

    @PostMapping("/account/{accountId}/agreements/preview")
    public SpecialAgreementModels.Preview previewSpecialAgreement(
            @PathVariable UUID accountId,
            @Valid @RequestBody SpecialAgreementModels.PreviewRequest request,
            Authentication authentication) {
        return adminSubscriptionService.previewSpecialAgreement(
                accountId, actorUserId(authentication), request.definition());
    }

    @PostMapping("/account/{accountId}/agreements")
    @ResponseStatus(HttpStatus.CREATED)
    public SpecialAgreementModels.Created createSpecialAgreement(
            @PathVariable UUID accountId,
            @Valid @RequestBody SpecialAgreementModels.ConfirmRequest request,
            Authentication authentication) {
        return adminSubscriptionService.createSpecialAgreement(
                accountId, actorUserId(authentication), request);
    }

    @GetMapping("/account/{accountId}/agreements")
    public PageResponse<SpecialAgreementModels.Summary> specialAgreements(
            @PathVariable UUID accountId,
            @RequestParam(required = false) SpecialAgreementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(adminSubscriptionService.listSpecialAgreements(
                accountId, status, CommercialProductPageRequest.of(
                        page, size, sort, direction, SPECIAL_AGREEMENT_SORTS,
                        "createdAt", Sort.Direction.DESC)));
    }

    @GetMapping("/account/{accountId}/agreements/{agreementId}")
    public SpecialAgreementModels.Detail specialAgreement(
            @PathVariable UUID accountId, @PathVariable UUID agreementId) {
        return adminSubscriptionService.getSpecialAgreement(accountId, agreementId);
    }

    @GetMapping("/agreements")
    public PageResponse<SpecialAgreementModels.Summary> allSpecialAgreements(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SpecialAgreementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(adminSubscriptionService.listAllSpecialAgreements(
                search, status, CommercialProductPageRequest.of(
                        page, size, sort, direction, SPECIAL_AGREEMENT_SORTS,
                        "createdAt", Sort.Direction.DESC)));
    }

    @PostMapping("/account/{accountId}/agreements/{agreementId}/cancel")
    public SpecialAgreementModels.Detail cancelSpecialAgreement(
            @PathVariable UUID accountId,
            @PathVariable UUID agreementId,
            @Valid @RequestBody SpecialAgreementModels.CancelRequest request,
            Authentication authentication) {
        return adminSubscriptionService.cancelSpecialAgreement(
                accountId, agreementId, actorUserId(authentication), request.reason());
    }

    @PostMapping("/account/{accountId}/agreements/{agreementId}/retry")
    public SpecialAgreementModels.Detail retrySpecialAgreement(
            @PathVariable UUID accountId,
            @PathVariable UUID agreementId,
            @Valid @RequestBody SpecialAgreementModels.RetryRequest request,
            Authentication authentication) {
        return adminSubscriptionService.retrySpecialAgreement(
                accountId, agreementId, actorUserId(authentication), request.reason());
    }

    @PostMapping("/account/{accountId}/agreements/{agreementId}/resolve-manual-review")
    public SpecialAgreementModels.Detail resolveSpecialAgreementManualReview(
            @PathVariable UUID accountId,
            @PathVariable UUID agreementId,
            @Valid @RequestBody SpecialAgreementModels.RetryRequest request,
            Authentication authentication) {
        return adminSubscriptionService.resolveSpecialAgreementManualReview(
                accountId, agreementId, actorUserId(authentication), request.reason());
    }

    @GetMapping("/agreements/analytics")
    public SpecialAgreementModels.Analytics specialAgreementAnalytics() {
        return adminSubscriptionService.specialAgreementAnalytics();
    }

    private UUID actorUserId(Authentication authentication) {
        return ((HiveAppUserDetails) authentication.getPrincipal()).getUserId();
    }

    private org.springframework.data.domain.Pageable overrideChoicePage(
            int page, int size, String sort, String direction, boolean quotaPackage) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Override-choice page must be non-negative and size must be between 1 and 100.");
        }
        String property = switch (sort) {
            case "code", "name" -> sort;
            case "featureCode" -> {
                if (!quotaPackage) throw new com.hiveapp.shared.exception.InvalidRequestException(
                        "Unsupported override-choice sort field: " + sort);
                yield "feature.code";
            }
            case "resource" -> {
                if (!quotaPackage) throw new com.hiveapp.shared.exception.InvalidRequestException(
                        "Unsupported override-choice sort field: " + sort);
                yield "resource";
            }
            default -> throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Unsupported override-choice sort field: " + sort);
        };
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException exception) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Sort direction must be asc or desc.");
        }
        return PageRequest.of(page, size,
                Sort.by(sortDirection, property).and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
