package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.account.dto.AccountBillingProfileModels;
import com.hiveapp.platform.client.plan.domain.constant.BillingTimelineEntryType;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.ClientSubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.ClientSubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.ClientSubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.SpecialAgreementModels;
import com.hiveapp.platform.client.plan.dto.BillingModels;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private static final Map<String, String> CHANGE_OPERATION_SORTS = Map.of(
            "createdAt", "createdAt",
            "effectiveAt", "effectiveAt",
            "status", "status",
            "timing", "timing");
    private static final Map<String, String> INVOICE_SORTS = Map.of(
            "issuedAt", "issuedAt",
            "invoiceNumber", "invoiceNumber",
            "status", "status",
            "amount", "totalAmount");

    private final SubscriptionService subscriptionService;

    @GetMapping("/me")
    public SubscriptionDto getMySubscription() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.getMySubscription(accountId);
    }

    @GetMapping("/agreements")
    public PageResponse<SpecialAgreementModels.ClientView> agreements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return PageResponse.from(subscriptionService.listMySpecialAgreements(
                accountId,
                CommercialProductPageRequest.of(
                        page, size, "createdAt", "desc", Map.of("createdAt", "createdAt"),
                        "createdAt", Sort.Direction.DESC)));
    }

    @GetMapping("/catalog")
    public ClientPlanCatalogResponse catalog() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.catalog(accountId);
    }

    @PostMapping("/preview")
    public ClientSubscriptionChangePreviewResponse preview(
            @Valid @RequestBody SubscriptionChangeRequest request
    ) {
        var context = HiveAppContextHolder.getContext();
        return ClientSubscriptionChangePreviewResponse.from(subscriptionService.previewChange(
                context.currentAccountId(), context.actorUserId(), request));
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientSubscriptionChangeApplyResponse apply(
            @Valid @RequestBody SubscriptionChangeApplyRequest request) {
        var context = HiveAppContextHolder.getContext();
        return ClientSubscriptionChangeApplyResponse.from(subscriptionService.applyChange(
                context.currentAccountId(), context.actorUserId(), request));
    }

    @GetMapping("/changes")
    public PageResponse<ClientSubscriptionChangeOperationDto> changes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return PageResponse.from(subscriptionService.listChangeOperations(
                accountId,
                CommercialProductPageRequest.of(
                        page, size, sort, direction, CHANGE_OPERATION_SORTS,
                        "createdAt", Sort.Direction.DESC))
                .map(ClientSubscriptionChangeOperationDto::from));
    }

    @DeleteMapping("/changes/{operationId}")
    public ClientSubscriptionChangeOperationDto cancelChange(@PathVariable UUID operationId) {
        var context = HiveAppContextHolder.getContext();
        return ClientSubscriptionChangeOperationDto.from(subscriptionService.cancelPendingChange(
                context.currentAccountId(), operationId, context.actorUserId()));
    }

    @GetMapping("/invoices")
    public PageResponse<BillingModels.InvoiceRow> invoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return PageResponse.from(subscriptionService.invoiceHistory(
                accountId,
                CommercialProductPageRequest.of(
                        page, size, sort, direction, INVOICE_SORTS,
                        "issuedAt", Sort.Direction.DESC)));
    }

    @GetMapping("/invoices/{invoiceId}")
    public BillingModels.ClientInvoiceDetail invoice(@PathVariable UUID invoiceId) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.invoice(accountId, invoiceId);
    }

    @GetMapping("/invoices/{invoiceId}/document")
    public BillingModels.InvoiceDocument invoiceDocument(@PathVariable UUID invoiceId) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.invoiceDocument(accountId, invoiceId);
    }

    @GetMapping("/financial-timeline")
    public PageResponse<BillingModels.FinancialTimelineEntry> financialTimeline(
            @RequestParam(required = false) BillingTimelineEntryType type,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) Instant occurredFrom,
            @RequestParam(required = false) Instant occurredUntil,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Timeline page must be non-negative and size must be between 1 and 100.");
        }
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return PageResponse.from(subscriptionService.financialTimeline(
                accountId, type, currencyCode, occurredFrom, occurredUntil,
                org.springframework.data.domain.PageRequest.of(page, size)));
    }

    @GetMapping("/billing-profile")
    public AccountBillingProfileModels.Profile billingProfile() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.billingProfile(accountId);
    }

    @PutMapping("/billing-profile")
    public AccountBillingProfileModels.Profile updateBillingProfile(
            @Valid @RequestBody AccountBillingProfileModels.UpdateRequest request
    ) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.updateBillingProfile(accountId, request);
    }
}
