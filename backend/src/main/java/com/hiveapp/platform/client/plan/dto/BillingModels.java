package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingLineType;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingProviderEventStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentKind;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingRefundKind;
import com.hiveapp.platform.client.plan.domain.constant.BillingRefundStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingTimelineEntryType;
import com.hiveapp.shared.payment.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BillingModels {
    private BillingModels() {}

    public record AccountIdentity(UUID id, String name) {}

    public record InvoiceRow(
            UUID id,
            String invoiceNumber,
            BillingInvoiceStatus status,
            BigDecimal totalAmount,
            String currencyCode,
            BillingCycle billingCycle,
            Instant periodStart,
            Instant periodEnd,
            Instant issuedAt,
            Instant settledAt,
            UUID changeOperationId,
            AccountIdentity account
    ) {}

    public record InvoiceLine(
            UUID id,
            int position,
            BillingLineType type,
            String sourceCode,
            String sourceName,
            long sourceVersion,
            UUID priceEntryId,
            int quantity,
            BigDecimal unitAmount,
            BigDecimal lineAmount,
            String currencyCode
    ) {}

    public record Payment(
            UUID id,
            BillingPaymentKind kind,
            BillingPaymentStatus status,
            BigDecimal amount,
            String currencyCode,
            boolean trustedForSettlement,
            String externalReference,
            String failureReason,
            UUID operatorUserId,
            String operatorReason,
            UUID retryOfPaymentId,
            String recoveryReference,
            Instant completedAt,
            Instant createdAt
    ) {}

    public record Credit(
            UUID id,
            BigDecimal amount,
            String currencyCode,
            String reason,
            String source,
            UUID operatorUserId,
            String externalReference,
            Instant issuedAt
    ) {}

    public record Refund(
            UUID id,
            UUID paymentId,
            BillingRefundKind kind,
            BillingRefundStatus status,
            BigDecimal amount,
            String currencyCode,
            String reason,
            UUID operatorUserId,
            String providerReference,
            String failureReason,
            Instant completedAt,
            Instant createdAt
    ) {}

    public record InvoiceDetail(
            InvoiceRow invoice,
            List<InvoiceLine> lines,
            List<Payment> payments,
            List<Credit> credits,
            List<Refund> refunds,
            BigDecimal creditedAmount,
            BigDecimal refundedAmount
    ) {}

    public record ClientInvoiceDetail(
            InvoiceRow invoice,
            List<InvoiceLine> lines,
            List<SafePayment> payments,
            BigDecimal creditedAmount,
            BigDecimal refundedAmount
    ) {}

    public record FinancialTimelineEntry(
            UUID recordId,
            BillingTimelineEntryType type,
            String status,
            BigDecimal amount,
            String currencyCode,
            UUID invoiceId,
            String invoiceNumber,
            Instant occurredAt
    ) {}

    public record DocumentParty(
            String name,
            String billingEmail,
            String address,
            String countryCode,
            String taxId
    ) {}

    /** Jurisdiction-neutral immutable data for print/export; never a tax-compliance claim. */
    public record InvoiceDocument(
            InvoiceRow invoice,
            DocumentParty seller,
            DocumentParty customer,
            List<InvoiceLine> lines,
            BigDecimal creditedAmount,
            BigDecimal refundedAmount,
            boolean fiscalReady,
            List<String> missingFiscalFields
    ) {
        public InvoiceDocument {
            lines = lines == null ? List.of() : List.copyOf(lines);
            missingFiscalFields = missingFiscalFields == null
                    ? List.of() : List.copyOf(missingFiscalFields);
        }
    }

    public record SafePayment(
            BillingPaymentKind kind,
            BillingPaymentStatus status,
            BigDecimal amount,
            String currencyCode,
            Instant completedAt
    ) {}

    public record RefundPreview(
            UUID paymentId,
            BigDecimal paymentAmount,
            BigDecimal reservedRefundAmount,
            BigDecimal remainingRefundableAmount,
            String currencyCode,
            boolean providerRefundAllowed,
            boolean manualRefundAllowed,
            String providerBlocker,
            String manualBlocker
    ) {}

    public record OutboxRow(
            UUID id,
            BillingOutboxOperation operation,
            UUID aggregateId,
            BillingOutboxStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant claimedAt,
            Instant processedAt,
            String lastError,
            Instant createdAt
    ) {}

    public record ProviderEventRow(
            UUID id,
            String provider,
            String eventId,
            BillingOutboxOperation operation,
            PaymentStatus providerStatus,
            BigDecimal amount,
            String currencyCode,
            BillingProviderEventStatus processingStatus,
            UUID aggregateId,
            UUID outboxCommandId,
            String providerReference,
            String attentionReason,
            Instant occurredAt,
            Instant processedAt,
            Instant createdAt
    ) {}

    public record ChargeRetryPreview(
            UUID previousPaymentId,
            BillingPaymentStatus previousPaymentStatus,
            BillingOutboxStatus previousCommandStatus,
            boolean retryAllowed,
            boolean providerConfirmationRequired,
            String blocker
    ) {}

    public record ManualSettlementRequest(
            @NotBlank @Size(max = 255) String reference,
            @NotBlank @Size(max = 2000) String reason
    ) {}

    public record CreditRequest(
            @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotBlank @Size(max = 2000) String reason,
            @NotBlank @Size(max = 32) String source,
            @Size(max = 255) String externalReference
    ) {}

    public record RefundRequest(
            @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotBlank @Size(max = 2000) String reason
    ) {}

    public record ManualRefundRequest(
            @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotBlank @Size(max = 2000) String reason,
            @NotBlank @Size(max = 255) String externalReference
    ) {}

    public record ChargeRetryRequest(
            @NotBlank @Size(max = 2000) String reason,
            @NotBlank @Size(max = 255) String recoveryReference,
            boolean providerConfirmedNotCaptured
    ) {}
}
