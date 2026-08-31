package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAnalyticsInterval;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAttentionType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.dto.CommercialAnalyticsModels;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.infrastructure.CommercialAnalyticsReadRepository;
import com.hiveapp.platform.client.plan.service.CommercialAnalyticsService;
import com.hiveapp.platform.registry.definition.AnalyticsFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.ForbiddenException;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PermissionNode(key = AnalyticsFeature.KEY, description = "Commercial Analytics",
        guard = PermissionNode.Guard.ON)
public class CommercialAnalyticsServiceImpl extends PlatformControlFeatureService
        implements CommercialAnalyticsService {
    private static final int MAX_FACTS = 100_000;
    private static final Duration MAX_RANGE = Duration.ofDays(366);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    private final CommercialAnalyticsReadRepository reads;
    private final SubscriptionChangeOperationRepository operations;
    private final Clock clock;

    public CommercialAnalyticsServiceImpl(
            CommercialAnalyticsReadRepository reads,
            SubscriptionChangeOperationRepository operations,
            Clock clock
    ) {
        this.reads = reads;
        this.operations = operations;
        this.clock = clock;
    }

    @Override
    protected FeatureDefinition featureDefinition() {
        return AnalyticsFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_summary", description = "Read separated commercial summary metrics")
    public CommercialAnalyticsModels.Overview overview(Query request) {
        NormalizedQuery query = normalize(request);
        List<CommercialAnalyticsReadRepository.FinancialFact> facts = reads.financialFacts(
                query.from(), query.until(), query.currencyCode(), query.billingCycle());
        List<CommercialAnalyticsModels.FinancialTotals> financial = financialTotals(facts);
        List<CommercialAnalyticsModels.ConfiguredRecurringValue> configured = reads
                .configuredRecurringValues().stream()
                .filter(value -> query.currencyCode() == null
                        || query.currencyCode().equals(value.currencyCode()))
                .filter(value -> query.billingCycle() == null
                        || query.billingCycle() == value.billingCycle())
                .map(value -> new CommercialAnalyticsModels.ConfiguredRecurringValue(
                        dimension(value.currencyCode(), value.billingCycle()),
                        decimal(value.amount()), value.subscriptions()))
                .toList();
        boolean offerAvailable = has("read_offer_series");
        boolean operationsAvailable = has("read_operations");
        var finality = reads.finalityCounts();
        return new CommercialAnalyticsModels.Overview(
                metadata(query),
                new CommercialAnalyticsModels.Availability(
                        has("read_financial_series"), has("read_subscription_series"),
                        offerAvailable, operationsAvailable),
                financial,
                configured,
                reads.currentSubscriptionCounts(),
                operationsAvailable ? reads.operationsNeedingAttention() : 0,
                reads.graceDeadlinesBetween(query.generatedAt(), query.generatedAt().plus(Duration.ofDays(7))),
                offerAvailable ? reads.offerOutcomeCounts(query.from(), query.until()) : Map.of(),
                new CommercialAnalyticsModels.Finality(
                        finality.pendingPayments(), finality.pendingRefunds(),
                        finality.pendingProviderCommands()));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_financial_series",
            description = "Read invoiced, collected, credited, and refunded series")
    public CommercialAnalyticsModels.FinancialSeries financialSeries(Query request) {
        NormalizedQuery query = normalize(request);
        List<Bucket> buckets = buckets(query);
        List<CommercialAnalyticsReadRepository.FinancialFact> facts = reads.financialFacts(
                query.from(), query.until(), query.currencyCode(), query.billingCycle());
        TreeMap<DimensionKey, List<CommercialAnalyticsModels.FinancialPoint>> dimensions =
                new TreeMap<>();
        Set<DimensionKey> keys = new LinkedHashSet<>();
        facts.forEach(fact -> keys.add(new DimensionKey(fact.currencyCode(), fact.billingCycle())));
        if (keys.isEmpty() && query.currencyCode() != null && query.billingCycle() != null) {
            keys.add(new DimensionKey(query.currencyCode(), query.billingCycle()));
        }
        for (DimensionKey key : keys) {
            List<CommercialAnalyticsModels.FinancialPoint> points = new ArrayList<>();
            for (Bucket bucket : buckets) {
                MoneyAccumulator sum = new MoneyAccumulator();
                facts.stream()
                        .filter(fact -> key.matches(fact.currencyCode(), fact.billingCycle()))
                        .filter(fact -> bucket.contains(fact.occurredAt()))
                        .forEach(sum::add);
                points.add(new CommercialAnalyticsModels.FinancialPoint(
                        bucket.start(), bucket.end(), bucket.provisional(),
                        decimal(sum.invoiced), decimal(sum.collected),
                        decimal(sum.credited), decimal(sum.refunded)));
            }
            dimensions.put(key, points);
        }
        return new CommercialAnalyticsModels.FinancialSeries(
                metadata(query),
                dimensions.entrySet().stream()
                        .map(entry -> new CommercialAnalyticsModels.FinancialDimensionSeries(
                                dimension(entry.getKey().currencyCode(), entry.getKey().billingCycle()),
                                entry.getValue()))
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_subscription_series",
            description = "Read subscription lifecycle and product movement series")
    public CommercialAnalyticsModels.SubscriptionSeries subscriptionSeries(Query request) {
        NormalizedQuery query = normalize(request);
        List<Bucket> buckets = buckets(query);
        var lifecycle = reads.lifecycleFacts(query.from(), query.until());
        List<ProductChange> changes = productChanges(query);
        List<CommercialAnalyticsModels.SubscriptionPoint> points = new ArrayList<>();
        for (Bucket bucket : buckets) {
            LinkedHashMap<String, Long> actions = new LinkedHashMap<>();
            lifecycle.stream().filter(fact -> bucket.contains(fact.occurredAt()))
                    .forEach(fact -> actions.merge(fact.action(), 1L, Long::sum));
            long additions = changes.stream()
                    .filter(change -> change.added() && bucket.contains(change.occurredAt())).count();
            long removals = changes.stream()
                    .filter(change -> !change.added() && bucket.contains(change.occurredAt())).count();
            points.add(new CommercialAnalyticsModels.SubscriptionPoint(
                    bucket.start(), bucket.end(), bucket.provisional(),
                    Map.copyOf(actions), additions, removals));
        }
        Map<ProductKey, MovementAccumulator> movements = new TreeMap<>();
        changes.forEach(change -> movements
                .computeIfAbsent(change.product(), ignored -> new MovementAccumulator())
                .add(change.added()));
        return new CommercialAnalyticsModels.SubscriptionSeries(
                metadata(query), points,
                movements.entrySet().stream()
                        .map(entry -> new CommercialAnalyticsModels.ProductMovement(
                                entry.getKey().type(), entry.getKey().code(),
                                entry.getValue().additions, entry.getValue().removals))
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_offer_series", description = "Read Offer outcome series")
    public CommercialAnalyticsModels.OfferSeries offerSeries(Query request) {
        NormalizedQuery query = normalize(request);
        List<CommercialAnalyticsReadRepository.OfferFact> facts =
                reads.offerFacts(query.from(), query.until());
        List<CommercialAnalyticsModels.OfferPoint> points = buckets(query).stream()
                .map(bucket -> {
                    EnumMap<OfferOutcome, Long> counts = new EnumMap<>(OfferOutcome.class);
                    for (OfferOutcome outcome : OfferOutcome.values()) counts.put(outcome, 0L);
                    facts.stream().filter(fact -> bucket.contains(fact.occurredAt()))
                            .forEach(fact -> counts.merge(
                                    OfferOutcome.valueOf(fact.outcome()), 1L, Long::sum));
                    return new CommercialAnalyticsModels.OfferPoint(
                            bucket.start(), bucket.end(), bucket.provisional(),
                            counts.get(OfferOutcome.RESERVED), counts.get(OfferOutcome.APPLIED),
                            counts.get(OfferOutcome.CANCELLED), counts.get(OfferOutcome.FAILED));
                })
                .toList();
        return new CommercialAnalyticsModels.OfferSeries(metadata(query), points);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "internal_product_holdings", guard = PermissionNode.Guard.OFF)
    public List<CommercialAnalyticsModels.ProductHolding> productHoldings() {
        if (!has("read_subscription_series")) {
            throw new ForbiddenException(
                    "Current product holdings require platform.analytics.read_subscription_series.");
        }
        return reads.productHoldings().stream()
                .map(item -> new CommercialAnalyticsModels.ProductHolding(
                        item.productType(), item.productCode(), item.subscriptions()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_operations",
            description = "Read commercial operational attention with Account identity")
    public Page<CommercialAnalyticsModels.AttentionRow> attention(
            CommercialAttentionType type,
            Pageable pageable
    ) {
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new InvalidRequestException("Analytics attention pages support 1 to 100 rows.");
        }
        return reads.attention(type, pageable).map(fact -> new CommercialAnalyticsModels.AttentionRow(
                fact.type(), fact.recordId(), fact.accountId(), fact.accountName(), fact.status(),
                fact.occurredAt(), fact.dueAt(), decimalOrNull(fact.amount()), fact.currencyCode(),
                fact.reason(), destination(fact.type(), fact.recordId(), fact.accountId())));
    }

    private List<ProductChange> productChanges(NormalizedQuery query) {
        var slice = operations
                .findAllByStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanOrderByUpdatedAtAscIdAsc(
                        SubscriptionChangeStatus.APPLIED, query.from(), query.until(),
                        PageRequest.of(0, MAX_FACTS + 1));
        if (slice.hasNext() || slice.getNumberOfElements() > MAX_FACTS) {
            throw new InvalidRequestException(
                    "The analytics range contains too many subscription changes; choose a narrower range.");
        }
        List<ProductChange> result = new ArrayList<>();
        for (SubscriptionChangeOperation operation : slice.getContent()) {
            Set<ProductKey> before = products(operation.getBeforeSnapshot());
            Set<ProductKey> after = products(operation.getTargetSnapshot());
            before.stream().filter(product -> !after.contains(product))
                    .forEach(product -> result.add(
                            new ProductChange(product, false, operation.getUpdatedAt())));
            after.stream().filter(product -> !before.contains(product))
                    .forEach(product -> result.add(
                            new ProductChange(product, true, operation.getUpdatedAt())));
        }
        return result;
    }

    private Set<ProductKey> products(SubscriptionEntitlementSnapshot snapshot) {
        LinkedHashSet<ProductKey> result = new LinkedHashSet<>();
        if (snapshot == null) return result;
        addProduct(result, CommercialSegmentProductType.PLAN, snapshot.planCode());
        snapshot.addOns().forEach(item ->
                addProduct(result, CommercialSegmentProductType.ADD_ON, item.code()));
        snapshot.quotaPackages().forEach(item ->
                addProduct(result, CommercialSegmentProductType.QUOTA_PACKAGE, item.code()));
        return result;
    }

    private void addProduct(Set<ProductKey> target, CommercialSegmentProductType type, String code) {
        if (code != null && !code.isBlank()) {
            target.add(new ProductKey(type, code.trim().toUpperCase(Locale.ROOT)));
        }
    }

    private List<CommercialAnalyticsModels.FinancialTotals> financialTotals(
            List<CommercialAnalyticsReadRepository.FinancialFact> facts
    ) {
        TreeMap<DimensionKey, MoneyAccumulator> totals = new TreeMap<>();
        facts.forEach(fact -> totals
                .computeIfAbsent(new DimensionKey(fact.currencyCode(), fact.billingCycle()),
                        ignored -> new MoneyAccumulator())
                .add(fact));
        return totals.entrySet().stream()
                .map(entry -> new CommercialAnalyticsModels.FinancialTotals(
                        dimension(entry.getKey().currencyCode(), entry.getKey().billingCycle()),
                        decimal(entry.getValue().invoiced), decimal(entry.getValue().collected),
                        decimal(entry.getValue().credited), decimal(entry.getValue().refunded)))
                .toList();
    }

    private CommercialAnalyticsModels.MoneyDimension dimension(
            String currencyCode,
            BillingCycle billingCycle
    ) {
        return new CommercialAnalyticsModels.MoneyDimension(currencyCode, billingCycle);
    }

    private CommercialAnalyticsModels.Metadata metadata(NormalizedQuery query) {
        return new CommercialAnalyticsModels.Metadata(
                query.generatedAt(), min(query.until(), query.generatedAt()),
                query.from(), query.until(), query.zone().getId(), query.interval(),
                buckets(query).stream().anyMatch(Bucket::provisional));
    }

    private NormalizedQuery normalize(Query request) {
        Instant generatedAt = clock.instant();
        Query source = request == null
                ? new Query(null, null, null, null, null, null) : request;
        Instant until = source.until() == null ? generatedAt : source.until();
        Instant from = source.from() == null ? until.minus(Duration.ofDays(30)) : source.from();
        if (!until.isAfter(from)) throw new InvalidRequestException("until must be after from.");
        if (Duration.between(from, until).compareTo(MAX_RANGE) > 0) {
            throw new InvalidRequestException("Analytics ranges cannot exceed 366 days.");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(source.timezone() == null || source.timezone().isBlank()
                    ? "UTC" : source.timezone().trim());
        } catch (RuntimeException invalid) {
            throw new InvalidRequestException("timezone must be a valid IANA timezone.", invalid);
        }
        String currency = source.currencyCode() == null || source.currencyCode().isBlank()
                ? null : source.currencyCode().trim().toUpperCase(Locale.ROOT);
        if (currency != null && !currency.matches("[A-Z]{3}")) {
            throw new InvalidRequestException("currencyCode must contain 3 letters.");
        }
        return new NormalizedQuery(
                from, until, zone,
                source.interval() == null ? CommercialAnalyticsInterval.DAY : source.interval(),
                currency, source.billingCycle(), generatedAt);
    }

    private List<Bucket> buckets(NormalizedQuery query) {
        List<Bucket> result = new ArrayList<>();
        Instant cursor = query.from();
        while (cursor.isBefore(query.until())) {
            Instant naturalEnd = nextBoundary(cursor, query.zone(), query.interval());
            Instant end = min(naturalEnd, query.until());
            boolean provisional = naturalEnd.isAfter(query.generatedAt())
                    && !cursor.isAfter(query.generatedAt());
            result.add(new Bucket(cursor, end, provisional));
            cursor = end;
        }
        return result;
    }

    private Instant nextBoundary(
            Instant cursor,
            ZoneId zone,
            CommercialAnalyticsInterval interval
    ) {
        ZonedDateTime at = cursor.atZone(zone);
        ZonedDateTime boundary = switch (interval) {
            case DAY -> at.toLocalDate().plusDays(1).atStartOfDay(zone);
            case WEEK -> at.toLocalDate()
                    .with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(zone);
            case MONTH -> at.toLocalDate().withDayOfMonth(1).plusMonths(1)
                    .atTime(LocalTime.MIDNIGHT).atZone(zone);
        };
        return boundary.toInstant();
    }

    private boolean has(String action) {
        return PermissionGuard.has(new Permission(AnalyticsFeature.CODE + "." + action));
    }

    private String destination(CommercialAttentionType type, java.util.UUID recordId,
                               java.util.UUID accountId) {
        return switch (type) {
            case PAST_DUE, SUSPENDED, CHANGE_NEEDS_ATTENTION ->
                    "/admin/subscriptions/" + accountId;
            case OPEN_INVOICE -> "/admin/billing/invoices/" + recordId;
        };
    }

    private String decimal(BigDecimal value) {
        return value.setScale(4).toPlainString();
    }

    private String decimalOrNull(BigDecimal value) {
        return value == null ? null : decimal(value);
    }

    private Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private enum OfferOutcome { RESERVED, APPLIED, CANCELLED, FAILED }

    private record NormalizedQuery(
            Instant from,
            Instant until,
            ZoneId zone,
            CommercialAnalyticsInterval interval,
            String currencyCode,
            BillingCycle billingCycle,
            Instant generatedAt
    ) {}

    private record Bucket(Instant start, Instant end, boolean provisional) {
        boolean contains(Instant value) {
            return !value.isBefore(start) && value.isBefore(end);
        }
    }

    private record DimensionKey(String currencyCode, BillingCycle billingCycle)
            implements Comparable<DimensionKey> {
        boolean matches(String currency, BillingCycle cycle) {
            return Objects.equals(currencyCode, currency) && billingCycle == cycle;
        }

        @Override
        public int compareTo(DimensionKey other) {
            int currency = currencyCode.compareTo(other.currencyCode);
            return currency != 0 ? currency : billingCycle.compareTo(other.billingCycle);
        }
    }

    private record ProductKey(CommercialSegmentProductType type, String code)
            implements Comparable<ProductKey> {
        @Override
        public int compareTo(ProductKey other) {
            int typeOrder = type.compareTo(other.type);
            return typeOrder != 0 ? typeOrder : code.compareTo(other.code);
        }
    }

    private record ProductChange(ProductKey product, boolean added, Instant occurredAt) {}

    private static final class MoneyAccumulator {
        private BigDecimal invoiced = ZERO;
        private BigDecimal collected = ZERO;
        private BigDecimal credited = ZERO;
        private BigDecimal refunded = ZERO;

        void add(CommercialAnalyticsReadRepository.FinancialFact fact) {
            switch (fact.metric()) {
                case "INVOICED" -> invoiced = invoiced.add(fact.amount());
                case "COLLECTED" -> collected = collected.add(fact.amount());
                case "CREDITED" -> credited = credited.add(fact.amount());
                case "REFUNDED" -> refunded = refunded.add(fact.amount());
                default -> throw new IllegalStateException("Unknown financial metric: " + fact.metric());
            }
        }
    }

    private static final class MovementAccumulator {
        private long additions;
        private long removals;

        void add(boolean added) {
            if (added) additions++; else removals++;
        }
    }
}
