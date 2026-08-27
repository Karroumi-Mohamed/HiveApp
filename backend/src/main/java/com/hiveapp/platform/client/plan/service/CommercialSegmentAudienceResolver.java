package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegment;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegmentActivation;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegmentProductSelection;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCurrentHolding;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentActivationRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentRepository;
import com.hiveapp.platform.client.plan.dto.CommercialSegmentViews;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.StaleActivationPreviewException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Portable database-backed resolver for explicit and closed typed Segment definitions. */
@Service
@RequiredArgsConstructor
public class CommercialSegmentAudienceResolver {

    public static final int ACTIVATION_ACCOUNT_LIMIT = 10_000;
    public static final int SAMPLE_LIMIT = 25;

    private final AccountRepository accountRepository;
    private final CommercialSegmentRepository segmentRepository;
    private final CommercialSegmentActivationRepository activationRepository;

    /** Count-only evaluation for list/detail workflows; never resolves IDs or mints evidence. */
    @Transactional(readOnly = true)
    public long count(CommercialSegment segment) {
        return accountRepository.count(specification(segment));
    }

    /** Resolves one definition in a bounded pair of database queries (content + count). */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Evaluation evaluate(CommercialSegment segment, Instant evaluatedAt) {
        var page = accountRepository.findAll(specification(segment), PageRequest.of(
                0, ACTIVATION_ACCOUNT_LIMIT + 1, Sort.by(Sort.Direction.ASC, "id")));
        List<UUID> ids = page.getContent().stream().map(Account::getId).distinct().sorted().toList();
        long total = page.getTotalElements();
        boolean withinLimit = total <= ACTIVATION_ACCOUNT_LIMIT;
        if (withinLimit && ids.size() != total) {
            throw new StaleActivationPreviewException();
        }
        List<UUID> acceptedIds = withinLimit ? ids : List.of();
        return new Evaluation(total, acceptedIds,
                ids.stream().limit(SAMPLE_LIMIT).toList(),
                fingerprint(segment, total, acceptedIds), evaluatedAt, withinLimit);
    }

    /** Returns the latest immutable activation for an active exact Segment revision. */
    @Transactional(readOnly = true)
    public FrozenAudience resolveFrozenReference(String reference) {
        CommercialSegment segment = resolveReference(reference);
        if (segment.getStatus() != CommercialSegmentStatus.ACTIVE) {
            return FrozenAudience.unavailable();
        }
        return activationRepository.findLatestWithAccounts(segment.getId())
                .map(activation -> new FrozenAudience(segment.getId(), activation.getId(),
                        activation.getAccountIds().stream().sorted().toList(), true))
                .orElseGet(FrozenAudience::unavailable);
    }

    @Transactional(readOnly = true)
    public boolean isResolvable(String reference) {
        return resolveFrozenReference(reference).available();
    }

    /** Resolves case-insensitive/operator-supplied input to the immutable canonical revision code. */
    @Transactional(readOnly = true)
    public String requireCanonicalActiveReference(String reference) {
        CommercialSegment segment = resolveReference(reference);
        if (segment.getStatus() != CommercialSegmentStatus.ACTIVE
                || !activationRepository.existsBySegment_Id(segment.getId())) {
            throw new InvalidRequestException(
                    "Segment reference is not an active revision with an immutable activation audience.");
        }
        return segment.getCode();
    }

    @Transactional(readOnly = true)
    public List<CommercialSegmentViews.AudienceIdentity> identities(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Map<UUID, Account> accounts = accountRepository.findAllWithOwnerByIdIn(ids).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        return ids.stream().distinct().map(id -> {
            Account account = accounts.get(id);
            if (account == null) {
                return new CommercialSegmentViews.AudienceIdentity(id, null, null, null, false);
            }
            return new CommercialSegmentViews.AudienceIdentity(
                    id, account.getName(), account.getSlug(),
                    account.getOwner() == null ? null : account.getOwner().getEmail(), account.isActive());
        }).toList();
    }

