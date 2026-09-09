package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductAction;
import com.hiveapp.platform.client.plan.domain.constant.CommercialProductBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialChoiceState;
import com.hiveapp.platform.client.plan.domain.constant.CommercialTargetingMode;
import com.hiveapp.platform.client.plan.domain.constant.PlanCodes;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.dto.AddOnChooserItemDto;
import com.hiveapp.platform.client.plan.dto.AddOnOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.PlanChooserItemDto;
import com.hiveapp.platform.client.plan.dto.PlanOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageChooserItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageOperationalListItemDto;
import com.hiveapp.platform.client.plan.service.CommercialProductOperationsService;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionGuard;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommercialProductOperationsServiceImpl implements CommercialProductOperationsService {

    private static final int MAX_SEARCH_LENGTH = 160;
    private static final List<SubscriptionStatus> CURRENT_SUBSCRIPTION_STATUSES =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING);
    private static final List<SubscriptionStatus> AFFECTED_SUBSCRIPTION_STATUSES =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING,
                    SubscriptionStatus.PAST_DUE, SubscriptionStatus.SUSPENDED);

    private final PlanRepository planRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final AddOnFeatureRepository addOnFeatureRepository;
    private final ProductPriceRepository productPriceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionChangeOperationRepository subscriptionChangeOperationRepository;
    private final Clock clock;
    private final AdminMutationAuthorizer adminMutationAuthorizer;

    @Override
    @Transactional(readOnly = true)
    public Page<PlanOperationalListItemDto> listPlans(
            String search,
            PlanStatus status,
            ProductSalesVisibility salesVisibility,
            PlanExtensionPolicy extensionPolicy,
            UUID lineageId,
            Pageable pageable
    ) {
        String term = normalizeSearch(search);
        Specification<Plan> specification = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            addSearch(cb, predicates, term, root.get("code"), root.get("name"), root.get("description"));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (salesVisibility != null) {
                predicates.add(cb.equal(root.get("salesVisibility"), salesVisibility));
            }
            if (extensionPolicy != null) {
                predicates.add(cb.equal(root.get("extensionPolicy"), extensionPolicy));
            }
            if (lineageId != null) predicates.add(cb.equal(root.get("lineageId"), lineageId));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<Plan> plans = planRepository.findAll(specification, pageable);
        Set<UUID> ids = ids(plans.getContent(), Plan::getId);
        Set<UUID> lineages = ids(plans.getContent(), Plan::getLineageId);
        Map<UUID, CompositionCount> compositions = compositionCounts(ids);
        Map<UUID, Long> prices = countMap(ids.isEmpty()
                ? List.of() : productPriceRepository.countApplicablePlanPrices(ids, clock.instant()));
        Map<UUID, Long> draftPrices = countMap(ids.isEmpty()
                ? List.of() : productPriceRepository.countDraftPlanPrices(ids));
        Map<UUID, Long> publishedPrices = countMap(ids.isEmpty()
                ? List.of() : productPriceRepository.countPublishedPlanPrices(ids));
        Map<UUID, Long> subscribers = countMap(ids.isEmpty()
                ? List.of() : subscriptionRepository.countCurrentByPlanIds(ids, CURRENT_SUBSCRIPTION_STATUSES));
        Map<UUID, Long> affectedSubscriptions = countMap(ids.isEmpty()
                ? List.of() : subscriptionRepository.countCurrentByPlanIds(
                        ids, AFFECTED_SUBSCRIPTION_STATUSES));
        Map<UUID, Long> subscriptionHistory = countMap(ids.isEmpty()
                ? List.of() : subscriptionRepository.countHistoryByPlanIds(ids));
        Map<UUID, Long> changeReferences = countMap(ids.isEmpty()
                ? List.of() : subscriptionChangeOperationRepository.countByTargetPlanIds(ids));
        Map<UUID, Long> addOnReferences = countMap(ids.isEmpty()
                ? List.of() : addOnRepository.countPlanReferences(ids));
        Map<UUID, Long> quotaReferences = countMap(ids.isEmpty()
                ? List.of() : quotaPackageRepository.countPlanReferences(ids));
        Map<UUID, Long> sourceReferences = countMap(ids.isEmpty()
                ? List.of() : planRepository.countSourceReferences(ids));
        Map<UUID, LineageSummary> lineageSummaries = lineageSummaries(
                lineages.isEmpty() ? List.of() : planRepository.findLineageSummaries(lineages));
        ActionPermissionSnapshot permissions = actionPermissions();
        return plans.map(plan -> toPlanRow(
                plan,
                compositions.getOrDefault(plan.getId(), CompositionCount.NONE),
                prices.getOrDefault(plan.getId(), 0L),
                draftPrices.getOrDefault(plan.getId(), 0L),
                publishedPrices.getOrDefault(plan.getId(), 0L),
                subscribers.getOrDefault(plan.getId(), 0L),
                affectedSubscriptions.getOrDefault(plan.getId(), 0L),
                subscriptionHistory.getOrDefault(plan.getId(), 0L)
                        + changeReferences.getOrDefault(plan.getId(), 0L)
                        + addOnReferences.getOrDefault(plan.getId(), 0L)
                        + quotaReferences.getOrDefault(plan.getId(), 0L)
                        + sourceReferences.getOrDefault(plan.getId(), 0L),
                lineageSummaries.getOrDefault(
                        plan.getLineageId(), new LineageSummary(plan.getRevisionNumber(), 0)),
                permissions));
    }

    @Override
    @Transactional(readOnly = true)
    public PlanOperationalListItemDto getPlanOperations(UUID planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
        CompositionCount composition = compositionCounts(Set.of(planId))
                .getOrDefault(planId, CompositionCount.NONE);
        long prices = countMap(productPriceRepository.countApplicablePlanPrices(
                Set.of(planId), clock.instant())).getOrDefault(planId, 0L);
        long draftPrices = countMap(productPriceRepository.countDraftPlanPrices(Set.of(planId)))
                .getOrDefault(planId, 0L);
        long publishedPrices = countMap(productPriceRepository.countPublishedPlanPrices(Set.of(planId)))
                .getOrDefault(planId, 0L);
        long subscribers = countMap(subscriptionRepository.countCurrentByPlanIds(
                Set.of(planId), CURRENT_SUBSCRIPTION_STATUSES)).getOrDefault(planId, 0L);
        long affectedSubscriptions = countMap(subscriptionRepository.countCurrentByPlanIds(
                Set.of(planId), AFFECTED_SUBSCRIPTION_STATUSES)).getOrDefault(planId, 0L);
        long deletionReferences = countMap(subscriptionRepository.countHistoryByPlanIds(Set.of(planId)))
                        .getOrDefault(planId, 0L)
                + countMap(subscriptionChangeOperationRepository.countByTargetPlanIds(Set.of(planId)))
                        .getOrDefault(planId, 0L)
                + countMap(addOnRepository.countPlanReferences(Set.of(planId))).getOrDefault(planId, 0L)
                + countMap(quotaPackageRepository.countPlanReferences(Set.of(planId))).getOrDefault(planId, 0L)
                + countMap(planRepository.countSourceReferences(Set.of(planId))).getOrDefault(planId, 0L);
        LineageSummary lineage = lineageSummaries(planRepository.findLineageSummaries(
                Set.of(plan.getLineageId()))).getOrDefault(
                        plan.getLineageId(), new LineageSummary(plan.getRevisionNumber(), 0));
        return toPlanRow(plan, composition, prices, draftPrices, publishedPrices,
                subscribers, affectedSubscriptions, deletionReferences,
                lineage, actionPermissions());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AddOnOperationalListItemDto> listAddOns(
            String search,
            AddOnStatus status,
            ProductSalesVisibility salesVisibility,
            UUID lineageId,
            String featureCode,
            String targetPlanCode,
            Pageable pageable
    ) {
        String term = normalizeSearch(search);
        String feature = normalizeOptional(featureCode, "featureCode", MAX_SEARCH_LENGTH);
        String planTarget = normalizeOptionalCode(targetPlanCode, "targetPlanCode");
        Specification<AddOn> specification = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            addSearch(cb, predicates, term, root.get("code"), root.get("name"), root.get("description"));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (salesVisibility != null) {
                predicates.add(cb.equal(root.get("salesVisibility"), salesVisibility));
            }
            if (lineageId != null) predicates.add(cb.equal(root.get("lineageId"), lineageId));
            if (feature != null) {
                Subquery<Integer> featureMatch = query.subquery(Integer.class);
                var item = featureMatch.from(AddOnFeature.class);
                featureMatch.select(cb.literal(1)).where(
                        cb.equal(item.get("addOn"), root),
                        cb.equal(cb.lower(item.get("feature").get("code")), feature.toLowerCase(Locale.ROOT)));
                predicates.add(cb.exists(featureMatch));
            }
            if (planTarget != null) {
                predicates.add(jsonCodeContains(cb, root.get("allowedPlanCodes"), planTarget));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<AddOn> addOns = addOnRepository.findAll(specification, pageable);
        Set<UUID> ids = ids(addOns.getContent(), AddOn::getId);
        Set<UUID> lineages = ids(addOns.getContent(), AddOn::getLineageId);
        Map<UUID, Long> features = countMap(
                ids.isEmpty() ? List.of() : addOnFeatureRepository.countByAddOnIds(ids));
        Map<UUID, Long> prices = countMap(ids.isEmpty()
                ? List.of() : productPriceRepository.countApplicableAddOnPrices(ids, clock.instant()));
        Map<UUID, Long> draftPrices = countMap(ids.isEmpty()
                ? List.of() : productPriceRepository.countDraftAddOnPrices(ids));
        Map<UUID, Long> publishedPrices = countMap(ids.isEmpty()
                ? List.of() : productPriceRepository.countPublishedAddOnPrices(ids));
        Map<UUID, Long> addOnReferences = countMap(ids.isEmpty()
                ? List.of() : addOnRepository.countInboundAddOnReferences(ids));
        Map<UUID, Long> quotaPackageReferences = countMap(ids.isEmpty()
                ? List.of() : addOnRepository.countInboundQuotaPackageReferences(ids));
        Map<UUID, LineageSummary> lineageSummaries = lineageSummaries(
                lineages.isEmpty() ? List.of() : addOnRepository.findLineageSummaries(lineages));
        ActionPermissionSnapshot permissions = actionPermissions();
        return addOns.map(addOn -> toAddOnRow(
                addOn,
                features.getOrDefault(addOn.getId(), 0L),
                prices.getOrDefault(addOn.getId(), 0L),
                draftPrices.getOrDefault(addOn.getId(), 0L),
                publishedPrices.getOrDefault(addOn.getId(), 0L),
                addOnReferences.getOrDefault(addOn.getId(), 0L),
                quotaPackageReferences.getOrDefault(addOn.getId(), 0L),
                lineageSummaries.getOrDefault(
                        addOn.getLineageId(), new LineageSummary(addOn.getRevisionNumber(), 0)),
                permissions));
    }

    @Override
    @Transactional(readOnly = true)
    public AddOnOperationalListItemDto getAddOnOperations(UUID addOnId) {
        AddOn addOn = addOnRepository.findById(addOnId)
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", addOnId));
        long features = countMap(addOnFeatureRepository.countByAddOnIds(Set.of(addOnId)))
                .getOrDefault(addOnId, 0L);
        long prices = countMap(productPriceRepository.countApplicableAddOnPrices(
                Set.of(addOnId), clock.instant())).getOrDefault(addOnId, 0L);
        long draftPrices = countMap(productPriceRepository.countDraftAddOnPrices(Set.of(addOnId)))
                .getOrDefault(addOnId, 0L);
        long publishedPrices = countMap(productPriceRepository.countPublishedAddOnPrices(Set.of(addOnId)))
                .getOrDefault(addOnId, 0L);
        long addOnReferences = countMap(addOnRepository.countInboundAddOnReferences(Set.of(addOnId)))
                .getOrDefault(addOnId, 0L);
        long quotaPackageReferences = countMap(
                addOnRepository.countInboundQuotaPackageReferences(Set.of(addOnId)))
                .getOrDefault(addOnId, 0L);
        LineageSummary lineage = lineageSummaries(addOnRepository.findLineageSummaries(
                Set.of(addOn.getLineageId()))).getOrDefault(
                        addOn.getLineageId(), new LineageSummary(addOn.getRevisionNumber(), 0));
        return toAddOnRow(
                addOn, features, prices, draftPrices, publishedPrices,
                addOnReferences, quotaPackageReferences, lineage, actionPermissions());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuotaPackageOperationalListItemDto> listQuotaPackages(
            String search,
            QuotaPackageStatus status,
            ProductSalesVisibility salesVisibility,
            UUID lineageId,
            String featureCode,
            String resource,
            String targetPlanCode,
            String targetAddOnCode,
            Pageable pageable
    ) {
        String term = normalizeSearch(search);
        String feature = normalizeOptional(featureCode, "featureCode", MAX_SEARCH_LENGTH);
        String quotaResource = normalizeOptional(resource, "resource", 100);
        String planTarget = normalizeOptionalCode(targetPlanCode, "targetPlanCode");
        String addOnTarget = normalizeOptionalCode(targetAddOnCode, "targetAddOnCode");
        Specification<QuotaPackage> specification = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            addSearch(cb, predicates, term, root.get("code"), root.get("name"), root.get("description"));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (salesVisibility != null) {
                predicates.add(cb.equal(root.get("salesVisibility"), salesVisibility));
            }
            if (lineageId != null) predicates.add(cb.equal(root.get("lineageId"), lineageId));
            if (feature != null) {
                predicates.add(cb.equal(
                        cb.lower(root.join("feature", JoinType.INNER).get("code")),
                        feature.toLowerCase(Locale.ROOT)));
            }
            if (quotaResource != null) {
                predicates.add(cb.equal(cb.lower(root.get("resource")),
                        quotaResource.toLowerCase(Locale.ROOT)));
            }
            if (planTarget != null) {
                predicates.add(jsonCodeContains(cb, root.get("allowedPlanCodes"), planTarget));
            }
            if (addOnTarget != null) {
                predicates.add(jsonCodeContains(cb, root.get("allowedAddOnCodes"), addOnTarget));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<QuotaPackage> packages = quotaPackageRepository.findAll(specification, pageable);
        Set<UUID> ids = ids(packages.getContent(), QuotaPackage::getId);
        Set<UUID> lineages = ids(packages.getContent(), QuotaPackage::getLineageId);
        Map<UUID, Long> prices = countMap(ids.isEmpty()
                ? List.of() : productPriceRepository.countApplicableQuotaPackagePrices(ids, clock.instant()));
        Map<UUID, Long> draftPrices = countMap(ids.isEmpty()
                ? List.of() : productPriceRepository.countDraftQuotaPackagePrices(ids));
        Map<UUID, Long> applicableDraftPrices = countMap(ids.isEmpty()
                ? List.of() : productPriceRepository
                        .countCurrentlyApplicableDraftQuotaPackagePrices(ids, clock.instant()));
        Map<UUID, Long> publishedPrices = countMap(ids.isEmpty()
                ? List.of() : productPriceRepository.countPublishedQuotaPackagePrices(ids));
        Map<UUID, LineageSummary> lineageSummaries = lineageSummaries(lineages.isEmpty()
                ? List.of() : quotaPackageRepository.findLineageSummaries(lineages));
        ActionPermissionSnapshot permissions = actionPermissions();
        return packages.map(item -> toQuotaPackageRow(
                item,
                prices.getOrDefault(item.getId(), 0L),
                draftPrices.getOrDefault(item.getId(), 0L),
                applicableDraftPrices.getOrDefault(item.getId(), 0L),
                publishedPrices.getOrDefault(item.getId(), 0L),
                lineageSummaries.getOrDefault(
                        item.getLineageId(), new LineageSummary(item.getRevisionNumber(), 0)),
                permissions));
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaPackageOperationalListItemDto getQuotaPackageOperations(UUID quotaPackageId) {
        QuotaPackage item = quotaPackageRepository.findDetailedById(quotaPackageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QuotaPackage", "id", quotaPackageId));
        long prices = countMap(productPriceRepository.countApplicableQuotaPackagePrices(
                Set.of(quotaPackageId), clock.instant())).getOrDefault(quotaPackageId, 0L);
        long draftPrices = countMap(productPriceRepository.countDraftQuotaPackagePrices(
                Set.of(quotaPackageId))).getOrDefault(quotaPackageId, 0L);
        long applicableDraftPrices = countMap(productPriceRepository
                .countCurrentlyApplicableDraftQuotaPackagePrices(
                        Set.of(quotaPackageId), clock.instant()))
                .getOrDefault(quotaPackageId, 0L);
        long publishedPrices = countMap(productPriceRepository.countPublishedQuotaPackagePrices(
                Set.of(quotaPackageId))).getOrDefault(quotaPackageId, 0L);
        LineageSummary lineage = lineageSummaries(quotaPackageRepository.findLineageSummaries(
                Set.of(item.getLineageId()))).getOrDefault(
                        item.getLineageId(), new LineageSummary(item.getRevisionNumber(), 0));
        return toQuotaPackageRow(
                item, prices, draftPrices, applicableDraftPrices,
                publishedPrices, lineage, actionPermissions());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PlanChooserItemDto> choosePlans(
            String search, ProductSalesVisibility salesVisibility, Pageable pageable) {
        String term = normalizeSearch(search);
        Specification<Plan> specification = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("status"), PlanStatus.ACTIVE));
            addSearch(cb, predicates, term, root.get("code"), root.get("name"));
            if (salesVisibility != null) {
                predicates.add(cb.equal(root.get("salesVisibility"), salesVisibility));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return planRepository.findAll(specification, pageable).map(this::toPlanChoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanChooserItemDto> resolvePlanChoices(Collection<UUID> ids) {
        Set<UUID> bounded = validateChoiceIds(ids);
        return planRepository.findAllByIdInOrderByNameAscIdAsc(bounded).stream()
                .map(this::toPlanChoice)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanChooserItemDto> resolvePlanChoicesByCode(Collection<String> codes) {
        List<String> bounded = validateChoiceCodes(codes);
        Map<String, Plan> found = planRepository.findAllByCodeInOrderByNameAscIdAsc(bounded).stream()
                .collect(Collectors.toMap(Plan::getCode, Function.identity()));
        return bounded.stream().map(code -> {
            Plan plan = found.get(code);
            return plan == null
                    ? new PlanChooserItemDto(null, code, null, null, null, 0, null, null,
                            CommercialChoiceState.MISSING)
                    : toPlanChoice(plan);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AddOnChooserItemDto> chooseAddOns(
            String search,
            ProductSalesVisibility salesVisibility,
            String featureCode,
            Pageable pageable
    ) {
        String term = normalizeSearch(search);
        String feature = normalizeOptional(featureCode, "featureCode", MAX_SEARCH_LENGTH);
        Specification<AddOn> specification = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("status"), AddOnStatus.ACTIVE));
            addSearch(cb, predicates, term, root.get("code"), root.get("name"));
            if (salesVisibility != null) {
                predicates.add(cb.equal(root.get("salesVisibility"), salesVisibility));
            }
            if (feature != null) {
                Subquery<Integer> featureMatch = query.subquery(Integer.class);
                var item = featureMatch.from(AddOnFeature.class);
                featureMatch.select(cb.literal(1)).where(
                        cb.equal(item.get("addOn"), root),
                        cb.equal(cb.lower(item.get("feature").get("code")), feature.toLowerCase(Locale.ROOT)));
                predicates.add(cb.exists(featureMatch));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return addOnRepository.findAll(specification, pageable).map(this::toAddOnChoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddOnChooserItemDto> resolveAddOnChoices(Collection<UUID> ids) {
        Set<UUID> bounded = validateChoiceIds(ids);
        return addOnRepository.findAllByIdInOrderByNameAscIdAsc(bounded).stream()
                .map(this::toAddOnChoice)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddOnChooserItemDto> resolveAddOnChoicesByCode(Collection<String> codes) {
        List<String> bounded = validateChoiceCodes(codes);
        Map<String, AddOn> found = addOnRepository.findAllByCodeInOrderByNameAscIdAsc(bounded).stream()
                .collect(Collectors.toMap(AddOn::getCode, Function.identity()));
        return bounded.stream().map(code -> {
            AddOn addOn = found.get(code);
            return addOn == null
                    ? new AddOnChooserItemDto(null, code, null, null, null, 0, null, null,
                            CommercialChoiceState.MISSING)
                    : toAddOnChoice(addOn);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuotaPackageChooserItemDto> chooseQuotaPackages(
            String search,
            ProductSalesVisibility salesVisibility,
            String featureCode,
            String resource,
            Pageable pageable
    ) {
        String term = normalizeSearch(search);
        String feature = normalizeOptional(featureCode, "featureCode", MAX_SEARCH_LENGTH);
        String quotaResource = normalizeOptional(resource, "resource", 100);
        Specification<QuotaPackage> specification = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("status"), QuotaPackageStatus.ACTIVE));
            addSearch(cb, predicates, term, root.get("code"), root.get("name"));
            if (salesVisibility != null) {
                predicates.add(cb.equal(root.get("salesVisibility"), salesVisibility));
            }
            if (feature != null) {
                predicates.add(cb.equal(cb.lower(root.join("feature", JoinType.INNER).get("code")),
                        feature.toLowerCase(Locale.ROOT)));
            }
            if (quotaResource != null) {
                predicates.add(cb.equal(cb.lower(root.get("resource")),
                        quotaResource.toLowerCase(Locale.ROOT)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return quotaPackageRepository.findAll(specification, pageable).map(this::toQuotaPackageChoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuotaPackageChooserItemDto> resolveQuotaPackageChoices(Collection<UUID> ids) {
        Set<UUID> bounded = validateChoiceIds(ids);
        return quotaPackageRepository.findAllByIdInOrderByNameAscIdAsc(bounded).stream()
                .map(this::toQuotaPackageChoice)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuotaPackageChooserItemDto> resolveQuotaPackageChoicesByCode(Collection<String> codes) {
        List<String> bounded = validateChoiceCodes(codes);
        Map<String, QuotaPackage> found = quotaPackageRepository
                .findAllByCodeInOrderByNameAscIdAsc(bounded).stream()
                .collect(Collectors.toMap(QuotaPackage::getCode, Function.identity()));
        return bounded.stream().map(code -> {
            QuotaPackage item = found.get(code);
            return item == null
                    ? new QuotaPackageChooserItemDto(null, code, null, null, null, null, null, 0,
                            null, null, CommercialChoiceState.MISSING)
                    : toQuotaPackageChoice(item);
        }).toList();
    }

    private PlanChooserItemDto toPlanChoice(Plan plan) {
        return new PlanChooserItemDto(
                plan.getId(), plan.getCode(), plan.getName(), plan.getStatus(), plan.getLineageId(),
                plan.getRevisionNumber(), plan.getExtensionPolicy(), plan.getSalesVisibility(),
                plan.getStatus() == PlanStatus.ACTIVE
                        ? CommercialChoiceState.SELECTABLE
                        : CommercialChoiceState.NO_LONGER_ACTIVE);
    }

    private AddOnChooserItemDto toAddOnChoice(AddOn addOn) {
        return new AddOnChooserItemDto(
                addOn.getId(), addOn.getCode(), addOn.getName(), addOn.getStatus(), addOn.getLineageId(),
                addOn.getRevisionNumber(), addOn.getSalesVisibility(),
                addOn.getAllowedPlanCodes().isEmpty()
                        ? CommercialTargetingMode.OPEN_COMPATIBLE
                        : CommercialTargetingMode.TARGETED,
                addOn.getStatus() == AddOnStatus.ACTIVE
                        ? CommercialChoiceState.SELECTABLE
                        : CommercialChoiceState.NO_LONGER_ACTIVE);
    }

    private QuotaPackageChooserItemDto toQuotaPackageChoice(QuotaPackage item) {
        return new QuotaPackageChooserItemDto(
                item.getId(), item.getCode(), item.getName(), item.getFeature().getCode(),
                item.getResource(), item.getStatus(), item.getLineageId(), item.getRevisionNumber(),
                item.getSalesVisibility(),
                item.getAllowedPlanCodes().isEmpty() && item.getAllowedAddOnCodes().isEmpty()
                        ? CommercialTargetingMode.OPEN_COMPATIBLE
                        : CommercialTargetingMode.TARGETED,
                item.getStatus() == QuotaPackageStatus.ACTIVE
                        ? CommercialChoiceState.SELECTABLE
                        : CommercialChoiceState.NO_LONGER_ACTIVE);
    }

    private Set<UUID> validateChoiceIds(Collection<UUID> values) {
        if (values == null || values.isEmpty()) {
            throw new InvalidRequestException("At least one selected product id is required.");
        }
        if (values.size() > 100 || values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new InvalidRequestException("Selected product ids must contain between 1 and 100 values.");
        }
        Set<UUID> ids = new LinkedHashSet<>(values);
        if (ids.size() != values.size()) {
            throw new InvalidRequestException("Selected product ids must not contain duplicates.");
        }
        return ids;
    }

    private List<String> validateChoiceCodes(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            throw new InvalidRequestException("At least one selected product code is required.");
        }
        if (values.size() > 100 || values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new InvalidRequestException(
                    "Selected product codes must contain between 1 and 100 values.");
        }
        List<String> normalized = values.stream()
                .map(value -> normalizeOptionalCode(value, "selected product code"))
                .toList();
        if (normalized.stream().distinct().count() != normalized.size()) {
            throw new InvalidRequestException(
                    "Selected product codes must not contain normalized duplicates.");
        }
        return normalized;
    }

    private PlanOperationalListItemDto toPlanRow(
            Plan plan,
            CompositionCount composition,
            long applicablePrices,
            long draftPrices,
            long publishedPrices,
            long subscribers,
            long affectedSubscriptions,
            long deletionReferences,
            LineageSummary lineage,
            ActionPermissionSnapshot permissions
    ) {
        List<CommercialProductBlocker> blockers = lifecycleBlockers(plan.getStatus(), applicablePrices);
        if (applicablePrices == 0 && draftPrices == 0 && publishedPrices == 0) {
            blockers.add(CommercialProductBlocker.NO_PRICE_STARTING_POINT);
        }
        if (publishedPrices > 0 && plan.getStatus() == PlanStatus.DRAFT) {
            blockers.add(CommercialProductBlocker.PUBLISHED_PRICE_HISTORY);
        }
        if (composition.total() == 0) blockers.add(CommercialProductBlocker.NO_FEATURES);
        if (composition.included() == 0) blockers.add(CommercialProductBlocker.NO_INCLUDED_FEATURES);
        if (PlanCodes.DEFAULT.equals(plan.getCode())) blockers.add(CommercialProductBlocker.DEFAULT_PLAN_LOCKED);
        addLineageBlockers(blockers, plan.getStatus() == PlanStatus.DRAFT,
                plan.getRevisionNumber(), lineage);
        EnumSet<CommercialProductAction> actions = EnumSet.noneOf(CommercialProductAction.class);
        switch (plan.getStatus()) {
            case DRAFT -> actions.addAll(Set.of(
                    CommercialProductAction.EDIT_DRAFT,
                    CommercialProductAction.MANAGE_COMPOSITION,
                    CommercialProductAction.MANAGE_PRICES,
                    CommercialProductAction.PREVIEW_ACTIVATION,
                    CommercialProductAction.ACTIVATE,
                    CommercialProductAction.ARCHIVE,
                    CommercialProductAction.DELETE_DRAFT));
            case ACTIVE -> {
                actions.add(CommercialProductAction.MANAGE_PRICES);
                if (!PlanCodes.DEFAULT.equals(plan.getCode())) actions.add(CommercialProductAction.DEACTIVATE);
                addRevisionAction(actions, plan.getRevisionNumber(), lineage);
            }
            case INACTIVE -> {
                actions.addAll(Set.of(CommercialProductAction.MANAGE_PRICES,
                        CommercialProductAction.PREVIEW_ACTIVATION,
                        CommercialProductAction.ACTIVATE, CommercialProductAction.ARCHIVE));
                addRevisionAction(actions, plan.getRevisionNumber(), lineage);
            }
            case ARCHIVED -> { }
        }
        if (publishedPrices > 0 || deletionReferences > 0) {
            actions.remove(CommercialProductAction.DELETE_DRAFT);
        }
        filterPlanActions(actions, permissions);
        return new PlanOperationalListItemDto(
                plan.getId(), plan.getCode(), plan.getName(), plan.getStatus(), plan.getLineageId(),
                plan.getRevisionNumber(), sourceId(plan.getSourcePlan()), plan.getCreationReason(),
                plan.getExtensionPolicy(), plan.getSalesVisibility(), plan.getVersion(),
                plan.getCreatedAt(), plan.getUpdatedAt(), composition.total(), composition.included(),
                applicablePrices, draftPrices, publishedPrices, subscribers, affectedSubscriptions,
                List.copyOf(actions), List.copyOf(blockers));
    }

    private AddOnOperationalListItemDto toAddOnRow(
            AddOn addOn,
            long featureCount,
            long applicablePrices,
            long draftPrices,
            long publishedPrices,
            long addOnReferences,
            long quotaPackageReferences,
            LineageSummary lineage,
            ActionPermissionSnapshot permissions
    ) {
        List<CommercialProductBlocker> blockers = lifecycleBlockers(addOn.getStatus(), applicablePrices);
        if (applicablePrices == 0 && draftPrices == 0 && publishedPrices == 0) {
            blockers.add(CommercialProductBlocker.NO_PRICE_STARTING_POINT);
        }
        if (publishedPrices > 0 && addOn.getStatus() == AddOnStatus.DRAFT) {
            blockers.add(CommercialProductBlocker.PUBLISHED_PRICE_HISTORY);
        }
        if (addOnReferences > 0) {
            blockers.add(CommercialProductBlocker.REFERENCED_BY_ADD_ON);
        }
        if (quotaPackageReferences > 0) {
            blockers.add(CommercialProductBlocker.REFERENCED_BY_QUOTA_PACKAGE);
        }
        if (featureCount == 0) blockers.add(CommercialProductBlocker.NO_FEATURES);
        addLineageBlockers(blockers, addOn.getStatus() == AddOnStatus.DRAFT,
                addOn.getRevisionNumber(), lineage);
        EnumSet<CommercialProductAction> actions = EnumSet.noneOf(CommercialProductAction.class);
        switch (addOn.getStatus()) {
            case DRAFT -> actions.addAll(Set.of(
                    CommercialProductAction.EDIT_DRAFT,
                    CommercialProductAction.MANAGE_COMPOSITION,
                    CommercialProductAction.MANAGE_PRICES,
                    CommercialProductAction.PREVIEW_ACTIVATION,
                    CommercialProductAction.ACTIVATE,
                    CommercialProductAction.ARCHIVE,
                    CommercialProductAction.DELETE_DRAFT));
            case ACTIVE -> {
                actions.addAll(Set.of(CommercialProductAction.MANAGE_PRICES,
                        CommercialProductAction.DEACTIVATE));
                addRevisionAction(actions, addOn.getRevisionNumber(), lineage);
            }
            case INACTIVE -> {
                actions.addAll(Set.of(CommercialProductAction.MANAGE_PRICES,
                        CommercialProductAction.PREVIEW_ACTIVATION,
                        CommercialProductAction.ACTIVATE, CommercialProductAction.ARCHIVE));
                addRevisionAction(actions, addOn.getRevisionNumber(), lineage);
            }
            case ARCHIVED -> { }
        }
        if (publishedPrices > 0 || addOnReferences > 0 || quotaPackageReferences > 0) {
            actions.remove(CommercialProductAction.DELETE_DRAFT);
        }
        filterAddOnActions(actions, permissions);
        return new AddOnOperationalListItemDto(
                addOn.getId(), addOn.getCode(), addOn.getName(), addOn.getStatus(), addOn.getLineageId(),
                addOn.getRevisionNumber(), sourceId(addOn.getSourceAddOn()), addOn.getCreationReason(),
                addOn.getSalesVisibility(), addOn.getRowVersion(), addOn.getCreatedAt(), addOn.getUpdatedAt(),
                featureCount, applicablePrices, draftPrices, publishedPrices,
                addOn.getAllowedPlanCodes().isEmpty()
                        ? CommercialTargetingMode.OPEN_COMPATIBLE
                        : CommercialTargetingMode.TARGETED,
                addOn.getAllowedPlanCodes().size(), addOn.getBlockedPlanCodes().size(),
                addOn.getDependencyCodes().size(), addOn.getExclusionCodes().size(),
                addOnReferences, quotaPackageReferences,
                List.copyOf(actions), List.copyOf(blockers));
    }

    private QuotaPackageOperationalListItemDto toQuotaPackageRow(
            QuotaPackage item,
            long applicablePrices,
            long draftPrices,
            long applicableDraftPrices,
            long publishedPrices,
            LineageSummary lineage,
            ActionPermissionSnapshot permissions
    ) {
        List<CommercialProductBlocker> blockers = lifecycleBlockers(item.getStatus(), applicablePrices);
        if (applicablePrices == 0 && draftPrices == 0 && publishedPrices == 0) {
            blockers.add(CommercialProductBlocker.NO_PRICE_STARTING_POINT);
        }
        if (publishedPrices > 0 && item.getStatus() == QuotaPackageStatus.DRAFT) {
            blockers.add(CommercialProductBlocker.PUBLISHED_PRICE_HISTORY);
        }
        addLineageBlockers(blockers, item.getStatus() == QuotaPackageStatus.DRAFT,
                item.getRevisionNumber(), lineage);
        EnumSet<CommercialProductAction> actions = EnumSet.of(
                CommercialProductAction.COMPARE, CommercialProductAction.READ_HISTORY);
        switch (item.getStatus()) {
            case DRAFT -> actions.addAll(Set.of(
                    CommercialProductAction.EDIT_DRAFT,
                    CommercialProductAction.MANAGE_PRICES,
                    CommercialProductAction.PREVIEW_ACTIVATION,
                    CommercialProductAction.ACTIVATE,
                    CommercialProductAction.ARCHIVE,
                    CommercialProductAction.DELETE_DRAFT));
            case ACTIVE -> {
                actions.addAll(Set.of(CommercialProductAction.MANAGE_PRICES,
                        CommercialProductAction.DEACTIVATE));
                addRevisionAction(actions, item.getRevisionNumber(), lineage);
            }
            case INACTIVE -> {
                actions.addAll(Set.of(CommercialProductAction.MANAGE_PRICES,
                        CommercialProductAction.PREVIEW_ACTIVATION,
                        CommercialProductAction.ACTIVATE,
                        CommercialProductAction.ARCHIVE));
                addRevisionAction(actions, item.getRevisionNumber(), lineage);
            }
            case ARCHIVED -> { }
        }
        if (publishedPrices > 0) actions.remove(CommercialProductAction.DELETE_DRAFT);
        filterQuotaPackageActions(actions, permissions);
        return new QuotaPackageOperationalListItemDto(
                item.getId(), item.getCode(), item.getName(), item.getFeature().getCode(), item.getResource(),
                item.getStatus(), item.getLineageId(), item.getRevisionNumber(),
                sourceId(item.getSourceQuotaPackage()), item.getCreationReason(), item.getSalesVisibility(),
                item.getRowVersion(), item.getCreatedAt(), item.getUpdatedAt(), applicablePrices,
                draftPrices, publishedPrices,
                item.getAllowedPlanCodes().isEmpty() && item.getAllowedAddOnCodes().isEmpty()
                        ? CommercialTargetingMode.OPEN_COMPATIBLE
                        : CommercialTargetingMode.TARGETED,
                item.getAllowedPlanCodes().size(), item.getAllowedAddOnCodes().size(),
                List.copyOf(actions), List.copyOf(blockers));
    }

    private <S extends Enum<S>> List<CommercialProductBlocker> lifecycleBlockers(S status, long activePrices) {
        List<CommercialProductBlocker> blockers = new ArrayList<>();
        String name = status.name();
        if ("DRAFT".equals(name)) blockers.add(CommercialProductBlocker.NOT_PUBLISHED);
        if ("INACTIVE".equals(name)) blockers.add(CommercialProductBlocker.PAUSED);
        if ("ARCHIVED".equals(name)) blockers.add(CommercialProductBlocker.ARCHIVED_TERMINAL);
        if (activePrices == 0) blockers.add(CommercialProductBlocker.NO_ACTIVE_PRICE);
        return blockers;
    }

    private void addLineageBlockers(
            List<CommercialProductBlocker> blockers,
            boolean currentDraft,
            int revision,
            LineageSummary summary
    ) {
        if (revision < summary.maximumRevision()) {
            blockers.add(CommercialProductBlocker.NOT_LATEST_REVISION);
        }
        if (!currentDraft && summary.draftCount() > 0) {
            blockers.add(CommercialProductBlocker.DRAFT_SUCCESSOR_EXISTS);
        }
    }

    private void addRevisionAction(
            EnumSet<CommercialProductAction> actions, int revision, LineageSummary summary) {
        if (revision == summary.maximumRevision() && summary.draftCount() == 0) {
            actions.add(CommercialProductAction.REVISE);
        }
    }

    private void filterPlanActions(
            EnumSet<CommercialProductAction> actions,
            ActionPermissionSnapshot permissions
    ) {
        filter(actions, permissions, Map.of(
                CommercialProductAction.EDIT_DRAFT, "platform.plans.update",
                CommercialProductAction.MANAGE_PRICES, "platform.price_books.list",
                CommercialProductAction.PREVIEW_ACTIVATION,
                        "platform.plans.preview_plan_activation",
                CommercialProductAction.ACTIVATE, "platform.plans.transition_status",
                CommercialProductAction.DEACTIVATE, "platform.plans.transition_status",
                CommercialProductAction.ARCHIVE, "platform.plans.transition_status",
                CommercialProductAction.DELETE_DRAFT, "platform.plans.delete",
                CommercialProductAction.REVISE, "platform.plans.revise"));
        if (!permissions.hasAny(
                "platform.plans.assign_feature",
                "platform.plans.update_feature",
                "platform.plans.remove_feature")) {
            actions.remove(CommercialProductAction.MANAGE_COMPOSITION);
        }
        if (!permissions.has("platform.plans.preview_delete")) {
            actions.remove(CommercialProductAction.DELETE_DRAFT);
        }
        if (!permissions.has("platform.price_books.delete_draft")) {
            actions.remove(CommercialProductAction.DELETE_DRAFT);
        }
        if (!permissions.has("platform.price_books.create")) {
            actions.remove(CommercialProductAction.REVISE);
        }
        filterPriceBookActions(actions, permissions, false);
    }

    private void filterAddOnActions(
            EnumSet<CommercialProductAction> actions,
            ActionPermissionSnapshot permissions
    ) {
        filter(actions, permissions, Map.of(
                CommercialProductAction.EDIT_DRAFT, "platform.plans.update_add_on",
                CommercialProductAction.MANAGE_PRICES, "platform.price_books.list",
                CommercialProductAction.PREVIEW_ACTIVATION,
                        "platform.plans.preview_add_on_activation",
                CommercialProductAction.ACTIVATE, "platform.plans.transition_add_on",
                CommercialProductAction.DEACTIVATE, "platform.plans.transition_add_on",
                CommercialProductAction.ARCHIVE, "platform.plans.transition_add_on",
                CommercialProductAction.DELETE_DRAFT, "platform.plans.delete_add_on",
                CommercialProductAction.REVISE, "platform.plans.revise_add_on"));
        if (!permissions.hasAny(
                "platform.plans.assign_add_on_feature",
                "platform.plans.update_add_on_feature",
                "platform.plans.remove_add_on_feature")) {
            actions.remove(CommercialProductAction.MANAGE_COMPOSITION);
        }
        if (!permissions.has("platform.price_books.create")) {
            actions.remove(CommercialProductAction.REVISE);
        }
        if (!permissions.has("platform.price_books.delete_draft")) {
            actions.remove(CommercialProductAction.DELETE_DRAFT);
        }
        filterPriceBookActions(actions, permissions, false);
    }

    private void filterQuotaPackageActions(
            EnumSet<CommercialProductAction> actions,
            ActionPermissionSnapshot permissions
    ) {
        filter(actions, permissions, Map.ofEntries(
                Map.entry(CommercialProductAction.EDIT_DRAFT, "platform.plans.update_quota_package"),
                Map.entry(CommercialProductAction.MANAGE_PRICES, "platform.price_books.list"),
                Map.entry(CommercialProductAction.PREVIEW_ACTIVATION,
                        "platform.plans.preview_quota_package_activation"),
                Map.entry(CommercialProductAction.ACTIVATE, "platform.plans.lifecycle_quota_package"),
                Map.entry(CommercialProductAction.DEACTIVATE, "platform.plans.lifecycle_quota_package"),
                Map.entry(CommercialProductAction.ARCHIVE, "platform.plans.lifecycle_quota_package"),
                Map.entry(CommercialProductAction.DELETE_DRAFT, "platform.plans.delete_quota_package"),
                Map.entry(CommercialProductAction.REVISE, "platform.plans.revise_quota_package"),
                Map.entry(CommercialProductAction.COMPARE, "platform.plans.compare_quota_package"),
                Map.entry(CommercialProductAction.READ_HISTORY, "platform.plans.read_quota_package_history")));
        filterPriceBookActions(actions, permissions, true);
        if (!permissions.has("platform.price_books.create")) {
            actions.remove(CommercialProductAction.REVISE);
        }
        if (!permissions.has("platform.price_books.delete_draft")) {
            actions.remove(CommercialProductAction.DELETE_DRAFT);
        }
    }

    private void filterPriceBookActions(
            EnumSet<CommercialProductAction> actions,
            ActionPermissionSnapshot permissions,
            boolean activationPublishesPrices
    ) {
        boolean priceBooksReadable = permissions.has("platform.price_books.list");
        boolean hasPriceMutation = List.of(
                        "platform.price_books.create",
                        "platform.price_books.update_draft",
                        "platform.price_books.activate",
                        "platform.price_books.change",
                        "platform.price_books.reactivate",
                        "platform.price_books.revise",
                        "platform.price_books.reschedule_change",
                        "platform.price_books.cancel_change",
                        "platform.price_books.archive",
                        "platform.price_books.delete_draft")
                .stream()
                .anyMatch(permissions::has);
        if (!priceBooksReadable || !hasPriceMutation) {
            actions.remove(CommercialProductAction.MANAGE_PRICES);
        }
        if (activationPublishesPrices
                && !permissions.has("platform.price_books.activate")) {
            actions.remove(CommercialProductAction.ACTIVATE);
        }
    }

    private void filter(
            EnumSet<CommercialProductAction> actions,
            ActionPermissionSnapshot permissionSnapshot,
            Map<CommercialProductAction, String> permissions
    ) {
        actions.removeIf(action -> {
            String permission = permissions.get(action);
            return permission != null && !permissionSnapshot.has(permission);
        });
    }

    private ActionPermissionSnapshot actionPermissions() {
        return new ActionPermissionSnapshot(adminMutationAuthorizer.currentActorGrantCeiling());
    }

    private Map<UUID, CompositionCount> compositionCounts(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, CompositionCount> result = new HashMap<>();
        for (Object[] row : planFeatureRepository.countCompositionByPlanIds(ids)) {
            result.put((UUID) row[0], new CompositionCount(number(row[1]), number(row[2])));
        }
        return result;
    }

    private Map<UUID, Long> countMap(List<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        rows.forEach(row -> result.put((UUID) row[0], number(row[1])));
        return result;
    }

    private Map<UUID, LineageSummary> lineageSummaries(List<Object[]> rows) {
        Map<UUID, LineageSummary> result = new HashMap<>();
        rows.forEach(row -> result.put((UUID) row[0],
                new LineageSummary(((Number) row[1]).intValue(), number(row[2]))));
        return result;
    }

    private long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private <T> Set<UUID> ids(List<T> values, Function<T, UUID> extractor) {
        return values.stream().map(extractor)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private UUID sourceId(Object source) {
        if (source == null) return null;
        if (source instanceof Plan plan) return plan.getId();
        if (source instanceof AddOn addOn) return addOn.getId();
        return ((QuotaPackage) source).getId();
    }

    @SafeVarargs
    private void addSearch(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            List<jakarta.persistence.criteria.Predicate> predicates,
            String search,
            jakarta.persistence.criteria.Expression<String>... fields
    ) {
        if (search == null) return;
        String pattern = "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";
        predicates.add(cb.or(java.util.Arrays.stream(fields)
                .map(field -> cb.like(cb.lower(field), pattern, '\\'))
                .toArray(jakarta.persistence.criteria.Predicate[]::new)));
    }

    private jakarta.persistence.criteria.Predicate jsonCodeContains(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<?> field,
            String code
    ) {
        return cb.like(field.as(String.class), "%\"" + escapeLike(code) + "\"%", '\\');
    }

    private String normalizeSearch(String value) {
        return normalizeOptional(value, "search", MAX_SEARCH_LENGTH);
    }

    private String normalizeOptional(String value, String name, int maximumLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new InvalidRequestException(name + " must not exceed " + maximumLength + " characters.");
        }
        return normalized;
    }

    private String normalizeOptionalCode(String value, String name) {
        String normalized = normalizeOptional(value, name, 100);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z][A-Z0-9_]*$")) {
            throw new InvalidRequestException(name + " must use uppercase letters, numbers, and underscores.");
        }
        return normalized;
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record CompositionCount(long total, long included) {
        private static final CompositionCount NONE = new CompositionCount(0, 0);
    }

    private record LineageSummary(int maximumRevision, long draftCount) {}

    private static final class ActionPermissionSnapshot {
        private final AdminMutationAuthorizer.GrantCeiling ceiling;
        private final Map<String, Boolean> decisions = new HashMap<>();

        private ActionPermissionSnapshot(AdminMutationAuthorizer.GrantCeiling ceiling) {
            this.ceiling = ceiling;
        }

        private boolean has(String permission) {
            return decisions.computeIfAbsent(permission,
                    code -> ceiling.allows(code) && PermissionGuard.has(new Permission(code)));
        }

        private boolean hasAny(String... permissions) {
            return java.util.Arrays.stream(permissions).anyMatch(this::has);
        }
    }
}
