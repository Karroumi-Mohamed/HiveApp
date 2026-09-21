package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.dto.PlanVersionModels.*;
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.exception.*;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

/** Invoked only through the guarded PlanAdminService transaction boundary. */
@Service
@RequiredArgsConstructor
public class PlanVersionOperations {
    private final PlanRepository plans;
    private final PlanPublicVersionRepository selections;
    private final PlanPublicSelection publicSelection;
    private final PlanFeatureRepository features;
    private final ProductPriceRepository prices;
    private final SubscriptionRepository subscriptions;
    private final PlanAdminReadModels readModels;
    private final CommercialProductOperationsService operations;
    private final CommercialCatalogResolver resolver;
    private final CommercialCatalogVersionService catalogVersion;
    private final AdminMutationAuthorizer actors;
    private final AuditTrail audit;
    private final Clock clock;

    public Page<Family> families(String search, Pageable pageable) {
        String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        if (term.length() > 160) throw new InvalidRequestException("Search is limited to 160 characters.");
        var roots = plans.findFamilyRoots(term, pageable);
        var ids = roots.stream().map(Plan::getLineageId).toList();
        if (ids.isEmpty()) return Page.empty(pageable);
        var choices = publicSelection.choices(roots.getContent());
        var drafts = drafts(ids);
        var counts = counts(plans.countVersionsByLineageIds(ids));
        boolean countsVisible = allowed("platform.plans.list_subscribers");
        var subscribers = countsVisible ? counts(subscriptions.countCurrentByPlanFamilyIds(ids,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING,
                        SubscriptionStatus.PAST_DUE, SubscriptionStatus.SUSPENDED))) : Map.<UUID, Long>of();
        boolean pricesVisible = allowed("platform.price_books.list");
        var currentPrices = pricesVisible ? prices(choices.values().stream().map(Plan::getId).toList())
                : Map.<UUID, List<Price>>of();
        return roots.map(root -> {
            Plan selected = choices.get(root.getLineageId());
            Plan draft = drafts.get(root.getLineageId());
            return new Family(root.getLineageId(), version(selected),
                    draft == null ? null : version(draft), counts.get(root.getLineageId()),
                    countsVisible ? subscribers.getOrDefault(root.getLineageId(), 0L) : null,
                    currentPrices.getOrDefault(selected.getId(), List.of()), pricesVisible);
        });
    }

    public Versions versions(UUID planId, Pageable pageable) {
        Plan plan = requirePlan(planId);
        Plan root = plans.findByLineageIdAndRevisionNumber(plan.getLineageId(), 1).orElseThrow();
        Plan selected = publicSelection.choices(List.of(root)).get(plan.getLineageId());
        Plan draft = drafts(List.of(plan.getLineageId())).get(plan.getLineageId());
        return new Versions(plan.getLineageId(), selected.getId(), draft == null ? null : draft.getId(),
                catalogVersion.currentRevision(), com.hiveapp.shared.api.PageResponse.from(
                        operations.listPlans(null, null, null, null, plan.getLineageId(), pageable)));
    }

    public Comparison compare(UUID sourceId, UUID targetId) {
        Plan source = requirePlan(sourceId);
        Plan target = requirePlan(targetId);
        if (!source.getLineageId().equals(target.getLineageId())) {
            throw new InvalidRequestException("Compare versions of the same Plan family.");
        }
        var rows = features.findAllByPlanIds(new HashSet<>(List.of(sourceId, targetId)));
        Map<String, PlanFeatureDto> before = featureMap(rows, sourceId);
        Map<String, PlanFeatureDto> after = featureMap(rows, targetId);
        Set<String> codes = new TreeSet<>(before.keySet());
        codes.addAll(after.keySet());
        var changes = codes.stream().map(code -> new FeatureChange(code, before.get(code), after.get(code),
                !sameFeature(before.get(code), after.get(code)))).toList();
        boolean pricesVisible = allowed("platform.price_books.list");
        var currentPrices = pricesVisible ? prices(new HashSet<>(List.of(sourceId, targetId)))
                : Map.<UUID, List<Price>>of();
        return new Comparison(source.getLineageId(), version(source), version(target),
                changes, source.getExtensionPolicy() != target.getExtensionPolicy(),
                source.getSalesVisibility() != target.getSalesVisibility(),
                currentPrices.getOrDefault(sourceId, List.of()), currentPrices.getOrDefault(targetId, List.of()),
                pricesVisible);
    }

    public PlanVersionModels.Version selectPublic(UUID planId, SelectPublic request) {
        catalogVersion.requireCurrent(request.expectedCatalogRevision());
        Plan plan = plans.findByIdForUpdate(planId).orElseThrow(() -> notFound(planId));
        requireVersion(plan, request.expectedVersion());
        if (plan.getSalesVisibility() != ProductSalesVisibility.PUBLIC || !plan.isActive()
                || !resolver.resolvePlan(planId, CommercialCatalogResolver.Audience.CLIENT_CATALOG, null).selectable()) {
            throw new InvalidStateException("The public version must be active, public and currently sellable.");
        }
        Plan root = plans.findByLineageIdAndRevisionNumber(plan.getLineageId(), 1).orElseThrow();
        Plan previous = publicSelection.choices(List.of(root)).get(plan.getLineageId());
        if (previous.getId().equals(planId)) return version(plan);
        var choice = selections.findById(plan.getLineageId()).orElseGet(PlanPublicVersion::new);
        choice.setLineageId(plan.getLineageId());
        choice.setPlan(plan);
        choice.setSelectedBy(actors.currentActorUserId());
        choice.setSelectedAt(clock.instant());
        selections.saveAndFlush(choice);
        record("platform.plans.select_public_version", plan, request.reason(),
                Map.of("publicPlanId", previous.getId()), Map.of("publicPlanId", planId));
        return version(plan);
    }

    public PlanVersionModels.Version metadata(UUID planId, Metadata request) {
        Plan plan = plans.findByIdForUpdate(planId).orElseThrow(() -> notFound(planId));
        requireVersion(plan, request.expectedVersion());
        if (plan.getStatus() == PlanStatus.ARCHIVED) throw new InvalidStateException("Archived versions cannot be edited.");
        var before = metadata(plan);
        plan.setName(request.name().strip());
        plan.setDescription(request.description());
        plans.saveAndFlush(plan);
        record("platform.plans.update_metadata", plan, request.reason(), before, metadata(plan));
        return version(plan);
    }

    private Map<String, Object> metadata(Plan plan) {
        return Map.of("name", plan.getName(), "description", Objects.toString(plan.getDescription(), ""));
    }
    private PlanVersionModels.Version version(Plan plan) {
        return new PlanVersionModels.Version(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(),
                plan.getLineageId(), plan.getRevisionNumber(),
                plan.getSourcePlan() == null ? null : plan.getSourcePlan().getId(), plan.getStatus(),
                plan.getExtensionPolicy(), plan.getSalesVisibility(), plan.getVersion());
    }
    private void record(String action, Plan plan, String reason, Map<String, ?> before, Map<String, ?> after) {
        Map<String, Object> evidence = new LinkedHashMap<>(after);
        evidence.put("reason", reason);
        evidence.put("lineageId", plan.getLineageId());
        evidence.put("productVersionNumber", plan.getRevisionNumber());
        audit.recordSuccess(action, "PLAN", plan.getId(), AuditActorSurface.PLATFORM_ADMIN,
                actors.currentActorUserId(), null, before, evidence);
    }
    private Map<UUID, Plan> drafts(Collection<UUID> ids) {
        return plans.findAllByLineageIdInAndStatus(ids, PlanStatus.DRAFT).stream()
                .collect(Collectors.toMap(Plan::getLineageId, plan -> plan));
    }
    private Map<UUID, List<Price>> prices(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var rows = prices.findCurrentByPlanIds(ids, clock.instant(), PageRequest.of(0, 1201));
        if (rows.size() > 1200) throw new InvalidStateException("Too many prices. Narrow the requested page.");
        return rows.stream().collect(Collectors.groupingBy(price -> price.getPlan().getId(),
                Collectors.mapping(price -> new Price(price.getId(), price.getAmount(), price.getCurrencyCode(),
                        price.getBillingCycle(), price.getEffectiveFrom(), price.getEffectiveUntil()), Collectors.toList())));
    }
    private Map<String, PlanFeatureDto> featureMap(List<PlanFeature> rows, UUID id) {
        return rows.stream().filter(row -> row.getPlan().getId().equals(id))
                .map(readModels::toDto).collect(Collectors.toMap(PlanFeatureDto::featureCode, row -> row));
    }
    private boolean sameFeature(PlanFeatureDto a, PlanFeatureDto b) {
        return a != null && b != null && a.mode() == b.mode()
                && new HashSet<>(a.quotaConfigs()).equals(new HashSet<>(b.quotaConfigs()));
    }
    private Map<UUID, Long> counts(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).longValue()));
    }
    private boolean allowed(String permission) { return PermissionGuard.has(new Permission(permission)); }
    private Plan requirePlan(UUID id) { return plans.findById(id).orElseThrow(() -> notFound(id)); }
    private ResourceNotFoundException notFound(UUID id) { return new ResourceNotFoundException("Plan", "id", id); }
    private void requireVersion(Plan plan, long expected) {
        if (plan.getVersion() != expected) throw new StaleResourceVersionException("The Plan changed. Reload and retry.");
    }
}
