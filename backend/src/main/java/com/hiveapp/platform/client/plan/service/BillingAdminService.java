package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.dto.AccountBillingProfileModels;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingProviderEventStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingTimelineEntryType;
import com.hiveapp.platform.client.plan.dto.BillingModels;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BillingAdminService {
    Page<BillingModels.InvoiceRow> listInvoices(
            String search,
            UUID accountId,
            BillingInvoiceStatus status,
            String currencyCode,
            BillingCycle billingCycle,
            Instant issuedFrom,
            Instant issuedUntil,
            Pageable pageable);

    BillingModels.InvoiceDetail invoice(UUID invoiceId);

    BillingModels.InvoiceDocument invoiceDocument(UUID invoiceId);

    Page<BillingModels.FinancialTimelineEntry> financialTimeline(
            UUID accountId,
            BillingTimelineEntryType type,
            String currencyCode,
            Instant occurredFrom,
            Instant occurredUntil,
            Pageable pageable);

    AccountBillingProfileModels.Profile billingProfile(UUID accountId);

    AccountBillingProfileModels.Profile updateBillingProfile(
            UUID accountId,
            AccountBillingProfileModels.UpdateRequest request);

    BillingModels.AccountIdentity accountIdentity(UUID invoiceId);

    List<BillingModels.Payment> payments(UUID invoiceId);

    BillingModels.Payment paymentReference(UUID paymentId);

    BillingModels.InvoiceDetail settleManually(
            UUID invoiceId,
            UUID operatorUserId,
            String reference,
            String reason);

    BillingModels.Credit issueCredit(
            UUID invoiceId,
            BigDecimal amount,
            String currencyCode,
            String reason,
            String source,
            UUID operatorUserId,
            String externalReference);

    BillingModels.RefundPreview previewRefund(UUID paymentId);

    BillingModels.Refund requestRefund(
            UUID paymentId,
            BigDecimal amount,
            String currencyCode,
            String reason,
            UUID operatorUserId,
            String idempotencyKey);

    BillingModels.Refund recordManualRefund(
            UUID paymentId,
            BigDecimal amount,
            String currencyCode,
            String reason,
            String externalReference,
            UUID operatorUserId,
            String idempotencyKey);

    Page<BillingModels.OutboxRow> reconciliation(
            BillingOutboxStatus status,
            BillingOutboxOperation operation,
            Pageable pageable);

    Page<BillingModels.ProviderEventRow> providerEvents(
            BillingProviderEventStatus status,
            BillingOutboxOperation operation,
            String provider,
            Pageable pageable);

    BillingModels.ProviderEventRow reprocessProviderEvent(UUID eventId);

    BillingModels.ChargeRetryPreview previewChargeRetry(UUID invoiceId);

    BillingModels.Payment retryCharge(
            UUID invoiceId,
            UUID operatorUserId,
            String idempotencyKey,
            String reason,
            String recoveryReference,
            boolean providerConfirmedNotCaptured);
}
