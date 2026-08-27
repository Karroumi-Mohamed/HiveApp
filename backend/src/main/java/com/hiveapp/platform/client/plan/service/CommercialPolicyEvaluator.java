package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyDecisionOutcome;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicyEffect;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyRepository;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyDecisionSnapshot;
import com.hiveapp.shared.exception.InvalidStateException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministically evaluates active policy revisions over their latest immutable activation
 * audience. Definition activation never mutates an Account; this service only produces terms for
 * an explicit subscription review.
 */
@Service
public class CommercialPolicyEvaluator {

    private static final int MAX_APPLICABLE_POLICIES = 100;
    private static final int MAX_APPLICABLE_EFFECTS = 500;

    private final CommercialPolicyRepository policyRepository;

    public CommercialPolicyEvaluator(CommercialPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Transactional(readOnly = true)
    public Evaluation evaluate(UUID accountId, Instant evaluatedAt) {
        Objects.requireNonNull(accountId, "Account is required");
        Objects.requireNonNull(evaluatedAt, "Evaluation time is required");
        List<CommercialPolicyRepository.ApplicablePolicyReference> references =
                policyRepository.findApplicablePolicyReferences(
                        accountId, evaluatedAt, PageRequest.of(0, MAX_APPLICABLE_POLICIES + 1));
        if (references.size() > MAX_APPLICABLE_POLICIES) {
            throw new InvalidStateException(
                    "More than 100 commercial policies apply to this Account; narrow the policy audience.");
        }
        if (references.isEmpty()) return Evaluation.empty(evaluatedAt);

        Map<UUID, UUID> activationIds = references.stream().collect(Collectors.toMap(
                CommercialPolicyRepository.ApplicablePolicyReference::getPolicyId,
                CommercialPolicyRepository.ApplicablePolicyReference::getActivationId));
        List<CommercialPolicy> policies = policyRepository.findAllDetailsByIdIn(activationIds.keySet());
        if (policies.size() != activationIds.size()) {
            throw new InvalidStateException("An active commercial policy revision disappeared during evaluation.");
        }
        List<Candidate> candidates = policies.stream()
                .flatMap(policy -> policy.getEffects().stream()
                        .map(effect -> new Candidate(policy, effect, activationIds.get(policy.getId()))))
                .sorted(candidateOrder())
                .toList();
        if (candidates.size() > MAX_APPLICABLE_EFFECTS) {
            throw new InvalidStateException(
                    "More than 500 commercial policy effects apply to this Account; narrow the policy audience.");
        }

        WinnerSet fixedPrices = choose(candidates.stream()
                .filter(candidate -> candidate.effect().getType()
                        == CommercialPolicyEffectType.FIXED_SUBSCRIPTION_PRICE).toList());
        WinnerSet discounts = choose(candidates.stream()
                .filter(candidate -> candidate.effect().getType() == CommercialPolicyEffectType.FIXED_DISCOUNT
                        || candidate.effect().getType() == CommercialPolicyEffectType.PERCENTAGE_DISCOUNT)
                .toList());
        Map<ProductKey, WinnerSet> products = groupedWinners(candidates.stream()
                .filter(candidate -> isProductDecision(candidate.effect().getType()))
                .filter(candidate -> candidate.effect().getProductType() != null)
                .toList(), candidate -> new ProductKey(
                        candidate.effect().getProductType(), candidate.effect().productId()));
        Map<QuotaKey, WinnerSet> quotas = groupedWinners(candidates.stream()
                .filter(candidate -> candidate.effect().getType()
                        == CommercialPolicyEffectType.ADDITIVE_QUOTA_BONUS)
                .toList(), candidate -> new QuotaKey(
                        candidate.effect().getFeature().getCode(), candidate.effect().getQuotaResource()));
        Map<String, WinnerSet> blockedFeatures = groupedWinners(candidates.stream()
                .filter(candidate -> candidate.effect().getType() == CommercialPolicyEffectType.BLOCK_FEATURE)
                .toList(), candidate -> candidate.effect().getFeature().getCode());

        return new Evaluation(evaluatedAt, fixedPrices, discounts, products, quotas, blockedFeatures);
    }

    private boolean isProductDecision(CommercialPolicyEffectType type) {
        return type == CommercialPolicyEffectType.ALLOW_PRODUCT_SELECTION
                || type == CommercialPolicyEffectType.BLOCK_PRODUCT_SELECTION
                || type == CommercialPolicyEffectType.GRANT_ADD_ON
                || type == CommercialPolicyEffectType.GRANT_QUOTA_PACKAGE;
    }

    private <K> Map<K, WinnerSet> groupedWinners(
            Collection<Candidate> candidates,
            Function<Candidate, K> classifier
    ) {
        Map<K, List<Candidate>> grouped = candidates.stream().collect(Collectors.groupingBy(
                classifier, LinkedHashMap::new, Collectors.toList()));
        Map<K, WinnerSet> result = new LinkedHashMap<>();
        grouped.forEach((key, values) -> result.put(key, choose(values)));
        return Map.copyOf(result);
    }

    private WinnerSet choose(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return WinnerSet.empty();
        List<Candidate> ordered = candidates.stream().sorted(candidateOrder()).toList();
        return new WinnerSet(ordered.getFirst(), ordered.subList(1, ordered.size()));
    }

    private Comparator<Candidate> candidateOrder() {
        return (left, right) -> CommercialPolicyPrecedence.deterministicOrder().compare(
                new CommercialPolicyPrecedence.Candidate(left.policy(), left.effect()),
                new CommercialPolicyPrecedence.Candidate(right.policy(), right.effect()));
    }

    public record ProductKey(CommercialPolicyProductType type, UUID id) {
        public ProductKey {
            Objects.requireNonNull(type, "Product type is required");
            Objects.requireNonNull(id, "Product id is required");
        }
    }

    public record QuotaKey(String featureCode, String resource) {}

    public record Candidate(
            CommercialPolicy policy,
            CommercialPolicyEffect effect,
            UUID activationId
    ) {
        public Candidate {
            Objects.requireNonNull(policy, "Policy is required");
            Objects.requireNonNull(effect, "Effect is required");
            Objects.requireNonNull(activationId, "Activation is required");
        }

        public CommercialPolicyDecisionSnapshot decision(
                CommercialPolicyDecisionOutcome outcome,
                BigDecimal evaluatedAmount,
                String evaluatedCurrencyCode,
                String explanation
        ) {
            return new CommercialPolicyDecisionSnapshot(
                    policy.getId(), activationId, policy.getLineageId(), policy.getRevisionNumber(),
                    policy.getCode(), policy.getName(), policy.getTargetKind(), policy.getPriority(),
                    effect.getId(), effect.getEffectOrder(), effect.getType(), effect.getProductType(),
                    effect.productId(), effect.productCode(),
                    effect.getFeature() == null ? null : effect.getFeature().getCode(),
                    effect.getQuotaResource(), effect.getQuantityDelta(), effect.getAmount(),
                    effect.getCurrencyCode(), effect.getPercentage(), effect.getMaximumAmount(),
                    effect.getMaximumCurrencyCode(), outcome, evaluatedAmount,
                    evaluatedCurrencyCode, explanation);
        }
    }

    public record WinnerSet(Candidate winner, List<Candidate> rejected) {
        public WinnerSet {
            rejected = rejected == null ? List.of() : List.copyOf(rejected);
        }

        public static WinnerSet empty() {
            return new WinnerSet(null, List.of());
        }

        public Optional<Candidate> winnerOptional() {
            return Optional.ofNullable(winner);
        }

        public List<Candidate> ordered() {
            if (winner == null) return List.of();
            List<Candidate> values = new ArrayList<>();
            values.add(winner);
            values.addAll(rejected);
            return List.copyOf(values);
        }

        public List<CommercialPolicyDecisionSnapshot> catalogDecisions() {
            List<CommercialPolicyDecisionSnapshot> decisions = new ArrayList<>();
            if (winner != null) {
                decisions.add(winner.decision(
                        CommercialPolicyDecisionOutcome.AVAILABLE, null, null,
                        "This effect wins for the Account at the evaluated time."));
            }
            rejected.forEach(candidate -> decisions.add(candidate.decision(
                    CommercialPolicyDecisionOutcome.REJECTED_LOWER_PRECEDENCE, null, null,
                    "A more specific or higher-priority policy effect wins.")));
            return List.copyOf(decisions);
        }
    }

    public record Evaluation(
            Instant evaluatedAt,
            WinnerSet fixedPrice,
            WinnerSet discount,
            Map<ProductKey, WinnerSet> products,
            Map<QuotaKey, WinnerSet> quotaBonuses,
            Map<String, WinnerSet> blockedFeatures
    ) {
        public Evaluation {
            products = Map.copyOf(products);
            quotaBonuses = Map.copyOf(quotaBonuses);
            blockedFeatures = Map.copyOf(blockedFeatures);
        }

        public static Evaluation empty(Instant evaluatedAt) {
            return new Evaluation(evaluatedAt, WinnerSet.empty(), WinnerSet.empty(),
                    Map.of(), Map.of(), Map.of());
        }

        public WinnerSet product(CommercialPolicyProductType type, UUID id) {
            return products.getOrDefault(new ProductKey(type, id), WinnerSet.empty());
        }

        public boolean allowsDirectSelection(CommercialPolicyProductType type, UUID id) {
            Candidate winner = product(type, id).winner();
            if (winner == null) return false;
            return winner.effect().getType() == CommercialPolicyEffectType.ALLOW_PRODUCT_SELECTION
                    || winner.effect().getType() == CommercialPolicyEffectType.GRANT_ADD_ON
                    || winner.effect().getType() == CommercialPolicyEffectType.GRANT_QUOTA_PACKAGE;
        }

        public boolean blocksProduct(CommercialPolicyProductType type, UUID id) {
            Candidate winner = product(type, id).winner();
            return winner != null
                    && winner.effect().getType() == CommercialPolicyEffectType.BLOCK_PRODUCT_SELECTION;
        }

        public List<CommercialPolicyDecisionSnapshot> catalogDecisions() {
            List<CommercialPolicyDecisionSnapshot> decisions = new ArrayList<>();
            decisions.addAll(fixedPrice.catalogDecisions());
            decisions.addAll(discount.catalogDecisions());
            products.entrySet().stream().sorted(Map.Entry.comparingByKey(
                            Comparator.comparing((ProductKey key) -> key.type().name())
                                    .thenComparing(ProductKey::id)))
                    .forEach(entry -> decisions.addAll(entry.getValue().catalogDecisions()));
            quotaBonuses.entrySet().stream().sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(QuotaKey::featureCode).thenComparing(QuotaKey::resource)))
                    .forEach(entry -> decisions.addAll(entry.getValue().catalogDecisions()));
            blockedFeatures.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> decisions.addAll(entry.getValue().catalogDecisions()));
            return List.copyOf(decisions);
        }
    }
}
