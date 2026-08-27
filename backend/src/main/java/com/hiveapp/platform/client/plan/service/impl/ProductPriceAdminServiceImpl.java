package com.hiveapp.platform.client.plan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceAction;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceAudit;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceBlocker;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceReplacementBlocker;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.CreateProductPriceRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceActivationPreview;
import com.hiveapp.platform.client.plan.dto.ProductPriceDto;
import com.hiveapp.platform.client.plan.dto.ProductPriceHistoryEntryDto;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementPreview;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementPreviewRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementResult;
import com.hiveapp.platform.client.plan.dto.UpdateProductPriceRequest;
import com.hiveapp.platform.client.plan.service.ProductPriceAdminService;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutation;
import com.hiveapp.platform.client.plan.service.ProductPriceResolver;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PriceBooksFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.PriceEntryOverlapException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import dev.karroumi.permissionizer.PermissionNode;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionGuard;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = PriceBooksFeature.KEY, description = "Product Price Book Management",
        guard = PermissionNode.Guard.ON)
public class ProductPriceAdminServiceImpl extends PlatformControlFeatureService
        implements ProductPriceAdminService {

    private final ProductPriceRepository productPriceRepository;
    private final PlanRepository planRepository;
    private final AddOnRepository addOnRepository;
    private final QuotaPackageRepository quotaPackageRepository;
    private final java.time.Clock clock;
    private final ProductPriceResolver productPriceResolver;
    private final AuditLogRepository auditLogRepository;
    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final AuditTrail auditTrail;
    private final AdminMutationAuthorizer adminMutationAuthorizer;

    @Override
    protected FeatureDefinition featureDefinition() {
        return PriceBooksFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "list", description = "List and filter product price entries")
    public Page<ProductPriceDto> list(String search, ProductPriceOwnerType ownerType, UUID ownerId,
                                      ProductPriceStatus status, String currencyCode,
                                      BillingCycle billingCycle, Pageable pageable) {
        validateCycle(billingCycle);
        String currency = normalizeOptionalCurrency(currencyCode);
        Specification<ProductPrice> specification = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (ownerType != null) {
                predicates.add(cb.equal(root.get("ownerType"), ownerType));
            }
            if (ownerId != null) {
                if (ownerType == null) {
                    throw new InvalidRequestException("ownerType is required when ownerId is supplied.");
                }
                String relationship = switch (ownerType) {
                    case PLAN -> "plan";
                    case ADD_ON -> "addOn";
                    case QUOTA_PACKAGE -> "quotaPackage";
                };
                predicates.add(cb.equal(root.get(relationship).get("id"), ownerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (currency != null) {
                predicates.add(cb.equal(root.get("currencyCode"), currency));
            }
            if (billingCycle != null) {
                predicates.add(cb.equal(root.get("billingCycle"), billingCycle));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                var plan = root.join("plan", JoinType.LEFT);
                var addOn = root.join("addOn", JoinType.LEFT);
                var quotaPackage = root.join("quotaPackage", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("currencyCode")), pattern),
                        cb.like(cb.lower(plan.get("code")), pattern),
                        cb.like(cb.lower(plan.get("name")), pattern),
                        cb.like(cb.lower(addOn.get("code")), pattern),
                        cb.like(cb.lower(addOn.get("name")), pattern),
                        cb.like(cb.lower(quotaPackage.get("code")), pattern),
                        cb.like(cb.lower(quotaPackage.get("name")), pattern)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<ProductPrice> prices = productPriceRepository.findAll(specification, pageable);
        List<ProductPrice> activationCandidates = prices.getContent().stream()
                .filter(price -> price.getStatus() == ProductPriceStatus.DRAFT
                        || price.getStatus() == ProductPriceStatus.INACTIVE)
                .toList();
        List<ProductPrice> activePrices = activationCandidates.isEmpty()
                ? List.of()
                : productPriceRepository.findAll(activeOverlapSpecification(activationCandidates));
        Map<UUID, Integer> maximumRevisions = maximumRevisions(prices.getContent());
        ActionPermissionSnapshot permissions = actionPermissions();
        return prices
                .map(price -> toDto(price, generalBlockers(price, activePrices),
                        maximumRevisions.get(price.getLineageId()) == price.getRevisionNumber(),
                        permissions));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "Read a product price entry")
    public ProductPriceDto get(UUID priceId) {
        return toDto(requirePrice(priceId), true);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_history", description = "Read product price mutation history")
    public Page<ProductPriceHistoryEntryDto> history(UUID priceId, Pageable pageable) {
        requirePrice(priceId);
        if (pageable == null || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new InvalidRequestException("History page size must be between 1 and 100.");
        }
        Pageable bounded = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AuditLog> logs = auditLogRepository.findAllByResourceTypeAndResourceId(
                ProductPriceAudit.RESOURCE_TYPE, priceId.toString(), bounded);
        var actorIds = logs.getContent().stream()
                .map(AuditLog::getActorUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> actorEmails = actorIds.isEmpty()
                ? Map.of()
                : adminUserRepository.findAllWithUserByUserIdIn(actorIds).stream()
                        .collect(Collectors.toMap(
                                admin -> admin.getUser().getId(),
                                admin -> admin.getUser().getEmail()));
        return logs.map(log -> new ProductPriceHistoryEntryDto(
                log.getId(), log.getOccurredAt(), log.getActorUserId(),
                actorEmails.get(log.getActorUserId()), log.getAction(), log.getOutcome(),
                log.getFailureType(), historyReason(log)));
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "create", description = "Create a draft product price entry")
    public ProductPriceDto createDraft(ProductPriceOwnerType ownerType, UUID ownerId,
                                       CreateProductPriceRequest request) {
        validateTerms(request.billingCycle(), request.effectiveFrom(), request.effectiveUntil());
        Money money = Money.of(request.amount(), request.currencyCode());
        ProductPrice price = switch (ownerType) {
            case PLAN -> ProductPrice.draft(requirePriceDraftOwnerPlan(ownerId), money,
                    request.billingCycle(), request.effectiveFrom(), request.effectiveUntil());
            case ADD_ON -> ProductPrice.draft(requirePriceDraftOwnerAddOn(ownerId), money,
                    request.billingCycle(), request.effectiveFrom(), request.effectiveUntil());
            case QUOTA_PACKAGE -> ProductPrice.draft(requirePriceDraftOwnerQuotaPackage(ownerId), money,
                    request.billingCycle(), request.effectiveFrom(), request.effectiveUntil());
        };
        return toDto(productPriceRepository.saveAndFlush(price), true);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "update_draft", description = "Edit draft product price terms")
    public ProductPriceDto updateDraft(UUID priceId, UpdateProductPriceRequest request) {
        ProductPrice hint = requirePrice(priceId);
        lockOwner(hint.getOwnerType(), hint.ownerId());
        entityManager.clear();
        ProductPrice price = requirePriceForUpdate(priceId);
        requireVersion(price, request.version());
        requireOwnerAllowsPriceDraft(price);
        validateTerms(request.billingCycle(), request.effectiveFrom(), request.effectiveUntil());
        translateState(() -> price.editDraft(Money.of(request.amount(), request.currencyCode()),
                request.billingCycle(), request.effectiveFrom(), request.effectiveUntil()));
        return toDto(productPriceRepository.saveAndFlush(price), true);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_activation", description = "Preview price activation blockers")
    public ProductPriceActivationPreview previewActivation(UUID priceId) {
        ProductPrice price = requirePrice(priceId);
        List<ProductPriceBlocker> blockers = activationBlockers(price);
        return new ProductPriceActivationPreview(price.getId(), blockers.isEmpty(), blockers);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "activate", description = "Publish a draft product price entry")
    public ProductPriceDto activate(UUID priceId, long version, String reason) {
        requireReason(reason);
        return activate(priceId, version, ProductPriceStatus.DRAFT);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "pause", description = "Pause a published product price for new sales")
    public ProductPriceDto pause(UUID priceId, long version, String reason) {
        requireReason(reason);
        ProductPrice price = requirePrice(priceId);
        requireVersion(price, version);
        translateState(price::pause);
        return toDto(productPriceRepository.saveAndFlush(price), true);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "reactivate", description = "Reactivate a paused product price")
    public ProductPriceDto reactivate(UUID priceId, long version, String reason) {
        requireReason(reason);
        return activate(priceId, version, ProductPriceStatus.INACTIVE);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "revise", description = "Create a successor draft price revision")
    public ProductPriceDto revise(UUID priceId, long version, String reason) {
        requireReason(reason);
        ProductPrice hint = requirePrice(priceId);
        lockOwner(hint.getOwnerType(), hint.ownerId());
        entityManager.clear();
        ProductPrice source = requirePriceForUpdate(priceId);
        requireVersion(source, version);
        requireOwnerAllowsPriceDraft(source);
        int maximumRevision = productPriceRepository.findMaximumRevisionNumber(source.getLineageId());
        if (maximumRevision != source.getRevisionNumber()) {
            throw new InvalidStateException("Only the latest price revision can be revised.");
        }
        ProductPrice successor = translateState(source::revise);
        return toDto(productPriceRepository.saveAndFlush(successor), true);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "preview_replacement", description = "Preview an atomic scheduled price replacement")
    public ProductPriceReplacementPreview previewReplacement(
            UUID successorPriceId,
            ProductPriceReplacementPreviewRequest request
    ) {
        ProductPrice successor = requirePrice(successorPriceId);
        ProductPrice current = requirePrice(request.currentPriceId());
        requireVersion(current, request.currentVersion());
        requireVersion(successor, request.successorVersion());
        List<ProductPriceReplacementBlocker> blockers = replacementBlockers(current, successor);
        return new ProductPriceReplacementPreview(
                current.getId(), current.getVersion(), successor.getId(), successor.getVersion(),
                successor.getEffectiveFrom(), blockers.isEmpty(), blockers);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "schedule_replacement", description = "Atomically schedule a successor price")
    public ProductPriceReplacementResult scheduleReplacement(
            UUID successorPriceId,
            ProductPriceReplacementRequest request
    ) {
        requireReason(request.reason());
        ProductPrice successorHint = requirePrice(successorPriceId);
        lockOwner(successorHint.getOwnerType(), successorHint.ownerId());

        // The initial lookup only identifies the owner lock. Clear it so both price rows are
        // re-read under pessimistic locks after any concurrent transaction that held the owner.
        entityManager.clear();
        List<UUID> orderedIds = java.util.stream.Stream.of(successorPriceId, request.currentPriceId())
                .distinct()
                .sorted()
                .toList();
        if (orderedIds.size() != 2) {
            throw new InvalidRequestException("Current and successor price entries must be different.");
        }
        Map<UUID, ProductPrice> locked = orderedIds.stream().collect(Collectors.toMap(
                java.util.function.Function.identity(), this::requirePriceForUpdate));
        ProductPrice successor = locked.get(successorPriceId);
        ProductPrice current = locked.get(request.currentPriceId());

        if (isExistingReplacement(current, successor)) {
            return replacementResult(current, successor, true);
        }
        requireVersion(current, request.currentVersion());
        requireVersion(successor, request.successorVersion());
        List<ProductPriceReplacementBlocker> blockers = replacementBlockers(current, successor);
        if (!blockers.isEmpty()) {
            throw new InvalidStateException("Price replacement cannot be scheduled: " + blockers + ".");
        }

        java.time.Instant previousUntil = current.getEffectiveUntil();
        translateState(() -> current.endForReplacementAt(successor.getEffectiveFrom()));
        translateState(successor::activate);
        productPriceRepository.saveAllAndFlush(List.of(current, successor));
        auditTrail.recordSuccess(
                "platform.price_books.schedule_replacement",
                ProductPriceAudit.RESOURCE_TYPE,
                current.getId(),
                AuditActorSurface.PLATFORM_ADMIN,
                adminMutationAuthorizer.currentActorUserId(),
                null,
                Map.of(
                        "reason", request.reason(),
                        "effectiveUntil", previousUntil == null ? "UNBOUNDED" : previousUntil,
                        "successorPriceId", successor.getId()),
                Map.of(
                        "effectiveUntil", current.getEffectiveUntil(),
                        "successorPriceId", successor.getId(),
                        "successorStatus", successor.getStatus()));
        return replacementResult(current, successor, false);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "archive", description = "Archive a non-active product price entry")
    public ProductPriceDto archive(UUID priceId, long version, String reason) {
        requireReason(reason);
        ProductPrice price = requirePrice(priceId);
        requireVersion(price, version);
        translateState(price::archive);
        return toDto(productPriceRepository.saveAndFlush(price), true);
    }

    @Override
    @Transactional
    @CommercialCatalogMutation
    @PermissionNode(key = "delete_draft", description = "Delete an unpublished draft price entry")
    public void deleteDraft(UUID priceId, long version) {
        ProductPrice price = requirePrice(priceId);
        requireVersion(price, version);
        if (price.getStatus() != ProductPriceStatus.DRAFT) {
            throw new InvalidStateException("Only an unpublished draft price entry can be deleted.");
        }
        if (price.isCompatibilityDefault()) {
            throw new InvalidStateException("A compatibility default price cannot be deleted.");
        }
        productPriceRepository.delete(price);
        productPriceRepository.flush();
    }

    private ProductPriceDto activate(UUID priceId, long version, ProductPriceStatus requiredStatus) {
        ProductPrice hint = requirePrice(priceId);
        lockOwner(hint.getOwnerType(), hint.ownerId());
        // The initial read only identifies the authoritative owner lock. A concurrent archive may
        // have committed while this command waited for that lock, so discard the managed hint and
        // re-read both the price and its owner under locks before evaluating lifecycle blockers.
        entityManager.clear();
        ProductPrice price = requirePriceForUpdate(priceId);
        requireVersion(price, version);
        if (price.getStatus() != requiredStatus) {
            throw new InvalidStateException("Price entry is not in the required " + requiredStatus + " state.");
        }
        List<ProductPriceBlocker> blockers = activationBlockers(price);
        if (blockers.contains(ProductPriceBlocker.ACTIVE_WINDOW_OVERLAP)) {
            throw new PriceEntryOverlapException(
                    "An active price already overlaps this owner, currency, billing cycle, and effective window.");
        }
        if (!blockers.isEmpty()) {
            throw new InvalidStateException("Price entry cannot be activated: " + blockers + ".");
        }
        translateState(price::activate);
        return toDto(productPriceRepository.saveAndFlush(price), true);
    }

    private List<ProductPriceBlocker> activationBlockers(ProductPrice price) {
        List<ProductPriceBlocker> blockers = new ArrayList<>();
        if (price.getStatus() == ProductPriceStatus.ARCHIVED) {
            blockers.add(ProductPriceBlocker.ARCHIVED_TERMINAL);
            return List.copyOf(blockers);
        }
        if (price.getStatus() != ProductPriceStatus.DRAFT && price.getStatus() != ProductPriceStatus.INACTIVE) {
            blockers.add(ProductPriceBlocker.WRONG_LIFECYCLE_STATE);
        }
        if (!ownerIsActive(price)) {
            blockers.add(ProductPriceBlocker.OWNER_NOT_ACTIVE);
        }
        if (price.getEffectiveUntil() != null && !price.getEffectiveUntil().isAfter(clock.instant())) {
            blockers.add(ProductPriceBlocker.EFFECTIVE_WINDOW_EXPIRED);
        }
        if (price.getId() != null && productPriceResolver.hasActiveOverlap(price)) {
            blockers.add(ProductPriceBlocker.ACTIVE_WINDOW_OVERLAP);
        }
        return List.copyOf(blockers);
    }

    private List<ProductPriceReplacementBlocker> replacementBlockers(
            ProductPrice current,
            ProductPrice successor
    ) {
        List<ProductPriceReplacementBlocker> blockers = new ArrayList<>();
        if (current.getStatus() != ProductPriceStatus.ACTIVE) {
            blockers.add(ProductPriceReplacementBlocker.CURRENT_NOT_ACTIVE);
        }
        if (successor.getStatus() != ProductPriceStatus.DRAFT) {
            blockers.add(ProductPriceReplacementBlocker.SUCCESSOR_NOT_DRAFT);
        }
        if (successor.getSourcePrice() == null
                || !current.getId().equals(successor.getSourcePrice().getId())
                || !current.getLineageId().equals(successor.getLineageId())) {
            blockers.add(ProductPriceReplacementBlocker.SUCCESSOR_NOT_DIRECT_REVISION);
        }
        if (!sameCommercialTuple(current, successor)) {
            blockers.add(ProductPriceReplacementBlocker.COMMERCIAL_TUPLE_MISMATCH);
        }
        java.time.Instant cutoff = successor.getEffectiveFrom();
        if (!cutoff.isAfter(clock.instant())) {
            blockers.add(ProductPriceReplacementBlocker.CUTOFF_NOT_FUTURE);
        }
        if (!cutoff.isAfter(current.getEffectiveFrom())
                || (current.getEffectiveUntil() != null
                    && current.getEffectiveUntil().isBefore(cutoff))) {
            blockers.add(ProductPriceReplacementBlocker.CURRENT_DOES_NOT_COVER_CUTOFF);
        }
        if (!ownerIsActive(successor)) {
            blockers.add(ProductPriceReplacementBlocker.OWNER_NOT_ACTIVE);
        }
        if (sameCommercialTuple(current, successor)
                && productPriceRepository.countActiveOverlapsExcluding(
                        successor.getOwnerType(), successor.ownerId(), successor.getCurrencyCode(),
                        successor.getBillingCycle(), successor.getEffectiveFrom(), successor.getEffectiveUntil(),
                        List.of(current.getId(), successor.getId())) > 0) {
            blockers.add(ProductPriceReplacementBlocker.OTHER_ACTIVE_WINDOW_OVERLAP);
        }
        return List.copyOf(blockers);
    }

    private boolean sameCommercialTuple(ProductPrice current, ProductPrice successor) {
        return current.getOwnerType() == successor.getOwnerType()
                && current.ownerId().equals(successor.ownerId())
                && current.getCurrencyCode().equals(successor.getCurrencyCode())
                && current.getBillingCycle() == successor.getBillingCycle();
    }

    private boolean isExistingReplacement(ProductPrice current, ProductPrice successor) {
        return current.getStatus() == ProductPriceStatus.ACTIVE
                && successor.getStatus() == ProductPriceStatus.ACTIVE
                && successor.getSourcePrice() != null
                && current.getId().equals(successor.getSourcePrice().getId())
                && sameCommercialTuple(current, successor)
                && successor.getEffectiveFrom().equals(current.getEffectiveUntil());
    }

    private ProductPriceReplacementResult replacementResult(
            ProductPrice current,
            ProductPrice successor,
            boolean existing
    ) {
        return new ProductPriceReplacementResult(
                toDto(current, true), toDto(successor, true), successor.getEffectiveFrom(), existing);
    }

    private ProductPriceDto toDto(ProductPrice price, boolean includeBlockers) {
        boolean latestRevision = productPriceRepository.findMaximumRevisionNumber(price.getLineageId())
                == price.getRevisionNumber();
        return toDto(price, includeBlockers ? generalBlockers(price) : List.of(), latestRevision,
                actionPermissions());
    }

    private ProductPriceDto toDto(ProductPrice price, List<ProductPriceBlocker> baseBlockers,
                                  boolean latestRevision,
                                  ActionPermissionSnapshot permissions) {
        List<ProductPriceBlocker> blockers = new ArrayList<>(baseBlockers);
        if (!latestRevision) {
            blockers.add(ProductPriceBlocker.SUCCESSOR_ALREADY_EXISTS);
        }
        EnumSet<ProductPriceAction> actions = EnumSet.noneOf(ProductPriceAction.class);
        switch (price.getStatus()) {
            case DRAFT -> {
                actions.add(ProductPriceAction.EDIT_DRAFT);
                actions.add(ProductPriceAction.PREVIEW_ACTIVATION);
                if (!hasActivationBlocker(blockers)) {
                    actions.add(ProductPriceAction.ACTIVATE);
                }
                actions.add(ProductPriceAction.ARCHIVE);
                if (!price.isCompatibilityDefault()) {
                    actions.add(ProductPriceAction.DELETE_DRAFT);
                }
            }
            case ACTIVE -> {
                actions.add(ProductPriceAction.PAUSE);
                if (latestRevision) {
                    actions.add(ProductPriceAction.REVISE);
                }
            }
            case INACTIVE -> {
                actions.add(ProductPriceAction.PREVIEW_ACTIVATION);
                if (!hasActivationBlocker(blockers)) {
                    actions.add(ProductPriceAction.REACTIVATE);
                }
                if (latestRevision) {
                    actions.add(ProductPriceAction.REVISE);
                }
                actions.add(ProductPriceAction.ARCHIVE);
            }
            case ARCHIVED -> { }
        }
        actions.removeIf(action -> !permissions.has(permissionFor(action)));
        return new ProductPriceDto(
                price.getId(), price.getOwnerType(), price.ownerId(), price.ownerCode(), price.ownerName(),
                price.getAmount(), price.getCurrencyCode(), price.getBillingCycle(), price.getStatus(),
                price.getEffectiveFrom(), price.getEffectiveUntil(), price.getLineageId(),
                price.getRevisionNumber(), price.getSourcePrice() == null ? null : price.getSourcePrice().getId(),
                price.isCompatibilityDefault(), price.getVersion(), price.getCreatedAt(), price.getUpdatedAt(),
                List.copyOf(actions), List.copyOf(blockers));
    }

    private String permissionFor(ProductPriceAction action) {
        return switch (action) {
            case EDIT_DRAFT -> "platform.price_books.update_draft";
            case PREVIEW_ACTIVATION -> "platform.price_books.preview_activation";
            case ACTIVATE -> "platform.price_books.activate";
            case PAUSE -> "platform.price_books.pause";
            case REACTIVATE -> "platform.price_books.reactivate";
            case REVISE -> "platform.price_books.revise";
            case ARCHIVE -> "platform.price_books.archive";
            case DELETE_DRAFT -> "platform.price_books.delete_draft";
        };
    }

    private ActionPermissionSnapshot actionPermissions() {
        return new ActionPermissionSnapshot(adminMutationAuthorizer.currentActorGrantCeiling());
    }

    private boolean hasActivationBlocker(List<ProductPriceBlocker> blockers) {
        return blockers.stream().anyMatch(blocker -> blocker != ProductPriceBlocker.SUCCESSOR_ALREADY_EXISTS);
    }

    private Map<UUID, Integer> maximumRevisions(List<ProductPrice> prices) {
        if (prices.isEmpty()) {
            return Map.of();
        }
        return productPriceRepository.findMaximumRevisionNumbers(prices.stream()
                        .map(ProductPrice::getLineageId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).intValue()));
    }

    private List<ProductPriceBlocker> generalBlockers(ProductPrice price) {
        if (price.getStatus() == ProductPriceStatus.ARCHIVED) {
            return List.of(ProductPriceBlocker.ARCHIVED_TERMINAL);
        }
        if (price.getStatus() == ProductPriceStatus.ACTIVE) {
            return List.of(ProductPriceBlocker.ACTIVE_MUST_BE_PAUSED);
        }
        return activationBlockers(price);
    }

    private List<ProductPriceBlocker> generalBlockers(ProductPrice price, List<ProductPrice> activePrices) {
        if (price.getStatus() == ProductPriceStatus.ARCHIVED) {
            return List.of(ProductPriceBlocker.ARCHIVED_TERMINAL);
        }
        if (price.getStatus() == ProductPriceStatus.ACTIVE) {
            return List.of(ProductPriceBlocker.ACTIVE_MUST_BE_PAUSED);
        }
        List<ProductPriceBlocker> blockers = new ArrayList<>();
        if (!ownerIsActive(price)) {
            blockers.add(ProductPriceBlocker.OWNER_NOT_ACTIVE);
        }
        if (price.getEffectiveUntil() != null && !price.getEffectiveUntil().isAfter(clock.instant())) {
            blockers.add(ProductPriceBlocker.EFFECTIVE_WINDOW_EXPIRED);
        }
        boolean overlap = activePrices.stream()
                .filter(candidate -> !candidate.getId().equals(price.getId()))
                .filter(candidate -> candidate.getOwnerType() == price.getOwnerType())
                .filter(candidate -> candidate.ownerId().equals(price.ownerId()))
                .filter(candidate -> candidate.getCurrencyCode().equals(price.getCurrencyCode()))
                .filter(candidate -> candidate.getBillingCycle() == price.getBillingCycle())
                .anyMatch(candidate -> overlaps(candidate.getEffectiveFrom(), candidate.getEffectiveUntil(),
                        price.getEffectiveFrom(), price.getEffectiveUntil()));
        if (overlap) {
            blockers.add(ProductPriceBlocker.ACTIVE_WINDOW_OVERLAP);
        }
        return List.copyOf(blockers);
    }

    private boolean overlaps(java.time.Instant leftFrom, java.time.Instant leftUntil,
                             java.time.Instant rightFrom, java.time.Instant rightUntil) {
        return (rightUntil == null || leftFrom.isBefore(rightUntil))
                && (leftUntil == null || leftUntil.isAfter(rightFrom));
    }

    private Specification<ProductPrice> activeOverlapSpecification(List<ProductPrice> candidates) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> tuples = candidates.stream()
                    .map(candidate -> {
                        String relationship = switch (candidate.getOwnerType()) {
                            case PLAN -> "plan";
                            case ADD_ON -> "addOn";
                            case QUOTA_PACKAGE -> "quotaPackage";
                        };
                        var terms = new ArrayList<jakarta.persistence.criteria.Predicate>();
                        terms.add(cb.equal(root.get("ownerType"), candidate.getOwnerType()));
                        terms.add(cb.equal(root.get(relationship).get("id"), candidate.ownerId()));
                        terms.add(cb.equal(root.get("currencyCode"), candidate.getCurrencyCode()));
                        terms.add(cb.equal(root.get("billingCycle"), candidate.getBillingCycle()));
                        if (candidate.getEffectiveUntil() != null) {
                            terms.add(cb.lessThan(root.get("effectiveFrom"), candidate.getEffectiveUntil()));
                        }
                        terms.add(cb.or(
                                cb.isNull(root.get("effectiveUntil")),
                                cb.greaterThan(root.get("effectiveUntil"), candidate.getEffectiveFrom())));
                        return cb.and(terms.toArray(jakarta.persistence.criteria.Predicate[]::new));
                    })
                    .toList();
            return cb.and(
                    cb.equal(root.get("status"), ProductPriceStatus.ACTIVE),
                    cb.or(tuples.toArray(jakarta.persistence.criteria.Predicate[]::new)));
        };
    }

    private boolean ownerIsActive(ProductPrice price) {
        return switch (price.getOwnerType()) {
            case PLAN -> price.getPlan().getStatus() == PlanStatus.ACTIVE
                    || price.getPlan().getStatus() == PlanStatus.DRAFT
                    || price.getPlan().getStatus() == PlanStatus.INACTIVE;
            case ADD_ON -> price.getAddOn().getStatus() == AddOnStatus.ACTIVE
                    || price.getAddOn().getStatus() == AddOnStatus.DRAFT
                    || price.getAddOn().getStatus() == AddOnStatus.INACTIVE;
            case QUOTA_PACKAGE -> price.getQuotaPackage().getStatus() == QuotaPackageStatus.ACTIVE
                    || price.getQuotaPackage().getStatus() == QuotaPackageStatus.DRAFT
                    || price.getQuotaPackage().getStatus() == QuotaPackageStatus.INACTIVE;
        };
    }

    private void lockOwner(ProductPriceOwnerType type, UUID id) {
        switch (type) {
            case PLAN -> requirePlan(id, true);
            case ADD_ON -> requireAddOn(id, true);
            case QUOTA_PACKAGE -> requireQuotaPackage(id, true);
        }
    }

    private Plan requirePriceDraftOwnerPlan(UUID id) {
        Plan owner = requirePlan(id, true);
        if (owner.getStatus() == PlanStatus.ARCHIVED) {
            throw new InvalidStateException("Archived Plans cannot receive new price drafts.");
        }
        return owner;
    }

    private AddOn requirePriceDraftOwnerAddOn(UUID id) {
        AddOn owner = requireAddOn(id, true);
        if (owner.getStatus() == AddOnStatus.ARCHIVED) {
            throw new InvalidStateException("Archived AddOns cannot receive new price drafts.");
        }
        return owner;
    }

    private QuotaPackage requirePriceDraftOwnerQuotaPackage(UUID id) {
        QuotaPackage owner = requireQuotaPackage(id, true);
        if (owner.getStatus() == QuotaPackageStatus.ARCHIVED) {
            throw new InvalidStateException(
                    "Archived capacity packages cannot receive new price drafts.");
        }
        return owner;
    }

    private void requireOwnerAllowsPriceDraft(ProductPrice price) {
        boolean archived = switch (price.getOwnerType()) {
            case PLAN -> price.getPlan().getStatus() == PlanStatus.ARCHIVED;
            case ADD_ON -> price.getAddOn().getStatus() == AddOnStatus.ARCHIVED;
            case QUOTA_PACKAGE -> price.getQuotaPackage().getStatus() == QuotaPackageStatus.ARCHIVED;
        };
        if (archived) {
            throw new InvalidStateException(
                    "Archived commercial products cannot create or edit price revisions.");
        }
    }

    private Plan requirePlan(UUID id, boolean lock) {
        return (lock ? planRepository.findByIdForUpdate(id) : planRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", id));
    }

    private AddOn requireAddOn(UUID id, boolean lock) {
        return (lock ? addOnRepository.findByIdForUpdate(id) : addOnRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("AddOn", "id", id));
    }

    private QuotaPackage requireQuotaPackage(UUID id, boolean lock) {
        return (lock ? quotaPackageRepository.findByIdForUpdate(id) : quotaPackageRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("QuotaPackage", "id", id));
    }

    private ProductPrice requirePrice(UUID id) {
        return productPriceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductPrice", "id", id));
    }

    private ProductPrice requirePriceForUpdate(UUID id) {
        return productPriceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductPrice", "id", id));
    }

    private void requireVersion(ProductPrice price, long expected) {
        if (price.getVersion() != expected) {
            throw new StaleResourceVersionException(
                    "Price entry changed since it was read. Reload it and retry.");
        }
    }

    private String normalizeOptionalCurrency(String value) {
        return value == null || value.isBlank() ? null : Money.normalizeCurrencyCode(value);
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("An operator reason is required for price lifecycle changes.");
        }
        if (reason.trim().length() > 500) {
            throw new InvalidRequestException("Operator reason must not exceed 500 characters.");
        }
    }

    private String historyReason(AuditLog log) {
        if (log.getRequestData() == null) {
            return null;
        }
        try {
            var request = objectMapper.readTree(log.getRequestData());
            var reason = request.get("reason");
            if ((reason == null || !reason.isTextual()) && request.path("request").isObject()) {
                reason = request.path("request").get("reason");
            }
            if ((reason == null || !reason.isTextual()) && request.path("before").isObject()) {
                reason = request.path("before").get("reason");
            }
            return reason != null && reason.isTextual() && !reason.textValue().isBlank()
                    ? reason.textValue().trim()
                    : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return null;
        }
    }

    private void validateTerms(BillingCycle cycle, java.time.Instant from, java.time.Instant until) {
        validateCycle(cycle);
        if (from == null) {
            throw new InvalidRequestException("Price effectiveFrom is required.");
        }
        if (until != null && !until.isAfter(from)) {
            throw new InvalidRequestException("Price effectiveUntil must be after effectiveFrom.");
        }
    }

    private void validateCycle(BillingCycle cycle) {
        if (cycle != null && cycle != BillingCycle.MONTHLY && cycle != BillingCycle.YEARLY) {
            throw new InvalidRequestException("Product prices support MONTHLY and YEARLY billing cycles only.");
        }
    }

    private void translateState(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            throw new InvalidStateException(exception.getMessage());
        }
    }

    private <T> T translateState(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalStateException exception) {
            throw new InvalidStateException(exception.getMessage());
        }
    }

    private static final class ActionPermissionSnapshot {
        private final AdminMutationAuthorizer.GrantCeiling ceiling;
        private final Map<String, Boolean> decisions = new HashMap<>();

        private ActionPermissionSnapshot(AdminMutationAuthorizer.GrantCeiling ceiling) {
            this.ceiling = ceiling;
        }

        private boolean has(String permissionCode) {
            return decisions.computeIfAbsent(permissionCode,
                    code -> ceiling.allows(code) && PermissionGuard.has(new Permission(code)));
        }
    }
}
