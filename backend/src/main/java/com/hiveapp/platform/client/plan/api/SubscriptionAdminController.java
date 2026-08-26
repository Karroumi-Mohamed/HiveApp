package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.admin.dto.AdminSubscriptionDto;
import com.hiveapp.platform.admin.dto.ManualCheckoutConfirmationRequest;
import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;
import com.hiveapp.platform.client.plan.dto.AssignablePlanPriceDto;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.shared.security.HiveAppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final AdminSubscriptionService adminSubscriptionService;

    @GetMapping("/accounts/search")
    public PageResponse<AccountDirectoryEntryDto> searchAccounts(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Page must be non-negative and size must be between 1 and 100");
        }
        return PageResponse.from(adminSubscriptionService.searchAccounts(
                query, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"))));
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

    @PostMapping("/account/{accountId}")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionDto create(
            @PathVariable UUID accountId,
            @RequestParam String planCode,
            @Valid @RequestBody(required = false) ProductPriceSelectionRequest priceSelection) {
        return adminSubscriptionService.createSubscription(accountId, planCode, priceSelection);
    }

    @PostMapping("/account/{accountId}/trial")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionDto createTrial(
            @PathVariable UUID accountId,
            @RequestParam String planCode,
            @RequestParam int trialDays,
            @Valid @RequestBody(required = false) ProductPriceSelectionRequest priceSelection
    ) {
        return adminSubscriptionService.createTrial(accountId, planCode, trialDays, priceSelection);
    }

    @GetMapping("/account/{accountId}/changes")
    public List<SubscriptionChangeOperationDto> changes(@PathVariable UUID accountId) {
        return adminSubscriptionService.listChangeOperations(accountId);
    }

    @PostMapping("/checkouts/{checkoutId}/confirm-manual")
    public SubscriptionCheckoutDto confirmCheckoutManually(
            @PathVariable UUID checkoutId,
            @Valid @RequestBody ManualCheckoutConfirmationRequest request,
            Authentication authentication
    ) {
        UUID actorUserId = ((HiveAppUserDetails) authentication.getPrincipal()).getUserId();
        return adminSubscriptionService.confirmCheckoutManually(
                checkoutId, actorUserId, request.reference(), request.reason());
    }

    @PatchMapping("/account/{accountId}/overrides")
    public SubscriptionDto updateOverrides(@PathVariable UUID accountId,
                                           @Valid @RequestBody UpdateSubscriptionOverridesRequest request) {
        return adminSubscriptionService.updateOverrides(
                accountId,
                request.addOnCodes(),
                request.quotaPackages());
    }
}
