package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.account.dto.AccountBillingProfileModels;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingProviderEventStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingTimelineEntryType;
import com.hiveapp.platform.client.plan.dto.BillingModels;
import com.hiveapp.platform.client.plan.service.BillingAdminService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.security.HiveAppUserDetails;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/billing")
@RequiredArgsConstructor
public class BillingAdminController {
    private static final Map<String, String> INVOICE_SORTS = Map.of(
            "issuedAt", "issuedAt",
            "invoiceNumber", "invoiceNumber",
            "status", "status",
            "amount", "totalAmount",
            "currency", "currencyCode",
            "cycle", "billingCycle");
    private static final Map<String, String> RECONCILIATION_SORTS = Map.of(
            "createdAt", "createdAt",
            "nextAttemptAt", "nextAttemptAt",
            "status", "status",
            "operation", "operation",
            "attemptCount", "attemptCount");
    private static final Map<String, String> PROVIDER_EVENT_SORTS = Map.of(
            "receivedAt", "createdAt",
            "occurredAt", "occurredAt",
            "status", "processingStatus",
            "operation", "operation",
            "provider", "provider");

    private final BillingAdminService billing;

    @GetMapping("/invoices")
    public PageResponse<BillingModels.InvoiceRow> invoices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) BillingInvoiceStatus status,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) BillingCycle billingCycle,
            @RequestParam(required = false) Instant issuedFrom,
            @RequestParam(required = false) Instant issuedUntil,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return PageResponse.from(billing.listInvoices(
                search, accountId, status, currencyCode, billingCycle, issuedFrom, issuedUntil,
                CommercialProductPageRequest.of(
                        page, size, sort, direction, INVOICE_SORTS,
                        "issuedAt", Sort.Direction.DESC)));
    }

    @GetMapping("/invoices/{invoiceId}")
    public BillingModels.InvoiceDetail invoice(@PathVariable UUID invoiceId) {
        return billing.invoice(invoiceId);
    }

    @GetMapping("/invoices/{invoiceId}/document")
    public BillingModels.InvoiceDocument invoiceDocument(@PathVariable UUID invoiceId) {
        return billing.invoiceDocument(invoiceId);
    }

    @GetMapping("/accounts/{accountId}/timeline")
    public PageResponse<BillingModels.FinancialTimelineEntry> financialTimeline(
            @PathVariable UUID accountId,
            @RequestParam(required = false) BillingTimelineEntryType type,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) Instant occurredFrom,
            @RequestParam(required = false) Instant occurredUntil,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(billing.financialTimeline(
                accountId, type, currencyCode, occurredFrom, occurredUntil,
                timelinePage(page, size)));
    }

    @GetMapping("/accounts/{accountId}/profile")
    public AccountBillingProfileModels.Profile billingProfile(@PathVariable UUID accountId) {
        return billing.billingProfile(accountId);
    }

    @PutMapping("/accounts/{accountId}/profile")
    public AccountBillingProfileModels.Profile updateBillingProfile(
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountBillingProfileModels.UpdateRequest request
    ) {
        return billing.updateBillingProfile(accountId, request);
    }

    @GetMapping("/invoices/{invoiceId}/account-identity")
    public BillingModels.AccountIdentity accountIdentity(@PathVariable UUID invoiceId) {
        return billing.accountIdentity(invoiceId);
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public List<BillingModels.Payment> payments(@PathVariable UUID invoiceId) {
        return billing.payments(invoiceId);
    }

    @GetMapping("/payments/{paymentId}/reference")
    public BillingModels.Payment paymentReference(@PathVariable UUID paymentId) {
        return billing.paymentReference(paymentId);
    }

    @PostMapping("/invoices/{invoiceId}/manual-settlement")
    public BillingModels.InvoiceDetail settleManually(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody BillingModels.ManualSettlementRequest request,
            Authentication authentication
    ) {
        return billing.settleManually(
                invoiceId, actor(authentication), request.reference(), request.reason());
    }

    @PostMapping("/invoices/{invoiceId}/credits")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingModels.Credit issueCredit(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody BillingModels.CreditRequest request,
            Authentication authentication
    ) {
        return billing.issueCredit(
                invoiceId, request.amount(), request.currencyCode(), request.reason(),
                request.source(), actor(authentication), request.externalReference());
    }

    @GetMapping("/payments/{paymentId}/refund-preview")
    public BillingModels.RefundPreview previewRefund(@PathVariable UUID paymentId) {
        return billing.previewRefund(paymentId);
    }

    @PostMapping("/payments/{paymentId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingModels.Refund requestRefund(
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BillingModels.RefundRequest request,
            Authentication authentication
    ) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        return billing.requestRefund(
                paymentId, request.amount(), request.currencyCode(), request.reason(),
                actor(authentication), normalizedKey);
    }

    @PostMapping("/payments/{paymentId}/manual-refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingModels.Refund recordManualRefund(
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BillingModels.ManualRefundRequest request,
            Authentication authentication
    ) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        return billing.recordManualRefund(
                paymentId, request.amount(), request.currencyCode(), request.reason(),
                request.externalReference(), actor(authentication), normalizedKey);
    }

    @GetMapping("/reconciliation")
    public PageResponse<BillingModels.OutboxRow> reconciliation(
            @RequestParam(required = false) BillingOutboxStatus status,
            @RequestParam(required = false) BillingOutboxOperation operation,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return PageResponse.from(billing.reconciliation(
                status, operation,
                CommercialProductPageRequest.of(
                        page, size, sort, direction, RECONCILIATION_SORTS,
                        "createdAt", Sort.Direction.DESC)));
    }

    @GetMapping("/provider-events")
    public PageResponse<BillingModels.ProviderEventRow> providerEvents(
            @RequestParam(required = false) BillingProviderEventStatus status,
            @RequestParam(required = false) BillingOutboxOperation operation,
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return PageResponse.from(billing.providerEvents(
                status, operation, provider,
                CommercialProductPageRequest.of(
                        page, size, sort, direction, PROVIDER_EVENT_SORTS,
                        "receivedAt", Sort.Direction.DESC)));
    }

    @PostMapping("/provider-events/{eventId}/reprocess")
    public BillingModels.ProviderEventRow reprocessProviderEvent(@PathVariable UUID eventId) {
        return billing.reprocessProviderEvent(eventId);
    }

    @GetMapping("/invoices/{invoiceId}/charge-retry-preview")
    public BillingModels.ChargeRetryPreview previewChargeRetry(@PathVariable UUID invoiceId) {
        return billing.previewChargeRetry(invoiceId);
    }

    @PostMapping("/invoices/{invoiceId}/charge-retries")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingModels.Payment retryCharge(
            @PathVariable UUID invoiceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BillingModels.ChargeRetryRequest request,
            Authentication authentication
    ) {
        return billing.retryCharge(
                invoiceId, actor(authentication), requireIdempotencyKey(idempotencyKey),
                request.reason(), request.recoveryReference(),
                request.providerConfirmedNotCaptured());
    }

    private UUID actor(Authentication authentication) {
        return ((HiveAppUserDetails) authentication.getPrincipal()).getUserId();
    }

    private String requireIdempotencyKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 160) {
            throw new InvalidRequestException("Idempotency-Key must contain 1 to 160 characters.");
        }
        return normalized;
    }

    private org.springframework.data.domain.Pageable timelinePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException(
                    "Timeline page must be non-negative and size must be between 1 and 100.");
        }
        return org.springframework.data.domain.PageRequest.of(page, size);
    }
}
