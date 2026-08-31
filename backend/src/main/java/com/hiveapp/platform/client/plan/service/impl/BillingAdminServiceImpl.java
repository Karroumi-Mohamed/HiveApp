package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentKind;
import com.hiveapp.platform.client.plan.domain.constant.BillingRefundStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import com.hiveapp.platform.client.plan.domain.entity.BillingOutboxCommand;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingRefundRepository;
import com.hiveapp.platform.client.plan.dto.BillingModels;
import com.hiveapp.platform.client.plan.service.BillingAdjustmentService;
import com.hiveapp.platform.client.plan.service.BillingAdminService;
import com.hiveapp.platform.client.plan.service.BillingReadService;
import com.hiveapp.platform.client.plan.service.SubscriptionCheckoutService;
import com.hiveapp.platform.registry.definition.BillingFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.money.Money;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionNode;
import jakarta.persistence.criteria.Predicate;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PermissionNode(key = BillingFeature.KEY, description = "Billing Operations",
        guard = PermissionNode.Guard.ON)
public class BillingAdminServiceImpl extends PlatformControlFeatureService
        implements BillingAdminService {
    private final BillingInvoiceRepository invoices;
    private final BillingPaymentAttemptRepository payments;
    private final BillingRefundRepository refunds;
    private final BillingOutboxCommandRepository outbox;
    private final BillingReadService reads;
    private final BillingAdjustmentService adjustments;
    private final SubscriptionCheckoutService checkouts;

    @Override
    protected FeatureDefinition featureDefinition() {
        return BillingFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list_invoices", description = "List and filter Invoices")
    public Page<BillingModels.InvoiceRow> listInvoices(
            String search,
            UUID accountId,
            BillingInvoiceStatus status,
            String currencyCode,
            BillingCycle billingCycle,
            Instant issuedFrom,
            Instant issuedUntil,
            Pageable pageable
    ) {
        boolean accountIdentity = has("read_account_identity");
        if (accountId != null && !accountIdentity) {
            throw new AccessDeniedException("Account identity permission is required for Account filtering");
        }
        String normalizedSearch = normalizeSearch(search);
        String normalizedCurrency = normalizeCurrency(currencyCode);
        if (issuedFrom != null && issuedUntil != null && !issuedUntil.isAfter(issuedFrom)) {
            throw new InvalidRequestException("issuedUntil must be after issuedFrom.");
        }
        Specification<BillingInvoice> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accountId != null) predicates.add(cb.equal(root.get("account").get("id"), accountId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (normalizedCurrency != null) {
                predicates.add(cb.equal(root.get("currencyCode"), normalizedCurrency));
            }
            if (billingCycle != null) predicates.add(cb.equal(root.get("billingCycle"), billingCycle));
            if (issuedFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("issuedAt"), issuedFrom));
            if (issuedUntil != null) predicates.add(cb.lessThan(root.get("issuedAt"), issuedUntil));
            if (normalizedSearch != null) {
                String pattern = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                Predicate invoiceNumber = cb.like(cb.lower(root.get("invoiceNumber")), pattern);
                predicates.add(accountIdentity
                        ? cb.or(invoiceNumber,
                        cb.like(cb.lower(root.get("account").get("name")), pattern))
                        : invoiceNumber);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return reads.listAdmin(specification, pageable, accountIdentity);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_invoice", description = "Read an Invoice and immutable lines")
    public BillingModels.InvoiceDetail invoice(UUID invoiceId) {
        return reads.adminDetail(
                invoiceId,
                has("read_account_identity"),
                has("read_payments"),
                has("read_payment_references"));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_account_identity", description = "Read Invoice Account identity")
    public BillingModels.AccountIdentity accountIdentity(UUID invoiceId) {
        BillingInvoice invoice = invoices.findDetailedById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingInvoice", "id", invoiceId));
        return new BillingModels.AccountIdentity(
                invoice.getAccount().getId(), invoice.getAccount().getName());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_payments", description = "Read Payment attempt evidence")
    public List<BillingModels.Payment> payments(UUID invoiceId) {
        return reads.payments(invoiceId, has("read_payment_references"));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_payment_references",
            description = "Read sensitive Payment and operator references")
    public BillingModels.Payment paymentReference(UUID paymentId) {
        var payment = payments.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingPaymentAttempt", "id", paymentId));
        return reads.payment(payment, true);
    }

    @Override
    @Transactional
    @PermissionNode(key = "manual_settlement", description = "Record a manual Invoice settlement")
    public BillingModels.InvoiceDetail settleManually(
            UUID invoiceId,
            UUID operatorUserId,
            String reference,
            String reason
    ) {
        BillingInvoice invoice = invoices.findDetailedById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingInvoice", "id", invoiceId));
        checkouts.confirmManual(invoice.getCheckout().getId(), operatorUserId, reference, reason);
        return reads.adminDetail(
                invoiceId,
                has("read_account_identity"),
                has("read_payments"),
                has("read_payment_references"));
    }

    @Override
    @Transactional
    @PermissionNode(key = "issue_credit", description = "Issue an audited Invoice Credit")
    public BillingModels.Credit issueCredit(
            UUID invoiceId,
            BigDecimal amount,
            String currencyCode,
            String reason,
            String source,
            UUID operatorUserId,
            String externalReference
    ) {
        var credit = adjustments.issueCredit(
                invoiceId, Money.of(amount, currencyCode), reason, source,
                operatorUserId, externalReference);
        return reads.credit(credit, has("read_payment_references"));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_refund", description = "Preview refundable Payment balance")
    public BillingModels.RefundPreview previewRefund(UUID paymentId) {
        var payment = payments.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingPaymentAttempt", "id", paymentId));
        BigDecimal reserved = refunds.sumAmountByPaymentIdAndStatusIn(
                paymentId, List.of(BillingRefundStatus.PENDING, BillingRefundStatus.SUCCEEDED));
        BigDecimal remaining = payment.getAmount().subtract(reserved).max(BigDecimal.ZERO);
        boolean trustedAndRemaining = payment.getStatus() == BillingPaymentStatus.SUCCEEDED
                && payment.isTrustedForSettlement() && remaining.signum() > 0;
        boolean providerAllowed = trustedAndRemaining
                && payment.getKind() == BillingPaymentKind.PROVIDER;
        boolean manualAllowed = trustedAndRemaining;
        String commonBlocker = trustedAndRemaining ? null
                : payment.getStatus() != BillingPaymentStatus.SUCCEEDED
                || !payment.isTrustedForSettlement()
                ? "Only a trusted succeeded Payment can be refunded."
                : "Payment is already fully refunded or reserved.";
        String providerBlocker = providerAllowed ? null
                : commonBlocker != null ? commonBlocker
                : "Manual settlements cannot use provider Refund transport.";
        return new BillingModels.RefundPreview(
                paymentId, payment.getAmount(), reserved, remaining,
                payment.getCurrencyCode(), providerAllowed, manualAllowed,
                providerBlocker, manualAllowed ? null : commonBlocker);
    }

    @Override
    @Transactional
    @PermissionNode(key = "create_refund", description = "Create an audited provider Refund")
    public BillingModels.Refund requestRefund(
            UUID paymentId,
            BigDecimal amount,
            String currencyCode,
            String reason,
            UUID operatorUserId,
            String idempotencyKey
    ) {
        var refund = adjustments.requestRefund(
                paymentId, Money.of(amount, currencyCode), reason,
                operatorUserId, idempotencyKey);
        return reads.refund(refund, has("read_payment_references"));
    }

    @Override
    @Transactional
    @PermissionNode(key = "record_manual_refund",
            description = "Record an externally completed manual Refund")
    public BillingModels.Refund recordManualRefund(
            UUID paymentId,
            BigDecimal amount,
            String currencyCode,
            String reason,
            String externalReference,
            UUID operatorUserId,
            String idempotencyKey
    ) {
        var refund = adjustments.recordManualRefund(
                paymentId, Money.of(amount, currencyCode), reason, operatorUserId,
                idempotencyKey, externalReference);
        return reads.refund(refund, has("read_payment_references"));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list_reconciliation", description = "List provider command state")
    public Page<BillingModels.OutboxRow> reconciliation(
            BillingOutboxStatus status,
            BillingOutboxOperation operation,
            Pageable pageable
    ) {
        Specification<BillingOutboxCommand> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (operation != null) predicates.add(cb.equal(root.get("operation"), operation));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return outbox.findAll(specification, pageable).map(command -> new BillingModels.OutboxRow(
                command.getId(), command.getOperation(), command.getAggregateId(), command.getStatus(),
                command.getAttemptCount(), command.getNextAttemptAt(), command.getClaimedAt(),
                command.getProcessedAt(), command.getLastError(), command.getCreatedAt()));
    }

    private boolean has(String action) {
        return PermissionGuard.has(new Permission(BillingFeature.CODE + "." + action));
    }

    private String normalizeSearch(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 160) throw new InvalidRequestException("Search is too long.");
        return normalized;
    }

    private String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) throw new InvalidRequestException("Currency must use 3 letters.");
        return normalized;
    }
}
