package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionAvailabilityReason;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionResolutionSource;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.constant.RetainedEntitlementState;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.ExtensionAvailabilityIssue;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnOverrideChoiceDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrideChoicePage;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageOverrideChoiceDto;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionOverrideChoiceService {

    private static final int MAX_SELECTIONS = 100;
    private static final int MAX_SEARCH_LENGTH = 160;
    private static final Pattern COMMERCIAL_CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final CommercialCatalogResolver commercialCatalogResolver;

    @Transactional(readOnly = true)
    public SubscriptionOverrideChoicePage<SubscriptionAddOnOverrideChoiceDto> chooseAddOns(
            Subscription subscription,
            String search,
            Collection<String> proposedAddOnCodes,
            Pageable pageable
    ) {
        Context context = context(subscription, proposedAddOnCodes);
        String term = normalizeSearch(search);
        Set<String> retainedCodes = context.snapshot().addOns().stream()
                .map(item -> item.code()).collect(Collectors.toSet());
        Specification<AddOn> specification = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), AddOnStatus.ACTIVE));
            if (term != null) {
                String pattern = "%" + term.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }
            if (!retainedCodes.isEmpty()) predicates.add(cb.not(root.get("code").in(retainedCodes)));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<AddOn> candidates = addOnRepository.findAll(specification, pageable);
        CommercialCatalogResolver.OverrideCandidateResolution resolution = commercialCatalogResolver
                .resolveOverrideCandidates(
                        context.planId(), context.tuple(),
                        candidates.getContent().stream().map(AddOn::getId).toList(), List.of(),
                        context.proposedAddOnCodes(), context.retainedPackageCodes(), context.retained());

        List<SubscriptionAddOnOverrideChoiceDto> content = candidates.getContent().stream()
                .map(candidate -> resolution.addOns().get(candidate.getCode()))
                .filter(java.util.Objects::nonNull)
                .filter(CommercialCatalogResolver.AddOnCandidateDecision::selectable)
                .map(decision -> selectableAddOn(decision.product()))
                .toList();
        return new SubscriptionOverrideChoicePage<>(
                content, retainedAddOns(context, resolution.plan()),
                pageable.getPageNumber(), pageable.getPageSize(), candidates.hasNext());
    }

    @Transactional(readOnly = true)
    public SubscriptionOverrideChoicePage<SubscriptionQuotaPackageOverrideChoiceDto> chooseQuotaPackages(
            Subscription subscription,
            String search,
            String featureCode,
            String resource,
            Collection<String> proposedAddOnCodes,
            Pageable pageable
    ) {
        Context context = context(subscription, proposedAddOnCodes);
        String term = normalizeSearch(search);
        String feature = normalizeOptional(featureCode, "featureCode", 160);
        String quotaResource = normalizeOptional(resource, "resource", 100);
        Set<String> retainedCodes = context.snapshot().quotaPackages().stream()
                .map(item -> item.code()).collect(Collectors.toSet());
        Specification<QuotaPackage> specification = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), QuotaPackageStatus.ACTIVE));
            if (term != null) {
                String pattern = "%" + term.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }
            if (feature != null) {
                predicates.add(cb.equal(cb.lower(root.join("feature").get("code")),
                        feature.toLowerCase(Locale.ROOT)));
            }
            if (quotaResource != null) {
                predicates.add(cb.equal(cb.lower(root.get("resource")),
                        quotaResource.toLowerCase(Locale.ROOT)));
            }
            if (!retainedCodes.isEmpty()) predicates.add(cb.not(root.get("code").in(retainedCodes)));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<QuotaPackage> candidates = quotaPackageRepository.findAll(specification, pageable);
        CommercialCatalogResolver.OverrideCandidateResolution resolution = commercialCatalogResolver
                .resolveOverrideCandidates(
                        context.planId(), context.tuple(), List.of(),
                        candidates.getContent().stream().map(QuotaPackage::getId).toList(),
                        context.proposedAddOnCodes(), context.retainedPackageCodes(), context.retained());
        List<SubscriptionQuotaPackageOverrideChoiceDto> content = candidates.getContent().stream()
                .map(candidate -> resolution.quotaPackages().get(candidate.getCode()))
                .filter(java.util.Objects::nonNull)
                .filter(CommercialCatalogResolver.QuotaPackageCandidateDecision::selectable)
                .map(decision -> selectablePackage(decision.product()))
                .toList();
        return new SubscriptionOverrideChoicePage<>(
                content, retainedPackages(context, resolution.plan()),
                pageable.getPageNumber(), pageable.getPageSize(), candidates.hasNext());
    }

    private Context context(
            Subscription subscription,
            Collection<String> proposedAddOnCodes
    ) {
        SubscriptionEntitlementSnapshot snapshot = subscriptionSnapshotReader
                .read(subscription.getEntitlementSnapshot())
                .orElseThrow(() -> new InvalidStateException(
                        "The current subscription has no entitlement snapshot."));
        SubscriptionOverrides overrides = subscriptionOverrideReader.read(subscription.getCustomOverrides());
        Set<String> proposed = proposedAddOnCodes == null
                ? overrides.addOnCodes() : normalizeCodes(proposedAddOnCodes);
        if (proposed.size() > MAX_SELECTIONS || snapshot.addOns().size() > MAX_SELECTIONS
                || snapshot.quotaPackages().size() > MAX_SELECTIONS) {
            throw new InvalidRequestException(
                    "Subscription override selections are limited to 100 products per type.");
        }
        Map<String, Integer> retainedPackages = snapshot.quotaPackages().stream()
                .collect(Collectors.toMap(item -> item.code(), item -> item.quantity()));
        CommercialCatalogResolver.RetainedSelection retained =
                new CommercialCatalogResolver.RetainedSelection(
                        snapshot.addOns().stream().map(item -> item.code()).collect(Collectors.toSet()),
                        retainedPackages, snapshot.planPriceEntryId());
        return new Context(
                subscription.getPlan().getId(), snapshot,
                new CommercialCatalogResolver.PriceTuple(
                        snapshot.currencyCode(), snapshot.billingCycle()),
                Set.copyOf(proposed), Set.copyOf(retainedPackages.keySet()), retained);
    }

    private SubscriptionAddOnOverrideChoiceDto selectableAddOn(
            CommercialCatalogResolver.AddOnResolution result) {
        ProductPrice price = firstPrice(result.prices());
        return new SubscriptionAddOnOverrideChoiceDto(
                result.addOn().getId(), result.code(), result.addOn().getName(),
                result.addOn().getFeatures().stream().map(item -> item.getFeature().getCode())
                        .collect(Collectors.toCollection(java.util.TreeSet::new)),
                result.dependencyClosureCodes(), price.getId(), price.getAmount(),
                price.getCurrencyCode(), price.getBillingCycle(), RetainedEntitlementState.SELECTABLE,
                false, false, List.of());
    }

    private SubscriptionQuotaPackageOverrideChoiceDto selectablePackage(
            CommercialCatalogResolver.QuotaPackageResolution result) {
        ProductPrice price = firstPrice(result.prices());
        QuotaPackage item = result.quotaPackage();
        return new SubscriptionQuotaPackageOverrideChoiceDto(
                item.getId(), item.getCode(), item.getName(), item.getFeature().getCode(),
                item.getResource(), item.getCapacityPerUnit(), item.isRepeatable(),
                item.getMaximumQuantity(), null, result.requiredAddOnCodes(), price.getId(),
                price.getAmount(), price.getCurrencyCode(), price.getBillingCycle(),
                RetainedEntitlementState.SELECTABLE, false, false, true, List.of());
    }

    private List<SubscriptionAddOnOverrideChoiceDto> retainedAddOns(
            Context context,
            CommercialCatalogResolver.PlanResolution resolution
    ) {
        Map<String, CommercialCatalogResolver.AddOnResolution> current = resolution.addOns().stream()
                .collect(Collectors.toMap(CommercialCatalogResolver.AddOnResolution::code, item -> item));
        return context.snapshot().addOns().stream().sorted(Comparator.comparing(item -> item.code()))
                .map(held -> {
                    var product = current.get(held.code());
                    boolean selectable = resolution.selectable() && product != null && product.selectable();
                    RetainedEntitlementState state = selectable ? RetainedEntitlementState.SELECTABLE
                            : product == null ? RetainedEntitlementState.HISTORICAL_ONLY
                            : RetainedEntitlementState.RETAINED_ONLY;
                    return new SubscriptionAddOnOverrideChoiceDto(
                            product == null ? null : product.addOn().getId(), held.code(), held.name(),
                            Set.copyOf(held.featureCodes()),
                            product == null ? Set.of() : product.dependencyClosureCodes(),
                            held.priceEntryId(), held.price(), held.currencyCode(), held.billingCycle(),
                            state, true, true,
                            retainedIssues(
                                    resolution.issues(),
                                    product == null ? missing(held.code()) : product.issues()));
                }).toList();
    }

    private List<SubscriptionQuotaPackageOverrideChoiceDto> retainedPackages(
            Context context,
            CommercialCatalogResolver.PlanResolution resolution
    ) {
        Map<String, CommercialCatalogResolver.QuotaPackageResolution> current =
                resolution.quotaPackages().stream().collect(Collectors.toMap(
                        CommercialCatalogResolver.QuotaPackageResolution::code, item -> item));
        return context.snapshot().quotaPackages().stream()
                .sorted(Comparator.comparing(item -> item.code()))
                .map(held -> {
                    var product = current.get(held.code());
                    boolean selectable = resolution.selectable() && product != null && product.selectable();
                    RetainedEntitlementState state = selectable ? RetainedEntitlementState.SELECTABLE
                            : product == null ? RetainedEntitlementState.HISTORICAL_ONLY
                            : RetainedEntitlementState.RETAINED_ONLY;
                    QuotaPackage currentItem = product == null ? null : product.quotaPackage();
                    return new SubscriptionQuotaPackageOverrideChoiceDto(
                            currentItem == null ? null : currentItem.getId(), held.code(), held.name(),
                            held.featureCode(), held.resource(), held.capacityPerUnit(),
                            currentItem != null && currentItem.isRepeatable(),
                            currentItem == null ? held.quantity() : currentItem.getMaximumQuantity(),
                            held.quantity(), product == null ? Set.of() : product.requiredAddOnCodes(),
                            held.priceEntryId(), held.unitPrice(), held.currencyCode(), held.billingCycle(),
                            state, true, true, selectable,
                            retainedIssues(
                                    resolution.issues(),
                                    product == null ? missing(held.code()) : product.issues()));
                }).toList();
    }

    private List<ExtensionAvailabilityIssue> retainedIssues(
            List<ExtensionAvailabilityIssue> planIssues,
            List<ExtensionAvailabilityIssue> productIssues
    ) {
        return java.util.stream.Stream.concat(planIssues.stream(), productIssues.stream())
                .distinct()
                .toList();
    }

    private ProductPrice firstPrice(List<ProductPrice> prices) {
        return prices.stream().min(Comparator.comparing(price -> price.getId().toString()))
                .orElseThrow(() -> new InvalidStateException(
                        "A selectable override choice has no exact active price."));
    }

    private List<ExtensionAvailabilityIssue> missing(String code) {
        return List.of(new ExtensionAvailabilityIssue(
                ExtensionAvailabilityReason.PRODUCT_NOT_FOUND,
                ExtensionResolutionSource.PRODUCT_LIFECYCLE, code));
    }

    private Set<String> normalizeCodes(Collection<String> codes) {
        if (codes.size() > MAX_SELECTIONS || codes.stream().anyMatch(java.util.Objects::isNull)) {
            throw new InvalidRequestException("Proposed AddOn selections must contain at most 100 codes.");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String code : codes) {
            String value = code.trim().toUpperCase(Locale.ROOT);
            if (!COMMERCIAL_CODE.matcher(value).matches() || !normalized.add(value)) {
                throw new InvalidRequestException(
                        "Proposed AddOn codes must be unique commercial product codes.");
            }
        }
        return Set.copyOf(normalized);
    }

    private String normalizeSearch(String value) {
        return normalizeOptional(value, "search", MAX_SEARCH_LENGTH);
    }

    private String normalizeOptional(String value, String label, int maximumLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new InvalidRequestException(label + " must contain at most " + maximumLength + " characters.");
        }
        return normalized;
    }

    private record Context(
            UUID planId,
            SubscriptionEntitlementSnapshot snapshot,
            CommercialCatalogResolver.PriceTuple tuple,
            Set<String> proposedAddOnCodes,
            Set<String> retainedPackageCodes,
            CommercialCatalogResolver.RetainedSelection retained
    ) {}
}
