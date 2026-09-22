package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.service.AccountBillingProfileService;
import com.hiveapp.platform.client.plan.domain.constant.BillingLineType;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentKind;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoiceLine;
import com.hiveapp.platform.client.plan.domain.entity.BillingOutboxCommand;
import com.hiveapp.platform.client.plan.domain.entity.BillingPaymentAttempt;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.repository.BillingInvoiceRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingPaymentAttemptRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.payment.BillingProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates immutable financial evidence from an already reviewed subscription snapshot. */
@Service
@RequiredArgsConstructor
public class BillingLedgerService {
    private final com.hiveapp.platform.communication.BusinessNotifications notifications;
    private final BillingInvoiceRepository invoices;
    private final BillingPaymentAttemptRepository payments;
    private final BillingOutboxCommandRepository outbox;
    private final AccountBillingProfileService billingProfiles;
    private final BillingProperties billingProperties;
    private final Clock clock;

    @Transactional
    public BillingPaymentAttempt invoiceAndQueueCharge(
            SubscriptionCheckout checkout,
            SubscriptionEntitlementSnapshot snapshot,
            Money finalTotal,
            UUID requestedByUserId
    ) {
        Objects.requireNonNull(snapshot, "Accepted subscription snapshot is required");
        if (finalTotal.amount().signum() <= 0) {
            throw new IllegalArgumentException("A provider charge requires a positive Invoice total");
        }
        if (checkout.getId() == null) throw new IllegalStateException("Checkout must be persisted first");
        if (invoices.findByCheckoutId(checkout.getId()).isPresent()) {
            throw new InvalidStateException("Checkout already has an Invoice.");
        }

        Instant now = clock.instant();
        BillingInvoice invoice = BillingInvoice.open(
                checkout,
                finalTotal,
                snapshot.billingCycle(),
                snapshot.effectiveFrom(),
                snapshot.effectiveUntil(),
                requestedByUserId,
                now);
        var customer = billingProfiles.snapshot(checkout.getAccount().getId());
        var issuer = billingProperties.getIssuer();
        invoice.snapshotDocumentParties(
                new BillingInvoice.PartySnapshot(
                        issuer.getName(), null, issuer.getAddress(),
                        issuer.getCountryCode(), issuer.getTaxId()),
                new BillingInvoice.PartySnapshot(
                        customer.legalName(), customer.billingEmail(), customer.address(),
                        customer.countryCode(), customer.taxId()));
        Money catalogue = addComponentLines(invoice, snapshot);
        catalogue.requireSameCurrency(finalTotal);
        Money adjustment = finalTotal.subtract(catalogue);
        if (adjustment.amount().signum() != 0) {
            invoice.addLine(BillingInvoiceLine.adjustment(
                    adjustmentLabel(snapshot), adjustment));
        }
        invoice = invoices.saveAndFlush(invoice);

        String idempotencyKey = "subscription-charge:" + checkout.getChangeOperation().getId();
        BillingPaymentAttempt payment = payments.saveAndFlush(
                BillingPaymentAttempt.pendingProvider(invoice, idempotencyKey));
        outbox.save(BillingOutboxCommand.pending(
                BillingOutboxOperation.CHARGE, payment.getId(), idempotencyKey, now));
        return payment;
    }

    /** Creates a positive open Invoice for an already received/manual settlement path. */
    @Transactional
    public BillingInvoice invoiceForManualSettlement(
            SubscriptionCheckout checkout,
            SubscriptionEntitlementSnapshot snapshot,
            Money finalTotal,
            UUID requestedByUserId
    ) {
        Objects.requireNonNull(snapshot, "Accepted subscription snapshot is required");
        if (finalTotal.amount().signum() <= 0) {
            throw new IllegalArgumentException("A manual settlement Invoice requires a positive total");
        }
        if (checkout.getId() == null) throw new IllegalStateException("Checkout must be persisted first");
        if (invoices.findByCheckoutId(checkout.getId()).isPresent()) {
            throw new InvalidStateException("Checkout already has an Invoice.");
        }
        Instant now = clock.instant();
        BillingInvoice invoice = BillingInvoice.open(
                checkout, finalTotal, snapshot.billingCycle(), snapshot.effectiveFrom(),
                snapshot.effectiveUntil(), requestedByUserId, now);
        var customer = billingProfiles.snapshot(checkout.getAccount().getId());
        var issuer = billingProperties.getIssuer();
        invoice.snapshotDocumentParties(
                new BillingInvoice.PartySnapshot(
                        issuer.getName(), null, issuer.getAddress(),
                        issuer.getCountryCode(), issuer.getTaxId()),
                new BillingInvoice.PartySnapshot(
                        customer.legalName(), customer.billingEmail(), customer.address(),
                        customer.countryCode(), customer.taxId()));
        Money catalogue = addComponentLines(invoice, snapshot);
        catalogue.requireSameCurrency(finalTotal);
        Money adjustment = finalTotal.subtract(catalogue);
        if (adjustment.amount().signum() != 0) {
            invoice.addLine(BillingInvoiceLine.adjustment(
                    "Special agreement adjustment", adjustment));
        }
        return invoices.saveAndFlush(invoice);
    }

