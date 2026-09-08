package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeOrigin;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.payment.PaymentStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates one replay-safe, same-terms renewal charge through the ordinary billing ledger. */
@Service
@RequiredArgsConstructor
public class SubscriptionBillingRenewalService {
    private static final List<SubscriptionChangeStatus> OUTSTANDING = List.of(
            SubscriptionChangeStatus.PENDING,
            SubscriptionChangeStatus.AWAITING_CONFIRMATION,
            SubscriptionChangeStatus.NEEDS_ATTENTION);

    private final SubscriptionChangeOperationRepository operations;
    private final SubscriptionCheckoutRepository checkouts;
    private final SubscriptionPeriodCalculator periods;
    private final SubscriptionSnapshotReader snapshots;
    private final BillingLedgerService ledger;
    private final java.time.Clock clock;

    @Transactional(readOnly = true)
    public boolean hasOutstandingPaidRenewal(Subscription subscription) {
        return operations.findTopByAccountIdAndStatusIn(subscription.getAccount().getId(), OUTSTANDING)
                .filter(o -> o.getSourceSubscription().getId().equals(subscription.getId()))
                .map(o -> o.getCheckout() != null && o.getCheckout().money().amount().signum() > 0)
                .orElse(false);
    }

    /**
     * Returns the already outstanding explicit change when one exists. Otherwise creates the
     * system-owned same-terms operation, Checkout, Invoice, Payment, and outbox command atomically.
     */
    @Transactional
    public SubscriptionChangeOperation ensureCharge(Subscription subscription) {
        var existing = operations.findTopByAccountIdAndStatusIn(
                subscription.getAccount().getId(), OUTSTANDING);
        if (existing.isPresent()) return existing.get();

        Money amount = subscription.currentMoney();
        if (amount == null || amount.amount().signum() <= 0) {
            throw new InvalidStateException("Paid renewal requires a positive accepted subscription price.");
        }
        var before = snapshots.read(subscription.getEntitlementSnapshot())
                .orElseThrow(() -> new InvalidStateException(
                        "Current subscription has no supported entitlement snapshot."));
        var next = periods.recurring(before.billingCycle(), subscription.getCurrentPeriodEnd());
        var target = before.withEffectivePeriod(next.startsAt(), next.endsAt());

        SubscriptionChangeOperation operation = new SubscriptionChangeOperation();
        operation.setAccount(subscription.getAccount());
        operation.setSourceSubscription(subscription);
        operation.setTargetPlan(subscription.getPlan());
        operation.setTiming(SubscriptionChangeTiming.AT_RENEWAL);
        operation.setStatus(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
        operation.setEffectiveAt(next.startsAt());
        operation.setRequestedSelection(subscription.getCustomOverrides());
        operation.setBeforeSnapshot(before);
        operation.setTargetSnapshot(target);
        operation.setCommercialPolicyEvaluation(target.commercialPolicyEvaluation());
        operation.setRequestOrigin(SubscriptionChangeOrigin.SYSTEM);
        operation.setRequestedByUserId(null);
        operation.setRequestReason("Automatic recurring renewal");
        operation = operations.saveAndFlush(operation);

        SubscriptionCheckout checkout = new SubscriptionCheckout();
        checkout.setAccount(subscription.getAccount());
        checkout.setChangeOperation(operation);
        checkout.setStatus(SubscriptionCheckoutStatus.PENDING_CONFIRMATION);
        checkout.setGatewayAttemptStatus(PaymentStatus.PENDING);
        checkout.setMoney(amount);
        checkout.setRequestedByUserId(null);
        checkout = checkouts.saveAndFlush(checkout);
        operation.setCheckout(checkout);
        operations.save(operation);
        ledger.invoiceAndQueueCharge(checkout, target, amount, null);
        return operation;
    }

    /** Called only for a locked, revalidated signed repricing instruction at its renewal boundary. */
    @Transactional
    public SubscriptionChangeOperation prepareRepricingCharge(
            Subscription subscription, com.hiveapp.platform.client.plan.domain.entity.SubscriptionRepricingItem item) {
        if (operations.existsByAccountIdAndStatusIn(subscription.getAccount().getId(), OUTSTANDING)) {
            throw new InvalidStateException("Another subscription operation is outstanding.");
        }
        var period = periods.recurring(item.getTargetSnapshot().billingCycle(), subscription.getCurrentPeriodEnd());
        var target = item.getTargetSnapshot().withEffectivePeriod(period.startsAt(), period.endsAt());
        Money amount = Money.of(item.getNewTotal(), target.currencyCode());
        var operation = new SubscriptionChangeOperation();
        operation.setAccount(subscription.getAccount()); operation.setSourceSubscription(subscription);
        operation.setTargetPlan(subscription.getPlan()); operation.setTiming(SubscriptionChangeTiming.AT_RENEWAL);
        operation.setStatus(amount.amount().signum() == 0 ? SubscriptionChangeStatus.PENDING : SubscriptionChangeStatus.AWAITING_CONFIRMATION);
        operation.setEffectiveAt(period.startsAt()); operation.setRequestedSelection(subscription.getCustomOverrides());
        operation.setBeforeSnapshot(subscription.getEntitlementSnapshot()); operation.setTargetSnapshot(target);
        operation.setRequestOrigin(SubscriptionChangeOrigin.PLATFORM_ADMIN);
        operation.setRequestedByUserId(item.getJob().getActorUserId()); operation.setRequestReason(item.getJob().getRequest().reason());
        operation.setRepricingItemId(item.getId()); operations.saveAndFlush(operation);
        var checkout = new SubscriptionCheckout();
        checkout.setAccount(subscription.getAccount()); checkout.setChangeOperation(operation);
        checkout.setStatus(amount.amount().signum() == 0 ? SubscriptionCheckoutStatus.CONFIRMED : SubscriptionCheckoutStatus.PENDING_CONFIRMATION);
        checkout.setMoney(amount); checkout.setRequestedByUserId(item.getJob().getActorUserId());
        if (amount.amount().signum() == 0) {
            checkout.setConfirmationSource(com.hiveapp.platform.client.plan.domain.constant.CheckoutConfirmationSource.NO_PAYMENT_REQUIRED);
            checkout.setConfirmationReference("no-payment:" + operation.getId());
            checkout.setConfirmationReason("Reviewed zero-price renewal; no payment required.");
            checkout.setConfirmedAt(clock.instant());
        }
        if (amount.amount().signum() > 0) checkout.setGatewayAttemptStatus(PaymentStatus.PENDING);
        checkouts.saveAndFlush(checkout); operation.setCheckout(checkout); operations.saveAndFlush(operation);
        if (amount.amount().signum() > 0) ledger.invoiceAndQueueCharge(checkout, target, amount, item.getJob().getActorUserId());
        else ledger.invoiceWithoutCharge(checkout, target, item.getJob().getActorUserId());
        return operation;
    }
}