    private CommercialSegment resolveReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new InvalidRequestException("Segment reference is required.");
        }
        String normalized = reference.trim();
        try {
            UUID id = UUID.fromString(normalized);
            return segmentRepository.findDetailById(id)
                    .orElseThrow(() -> new InvalidRequestException(
                            "Segment reference is not an active published revision."));
        } catch (IllegalArgumentException notUuid) {
            return segmentRepository.findByCode(normalized.toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new InvalidRequestException(
                            "Segment reference is not an active published revision."));
        }
    }

    private Specification<Account> specification(CommercialSegment segment) {
        if (segment.getKind() == CommercialSegmentKind.EXPLICIT_ACCOUNTS) {
            Set<UUID> ids = segment.getExplicitAccounts().stream().map(Account::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return (root, query, cb) -> root.get("id").in(ids);
        }
        return (root, query, cb) -> {
            List<Predicate> accountPredicates = new ArrayList<>();
            if (segment.getAccountCreatedFrom() != null) {
                accountPredicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"), segment.getAccountCreatedFrom()));
            }
            if (segment.getAccountCreatedUntil() != null) {
                accountPredicates.add(cb.lessThan(
                        root.get("createdAt"), segment.getAccountCreatedUntil()));
            }

            boolean needsSubscription = !segment.getCurrentPlanRevisionIds().isEmpty()
                    || !segment.getSubscriptionStatuses().isEmpty()
                    || !segment.getCurrencyCodes().isEmpty()
                    || !segment.getBillingCycles().isEmpty()
                    || !segment.getProductHoldings().isEmpty();
            if (needsSubscription) {
                Subquery<Long> current = query.subquery(Long.class);
                Root<Subscription> subscription = current.from(Subscription.class);
                List<Predicate> subscriptionPredicates = new ArrayList<>();
                subscriptionPredicates.add(cb.equal(subscription.get("currentAccountId"), root.get("id")));
                if (!segment.getCurrentPlanRevisionIds().isEmpty()) {
                    subscriptionPredicates.add(subscription.get("plan").get("id")
                            .in(segment.getCurrentPlanRevisionIds()));
                }
                if (!segment.getSubscriptionStatuses().isEmpty()) {
                    subscriptionPredicates.add(subscription.get("status")
                            .in(segment.getSubscriptionStatuses()));
                }
                if (!segment.getCurrencyCodes().isEmpty()) {
                    subscriptionPredicates.add(subscription.get("snapshotCurrencyCode")
                            .in(segment.getCurrencyCodes()));
                }
                if (!segment.getBillingCycles().isEmpty()) {
                    subscriptionPredicates.add(subscription.get("snapshotBillingCycle")
                            .in(segment.getBillingCycles()));
                }
                if (!segment.getProductHoldings().isEmpty()) {
                    Join<Subscription, SubscriptionCurrentHolding> holding =
                            subscription.join("currentHoldings");
                    List<Predicate> alternatives = segment.getProductHoldings().stream()
                            .sorted(Comparator.comparing((CommercialSegmentProductSelection item) ->
                                            item.getType().name())
                                    .thenComparing(CommercialSegmentProductSelection::getCode))
                            .map(item -> cb.and(
                                    cb.equal(holding.get("productType"), item.getType()),
                                    cb.equal(holding.get("productCode"), item.getCode())))
                            .toList();
                    subscriptionPredicates.add(cb.or(alternatives.toArray(Predicate[]::new)));
                }
                current.select(cb.literal(1L)).where(
                        subscriptionPredicates.toArray(Predicate[]::new));
                accountPredicates.add(cb.exists(current));
            }
            return cb.and(accountPredicates.toArray(Predicate[]::new));
        };
    }

    private String fingerprint(CommercialSegment segment, long total, List<UUID> ids) {
        String definition = String.join("|",
                segment.getId().toString(),
                Long.toString(segment.getVersion()),
                segment.getKind().name(),
                segment.getCurrentPlanRevisionIds().stream().sorted().map(UUID::toString)
                        .collect(Collectors.joining(",")),
                segment.getSubscriptionStatuses().stream().map(Enum::name).sorted()
                        .collect(Collectors.joining(",")),
                segment.getCurrencyCodes().stream().sorted().collect(Collectors.joining(",")),
                segment.getBillingCycles().stream().map(Enum::name).sorted()
                        .collect(Collectors.joining(",")),
                String.valueOf(segment.getAccountCreatedFrom()),
                String.valueOf(segment.getAccountCreatedUntil()),
                segment.getProductHoldings().stream()
                        .map(item -> item.getType().name() + ":" + item.getCode()).sorted()
                        .collect(Collectors.joining(",")),
                Long.toString(total),
                ids.stream().map(UUID::toString).collect(Collectors.joining(",")));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(definition.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record Evaluation(
            long total,
            List<UUID> accountIds,
            List<UUID> sampleIds,
            String fingerprint,
            Instant evaluatedAt,
            boolean withinLimit
    ) {
        public Evaluation {
            accountIds = List.copyOf(accountIds);
            sampleIds = List.copyOf(sampleIds);
        }

        public boolean activatable() {
            return withinLimit && total > 0;
        }
    }

    public record FrozenAudience(
            UUID segmentId,
            UUID activationId,
            List<UUID> accountIds,
            boolean available
    ) {
        public FrozenAudience { accountIds = List.copyOf(accountIds); }
        static FrozenAudience unavailable() {
            return new FrozenAudience(null, null, List.of(), false);
        }
    }
}
