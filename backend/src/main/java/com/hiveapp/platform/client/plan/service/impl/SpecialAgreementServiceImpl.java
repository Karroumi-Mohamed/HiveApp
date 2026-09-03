package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementAttentionStage;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementEndInstruction;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementPricingMode;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementSettlementMode;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeOrigin;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause;
import com.hiveapp.platform.client.plan.domain.entity.SpecialCommercialAgreement;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.repository.SpecialCommercialAgreementRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.dto.SpecialAgreementModels;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.client.plan.service.SpecialAgreementLifecycleService;
import com.hiveapp.platform.client.plan.service.SpecialAgreementSelectionAssessment;
import com.hiveapp.platform.client.plan.service.SpecialAgreementService;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeActivationService;
import com.hiveapp.platform.client.plan.service.SubscriptionCheckoutService;
import com.hiveapp.platform.client.plan.service.SubscriptionPeriodCalculator;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.audit.AuditedMutation;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleSpecialAgreementPreviewException;
import com.hiveapp.shared.money.Money;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpecialAgreementServiceImpl implements SpecialAgreementService {
    private static final Set<SpecialAgreementStatus> LIVE = Set.of(
            SpecialAgreementStatus.SCHEDULED,
            SpecialAgreementStatus.AWAITING_SETTLEMENT,
            SpecialAgreementStatus.ACTIVE,
            SpecialAgreementStatus.NEEDS_ATTENTION);

    private final SubscriptionServiceImpl subscriptions;
    private final AccountRepository accounts;
    private final SpecialCommercialAgreementRepository agreements;
    private final SubscriptionChangeOperationRepository operations;
    private final SubscriptionCheckoutService checkouts;
    private final SubscriptionChangeActivationService activation;
    private final SubscriptionPeriodCalculator periods;
    private final CommercialCatalogVersionService catalogVersions;
    private final RegistryCatalogVersionService registryVersions;
    private final CommercialPreviewTokenService tokens;
    private final SpecialAgreementLifecycleService lifecycle;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public SpecialAgreementModels.Preview preview(
            UUID accountId, UUID actorUserId, SpecialAgreementModels.Definition definition) {
        String registryVersion = registryVersions.currentVersion();
        return catalogVersions.readConsistently(catalogRevision -> {
            Instant evaluatedAt = clock.instant();
            SpecialAgreementSelectionAssessment selection;
            try {
                selection = subscriptions.assessSpecialAgreementPreview(
                        accountId, definition.selection(), definition.quotaBonuses(), evaluatedAt);
            } catch (IllegalStateException | ArithmeticException exception) {
                throw privateCapacityRequest(exception);
            }
            Pricing pricing = price(definition, selection, evaluatedAt);
            String fingerprint = fingerprint(selection, definition, pricing);
            var evidence = tokens.issue(
                    CommercialPreviewKind.SPECIAL_COMMERCIAL_AGREEMENT,
                    selection.current().getId(), selection.current().getVersion(), actorUserId,
                    catalogRevision, registryVersion, fingerprint, evaluatedAt);
            return toPreview(selection, definition, pricing, catalogRevision, registryVersion,
                    evidence.evaluatedAt(), evidence.expiresAt(), evidence.token());
        }, StaleSpecialAgreementPreviewException::new);
    }

    @Override
    @Transactional
    @AuditedMutation(
            action = "platform.client.subscription.special_agreement.create",
            resourceType = "SPECIAL_COMMERCIAL_AGREEMENT")
    public SpecialAgreementModels.Created confirm(
            UUID accountId, UUID actorUserId, SpecialAgreementModels.ConfirmRequest request) {
        accounts.findByIdForSubscriptionUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        if (agreements.existsByAccountIdAndStatusIn(accountId, LIVE)) {
            throw new InvalidStateException(
                    "Account already has a live special agreement. Complete or cancel it first.");
        }
        if (operations.existsByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionChangeStatus.PENDING,
                        SubscriptionChangeStatus.AWAITING_CONFIRMATION))) {
            throw new InvalidStateException(
                    "Account already has an outstanding subscription change. Resolve it first.");
        }
        long catalogRevision = catalogVersions.currentRevision();
        String registryVersion = registryVersions.currentVersion();
        SpecialAgreementSelectionAssessment selection;
        try {
            selection = subscriptions.assessSpecialAgreementForApply(
                    accountId, request.definition().selection(), request.definition().quotaBonuses());
        } catch (RuntimeException exception) {
            if (exception instanceof InvalidRequestException
                    || exception instanceof InvalidStateException
                    || exception instanceof ResourceNotFoundException) {
                throw new StaleSpecialAgreementPreviewException();
            }
            throw exception;
        }
        catalogVersions.requireCurrent(catalogRevision);
        try {
            registryVersions.requireCurrent(registryVersion);
        } catch (InvalidStateException exception) {
            throw new StaleSpecialAgreementPreviewException();
        }
        Instant now = clock.instant();
        Pricing pricing = price(request.definition(), selection, now);
        String fingerprint = fingerprint(selection, request.definition(), pricing);
        tokens.requireValid(
                request.previewToken(), CommercialPreviewKind.SPECIAL_COMMERCIAL_AGREEMENT,
                selection.current().getId(), selection.current().getVersion(), actorUserId,
                catalogRevision, registryVersion, fingerprint,
                StaleSpecialAgreementPreviewException::new);
        if (!selection.allowed() || !selection.conflicts().isEmpty()) {
            throw new InvalidStateException(
                    "Special agreement cannot start until subscription conflicts are resolved.");
        }

        SubscriptionChangeOperation operation = new SubscriptionChangeOperation();
        operation.setAccount(selection.current().getAccount());
        operation.setSourceSubscription(selection.current());
        operation.setTargetPlan(selection.targetPlan());
        operation.setTiming(SubscriptionChangeTiming.IMMEDIATE);
        operation.setStatus(pricing.agreed().amount().signum() > 0
                ? SubscriptionChangeStatus.AWAITING_CONFIRMATION
                : SubscriptionChangeStatus.PENDING);
        operation.setEffectiveAt(request.definition().startsAt());
        operation.setRequestedSelection(selection.selection());
        operation.setBeforeSnapshot(selection.current().getEntitlementSnapshot());
        operation.setTargetSnapshot(selection.targetSnapshot().withEffectivePeriod(
                request.definition().startsAt(), request.definition().endsAt()));
        operation.setCommercialPolicyEvaluation(
                selection.targetSnapshot().commercialPolicyEvaluation());
        operation.setRequestOrigin(SubscriptionChangeOrigin.PLATFORM_ADMIN);
        operation.setRequestedByUserId(actorUserId);
        operation.setRequestReason(request.reason().trim());
        operation = operations.saveAndFlush(operation);

        Money previous = currentMoney(selection);
        SpecialCommercialAgreement agreement = new SpecialCommercialAgreement();
        agreement.setAccount(selection.current().getAccount());
        agreement.setSourceSubscription(selection.current());
        agreement.setTargetPlan(selection.targetPlan());
        agreement.setChangeOperation(operation);
        agreement.setStatus(pricing.agreed().amount().signum() > 0
                ? SpecialAgreementStatus.AWAITING_SETTLEMENT
                : SpecialAgreementStatus.SCHEDULED);
        agreement.setPricingMode(request.definition().pricingMode());
        agreement.setSettlementMode(request.definition().settlementMode());
        agreement.setEndInstruction(request.definition().endInstruction());
        agreement.setStartsAt(request.definition().startsAt());
        agreement.setEndsAt(request.definition().endsAt());
        agreement.setCatalogueCycleAmount(pricing.catalogueCycle().amount());
        agreement.setCatalogueTermAmount(pricing.catalogueTerm() == null
                ? null : pricing.catalogueTerm().amount());
        agreement.setAgreedTermAmount(pricing.agreed().amount());
        agreement.setFollowOnAmount(pricing.followOn() == null
                ? null : pricing.followOn().amount());
        agreement.setPreviousRecurringAmount(previous.amount());
        agreement.setCurrencyCode(pricing.agreed().currencyCode());
        agreement.setRequestedSelection(selection.selection());
        agreement.setQuotaBonuses(request.definition().quotaBonuses());
        agreement.setBeforeSnapshot(selection.current().getEntitlementSnapshot());
        agreement.setTermSnapshot(operation.getTargetSnapshot());
        agreement.setCreatedByUserId(actorUserId);
        agreement.setReason(request.reason().trim());
        agreement = agreements.saveAndFlush(agreement);

        if (pricing.agreed().amount().signum() > 0) {
            if (request.definition().settlementMode() == SpecialAgreementSettlementMode.MANUAL) {
                checkouts.initiateManual(operation, pricing.agreed(), actorUserId);
            } else {
                checkouts.initiate(operation, pricing.agreed(), actorUserId);
            }
        } else {
            checkouts.recordNoPaymentRequired(operation, actorUserId);
            if (!operation.getEffectiveAt().isAfter(now)) {
                activation.activate(operation, operation.getEffectiveAt());
            }
        }
        SpecialAgreementModels.Detail detail = get(accountId, agreement.getId());
        return new SpecialAgreementModels.Created(detail, detail.checkout());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SpecialAgreementModels.Summary> list(
            UUID accountId, SpecialAgreementStatus status, Pageable pageable) {
        Page<SpecialCommercialAgreement> page = status == null
                ? agreements.findAllByAccountId(accountId, pageable)
                : agreements.findAllByAccountIdAndStatus(accountId, status, pageable);
        return page.map(this::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SpecialAgreementModels.Summary> listAll(
            String search, SpecialAgreementStatus status, Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank()
                ? null : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        return agreements.searchAll(status, normalizedSearch, pageable).map(this::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public SpecialAgreementModels.Detail get(UUID accountId, UUID agreementId) {
        return detail(agreements.findByIdAndAccountId(agreementId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SpecialCommercialAgreement", "id", agreementId)));
    }

    @Override
    @Transactional
    @AuditedMutation(
            action = "platform.client.subscription.special_agreement.cancel",
            resourceType = "SPECIAL_COMMERCIAL_AGREEMENT")
    public SpecialAgreementModels.Detail cancel(
            UUID accountId, UUID agreementId, UUID actorUserId, String reason) {
        SpecialCommercialAgreement agreement = locked(accountId, agreementId);
        if (agreement.getStatus() != SpecialAgreementStatus.SCHEDULED
                && agreement.getStatus() != SpecialAgreementStatus.AWAITING_SETTLEMENT) {
            throw new InvalidStateException("Only an unapplied special agreement can be cancelled.");
        }
        var checkout = agreement.getChangeOperation().getCheckout();
        if (checkout != null && checkout.getStatus() == SubscriptionCheckoutStatus.CONFIRMED
                && agreement.agreedMoney().amount().signum() > 0) {
            throw new InvalidStateException(
                    "A settled special agreement cannot be cancelled; create a reviewed correction.");
        }
        if (checkout != null && checkout.getStatus() != SubscriptionCheckoutStatus.CONFIRMED) {
            checkouts.cancelFor(agreement.getChangeOperation());
        }
        SubscriptionChangeOperation operation = agreement.getChangeOperation();
        operation.setStatus(SubscriptionChangeStatus.CANCELLED);
        operation.setCancellationOrigin(SubscriptionChangeOrigin.PLATFORM_ADMIN);
        operation.setCancelledByUserId(actorUserId);
        operation.setCancellationReason(requireReason(reason));
        operation.setCancelledAt(clock.instant());
        operations.save(operation);
        agreement.setStatus(SpecialAgreementStatus.CANCELLED);
        agreement.setCancelledAt(clock.instant());
        agreement.setCancelledByUserId(actorUserId);
        agreement.setCancellationReason(requireReason(reason));
        return detail(agreements.save(agreement));
    }

    @Override
    @Transactional
    @AuditedMutation(
            action = "platform.client.subscription.special_agreement.retry",
            resourceType = "SPECIAL_COMMERCIAL_AGREEMENT")
    public SpecialAgreementModels.Detail retry(
            UUID accountId, UUID agreementId, UUID actorUserId, String reason) {
        SpecialCommercialAgreement agreement = locked(accountId, agreementId);
        lifecycle.retry(agreement, actorUserId, requireReason(reason));
        return detail(agreement);
    }

    @Override
    @Transactional
    @AuditedMutation(
            action = "platform.client.subscription.special_agreement.resolve_manual_review",
            resourceType = "SPECIAL_COMMERCIAL_AGREEMENT")
    public SpecialAgreementModels.Detail resolveManualReview(
            UUID accountId, UUID agreementId, UUID actorUserId, String reason) {
        SpecialCommercialAgreement agreement = locked(accountId, agreementId);
        lifecycle.resolveManualReview(agreement, actorUserId, requireReason(reason));
        return detail(agreement);
    }

    @Override
    @Transactional(readOnly = true)
    public SpecialAgreementModels.Analytics analytics() {
        return new SpecialAgreementModels.Analytics(
                agreements.count(),
                agreements.countByStatus(SpecialAgreementStatus.SCHEDULED),
                agreements.countByStatus(SpecialAgreementStatus.AWAITING_SETTLEMENT),
                agreements.countByStatus(SpecialAgreementStatus.ACTIVE),
                agreements.countByStatus(SpecialAgreementStatus.COMPLETED),
                agreements.countByStatus(SpecialAgreementStatus.CANCELLED),
                agreements.countByStatus(SpecialAgreementStatus.NEEDS_ATTENTION),
                agreements.countByPricingMode(SpecialAgreementPricingMode.COMPLIMENTARY),
                agreements.countBySettlementMode(SpecialAgreementSettlementMode.PROVIDER),
                agreements.countBySettlementMode(SpecialAgreementSettlementMode.MANUAL),
                agreements.aggregateMoneyByCurrency().stream()
                        .map(row -> currencyAnalytics(
                                (String) row[0],
                                (java.math.BigDecimal) row[1],
                                (java.math.BigDecimal) row[2],
                                (java.math.BigDecimal) row[3],
                                (java.math.BigDecimal) row[4],
                                (java.math.BigDecimal) row[5]))
                        .toList());
    }

    private SpecialAgreementModels.CurrencyAnalytics currencyAnalytics(
            String currency, java.math.BigDecimal catalogue, java.math.BigDecimal agreed,
            java.math.BigDecimal complimentary, java.math.BigDecimal invoiced,
            java.math.BigDecimal collected) {
        return new SpecialAgreementModels.CurrencyAnalytics(
                currency, Money.of(catalogue, currency).amount(),
                Money.of(agreed, currency).amount(),
                Money.of(complimentary, currency).amount(),
                Money.of(invoiced, currency).amount(),
                Money.of(collected, currency).amount());
    }

    private Pricing price(
            SpecialAgreementModels.Definition definition,
            SpecialAgreementSelectionAssessment selection,
            Instant evaluatedAt) {
        periods.exact(definition.startsAt(), definition.endsAt());
        if (definition.startsAt().isBefore(evaluatedAt.minus(5, ChronoUnit.MINUTES))) {
            throw new InvalidRequestException("Agreement start cannot be in the past.");
        }
        if (definition.endsAt().isAfter(definition.startsAt().plus(10 * 366L, ChronoUnit.DAYS))) {
            throw new InvalidRequestException("Agreement term cannot exceed ten years.");
        }
        Money cycle = Money.of(selection.catalogueCycleAmount(), selection.currencyCode());
        var completeCycles = periods.completeCycles(
                selection.targetSnapshot().billingCycle(), definition.startsAt(), definition.endsAt());
        Money catalogueTerm = completeCycles.isPresent()
                ? cycle.multiply(completeCycles.getAsLong()) : null;
        Money agreed = switch (definition.pricingMode()) {
            case CATALOGUE_TOTAL -> {
                if (catalogueTerm == null) {
                    throw new InvalidRequestException(
                            "Catalogue pricing requires a term made of complete billing cycles.");
                }
                if (definition.customTotal() != null) {
                    throw new InvalidRequestException(
                            "Catalogue pricing does not accept a custom total.");
                }
                yield catalogueTerm;
            }
            case CUSTOM_TOTAL -> customMoney(
                    definition.customTotal(), definition.currencyCode(), selection.currencyCode(),
                    "Custom agreement total is required.");
            case COMPLIMENTARY -> {
                if (definition.customTotal() != null) {
                    throw new InvalidRequestException(
                            "A complimentary agreement does not accept a custom total.");
                }
                yield Money.zero(selection.currencyCode());
            }
        };
        if ((agreed.amount().signum() == 0)
                != (definition.settlementMode() == SpecialAgreementSettlementMode.NONE)) {
            throw new InvalidRequestException(
                    "Only a zero-total agreement may use no-payment settlement.");
        }
        Money followOn = followOnMoney(definition, cycle, selection.currencyCode());
        return new Pricing(cycle, catalogueTerm, agreed, followOn,
                completeCycles.isPresent() ? completeCycles.getAsLong() : 0L);
    }

    private Money followOnMoney(
            SpecialAgreementModels.Definition definition, Money catalogueCycle, String currency) {
        if (definition.endInstruction() != SpecialAgreementEndInstruction.CONTINUE_REVIEWED_TERMS) {
            if (definition.followOnPricingMode() != null || definition.followOnCustomAmount() != null) {
                throw new InvalidRequestException(
                        "Only continuation accepts follow-on pricing.");
            }
            return null;
        }
        if (definition.followOnPricingMode() == null) {
            throw new InvalidRequestException("Continuation pricing is required.");
        }
        return switch (definition.followOnPricingMode()) {
            case CATALOGUE_TOTAL -> catalogueCycle;
            case CUSTOM_TOTAL -> customMoney(
                    definition.followOnCustomAmount(), definition.currencyCode(), currency,
                    "Custom follow-on amount is required.");
            case COMPLIMENTARY -> Money.zero(currency);
        };
    }

    private Money customMoney(
            java.math.BigDecimal amount, String requestedCurrency, String expectedCurrency,
            String missingMessage) {
        if (amount == null) throw new InvalidRequestException(missingMessage);
        if (requestedCurrency == null
                || !Money.normalizeCurrencyCode(requestedCurrency).equals(expectedCurrency)) {
            throw new InvalidRequestException(
                    "Agreement currency must match the selected subscription currency.");
        }
        Money money = Money.of(amount, expectedCurrency);
        if (money.isNegative()) {
            throw new InvalidRequestException("Agreement amounts cannot be negative.");
        }
        return money;
    }

    private SpecialAgreementModels.Preview toPreview(
            SpecialAgreementSelectionAssessment selection,
            SpecialAgreementModels.Definition definition,
            Pricing pricing,
            long catalogRevision,
            String registryVersion,
            Instant evaluatedAt,
            Instant expiresAt,
            String token) {
        java.math.BigDecimal variance = pricing.catalogueTerm() == null
                ? null : pricing.agreed().subtract(pricing.catalogueTerm()).amount();
        return new SpecialAgreementModels.Preview(
                selection.current().getId(), selection.current().getVersion(), catalogRevision,
                registryVersion, evaluatedAt, expiresAt, token,
                definition.startsAt(), definition.endsAt(), pricing.completeCycles(),
                pricing.catalogueCycle().amount(),
                pricing.catalogueTerm() == null ? null : pricing.catalogueTerm().amount(),
                pricing.agreed().amount(), variance,
                pricing.followOn() == null ? null : pricing.followOn().amount(),
                pricing.agreed().currencyCode(), definition.pricingMode(),
                definition.settlementMode(), definition.endInstruction(),
                selection.currentEntitlements(), selection.targetEntitlements(),
                selection.conflicts(), selection.allowed() && selection.conflicts().isEmpty());
    }

    private String fingerprint(
            SpecialAgreementSelectionAssessment selection,
            SpecialAgreementModels.Definition definition,
            Pricing pricing) {
        StringBuilder value = new StringBuilder(selection.selectionFingerprint());
        append(value, definition.startsAt());
        append(value, definition.endsAt());
        append(value, definition.pricingMode());
        append(value, definition.settlementMode());
        append(value, definition.endInstruction());
        append(value, definition.followOnPricingMode());
        append(value, pricing.catalogueTerm() == null ? null : pricing.catalogueTerm().amount());
        append(value, pricing.agreed().amount());
        append(value, pricing.followOn() == null ? null : pricing.followOn().amount());
        append(value, pricing.agreed().currencyCode());
        definition.quotaBonuses().stream()
                .sorted(Comparator.comparing(item -> item.featureCode() + "\n" + item.resource()))
                .forEach(item -> append(value,
                        item.featureCode() + ":" + item.resource() + ":" + item.quantity()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void append(StringBuilder value, Object item) {
        value.append('\n').append(item == null ? "<none>" : item);
    }

    private Money currentMoney(SpecialAgreementSelectionAssessment selection) {
        Money current = selection.current().currentMoney();
        return current != null ? current : Money.zero(selection.currencyCode());
    }

    private SpecialCommercialAgreement locked(UUID accountId, UUID agreementId) {
        SpecialCommercialAgreement agreement = agreements.findByIdForUpdate(agreementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SpecialCommercialAgreement", "id", agreementId));
        if (!agreement.getAccount().getId().equals(accountId)) {
            throw new ResourceNotFoundException("SpecialCommercialAgreement", "id", agreementId);
        }
        return agreement;
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("Operator reason is required.");
        }
        return reason.trim();
    }

    private SpecialAgreementModels.Summary summary(SpecialCommercialAgreement agreement) {
        return new SpecialAgreementModels.Summary(
                agreement.getId(), agreement.getVersion(), agreement.getAccount().getId(),
                agreement.getAccount().getName(), agreement.getTargetPlan().getCode(),
                agreement.getTargetPlan().getName(), agreement.getStatus(),
                agreement.getPricingMode(), agreement.getSettlementMode(),
                agreement.getEndInstruction(), agreement.getStartsAt(), agreement.getEndsAt(),
                agreement.agreedMoney().amount(), agreement.getCurrencyCode(),
                agreement.getAttentionStage(), actions(agreement), agreement.getCreatedAt());
    }

    private SpecialAgreementModels.Detail detail(SpecialCommercialAgreement agreement) {
        var operation = agreement.getChangeOperation();
        var checkout = operation == null ? null : operation.getCheckout();
        return new SpecialAgreementModels.Detail(
                summary(agreement), agreement.getRequestedSelection(),
                agreement.getTermSnapshot().addOns().stream()
                        .map(item -> new SpecialAgreementModels.SelectedAddOn(
                                item.code(), item.name()))
                        .toList(),
                agreement.getTermSnapshot().quotaPackages().stream()
                        .map(item -> new SpecialAgreementModels.SelectedQuotaPackage(
                                item.code(), item.name(), item.resource(),
                                item.capacityPerUnit(), item.quantity()))
                        .toList(),
                agreement.getQuotaBonuses(),
                moneyAmount(agreement.getCatalogueCycleAmount(), agreement),
                moneyAmount(agreement.getCatalogueTermAmount(), agreement),
                agreement.followOnMoney() == null ? null : agreement.followOnMoney().amount(),
                agreement.previousMoney().amount(),
                agreement.getSourceSubscription().getId(),
                agreement.getResultSubscription() == null ? null : agreement.getResultSubscription().getId(),
                operation == null ? null : operation.getId(), checkout(checkout),
                agreement.getCreatedByUserId(), agreement.getReason(), agreement.getAttentionReason(),
                agreement.getActivatedAt(),
                agreement.getCompletedAt(), agreement.getCancelledAt(),
                agreement.getCancelledByUserId(), agreement.getCancellationReason());
    }

    private java.math.BigDecimal moneyAmount(
            java.math.BigDecimal amount, SpecialCommercialAgreement agreement) {
        return amount == null ? null : Money.of(amount, agreement.getCurrencyCode()).amount();
    }

    private SpecialAgreementModels.Checkout checkout(
            com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout checkout) {
        if (checkout == null) return null;
        return new SpecialAgreementModels.Checkout(
                checkout.getId(), checkout.getStatus(), checkout.money().amount(),
                checkout.money().currencyCode(), checkout.getConfirmationSource(),
                checkout.getConfirmedAt());
    }

    private InvalidRequestException privateCapacityRequest(RuntimeException exception) {
        if (exception instanceof ArithmeticException
                || (exception.getMessage() != null
                && exception.getMessage().startsWith("Offer quota bonus"))) {
            return new InvalidRequestException(
                    "Private capacity must target a finite quota in the selected agreement content.",
                    exception);
        }
        throw exception;
    }

    private SpecialAgreementModels.AvailableActions actions(SpecialCommercialAgreement agreement) {
        var checkout = agreement.getChangeOperation() == null
                ? null : agreement.getChangeOperation().getCheckout();
        boolean positiveSettled = checkout != null
                && checkout.getStatus() == SubscriptionCheckoutStatus.CONFIRMED
                && agreement.agreedMoney().amount().signum() > 0;
        return new SpecialAgreementModels.AvailableActions(
                (agreement.getStatus() == SpecialAgreementStatus.SCHEDULED
                        || agreement.getStatus() == SpecialAgreementStatus.AWAITING_SETTLEMENT)
                        && !positiveSettled,
                agreement.getStatus() == SpecialAgreementStatus.AWAITING_SETTLEMENT
                        && agreement.getSettlementMode() == SpecialAgreementSettlementMode.MANUAL,
                agreement.getStatus() == SpecialAgreementStatus.NEEDS_ATTENTION
                        && agreement.getAttentionStage() == SpecialAgreementAttentionStage.START,
                agreement.getStatus() == SpecialAgreementStatus.NEEDS_ATTENTION
                        && agreement.getAttentionStage() == SpecialAgreementAttentionStage.END
                        && agreement.getEndInstruction() != SpecialAgreementEndInstruction.MANUAL_REVIEW,
                manualReviewDecisionApplied(agreement));
    }

    private boolean manualReviewDecisionApplied(SpecialCommercialAgreement agreement) {
        if (agreement.getStatus() != SpecialAgreementStatus.NEEDS_ATTENTION
                || agreement.getAttentionStage() != SpecialAgreementAttentionStage.END
                || agreement.getEndInstruction() != SpecialAgreementEndInstruction.MANUAL_REVIEW
                || agreement.getResultSubscription() == null) {
            return false;
        }
        var result = agreement.getResultSubscription();
        return result.getStatus() != SubscriptionStatus.SUSPENDED
                || result.getSuspensionCause() != SubscriptionSuspensionCause.AGREEMENT_REVIEW;
    }

    private record Pricing(
            Money catalogueCycle,
            Money catalogueTerm,
            Money agreed,
            Money followOn,
            long completeCycles) {}
}