    /** Records a settled zero-total Invoice without inventing a Payment attempt. */
    @Transactional
    public BillingInvoice invoiceWithoutCharge(
            SubscriptionCheckout checkout,
            SubscriptionEntitlementSnapshot snapshot,
            UUID requestedByUserId
    ) {
        Objects.requireNonNull(snapshot, "Accepted subscription snapshot is required");
        if (checkout.money().amount().signum() != 0) {
            throw new IllegalArgumentException("A no-charge Invoice requires a zero-total Checkout");
        }
        if (checkout.getId() == null) throw new IllegalStateException("Checkout must be persisted first");
        if (invoices.findByCheckoutId(checkout.getId()).isPresent()) {
            throw new InvalidStateException("Checkout already has an Invoice.");
        }
        Instant now = clock.instant();
        BillingInvoice invoice = BillingInvoice.open(
                checkout, checkout.money(), snapshot.billingCycle(), snapshot.effectiveFrom(),
                snapshot.effectiveUntil(), requestedByUserId, now);
        var customer = billingProfiles.snapshot(checkout.getAccount().getId());
        var issuer = billingProperties.getIssuer();
        invoice.snapshotDocumentParties(
                new BillingInvoice.PartySnapshot(
                        issuer.getName(), null, issuer.getAddress(),
                        issuer.getCountryCode(), issuer.getTaxId()),
                new BillingInvoice.PartySnapshot(
                        customer.legalName(), customer.billingEmail(), customer.address(),
                        customer.countryCode(), customer.taxId()));
        Money catalogue = addComponentLines(invoice, snapshot);
        if (catalogue.amount().signum() != 0) {
            invoice.addLine(BillingInvoiceLine.adjustment(
                    "Complimentary special agreement", catalogue.multiply(-1)));
        }
        return invoices.saveAndFlush(invoice);
    }

    @Transactional
    public BillingPaymentAttempt recordManualSettlement(
            UUID checkoutId,
            UUID operatorUserId,
            String reference,
            String reason
    ) {
        String normalizedReference = requireText(reference, "Manual settlement reference is required");
        BillingInvoice snapshot = invoices.findByCheckoutId(checkoutId)
                .orElseThrow(() -> new InvalidStateException("Checkout has no Invoice to settle."));
        BillingInvoice invoice = lockInvoiceAfterProviderIntent(
                snapshot,
                "Automatic charge cancelled in favor of manual settlement.");
        var replay = payments.findByExternalReference(normalizedReference);
        if (replay.isPresent()) {
            BillingPaymentAttempt existing = replay.get();
            if (existing.getInvoice().getId().equals(invoice.getId())
                    && existing.money().equals(invoice.money())) {
                return existing;
            }
            throw new InvalidStateException("Manual settlement reference is already used.");
        }
        if (invoice.getStatus() != com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus.OPEN) {
            throw new InvalidStateException("Only an open Invoice can be settled.");
        }
        Instant now = clock.instant();
        BillingPaymentAttempt payment = BillingPaymentAttempt.manualSucceeded(
                invoice, operatorUserId, normalizedReference, reason, now);
        payment = payments.saveAndFlush(payment);
        invoice.settle(now);
        invoices.save(invoice);
        notifications.payment(invoice, payment.getId(), true);
        return payment;
    }

    @Transactional
    public void cancelForCheckout(UUID checkoutId) {
        BillingInvoice snapshot = invoices.findByCheckoutId(checkoutId)
                .orElseThrow(() -> new InvalidStateException("Checkout has no Invoice to cancel."));
        BillingInvoice invoice = lockInvoiceAfterProviderIntent(
                snapshot,
                "Checkout cancelled before automatic charge dispatch.");
        if (invoice.getStatus() != com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus.OPEN) {
            throw new InvalidStateException("Only an open Invoice can be cancelled.");
        }
        invoice.cancel();
        invoices.save(invoice);
        notifications.paymentCancelled(invoice);
    }

