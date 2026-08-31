package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingRefundStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingTimelineEntryType;
import com.hiveapp.platform.client.plan.domain.entity.BillingCredit;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoiceLine;
import com.hiveapp.platform.client.plan.domain.entity.BillingPaymentAttempt;
import com.hiveapp.platform.client.plan.domain.entity.BillingRefund;
import com.hiveapp.platform.client.plan.domain.repository.BillingCreditRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingFinancialTimelineRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingRefundRepository;
import com.hiveapp.platform.client.plan.dto.BillingModels;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingReadService {
    private final BillingInvoiceRepository invoices;
    private final BillingPaymentAttemptRepository payments;
    private final BillingCreditRepository credits;
    private final BillingRefundRepository refunds;
    private final BillingFinancialTimelineRepository timeline;

    @Transactional(readOnly = true)
    public Page<BillingModels.InvoiceRow> listAdmin(
            Specification<BillingInvoice> specification,
            Pageable pageable,
            boolean includeAccountIdentity
    ) {
        return invoices.findAll(specification, pageable)
                .map(invoice -> invoiceRow(invoice, includeAccountIdentity));
    }

    @Transactional(readOnly = true)
    public Page<BillingModels.InvoiceRow> listClient(UUID accountId, Pageable pageable) {
        return invoices.findAllByAccountId(accountId, pageable)
                .map(invoice -> invoiceRow(invoice, false));
    }

    @Transactional(readOnly = true)
    public BillingModels.InvoiceDetail adminDetail(
            UUID invoiceId,
            boolean includeAccountIdentity,
            boolean includePaymentEvidence,
            boolean includeSensitiveReferences
    ) {
        BillingInvoice invoice = invoices.findDetailedById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingInvoice", "id", invoiceId));
        List<BillingPaymentAttempt> paymentRows = includePaymentEvidence
                ? payments.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId) : List.of();
        List<BillingCredit> creditRows = credits.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        List<BillingRefund> refundRows = refunds.findAllByPaymentInvoiceIdOrderByCreatedAtDesc(invoiceId);
        return new BillingModels.InvoiceDetail(
                invoiceRow(invoice, includeAccountIdentity),
                invoice.getLines().stream().map(this::line).toList(),
                paymentRows.stream().map(payment -> payment(payment, includeSensitiveReferences)).toList(),
                creditRows.stream().map(credit -> credit(credit, includeSensitiveReferences)).toList(),
                refundRows.stream().map(refund -> refund(refund, includeSensitiveReferences)).toList(),
                creditRows.stream().map(BillingCredit::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                refundRows.stream()
                        .filter(refund -> refund.getStatus() == BillingRefundStatus.SUCCEEDED)
                        .map(BillingRefund::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Transactional(readOnly = true)
    public BillingModels.ClientInvoiceDetail clientDetail(UUID accountId, UUID invoiceId) {
        BillingInvoice invoice = invoices.findDetailedByIdAndAccountId(invoiceId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingInvoice", "id", invoiceId));
        List<BillingPaymentAttempt> paymentRows = payments
                .findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        List<BillingCredit> creditRows = credits.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        List<BillingRefund> refundRows = refunds.findAllByPaymentInvoiceIdOrderByCreatedAtDesc(invoiceId);
        return new BillingModels.ClientInvoiceDetail(
                invoiceRow(invoice, false),
                invoice.getLines().stream().map(this::line).toList(),
                paymentRows.stream().map(payment -> new BillingModels.SafePayment(
                        payment.getKind(), payment.getStatus(), payment.getAmount(),
                        payment.getCurrencyCode(), payment.getCompletedAt())).toList(),
                creditRows.stream().map(BillingCredit::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                refundRows.stream()
                        .filter(refund -> refund.getStatus() == BillingRefundStatus.SUCCEEDED)
                        .map(BillingRefund::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Transactional(readOnly = true)
    public Page<BillingModels.FinancialTimelineEntry> financialTimeline(
            UUID accountId,
            BillingTimelineEntryType type,
            String currencyCode,
            Instant occurredFrom,
            Instant occurredUntil,
            Pageable pageable
    ) {
        String currency = currencyCode == null || currencyCode.isBlank()
                ? null : currencyCode.trim().toUpperCase(Locale.ROOT);
        if (currency != null && currency.length() != 3) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Currency must use 3 letters.");
        }
        if (occurredFrom != null && occurredUntil != null && !occurredUntil.isAfter(occurredFrom)) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "occurredUntil must be after occurredFrom.");
        }
        return timeline.findByAccount(
                accountId, type == null ? null : type.name(), currency,
                occurredFrom, occurredUntil, pageable).map(row ->
                new BillingModels.FinancialTimelineEntry(
                        UUID.fromString(row.getRecordId()), BillingTimelineEntryType.valueOf(row.getEntryType()),
                        row.getEntryStatus(), row.getAmount(), row.getCurrencyCode(),
                        UUID.fromString(row.getInvoiceId()), row.getInvoiceNumber(), row.getOccurredAt().toInstant()));
    }

    @Transactional(readOnly = true)
    public BillingModels.InvoiceDocument adminDocument(UUID invoiceId) {
        BillingInvoice invoice = invoices.findDetailedById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingInvoice", "id", invoiceId));
        return document(invoice);
    }

    @Transactional(readOnly = true)
    public BillingModels.InvoiceDocument clientDocument(UUID accountId, UUID invoiceId) {
        BillingInvoice invoice = invoices.findDetailedByIdAndAccountId(invoiceId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingInvoice", "id", invoiceId));
        return document(invoice);
    }

    @Transactional(readOnly = true)
    public List<BillingModels.Payment> payments(UUID invoiceId, boolean includeSensitiveReferences) {
        if (!invoices.existsById(invoiceId)) {
            throw new ResourceNotFoundException("BillingInvoice", "id", invoiceId);
        }
        return payments.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
                .map(payment -> payment(payment, includeSensitiveReferences))
                .toList();
    }

    public BillingModels.Credit credit(BillingCredit credit, boolean includeSensitiveReferences) {
        return new BillingModels.Credit(
                credit.getId(), credit.getAmount(), credit.getCurrencyCode(), credit.getReason(),
                credit.getSource(), includeSensitiveReferences ? credit.getOperatorUserId() : null,
                includeSensitiveReferences ? credit.getExternalReference() : null,
                credit.getIssuedAt());
    }

    public BillingModels.Refund refund(BillingRefund refund, boolean includeSensitiveReferences) {
        return new BillingModels.Refund(
                refund.getId(), refund.getPayment().getId(), refund.getKind(),
                refund.getStatus(), refund.getAmount(),
                refund.getCurrencyCode(), refund.getReason(),
                includeSensitiveReferences ? refund.getOperatorUserId() : null,
                includeSensitiveReferences ? refund.getProviderReference() : null,
                refund.getFailureReason(), refund.getCompletedAt(), refund.getCreatedAt());
    }

    private BillingModels.InvoiceDocument document(BillingInvoice invoice) {
        UUID invoiceId = invoice.getId();
        List<BillingCredit> creditRows = credits.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        List<BillingRefund> refundRows = refunds.findAllByPaymentInvoiceIdOrderByCreatedAtDesc(invoiceId);
        BigDecimal credited = creditRows.stream().map(BillingCredit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = refundRows.stream()
                .filter(refund -> refund.getStatus() == BillingRefundStatus.SUCCEEDED)
                .map(BillingRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> missing = new ArrayList<>();
        requireFiscal(invoice.getSellerAddress(), "SELLER_ADDRESS", missing);
        requireFiscal(invoice.getSellerCountryCode(), "SELLER_COUNTRY", missing);
        requireFiscal(invoice.getSellerTaxId(), "SELLER_TAX_ID", missing);
        requireFiscal(invoice.getCustomerAddress(), "CUSTOMER_ADDRESS", missing);
        requireFiscal(invoice.getCustomerCountryCode(), "CUSTOMER_COUNTRY", missing);
        requireFiscal(invoice.getCustomerTaxId(), "CUSTOMER_TAX_ID", missing);
        missing.add("TAX_CALCULATION_NOT_IMPLEMENTED");
        missing.add("JURISDICTIONAL_NUMBERING_NOT_IMPLEMENTED");
        return new BillingModels.InvoiceDocument(
                invoiceRow(invoice, false),
                new BillingModels.DocumentParty(
                        invoice.getSellerName(), null, invoice.getSellerAddress(),
                        invoice.getSellerCountryCode(), invoice.getSellerTaxId()),
                new BillingModels.DocumentParty(
                        invoice.getCustomerName(), invoice.getCustomerBillingEmail(),
                        invoice.getCustomerAddress(), invoice.getCustomerCountryCode(),
                        invoice.getCustomerTaxId()),
                invoice.getLines().stream().map(this::line).toList(),
                credited, refunded, missing.isEmpty(), missing);
    }

    private void requireFiscal(String value, String code, List<String> missing) {
        if (value == null || value.isBlank()) missing.add(code);
    }

    private BillingModels.InvoiceRow invoiceRow(BillingInvoice invoice, boolean includeAccountIdentity) {
        BillingModels.AccountIdentity account = includeAccountIdentity
                ? new BillingModels.AccountIdentity(invoice.getAccount().getId(), invoice.getAccount().getName())
                : null;
        return new BillingModels.InvoiceRow(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getStatus(),
                invoice.getTotalAmount(), invoice.getCurrencyCode(), invoice.getBillingCycle(),
                invoice.getPeriodStart(), invoice.getPeriodEnd(), invoice.getIssuedAt(),
                invoice.getSettledAt(), invoice.getChangeOperationId(), account);
    }

    private BillingModels.InvoiceLine line(BillingInvoiceLine line) {
        return new BillingModels.InvoiceLine(
                line.getId(), line.getPosition(), line.getType(), line.getSourceCode(),
                line.getSourceName(), line.getSourceVersion(), line.getPriceEntryId(),
                line.getQuantity(), line.getUnitAmount(), line.getLineAmount(), line.getCurrencyCode());
    }

    public BillingModels.Payment payment(
            BillingPaymentAttempt payment,
            boolean includeSensitiveReferences
    ) {
        return new BillingModels.Payment(
                payment.getId(), payment.getKind(), payment.getStatus(), payment.getAmount(),
                payment.getCurrencyCode(), payment.isTrustedForSettlement(),
                includeSensitiveReferences ? payment.getExternalReference() : null,
                payment.getFailureReason(),
                includeSensitiveReferences ? payment.getOperatorUserId() : null,
                includeSensitiveReferences ? payment.getOperatorReason() : null,
                payment.getRetryOfPaymentId(),
                includeSensitiveReferences ? payment.getRecoveryReference() : null,
                payment.getCompletedAt(), payment.getCreatedAt());
    }
}
