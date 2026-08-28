package com.hiveapp.platform.client.plan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminMutationAuthorizer;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.account.service.AccountDirectoryService;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.service.*;
import com.hiveapp.platform.registry.definition.*;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.exception.*;
import dev.karroumi.permissionizer.PermissionNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@PermissionNode(
    key = OffersFeature.KEY,
    description = "Commercial Offer management",
    guard = PermissionNode.Guard.ON)
public class CommercialOfferAdminServiceImpl extends PlatformControlFeatureService
    implements CommercialOfferAdminService {
  private final CommercialOfferRepository offers;
  private final CommercialOfferCodeReservationRepository codes;
  private final CommercialOfferLineageRepository lineages;
  private final CommercialOfferCapacityRepository capacities;
  private final CommercialOfferRedemptionRepository redemptions;
  private final CommercialCampaignRepository campaigns;
  private final ProductPriceRepository prices;
  private final PlanFeatureRepository planFeatures;
  private final AddOnFeatureRepository addOnFeatures;
  private final CommercialOfferPublicationValidator publicationValidator;
  private final CommercialOfferDefinitionAssessor definitionAssessor;
  private final CommercialOfferAdminProjectionMapper projections;
  private final CommercialOfferClientProjectionMapper clientProjections;
  private final SubscriptionChangeOperationRepository subscriptionOperations;
  private final SubscriptionChangeOperationProjectionMapper operationProjections;
  private final AdminUserRepository admins;
  private final AdminMutationAuthorizer authorizer;
  private final CommercialCatalogVersionService catalogVersions;
  private final RegistryCatalogVersionService registryVersions;
  private final CommercialPreviewTokenService previewTokens;
  private final Clock clock;
  private final CommercialOfferCodeHasher codeHasher;
  private final AccountDirectoryService accountDirectoryService;
  private final CommercialOfferService commercialOfferService;
  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;

  @Override
  protected FeatureDefinition featureDefinition() {
    return OffersFeature.definition();
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "list", description = "List Offers")
  public Page<CommercialOfferViews.Summary> list(
      String search,
      CommercialOfferStatus status,
      UUID campaignId,
      CommercialOfferDiscovery discovery,
      CommercialOfferAcceptance acceptance,
      boolean archived,
      Pageable p) {
    Specification<CommercialOffer> s =
        (r, q, c) -> {
          var ps = new ArrayList<jakarta.persistence.criteria.Predicate>();
          if (!archived && status != CommercialOfferStatus.ARCHIVED)
            ps.add(c.notEqual(r.get("status"), CommercialOfferStatus.ARCHIVED));
          if (status != null) ps.add(c.equal(r.get("status"), status));
          if (campaignId != null) {
            ps.add(c.equal(r.get("lineage").get("campaign").get("id"), campaignId));
          }
          if (discovery != null) {
            ps.add(c.equal(r.get("lineage").get("discovery"), discovery));
          }
          if (acceptance != null) {
            ps.add(c.equal(r.get("lineage").get("acceptance"), acceptance));
          }
          if (search != null && !search.isBlank()) {
            String x =
                "%"
                    + search.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_")
                    + "%";
            ps.add(
                c.or(
                    c.like(c.lower(r.get("name")), x, '\\'),
                    c.like(c.lower(r.get("lineage").get("businessCode")), x, '\\')));
          }
          return c.and(ps.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var ceiling = authorizer.currentActorGrantCeiling();
    Page<CommercialOffer> page = offers.findAll(s, bounded(p));
    if (page.isEmpty()) return Page.empty(page.getPageable());
    Set<UUID> ids =
        page.getContent().stream()
            .map(CommercialOffer::getId)
            .collect(java.util.stream.Collectors.toSet());
    Set<UUID> lineageIds =
        page.getContent().stream()
            .map(CommercialOffer::getLineageId)
            .collect(java.util.stream.Collectors.toSet());
    Set<UUID> referenced = offers.findReferencedSourceIds(ids);
    Set<UUID> published =
        offers.findLineageIdsWithStatus(lineageIds, CommercialOfferStatus.PUBLISHED);
    Set<UUID> drafts = offers.findLineageIdsWithStatus(lineageIds, CommercialOfferStatus.DRAFT);
    List<CommercialOfferViews.Summary> content =
        page.getContent().stream()
            .map(
                offer ->
                    summary(
                        offer,
                        ceiling,
                        new ActionContext(
                            referenced.contains(offer.getId()),
                            published.contains(offer.getLineageId()),
                            drafts.contains(offer.getLineageId()))))
            .toList();
    return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "read", description = "Read Offer")
  public CommercialOfferViews.Detail get(UUID id) {
    return detail(require(id));
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "read_operations", description = "Read Offer operations")
  public CommercialOfferViews.OperationState operations(UUID id) {
    CommercialOffer offer =
        offers
            .findOperationsById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CommercialOffer", "id", id));
    ActionState state = actions(offer, authorizer.currentActorGrantCeiling(), true);
    return new CommercialOfferViews.OperationState(
        offer.getId(),
        offer.getBusinessCode(),
        offer.getName(),
        offer.getStatus(),
        offer.getRevisionNumber(),
        offer.getVersion(),
        state.available(),
        state.blocked());
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "read_editable_definition", description = "Read editable Offer definition")
  public CommercialOfferViews.EditableDefinition editableDefinition(UUID id) {
    CommercialOffer offer = require(id);
    if (offer.getStatus() != CommercialOfferStatus.DRAFT) {
      throw new InvalidStateException("Only a draft Offer has an editable definition.");
    }
    var exactPrices = clientProjections.exactPrices(List.of(offer));
    return new CommercialOfferViews.EditableDefinition(
        offer.getId(),
        offer.getBusinessCode(),
        offer.getStatus(),
        offer.getName(),
        offer.getDescription(),
        offer.getStartsAt(),
        offer.getEndsAt(),
        offer.getDiscovery(),
        offer.getAcceptance(),
        offer.getCustomerCodeHash() != null,
        projections.campaignChoice(offer.getCampaign()),
        offer.getLineageId(),
        offer.getRevisionNumber(),
        definitionAssessor.lineageTermsEditable(offer),
        offer.getGlobalLimit(),
        offer.getPerAccountLimit(),
        offer.getSelection(),
        clientProjections.selection(offer, exactPrices),
        offer.getEffects(),
        offer.getVersion());
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(
      key = "preview_create_definition",
      description = "Preview a new Offer definition")
  public CommercialOfferViews.DefinitionPreview previewCreateDefinition(
      CommercialOfferRequests.Create request) {
    var assessment = definitionAssessor.assessCreate(request);
    return definitionPreview(null, null, assessment);
  }

  @Override
  @Transactional
  @CommercialCatalogMutation
  @PermissionNode(key = "create", description = "Create Offer draft")
  public CommercialOfferViews.Mutation create(CommercialOfferRequests.Create r) {
    definitionAssessor.requireValid(definitionAssessor.assessCreate(r));
    var campaign = requireCampaign(r.campaignId());
    var owner = currentOwner();
    var lineage =
        lineages.saveAndFlush(
            CommercialOfferLineage.create(
                nextCode(r.name()),
                campaign,
                r.discovery(),
                r.acceptance(),
                codeHasher.hash(r.customerCode()),
                r.globalLimit(),
                r.perAccountLimit(),
                owner));
    var o =
        CommercialOffer.draft(
            lineage,
            r.name(),
            r.description(),
            r.startsAt(),
            r.endsAt(),
            r.selection(),
            r.effects());
    try {
      o = offers.saveAndFlush(o);
      capacities.saveAndFlush(CommercialOfferCapacity.create(o.getLineageId()));
      return mutation(o);
    } catch (DataIntegrityViolationException e) {
      throw new InvalidStateException("Offer changed concurrently; reload and retry.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(
      key = "preview_update_definition",
      description = "Preview an Offer definition update")
  public CommercialOfferViews.DefinitionPreview previewUpdateDefinition(
      UUID id, CommercialOfferRequests.Update request) {
    var offer = require(id);
    if (offer.getStatus() != CommercialOfferStatus.DRAFT) {
      throw new InvalidStateException("Only a draft Offer has an editable definition.");
    }
    var assessment = definitionAssessor.assessUpdate(offer, request);
    return definitionPreview(offer.getId(), offer.getVersion(), assessment);
  }

  @Override
  @Transactional
  @CommercialCatalogMutation
  @PermissionNode(key = "update", description = "Update Offer draft")
  public CommercialOfferViews.Mutation update(UUID id, CommercialOfferRequests.Update r) {
    var o = lock(id);
    version(o, r.version());
    definitionAssessor.requireValid(definitionAssessor.assessUpdate(o, r));
    translate(
        () ->
            o.edit(
                r.name(), r.description(), r.startsAt(), r.endsAt(), r.selection(), r.effects()));
    if (r.lineageTerms() != null) {
      if (o.getRevisionNumber() != 1 || o.getLineage().getFirstPublishedAt() != null)
        throw new InvalidStateException("Published Offer lineage terms are immutable.");
      var t = r.lineageTerms();
      String hash =
          switch (t.customerCodeChange().mode()) {
            case KEEP -> o.getCustomerCodeHash();
            case REMOVE -> null;
            case REPLACE -> {
              if (t.customerCodeChange().value() == null
                  || t.customerCodeChange().value().isBlank())
                throw new InvalidRequestException("Replacement customer code is required.");
              yield codeHasher.hash(t.customerCodeChange().value());
            }
          };
      o.getLineage()
          .editBeforeFirstPublication(
              t.discovery(), t.acceptance(), hash, t.globalLimit(), t.perAccountLimit());
    }
    offers.flush();
    return mutation(o);
  }

  @Override
  @Transactional
  @CommercialCatalogMutation
  @PermissionNode(key = "duplicate", description = "Duplicate Offer")
  public CommercialOfferViews.Mutation duplicate(UUID id, CommercialOfferRequests.Duplicate r) {
    var src = lock(id);
    version(src, r.version());
    var lineage =
        lineages.saveAndFlush(
            CommercialOfferLineage.create(
                nextCode(r.name()),
                src.getCampaign(),
                src.getDiscovery(),
                src.getAcceptance(),
                null,
                src.getGlobalLimit(),
                src.getPerAccountLimit(),
                currentOwner()));
    var copy = offers.saveAndFlush(src.duplicate(lineage, r.name()));
    capacities.saveAndFlush(CommercialOfferCapacity.create(copy.getLineageId()));
    return mutation(copy);
  }

  @Override
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @CommercialCatalogMutation
  @PermissionNode(key = "revise", description = "Revise Offer")
  public CommercialOfferViews.Mutation revise(UUID id, CommercialOfferRequests.VersionReason r) {
    var src = lock(id);
    version(src, r.version());
    offers.lockLineage(src.getLineageId());
    try {
      return mutation(offers.saveAndFlush(src.revise(offers.maxRevision(src.getLineageId()) + 1)));
    } catch (DataIntegrityViolationException e) {
      throw new DraftSuccessorExistsException("An Offer draft successor already exists.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "revisions", description = "Read Offer revisions")
  public Page<CommercialOfferViews.Revision> revisions(UUID id, Pageable p) {
    var o = require(id);
    return offers
        .findAllByLineage_Id(o.getLineageId(), bounded(p))
        .map(
            x ->
                new CommercialOfferViews.Revision(
                    x.getId(),
                    x.getRevisionNumber(),
                    x.getStatus(),
                    x.getSourceOffer() == null ? null : x.getSourceOffer().getId(),
                    x.getVersion(),
                    x.getCreatedAt()));
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "compare", description = "Compare Offer revisions")
  public CommercialOfferViews.Comparison compare(UUID a, UUID b) {
    var l = require(a);
    var r = require(b);
    Set<String> changed = new LinkedHashSet<>();
    if (!Objects.equals(l.getName(), r.getName())) changed.add("name");
    if (!Objects.equals(l.getDescription(), r.getDescription())) changed.add("description");
    if (l.getStatus() != r.getStatus()) changed.add("status");
    if (!Objects.equals(l.getCampaign().getId(), r.getCampaign().getId())) changed.add("campaign");
    if (l.getDiscovery() != r.getDiscovery()) changed.add("discovery");
    if (l.getAcceptance() != r.getAcceptance()) changed.add("acceptance");
    if ((l.getCustomerCodeHash() == null) != (r.getCustomerCodeHash() == null)) {
      changed.add("customerCode");
    }
    if (!Objects.equals(l.getSelection(), r.getSelection())) changed.add("selection");
    if (!Objects.equals(l.getEffects(), r.getEffects())) changed.add("effects");
    if (!Objects.equals(l.getStartsAt(), r.getStartsAt())
        || !Objects.equals(l.getEndsAt(), r.getEndsAt())) changed.add("window");
    if (!Objects.equals(l.getGlobalLimit(), r.getGlobalLimit())
        || !Objects.equals(l.getPerAccountLimit(), r.getPerAccountLimit())) changed.add("capacity");
    Map<UUID, ProductPrice> exactPrices = clientProjections.exactPrices(List.of(l, r));
    boolean directSuccessor =
        l.getSourceOffer() != null && l.getSourceOffer().getId().equals(r.getId())
            || r.getSourceOffer() != null && r.getSourceOffer().getId().equals(l.getId());
    return new CommercialOfferViews.Comparison(
        a,
        b,
        l.getLineageId().equals(r.getLineageId()),
        directSuccessor,
        Set.copyOf(changed),
        projections.comparisonDefinition(l, clientProjections.selection(l, exactPrices)),
        projections.comparisonDefinition(r, clientProjections.selection(r, exactPrices)));
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  @PermissionNode(key = "preview_publish", description = "Preview Offer publication")
  public CommercialOfferViews.PublicationPreview previewPublication(UUID id) {
    var o = require(id);
    long catalog = catalogVersions.currentRevision();
    String registry = registryVersions.currentVersion();
    var blockers = publicationValidator.blockers(o);
    var definitionIssues = publicationValidator.definitionIssues(o);
    Instant now = clock.instant();
    String fp = fingerprint(o, blockers, definitionIssues);
    var evidence =
        previewTokens.issue(
            CommercialPreviewKind.COMMERCIAL_OFFER_PUBLICATION,
            o.getId(),
            o.getVersion(),
            authorizer.currentActorUserId(),
            catalog,
            registry,
            fp,
            now);
    return new CommercialOfferViews.PublicationPreview(
        id,
        o.getVersion(),
        o.getStatus() == CommercialOfferStatus.RETIRED
            ? CommercialOfferPublicationMode.RESTORE
            : CommercialOfferPublicationMode.PUBLISH,
        blockers.isEmpty() && definitionIssues.isEmpty(),
        blockers,
        definitionIssues,
        evidence.evaluatedAt(),
        evidence.expiresAt(),
        evidence.token());
  }

  @Override
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @CommercialCatalogMutation
  @PermissionNode(key = "publish", description = "Publish reviewed Offer")
  public CommercialOfferViews.Mutation publish(UUID id, CommercialOfferRequests.Publish r) {
    registryVersions.lockForMutation();
    String registry = registryVersions.currentVersion();
    var o = lock(id);
    offers.lockLineage(o.getLineageId());
    long catalog = catalogVersions.currentRevision();
    var blockers = publicationValidator.blockers(o);
    var definitionIssues = publicationValidator.definitionIssues(o);
    previewTokens.requireValid(
        r.previewToken(),
        CommercialPreviewKind.COMMERCIAL_OFFER_PUBLICATION,
        o.getId(),
        o.getVersion(),
        authorizer.currentActorUserId(),
        catalog,
        registry,
        fingerprint(o, blockers, definitionIssues),
        StaleOfferPreviewException::new);
    if (r.version() != o.getVersion()) throw new StaleOfferPreviewException();
    if (!blockers.isEmpty() || !definitionIssues.isEmpty())
      throw new InvalidStateException("Offer cannot be published: " + blockers);
    reserveCode(o);
    offers
        .findFirstByLineage_IdAndStatus(o.getLineageId(), CommercialOfferStatus.PUBLISHED)
        .filter(old -> !old.getId().equals(o.getId()))
        .ifPresent(old -> old.retire(clock.instant()));
    Instant publishedAt = clock.instant();
    o.getLineage().markFirstPublished(publishedAt);
    translate(() -> o.publish(publishedAt));
    offers.flush();
    return mutation(o);
  }

  @Override
  @Transactional
  @CommercialCatalogMutation
  @PermissionNode(key = "retire", description = "Retire Offer")
  public CommercialOfferViews.Mutation retire(UUID id, CommercialOfferRequests.VersionReason r) {
    var o = lock(id);
    version(o, r.version());
    translate(() -> o.retire(clock.instant()));
    offers.flush();
    return mutation(o);
  }

  @Override
  @Transactional
  @CommercialCatalogMutation
  @PermissionNode(key = "restore", description = "Restore Offer")
  public CommercialOfferViews.Mutation restore(UUID id, CommercialOfferRequests.Publish r) {
    registryVersions.lockForMutation();
    String registry = registryVersions.currentVersion();
    var o = lock(id);
    offers.lockLineage(o.getLineageId());
    long catalog = catalogVersions.currentRevision();
    var blockers = publicationValidator.blockers(o);
    var definitionIssues = publicationValidator.definitionIssues(o);
    previewTokens.requireValid(
        r.previewToken(),
        CommercialPreviewKind.COMMERCIAL_OFFER_PUBLICATION,
        o.getId(),
        o.getVersion(),
        authorizer.currentActorUserId(),
        catalog,
        registry,
        fingerprint(o, blockers, definitionIssues),
        StaleOfferPreviewException::new);
    if (r.version() != o.getVersion()) throw new StaleOfferPreviewException();
    if (!blockers.isEmpty() || !definitionIssues.isEmpty())
      throw new InvalidStateException("Offer cannot be restored: " + blockers);
    offers
        .findFirstByLineage_IdAndStatus(o.getLineageId(), CommercialOfferStatus.PUBLISHED)
        .ifPresent(
            x -> {
              throw new InvalidStateException(
                  "A published successor already occupies this Offer lineage.");
            });
    translate(o::restore);
    offers.flush();
    return mutation(o);
  }

  @Override
  @Transactional
  @CommercialCatalogMutation
  @PermissionNode(key = "archive", description = "Archive Offer")
  public CommercialOfferViews.Mutation archive(UUID id, CommercialOfferRequests.VersionReason r) {
    var o = lock(id);
    version(o, r.version());
    translate(() -> o.archive(clock.instant()));
    offers.flush();
    return mutation(o);
  }

  @Override
  @Transactional
  @CommercialCatalogMutation
  @PermissionNode(key = "delete", description = "Delete Offer draft")
  public void deleteDraft(UUID id, CommercialOfferRequests.VersionReason r) {
    var o = lock(id);
    version(o, r.version());
    if (o.getStatus() != CommercialOfferStatus.DRAFT)
      throw new InvalidStateException("Only a draft Offer can be deleted.");
    if (offers.existsBySourceOffer_Id(o.getId()))
      throw new InvalidStateException("An Offer referenced by another revision cannot be deleted.");
    boolean deleteLineage = offers.countByLineage_Id(o.getLineageId()) == 1;
    UUID lineageId = o.getLineageId();
    offers.delete(o);
    offers.flush();
    if (deleteLineage) {
      capacities.findByLineageId(lineageId).ifPresent(capacities::delete);
      lineages.deleteById(lineageId);
      lineages.flush();
    }
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "read_owner", description = "Read Offer owner")
  public CommercialOfferViews.Owner owner(UUID id) {
    var o = require(id);
    var owner = o.getOwner();
    var user = owner.getUser();
    return new CommercialOfferViews.Owner(
        o.getId(),
        o.getStatus(),
        o.getVersion(),
        owner.getId(),
        user.getId(),
        user.getEmail(),
        user.getUsername(),
        user.getFullName(),
        owner.isActive());
  }

  @Override
  @Transactional
  @CommercialCatalogMutation
  @PermissionNode(key = "reassign_owner", description = "Reassign Offer lineage owner")
  public CommercialOfferViews.Mutation reassignOwner(
      UUID id, CommercialOfferRequests.ReassignOwner r) {
    var o = lock(id);
    version(o, r.version());
    var owner =
        admins
            .findWithUserById(r.ownerAdminUserId())
            .filter(AdminUser::isActive)
            .orElseThrow(() -> new InvalidRequestException("Offer owner must be active."));
    o.getLineage().reassignOwner(owner);
    lineages.flush();
    return mutation(o);
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "read_stats", description = "Read Offer outcome counts")
  public CommercialOfferViews.Stats stats(UUID id) {
    var o = require(id);
    UUID l = o.getLineageId();
    Map<CommercialOfferRedemptionStatus, Long> counts =
        new EnumMap<>(CommercialOfferRedemptionStatus.class);
    for (Object[] row : redemptions.countStatusesByOfferLineageId(l)) {
      counts.put((CommercialOfferRedemptionStatus) row[0], ((Number) row[1]).longValue());
    }
    CommercialOfferCapacity capacity = capacities.findByLineageId(l).orElse(null);
    Long remaining =
        o.getGlobalLimit() == null || capacity == null
            ? null
            : Math.max(
                0, o.getGlobalLimit() - capacity.getReservedCount() - capacity.getAppliedCount());
    return new CommercialOfferViews.Stats(
        counts.getOrDefault(CommercialOfferRedemptionStatus.RESERVED, 0L),
        counts.getOrDefault(CommercialOfferRedemptionStatus.APPLIED, 0L),
        counts.getOrDefault(CommercialOfferRedemptionStatus.CANCELLED, 0L),
        counts.getOrDefault(CommercialOfferRedemptionStatus.FAILED, 0L),
        o.getGlobalLimit(),
        remaining);
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "read_redemptions", description = "Read Offer redemption outcomes")
  public Page<CommercialOfferViews.Redemption> redemptions(
      UUID id,
      CommercialOfferRedemptionStatus status,
      CommercialOfferSurface surface,
      Pageable p) {
    UUID lineageId = require(id).getLineageId();
    Specification<CommercialOfferRedemption> specification =
        (root, query, cb) -> {
          List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
          predicates.add(cb.equal(root.get("offerLineageId"), lineageId));
          if (status != null) predicates.add(cb.equal(root.get("status"), status));
          if (surface != null) predicates.add(cb.equal(root.get("surface"), surface));
          return cb.and(
              predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    Page<CommercialOfferRedemption> page =
        redemptions.findAll(specification, bounded(p));
    return enrichRedemptions(page);
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(
      key = "read_redemption_detail",
      description = "Read one Offer redemption outcome")
  public CommercialOfferViews.Redemption redemption(UUID id, UUID redemptionId) {
    UUID lineageId = require(id).getLineageId();
    CommercialOfferRedemption redemption =
        redemptions
            .findByIdAndOfferLineageId(redemptionId, lineageId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "CommercialOfferRedemption", "id", redemptionId));
    Map<UUID, ProductPrice> exactPrices =
        clientProjections.exactPrices(List.of(redemption.getOffer()));
    SubscriptionChangeOperation operation =
        subscriptionOperations.findByOfferRedemptionId(redemptionId).orElse(null);
    return projections.redemption(
        redemption,
        clientProjections.selection(redemption.getOffer(), exactPrices),
        operation == null ? null : operationProjections.client(operation));
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(
      key = "read_redemption_identities",
      description = "Read Offer redemption Account identities")
  public List<CommercialOfferViews.RedemptionIdentity> resolveRedemptionIdentities(
      UUID id, Collection<UUID> redemptionIds) {
    if (redemptionIds == null || redemptionIds.isEmpty() || redemptionIds.size() > 100) {
      throw new InvalidRequestException("Between 1 and 100 redemption ids are required.");
    }
    UUID lineageId = require(id).getLineageId();
    return redemptions
        .findAllWithIdentitiesByOfferLineageIdAndIdIn(
            lineageId, new LinkedHashSet<>(redemptionIds))
        .stream()
        .sorted(Comparator.comparing(r -> r.getId().toString()))
        .map(
            r ->
                new CommercialOfferViews.RedemptionIdentity(
                    r.getId(),
                    r.getAccount().getId(),
                    r.getAccount().getName(),
                    r.getActorUserId()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "history", description = "Read Offer lineage history")
  public Page<CommercialOfferViews.History> history(UUID id, Pageable p) {
    var offer = require(id);
    var revisionIds =
        offers.findIdsByLineageId(offer.getLineageId()).stream().map(UUID::toString).toList();
    var page =
        auditLogRepository.findAllByResourceTypeAndResourceIdIn(
            "COMMERCIAL_OFFER_ADMIN", revisionIds, historyPage(p, "occurredAt"));
    Set<UUID> actorIds =
        page.getContent().stream()
            .map(com.hiveapp.shared.audit.domain.AuditLog::getActorUserId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    Map<UUID, String> actorEmails =
        admins.findAllWithUserByUserIdIn(actorIds).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    admin -> admin.getUser().getId(), admin -> admin.getUser().getEmail()));
    return page.map(
        log ->
            new CommercialOfferViews.History(
                log.getId(),
                log.getOccurredAt(),
                log.getAction(),
                log.getOutcome(),
                log.getActorUserId(),
                actorEmails.get(log.getActorUserId()),
                historyReason(log)));
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(
      key = "choose_accounts",
      description = "Choose safe Accounts for one-Account Offer operations")
  public Page<AccountDirectoryEntryDto> chooseAccounts(String query, Boolean active, Pageable p) {
    return accountDirectoryService.search(query, active, bounded(p));
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(
      key = "resolve_account_choices",
      description = "Resolve selected Accounts for Offer operations")
  public List<AccountDirectoryEntryDto> resolveAccountChoices(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty() || ids.size() > 100)
      throw new InvalidRequestException("Between 1 and 100 Account ids are required.");
    return accountDirectoryService.resolve(ids);
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "choose_products", description = "Choose exact Offer products and prices")
  public Page<CommercialOfferViews.PricedChoice> chooseProducts(
      ProductPriceOwnerType type, String query, Pageable p) {
    String term = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
    Specification<ProductPrice> spec =
        (root, q, cb) -> {
          var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
          predicates.add(cb.equal(root.get("ownerType"), type));
          if (term != null) {
            String relation =
                switch (type) {
                  case PLAN -> "plan";
                  case ADD_ON -> "addOn";
                  case QUOTA_PACKAGE -> "quotaPackage";
                };
            String pattern = "%" + term.replace("%", "\\%").replace("_", "\\_") + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get(relation).get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get(relation).get("code")), pattern, '\\')));
          }
          return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    return prices.findAll(spec, bounded(p)).map(projections::pricedChoice);
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(
      key = "resolve_product_choices",
      description = "Resolve selected exact Offer prices")
  public List<CommercialOfferViews.PricedChoice> resolveProductChoices(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty() || ids.size() > 100)
      throw new InvalidRequestException("Between 1 and 100 Price ids are required.");
    return prices.findAllByIdIn(ids).stream().map(projections::pricedChoice).toList();
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(
      key = "choose_quota_resources",
      description = "Choose typed finite quota resources for Offer bonuses")
  public List<CommercialOfferViews.QuotaResourceChoice> chooseQuotaResources(
      Collection<UUID> selectedPriceIds, String query) {
    if (selectedPriceIds == null || selectedPriceIds.isEmpty() || selectedPriceIds.size() > 100) {
      throw new InvalidRequestException("Between 1 and 100 exact Price ids are required.");
    }
    Set<UUID> uniqueIds = new LinkedHashSet<>(selectedPriceIds);
    List<ProductPrice> selectedPrices = prices.findAllByIdIn(uniqueIds);
    if (selectedPrices.size() != uniqueIds.size()) {
      throw new InvalidRequestException("One or more selected exact Prices no longer exist.");
    }
    List<UUID> planIds =
        selectedPrices.stream()
            .filter(price -> price.getOwnerType() == ProductPriceOwnerType.PLAN)
            .map(price -> price.getPlan().getId())
            .distinct()
            .toList();
    if (planIds.size() != 1) {
      throw new InvalidRequestException("Exactly one Plan Price is required.");
    }
    List<UUID> addOnIds =
        selectedPrices.stream()
            .filter(price -> price.getOwnerType() == ProductPriceOwnerType.ADD_ON)
            .map(price -> price.getAddOn().getId())
            .distinct()
            .toList();
    Map<String, CommercialOfferViews.QuotaResourceChoice> selectedResources = new LinkedHashMap<>();
    planFeatures.findAllByPlanId(planIds.getFirst()).stream()
        .filter(item -> item.getMode() == PlanFeatureMode.INCLUDED)
        .forEach(
            item ->
                addFiniteQuotaResources(
                    selectedResources, item.getFeature(), item.getQuotaConfigs()));
    if (!addOnIds.isEmpty()) {
      addOnFeatures.findAllByAddOnIds(addOnIds).stream()
          .forEach(
              item ->
                  addFiniteQuotaResources(
                      selectedResources, item.getFeature(), item.getQuotaConfigs()));
    }
    String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    return selectedResources.values().stream()
        .filter(
            item ->
                term.isBlank()
                    || item.featureCode().toLowerCase(Locale.ROOT).contains(term)
                    || item.resource().toLowerCase(Locale.ROOT).contains(term))
        .limit(100)
        .toList();
  }

  private void addFiniteQuotaResources(
      Map<String, CommercialOfferViews.QuotaResourceChoice> result,
      com.hiveapp.platform.registry.domain.entity.Feature feature,
      List<com.hiveapp.shared.quota.QuotaLimitEntry> limits) {
    Map<String, String> units =
        feature.getQuotaSchema().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    com.hiveapp.shared.quota.QuotaSlot::resource,
                    com.hiveapp.shared.quota.QuotaSlot::unit,
                    (left, right) -> left));
    limits.stream()
        .filter(limit -> limit.mode() == com.hiveapp.shared.quota.QuotaLimitMode.FINITE)
        .forEach(
            limit -> {
              String key = feature.getCode() + "\u0000" + limit.resource();
              result.putIfAbsent(
                  key,
                  new CommercialOfferViews.QuotaResourceChoice(
                      feature.getCode(),
                      feature.getCode(),
                      limit.resource(),
                      units.getOrDefault(limit.resource(), limit.resource())));
            });
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "choose_owners", description = "Choose active Offer owners")
  public Page<CommercialOfferViews.OwnerChoice> chooseOwners(String query, Pageable p) {
    return admins
        .searchPageWithUser(
            query == null || query.isBlank() ? null : query.trim(), true, bounded(p))
        .map(projections::ownerChoice);
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "resolve_owner_choices", description = "Resolve selected Offer owners")
  public List<CommercialOfferViews.OwnerChoice> resolveOwnerChoices(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty() || ids.size() > 100)
      throw new InvalidRequestException("Between 1 and 100 owner ids are required.");
    return admins.findAllWithUserByIdIn(ids).stream().map(projections::ownerChoice).toList();
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(
      key = "choose_campaigns",
      description = "Choose exact Campaign revisions for Offers")
  public Page<CommercialOfferViews.CampaignChoice> chooseCampaigns(String query, Pageable p) {
    String term = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
    Specification<CommercialCampaign> spec =
        (root, q, cb) -> {
          var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
          predicates.add(cb.notEqual(root.get("status"), CommercialCampaignStatus.ARCHIVED));
          if (term != null) {
            String pattern = "%" + term.replace("%", "\\%").replace("_", "\\_") + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("code")), pattern, '\\')));
          }
          return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    return campaigns.findAll(spec, bounded(p)).map(projections::campaignChoice);
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(
      key = "resolve_campaign_choices",
      description = "Resolve selected exact Campaign revisions")
  public List<CommercialOfferViews.CampaignChoice> resolveCampaignChoices(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty() || ids.size() > 100)
      throw new InvalidRequestException("Between 1 and 100 Campaign ids are required.");
    return campaigns.findAllById(ids).stream().map(projections::campaignChoice).toList();
  }

  @Override
  @Transactional(readOnly = true)
  @PermissionNode(key = "preview_for_account", description = "Preview an Offer for one Account")
  public CommercialOfferViews.AccountEligibilityAssessment previewForAccount(
      UUID offerId, UUID accountId) {
    authorizer.requireCanManagePermission(
        "platform.subscriptions.preview_change", "preview an Offer subscription change with");
    return commercialOfferService.assessForOperator(
        accountId, authorizer.currentActorUserId(), offerId);
  }

  @Override
  @Transactional
  @PermissionNode(key = "apply_for_account", description = "Apply a reviewed Offer for one Account")
  public CommercialOfferViews.AdminAcceptance applyForAccount(
      UUID offerId,
      UUID accountId,
      String idempotencyKey,
      CommercialOfferRequests.OperatorAccept request) {
    authorizer.requireCanManagePermission(
        "platform.offers.preview_for_account", "preview an Offer for one Account with");
    authorizer.requireCanManagePermission(
        "platform.subscriptions.preview_change", "preview an Offer subscription change with");
    authorizer.requireCanManagePermission(
        "platform.subscriptions.apply_change", "apply an Offer subscription change with");
    return commercialOfferService.acceptAsOperator(
        accountId, authorizer.currentActorUserId(), offerId, idempotencyKey, request);
  }

  private void reserveCode(CommercialOffer o) {
    if (o.getCustomerCodeHash() == null) return;
    var existing = codes.findByNormalizedCodeHash(o.getCustomerCodeHash());
    if (existing.isPresent() && !existing.get().getLineageId().equals(o.getLineageId()))
      throw new OfferCodeConflictException();
    if (existing.isEmpty())
      try {
        codes.saveAndFlush(
            CommercialOfferCodeReservation.reserveHash(o.getCustomerCodeHash(), o.getLineageId()));
      } catch (DataIntegrityViolationException e) {
        throw new OfferCodeConflictException();
      }
  }

  private CommercialOffer require(UUID id) {
    return offers
        .findDetailById(id)
        .orElseThrow(() -> new ResourceNotFoundException("CommercialOffer", "id", id));
  }

  private CommercialOffer lock(UUID id) {
    return offers
        .findForUpdate(id)
        .orElseThrow(() -> new ResourceNotFoundException("CommercialOffer", "id", id));
  }

  private CommercialCampaign requireCampaign(UUID id) {
    return campaigns
        .findDetailById(id)
        .orElseThrow(() -> new ResourceNotFoundException("CommercialCampaign", "id", id));
  }

  private AdminUser currentOwner() {
    return admins
        .findWithUserById(authorizer.currentActorAdminUserId())
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "AdminUser", "id", authorizer.currentActorAdminUserId()));
  }

  private void version(CommercialOffer o, long v) {
    if (o.getVersion() != v)
      throw new StaleResourceVersionException("Offer changed since it was read.");
  }

  private String nextCode(String n) {
    return CommercialCodeGenerator.generate(n, "OFFER", lineages::existsByBusinessCode);
  }

  private String fingerprint(
      CommercialOffer o,
      List<CommercialOfferBlocker> blockers,
      List<CommercialOfferViews.DefinitionIssue> definitionIssues) {
    return sha(
        o.getId()
            + "|"
            + o.getVersion()
            + "|"
            + o.getCampaign().getId()
            + "|"
            + o.getSelection()
            + "|"
            + o.getEffects()
            + "|"
            + blockers
            + "|"
            + definitionIssues);
  }

  static String sha(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private CommercialOfferViews.Summary summary(CommercialOffer o) {
    return summary(o, authorizer.currentActorGrantCeiling());
  }

  private CommercialOfferViews.Summary summary(
      CommercialOffer o, AdminMutationAuthorizer.GrantCeiling ceiling) {
    return summary(o, ceiling, actionContext(o));
  }

  private CommercialOfferViews.Summary summary(
      CommercialOffer o, AdminMutationAuthorizer.GrantCeiling ceiling, ActionContext context) {
    var a = actions(o, ceiling, context);
    return new CommercialOfferViews.Summary(
        o.getId(),
        o.getBusinessCode(),
        o.getName(),
        o.getStatus(),
        o.getDiscovery(),
        o.getAcceptance(),
        projections.campaignChoice(o.getCampaign()),
        o.getStartsAt(),
        o.getEndsAt(),
        o.getRevisionNumber(),
        o.getVersion(),
        a.available(),
        a.blocked());
  }

  private CommercialOfferViews.Detail detail(CommercialOffer o) {
    var a = actions(o, authorizer.currentActorGrantCeiling(), true);
    Map<UUID, ProductPrice> exactPrices = clientProjections.exactPrices(List.of(o));
    return new CommercialOfferViews.Detail(
        o.getId(),
        o.getBusinessCode(),
        o.getName(),
        o.getDescription(),
        o.getStatus(),
        o.getDiscovery(),
        o.getAcceptance(),
        o.getCustomerCodeHash() != null,
        o.getCampaign().getId(),
        o.getCampaign().getCode(),
        projections.campaignChoice(o.getCampaign()),
        o.getStartsAt(),
        o.getEndsAt(),
        o.getLineageId(),
        o.getRevisionNumber(),
        o.getGlobalLimit(),
        o.getPerAccountLimit(),
        o.getSelection(),
        clientProjections.selection(o, exactPrices),
        o.getEffects(),
        o.getPublishedAt(),
        o.getRetiredAt(),
        o.getArchivedAt(),
        o.getVersion(),
        a.available(),
        a.blocked());
  }

  private ActionState actions(CommercialOffer o, AdminMutationAuthorizer.GrantCeiling ceiling) {
    return actions(o, ceiling, false);
  }

  private ActionState actions(
      CommercialOffer o, AdminMutationAuthorizer.GrantCeiling ceiling, boolean validateDefinition) {
    return actions(o, ceiling, actionContext(o), validateDefinition);
  }

  private ActionState actions(
      CommercialOffer o, AdminMutationAuthorizer.GrantCeiling ceiling, ActionContext context) {
    return actions(o, ceiling, context, false);
  }

  private ActionState actions(
      CommercialOffer o,
      AdminMutationAuthorizer.GrantCeiling ceiling,
      ActionContext context,
      boolean validateDefinition) {
    boolean invalidSelection =
        validateDefinition
            && (o.getStatus() == CommercialOfferStatus.DRAFT
                || o.getStatus() == CommercialOfferStatus.RETIRED)
            && publicationValidator.hasInvalidSelection(o);
    List<CommercialOfferAction> available = new ArrayList<>();
    Map<CommercialOfferAction, List<CommercialOfferBlocker>> blocked =
        new EnumMap<>(CommercialOfferAction.class);
    for (CommercialOfferAction action : CommercialOfferAction.values()) {
      if (!ceiling.allowsAll(actionPermissions(action))) continue;
      List<CommercialOfferBlocker> blockers = actionBlockers(o, action, context, invalidSelection);
      if (blockers.isEmpty()) available.add(action);
      else blocked.put(action, blockers);
    }
    return new ActionState(List.copyOf(available), Map.copyOf(blocked));
  }

  private Set<String> actionPermissions(CommercialOfferAction action) {
    String base = "platform.offers.";
    return switch (action) {
      case PUBLISH -> Set.of(base + "preview_publish", base + "publish");
      case RESTORE -> Set.of(base + "preview_publish", base + "restore");
      case PREVIEW_FOR_ACCOUNT ->
          Set.of(base + "preview_for_account", "platform.subscriptions.preview_change");
      case APPLY_FOR_ACCOUNT ->
          Set.of(
              base + "preview_for_account",
              base + "apply_for_account",
              "platform.subscriptions.preview_change",
              "platform.subscriptions.apply_change");
      case READ_DEFINITION -> Set.of(base + "read_editable_definition");
      case UPDATE -> Set.of(base + "update");
      case DUPLICATE -> Set.of(base + "duplicate");
      case REVISE -> Set.of(base + "revise");
      case REVISIONS -> Set.of(base + "revisions");
      case COMPARE -> Set.of(base + "compare");
      case HISTORY -> Set.of(base + "history");
      case PREVIEW_PUBLICATION -> Set.of(base + "preview_publish");
      case RETIRE -> Set.of(base + "retire");
      case ARCHIVE -> Set.of(base + "archive");
      case DELETE_DRAFT -> Set.of(base + "delete");
      case OWNER -> Set.of(base + "read_owner");
      case REASSIGN_OWNER -> Set.of(base + "reassign_owner");
      case READ_STATS -> Set.of(base + "read_stats");
      case READ_REDEMPTIONS -> Set.of(base + "read_redemptions");
      case READ_REDEMPTION_IDENTITIES -> Set.of(base + "read_redemption_identities");
    };
  }

  private List<CommercialOfferBlocker> actionBlockers(
      CommercialOffer o,
      CommercialOfferAction action,
      ActionContext context,
      boolean invalidSelection) {
    List<CommercialOfferBlocker> blockers = new ArrayList<>();
    switch (action) {
      case UPDATE, READ_DEFINITION -> {
        if (o.getStatus() != CommercialOfferStatus.DRAFT) {
          blockers.add(CommercialOfferBlocker.NOT_DRAFT);
        }
      }
      case DELETE_DRAFT -> {
        if (o.getStatus() != CommercialOfferStatus.DRAFT) {
          blockers.add(CommercialOfferBlocker.NOT_DRAFT);
        }
        if (context.derived()) blockers.add(CommercialOfferBlocker.HAS_DERIVED_OFFERS);
      }
      case PUBLISH -> {
        if (o.getStatus() != CommercialOfferStatus.DRAFT) {
          blockers.add(CommercialOfferBlocker.NOT_DRAFT);
        } else {
          blockers.addAll(publicationValidator.actionBlockers(o, context.publishedExists()));
          if (invalidSelection) blockers.add(CommercialOfferBlocker.INVALID_SELECTION);
        }
      }
      case PREVIEW_PUBLICATION -> {
        if (o.getStatus() != CommercialOfferStatus.DRAFT
            && o.getStatus() != CommercialOfferStatus.RETIRED) {
          blockers.add(CommercialOfferBlocker.NOT_DRAFT);
        } else {
          blockers.addAll(publicationValidator.actionBlockers(o, context.publishedExists()));
          if (invalidSelection) blockers.add(CommercialOfferBlocker.INVALID_SELECTION);
        }
      }
      case RETIRE -> {
        if (o.getStatus() != CommercialOfferStatus.PUBLISHED)
          blockers.add(CommercialOfferBlocker.NOT_PUBLISHED);
      }
      case RESTORE -> {
        if (o.getStatus() != CommercialOfferStatus.RETIRED) {
          blockers.add(CommercialOfferBlocker.NOT_RETIRED);
        } else {
          blockers.addAll(publicationValidator.actionBlockers(o, context.publishedExists()));
          if (invalidSelection) blockers.add(CommercialOfferBlocker.INVALID_SELECTION);
        }
      }
      case ARCHIVE -> {
        if (o.getStatus() != CommercialOfferStatus.RETIRED)
          blockers.add(CommercialOfferBlocker.NOT_RETIRED);
      }
      case REVISE -> {
        if (o.getStatus() != CommercialOfferStatus.PUBLISHED
            && o.getStatus() != CommercialOfferStatus.RETIRED) {
          blockers.add(CommercialOfferBlocker.NOT_PUBLISHED);
        }
        if (context.draftExists()) blockers.add(CommercialOfferBlocker.DRAFT_SUCCESSOR_EXISTS);
      }
      case PREVIEW_FOR_ACCOUNT, APPLY_FOR_ACCOUNT -> {
        Instant now = clock.instant();
        if (o.getStatus() != CommercialOfferStatus.PUBLISHED) {
          blockers.add(CommercialOfferBlocker.NOT_PUBLISHED);
        }
        if (o.getCampaign().getStatus() != CommercialCampaignStatus.ACTIVE) {
          blockers.add(CommercialOfferBlocker.CAMPAIGN_NOT_ACTIVE);
        }
        if (now.isBefore(o.getStartsAt())) {
          blockers.add(CommercialOfferBlocker.WINDOW_NOT_STARTED);
        }
        if (!now.isBefore(o.getEndsAt())) {
          blockers.add(CommercialOfferBlocker.WINDOW_ENDED);
        }
      }
      default -> {}
    }
    return List.copyOf(new LinkedHashSet<>(blockers));
  }

  private ActionContext actionContext(CommercialOffer offer) {
    return new ActionContext(
        offers.existsBySourceOffer_Id(offer.getId()),
        offers.existsByLineage_IdAndStatus(offer.getLineageId(), CommercialOfferStatus.PUBLISHED),
        offers.existsByLineage_IdAndStatus(offer.getLineageId(), CommercialOfferStatus.DRAFT));
  }

  private record ActionContext(boolean derived, boolean publishedExists, boolean draftExists) {}

  private record ActionState(
      List<CommercialOfferAction> available,
      Map<CommercialOfferAction, List<CommercialOfferBlocker>> blocked) {}

  private CommercialOfferViews.DefinitionPreview definitionPreview(
      UUID offerId,
      Long expectedVersion,
      CommercialOfferDefinitionAssessor.Assessment assessment) {
    return new CommercialOfferViews.DefinitionPreview(
        offerId,
        expectedVersion,
        assessment.valid(),
        assessment.lineageTermsEditable(),
        assessment.campaign(),
        assessment.resolvedSelection(),
        assessment.issues());
  }

  private Page<CommercialOfferViews.Redemption> enrichRedemptions(
      Page<CommercialOfferRedemption> page) {
    if (page.isEmpty()) return Page.empty(page.getPageable());
    List<CommercialOffer> pageOffers =
        page.getContent().stream().map(CommercialOfferRedemption::getOffer).toList();
    Map<UUID, ProductPrice> exactPrices = clientProjections.exactPrices(pageOffers);
    Set<UUID> redemptionIds =
        page.getContent().stream()
            .map(CommercialOfferRedemption::getId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    Map<UUID, SubscriptionChangeOperation> operations =
        subscriptionOperations.findAllByOfferRedemptionIdIn(redemptionIds).stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    SubscriptionChangeOperation::getOfferRedemptionId,
                    java.util.function.Function.identity()));
    List<CommercialOfferViews.Redemption> content =
        page.getContent().stream()
            .map(
                redemption -> {
                  SubscriptionChangeOperation operation = operations.get(redemption.getId());
                  return projections.redemption(
                      redemption,
                      clientProjections.selection(redemption.getOffer(), exactPrices),
                      operation == null ? null : operationProjections.client(operation));
                })
            .toList();
    return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
  }

  private CommercialOfferViews.Mutation mutation(CommercialOffer offer) {
    return new CommercialOfferViews.Mutation(offer.getId(), offer.getStatus(), offer.getVersion());
  }

  private Pageable bounded(Pageable p) {
    if (p == null || p.getPageNumber() < 0 || p.getPageSize() < 1 || p.getPageSize() > 100)
      throw new InvalidRequestException("Page must be non-negative and size between 1 and 100.");
    return p;
  }

  private Pageable historyPage(Pageable pageable, String property) {
    Pageable bounded = bounded(pageable);
    return PageRequest.of(
        bounded.getPageNumber(),
        bounded.getPageSize(),
        Sort.by(Sort.Direction.DESC, property).and(Sort.by(Sort.Direction.DESC, "id")));
  }

  private String historyReason(com.hiveapp.shared.audit.domain.AuditLog log) {
    if (log.getRequestData() == null) return null;
    try {
      return findTextField(objectMapper.readTree(log.getRequestData()), "reason", 0);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
      return null;
    }
  }

  private String findTextField(
      com.fasterxml.jackson.databind.JsonNode node, String fieldName, int depth) {
    if (node == null || depth > 8) return null;
    if (node.isObject()) {
      var direct = node.get(fieldName);
      if (direct != null && direct.isTextual() && !direct.textValue().isBlank()) {
        return direct.textValue().trim();
      }
      var fields = node.fields();
      while (fields.hasNext()) {
        String nested = findTextField(fields.next().getValue(), fieldName, depth + 1);
        if (nested != null) return nested;
      }
    } else if (node.isArray()) {
      for (var child : node) {
        String nested = findTextField(child, fieldName, depth + 1);
        if (nested != null) return nested;
      }
    }
    return null;
  }

  private void translate(Runnable r) {
    try {
      r.run();
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException(e.getMessage());
    } catch (IllegalStateException e) {
      throw new InvalidStateException(e.getMessage());
    }
  }
}