    /**
     * Locks in provider-dispatch order (command, payment, invoice) so cancellation cannot race a charge.
     */
    private BillingInvoice lockInvoiceAfterProviderIntent(
            BillingInvoice snapshot,
            String cancellationReason
    ) {
        var providerSnapshot = payments.findFirstByInvoiceIdAndKindOrderByCreatedAtDesc(
                snapshot.getId(), BillingPaymentKind.PROVIDER);
        if (providerSnapshot.isEmpty()) {
            return invoices.findByIdForUpdate(snapshot.getId())
                    .orElseThrow(() -> new InvalidStateException("Invoice no longer exists."));
        }

        BillingPaymentAttempt paymentSnapshot = providerSnapshot.get();
        BillingOutboxCommand command = outbox.findByAggregateIdAndOperationForUpdate(
                        paymentSnapshot.getId(), BillingOutboxOperation.CHARGE)
                .orElseThrow(() -> new InvalidStateException(
                        "Provider payment has no durable dispatch command."));
        BillingPaymentAttempt payment = payments.findByIdForUpdate(paymentSnapshot.getId())
                .orElseThrow(() -> new InvalidStateException("Provider payment no longer exists."));
        BillingInvoice invoice = invoices.findByIdForUpdate(snapshot.getId())
                .orElseThrow(() -> new InvalidStateException("Invoice no longer exists."));

        if (payment.getStatus() == BillingPaymentStatus.PENDING) {
            if (command.getStatus() != BillingOutboxStatus.PENDING) {
                throw new InvalidStateException(
                        "Automatic payment was already dispatched; reconcile it before continuing.");
            }
            Instant now = clock.instant();
            command.cancel(cancellationReason, now);
            payment.cancel(cancellationReason, now);
            outbox.save(command);
            payments.save(payment);
        } else if (payment.getStatus() == BillingPaymentStatus.SUCCEEDED
                && payment.isTrustedForSettlement()) {
            throw new InvalidStateException("Invoice already has a trusted provider settlement.");
        }
        return invoice;
    }

    private Money addComponentLines(
            BillingInvoice invoice,
            SubscriptionEntitlementSnapshot snapshot
    ) {
        Money total = Money.zero(snapshot.currencyCode());
        Money planPrice = Money.of(snapshot.basePrice(), snapshot.currencyCode());
        // Content-only version changes retain the original financial definition. The checkout
        // snapshot records effective content; invoice lines must identify the tariff's owner.
        var financialSource = snapshot.financialPlanSource();
        invoice.addLine(BillingInvoiceLine.component(
                BillingLineType.PLAN,
                financialSource == null ? snapshot.planCode() : financialSource.planCode(),
                displayName(snapshot.planName(), snapshot.planCode()),
                financialSource == null ? snapshot.planDefinitionVersion() : financialSource.productVersionNumber(),
                snapshot.planPriceEntryId(),
                1,
                planPrice));
        total = total.add(planPrice);

        for (var addOn : snapshot.addOns()) {
            Money unit = Money.of(addOn.price(), addOn.currencyCode());
            total = total.add(unit);
            invoice.addLine(BillingInvoiceLine.component(
                    BillingLineType.ADD_ON,
                    addOn.code(),
                    displayName(addOn.name(), addOn.code()),
                    addOn.definitionVersion(),
                    addOn.priceEntryId(),
                    1,
                    unit));
        }
        for (var quotaPackage : snapshot.quotaPackages()) {
            Money unit = Money.of(quotaPackage.unitPrice(), quotaPackage.currencyCode());
            Money line = unit.multiply(quotaPackage.quantity());
            total = total.add(line);
            invoice.addLine(BillingInvoiceLine.component(
                    BillingLineType.QUOTA_PACKAGE,
                    quotaPackage.code(),
                    displayName(quotaPackage.name(), quotaPackage.code()),
                    quotaPackage.definitionVersion(),
                    quotaPackage.priceEntryId(),
                    quotaPackage.quantity(),
                    unit));
        }
        return total;
    }

    private String adjustmentLabel(SubscriptionEntitlementSnapshot snapshot) {
        if (snapshot.offerEvaluation() != null) return "Commercial Offer adjustment";
        if (snapshot.commercialPolicyEvaluation() != null) return "Commercial Policy adjustment";
        return "Accepted commercial adjustment";
    }

    private String displayName(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name.trim();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
