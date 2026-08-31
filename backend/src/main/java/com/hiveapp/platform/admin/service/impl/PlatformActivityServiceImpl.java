package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.identity.service.IdentityService;
import com.hiveapp.identity.service.UserView;
import com.hiveapp.platform.admin.dto.PlatformActivityModels;
import com.hiveapp.platform.admin.service.PlatformActivityService;
import com.hiveapp.platform.client.account.dto.AccountIdentityDirectoryEntryDto;
import com.hiveapp.platform.client.account.service.AccountDirectoryService;
import com.hiveapp.platform.registry.definition.ActivitiesFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionNode;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PermissionNode(key = ActivitiesFeature.KEY, description = "Platform Activities",
        guard = PermissionNode.Guard.ON)
public class PlatformActivityServiceImpl extends PlatformControlFeatureService
        implements PlatformActivityService {
    private static final Duration DEFAULT_RANGE = Duration.ofHours(24);
    private static final Duration MAX_RANGE = Duration.ofDays(366);
    private static final int MAX_ACTION_LENGTH = 180;
    private static final int MAX_RESOURCE_TYPE_LENGTH = 100;
    private static final int MAX_RESOURCE_ID_LENGTH = 100;

    private final AuditLogRepository auditLogs;
    private final IdentityService identities;
    private final AccountDirectoryService accounts;
    private final Clock clock;

    public PlatformActivityServiceImpl(
            AuditLogRepository auditLogs,
            IdentityService identities,
            AccountDirectoryService accounts,
            Clock clock
    ) {
        this.auditLogs = auditLogs;
        this.identities = identities;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    protected FeatureDefinition featureDefinition() {
        return ActivitiesFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "Read safe platform activity metadata")
    public Page<PlatformActivityModels.Activity> search(Query request, Pageable pageable) {
        NormalizedQuery query = normalize(request);
        Page<AuditLog> page = auditLogs.findAll(specification(query), pageable);
        return map(page);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "internal_detail", guard = PermissionNode.Guard.OFF)
    public PlatformActivityModels.Activity detail(UUID id) {
        require("read");
        AuditLog log = requireLog(id);
        return map(List.of(log)).get(log.getId());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_payload",
            description = "Read sanitized activity request and result summaries")
    public PlatformActivityModels.Payload payload(UUID id) {
        require("read");
        AuditLog log = requireLog(id);
        return new PlatformActivityModels.Payload(
                log.getId(), log.getRequestData(), log.getResultData(), log.getFailureType());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_actor_identity",
            description = "Resolve actor identity for selected platform activities")
    public List<PlatformActivityModels.ActorResolution> actorIdentities(List<UUID> activityIds) {
        require("read");
        List<AuditLog> logs = requireSelected(activityIds);
        Map<UUID, UserView> views = identities.findUserViews(logs.stream()
                        .map(AuditLog::getActorUserId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(UserView::id, Function.identity()));
        return logs.stream().map(log -> {
            UserView actor = log.getActorUserId() == null ? null : views.get(log.getActorUserId());
            return new PlatformActivityModels.ActorResolution(
                    log.getId(), log.getActorUserId(),
                    actor == null ? null : new PlatformActivityModels.ActorIdentity(
                            displayName(actor), actor.email(), actor.active()));
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_account_identity",
            description = "Resolve target Account identity for selected platform activities")
    public List<PlatformActivityModels.AccountResolution> accountIdentities(List<UUID> activityIds) {
        require("read");
        List<AuditLog> logs = requireSelected(activityIds);
        Map<UUID, AccountIdentityDirectoryEntryDto> views = accounts.resolveIdentities(logs.stream()
                        .map(AuditLog::getTargetAccountId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(
                        AccountIdentityDirectoryEntryDto::id, Function.identity()));
        return logs.stream().map(log -> {
            var account = log.getTargetAccountId() == null
                    ? null : views.get(log.getTargetAccountId());
            return new PlatformActivityModels.AccountResolution(
                    log.getId(), log.getTargetAccountId(),
                    account == null ? null : new PlatformActivityModels.AccountIdentity(
                            account.name(), account.slug(), account.active()));
        }).toList();
    }

    private Page<PlatformActivityModels.Activity> map(Page<AuditLog> page) {
        Map<UUID, PlatformActivityModels.Activity> mapped = map(page.getContent());
        return page.map(log -> mapped.get(log.getId()));
    }

    private Map<UUID, PlatformActivityModels.Activity> map(List<AuditLog> logs) {
        if (logs.isEmpty()) return Map.of();
        boolean actorIdentityAllowed = has("read_actor_identity");
        boolean accountIdentityAllowed = has("read_account_identity");

        Map<UUID, UserView> actorViews = actorIdentityAllowed
                ? identities.findUserViews(logs.stream()
                        .map(AuditLog::getActorUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                        .stream().collect(Collectors.toMap(UserView::id, Function.identity()))
                : Map.of();
        Map<UUID, AccountIdentityDirectoryEntryDto> accountViews = accountIdentityAllowed
                ? accounts.resolveIdentities(logs.stream()
                        .map(AuditLog::getTargetAccountId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                        .stream().collect(Collectors.toMap(
                                AccountIdentityDirectoryEntryDto::id, Function.identity()))
                : Map.of();

        return logs.stream().collect(Collectors.toMap(
                AuditLog::getId,
                log -> toActivity(
                        log,
                        log.getActorUserId() == null ? null : actorViews.get(log.getActorUserId()),
                        log.getTargetAccountId() == null
                                ? null : accountViews.get(log.getTargetAccountId()))));
    }

    private PlatformActivityModels.Activity toActivity(
            AuditLog log,
            UserView actor,
            AccountIdentityDirectoryEntryDto account
    ) {
        return new PlatformActivityModels.Activity(
                log.getId(),
                log.getOccurredAt(),
                log.getActorSurface(),
                log.getActorUserId(),
                actor == null ? null : new PlatformActivityModels.ActorIdentity(
                        displayName(actor), actor.email(), actor.active()),
                log.getClientAccountId(),
                log.getTargetAccountId(),
                account == null ? null : new PlatformActivityModels.AccountIdentity(
                        account.name(), account.slug(), account.active()),
                log.getTargetCompanyId(),
                log.getCollaborationId(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getOutcome(),
                log.getRequestMethod(),
                log.getRequestPath(),
                log.getRequestId(),
                log.getRequestData() != null || log.getResultData() != null
                        || log.getFailureType() != null);
    }

    private String displayName(UserView actor) {
        String name = ((actor.firstName() == null ? "" : actor.firstName()) + " "
                + (actor.lastName() == null ? "" : actor.lastName())).trim();
        return name.isBlank() ? actor.username() : name;
    }

    private AuditLog requireLog(UUID id) {
        return auditLogs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", "id", id));
    }

    private List<AuditLog> requireSelected(List<UUID> activityIds) {
        if (activityIds == null || activityIds.isEmpty() || activityIds.size() > 100
                || activityIds.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRequestException("Activity identity resolution supports 1 to 100 ids.");
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(activityIds);
        if (ids.size() != activityIds.size()) {
            throw new InvalidRequestException("Activity identity ids must not contain duplicates.");
        }
        Map<UUID, AuditLog> found = auditLogs.findAllById(ids).stream()
                .collect(Collectors.toMap(AuditLog::getId, Function.identity()));
        if (found.size() != ids.size()) {
            throw new ResourceNotFoundException("Activity", "ids", ids);
        }
        return ids.stream().map(found::get).toList();
    }

    private NormalizedQuery normalize(Query request) {
        Query source = request == null
                ? new Query(null, null, null, null, null, null, null, null, null)
                : request;
        Instant until = source.until() == null ? clock.instant() : source.until();
        Instant from = source.from() == null ? until.minus(DEFAULT_RANGE) : source.from();
        if (!from.isBefore(until)) {
            throw new InvalidRequestException("Activity range start must be before its end.");
        }
        if (Duration.between(from, until).compareTo(MAX_RANGE) > 0) {
            throw new InvalidRequestException("Activity ranges cannot exceed 366 days.");
        }
        return new NormalizedQuery(
                from,
                until,
                source.outcome(),
                source.actorSurface(),
                bounded(source.actionPrefix(), MAX_ACTION_LENGTH, "Action prefix"),
                upper(bounded(source.resourceType(), MAX_RESOURCE_TYPE_LENGTH, "Resource type")),
                bounded(source.resourceId(), MAX_RESOURCE_ID_LENGTH, "Resource id"),
                source.actorUserId(),
                source.targetAccountId());
    }

    private Specification<AuditLog> specification(NormalizedQuery query) {
        return (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
            predicates.add(cb.lessThan(root.get("occurredAt"), query.until()));
            if (query.outcome() != null) {
                predicates.add(cb.equal(root.get("outcome"), query.outcome()));
            }
            if (query.actorSurface() != null) {
                predicates.add(cb.equal(root.get("actorSurface"), query.actorSurface()));
            }
            if (query.actionPrefix() != null) {
                predicates.add(cb.like(cb.lower(root.get("action")),
                        escapeLike(query.actionPrefix().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (query.resourceType() != null) {
                predicates.add(cb.equal(root.get("resourceType"), query.resourceType()));
            }
            if (query.resourceId() != null) {
                predicates.add(cb.equal(root.get("resourceId"), query.resourceId()));
            }
            if (query.actorUserId() != null) {
                predicates.add(cb.equal(root.get("actorUserId"), query.actorUserId()));
            }
            if (query.targetAccountId() != null) {
                predicates.add(cb.equal(root.get("targetAccountId"), query.targetAccountId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String bounded(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new InvalidRequestException(label + " cannot exceed " + max + " characters.");
        }
        return normalized;
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private boolean has(String action) {
        return PermissionGuard.has(new Permission(ActivitiesFeature.CODE + "." + action));
    }

    private void require(String action) {
        if (!has(action)) {
            throw new ForbiddenException(
                    "This operation requires " + ActivitiesFeature.CODE + "." + action + ".");
        }
    }

    private record NormalizedQuery(
            Instant from,
            Instant until,
            com.hiveapp.shared.audit.domain.AuditOutcome outcome,
            com.hiveapp.shared.audit.domain.AuditActorSurface actorSurface,
            String actionPrefix,
            String resourceType,
            String resourceId,
            UUID actorUserId,
            UUID targetAccountId
    ) {}
}
