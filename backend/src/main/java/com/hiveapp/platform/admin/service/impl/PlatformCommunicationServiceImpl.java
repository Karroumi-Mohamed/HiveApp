package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.dto.PlatformCommunicationModels;
import com.hiveapp.platform.admin.service.PlatformCommunicationService;
import com.hiveapp.platform.registry.definition.CommunicationsFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.email.delivery.EmailDelivery;
import com.hiveapp.shared.email.delivery.EmailDeliveryRepository;
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
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PermissionNode(key = CommunicationsFeature.KEY, description = "Platform Communications",
        guard = PermissionNode.Guard.ON)
public class PlatformCommunicationServiceImpl extends PlatformControlFeatureService
        implements PlatformCommunicationService {
    private static final Duration DEFAULT_RANGE = Duration.ofDays(30);
    private static final Duration MAX_RANGE = Duration.ofDays(366);

    private final EmailDeliveryRepository deliveries;
    private final Clock clock;

    public PlatformCommunicationServiceImpl(EmailDeliveryRepository deliveries, Clock clock) {
        this.deliveries = deliveries;
        this.clock = clock;
    }

    @Override
    protected FeatureDefinition featureDefinition() {
        return CommunicationsFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read", description = "Read safe credential-email delivery metadata")
    public PlatformCommunicationModels.Summary summary() {
        EnumMap<com.hiveapp.shared.email.delivery.EmailDeliveryStatus, Long> byStatus =
                new EnumMap<>(com.hiveapp.shared.email.delivery.EmailDeliveryStatus.class);
        for (var status : com.hiveapp.shared.email.delivery.EmailDeliveryStatus.values()) {
            byStatus.put(status, 0L);
        }
        deliveries.countByStatus().forEach(row -> byStatus.put(row.getValue(), row.getTotal()));

        EnumMap<com.hiveapp.identity.domain.constant.CredentialTokenPurpose, Long> byPurpose =
                new EnumMap<>(com.hiveapp.identity.domain.constant.CredentialTokenPurpose.class);
        for (var purpose : com.hiveapp.identity.domain.constant.CredentialTokenPurpose.values()) {
            byPurpose.put(purpose, 0L);
        }
        deliveries.countByPurpose().forEach(row -> byPurpose.put(row.getValue(), row.getTotal()));
        return new PlatformCommunicationModels.Summary(
                byStatus.values().stream().mapToLong(Long::longValue).sum(),
                Map.copyOf(byStatus), Map.copyOf(byPurpose));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "internal_list", guard = PermissionNode.Guard.OFF)
    public Page<PlatformCommunicationModels.Delivery> search(Query request, Pageable pageable) {
        require("read");
        NormalizedQuery query = normalize(request);
        boolean identityVisible = has("read_recipient_identity");
        boolean failureVisible = has("read_failure_evidence");
        return deliveries.findAll(specification(query), pageable)
                .map(delivery -> toDto(delivery, identityVisible, failureVisible));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "internal_detail", guard = PermissionNode.Guard.OFF)
    public PlatformCommunicationModels.Delivery detail(UUID id) {
        require("read");
        return toDto(
                requireDelivery(id),
                has("read_recipient_identity"),
                has("read_failure_evidence"));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_recipient_identity",
            description = "Read credential-email recipient identity")
    public PlatformCommunicationModels.RecipientIdentity recipientIdentity(UUID id) {
        require("read");
        EmailDelivery delivery = requireDelivery(id);
        return new PlatformCommunicationModels.RecipientIdentity(
                delivery.getId(), delivery.getRecipientUserId(), delivery.getRecipientEmail());
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_failure_evidence",
            description = "Read bounded credential-email delivery failure evidence")
    public PlatformCommunicationModels.FailureEvidence failureEvidence(UUID id) {
        require("read");
        EmailDelivery delivery = requireDelivery(id);
        return new PlatformCommunicationModels.FailureEvidence(
                delivery.getId(), delivery.getAttemptedAt(), delivery.getDeliveredAt(),
                delivery.getFailureCode());
    }

    private PlatformCommunicationModels.Delivery toDto(
            EmailDelivery delivery,
            boolean identity,
            boolean failure
    ) {
        return new PlatformCommunicationModels.Delivery(
                delivery.getId(),
                delivery.getAccountId(),
                identity ? delivery.getRecipientUserId() : null,
                identity ? delivery.getRecipientEmail() : null,
                delivery.getPurpose(),
                delivery.getStatus(),
                delivery.getCreatedAt(),
                failure ? delivery.getAttemptedAt() : null,
                failure ? delivery.getDeliveredAt() : null,
                failure ? delivery.getFailureCode() : null,
                identity,
                failure);
    }

    private EmailDelivery requireDelivery(UUID id) {
        return deliveries.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Email delivery", "id", id));
    }

    private NormalizedQuery normalize(Query request) {
        Query source = request == null ? new Query(null, null, null, null, null, null) : request;
        if (source.recipientUserId() != null) {
            require("read_recipient_identity");
        }
        Instant until = source.until() == null ? clock.instant() : source.until();
        Instant from = source.from() == null ? until.minus(DEFAULT_RANGE) : source.from();
        if (!from.isBefore(until)) {
            throw new InvalidRequestException("Communication range start must be before its end.");
        }
        if (Duration.between(from, until).compareTo(MAX_RANGE) > 0) {
            throw new InvalidRequestException("Communication ranges cannot exceed 366 days.");
        }
        return new NormalizedQuery(from, until, source.status(), source.purpose(),
                source.accountId(), source.recipientUserId());
    }

    private Specification<EmailDelivery> specification(NormalizedQuery query) {
        return (root, ignored, cb) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.from()));
            predicates.add(cb.lessThan(root.get("createdAt"), query.until()));
            if (query.status() != null) predicates.add(cb.equal(root.get("status"), query.status()));
            if (query.purpose() != null) predicates.add(cb.equal(root.get("purpose"), query.purpose()));
            if (query.accountId() != null) predicates.add(cb.equal(root.get("accountId"), query.accountId()));
            if (query.recipientUserId() != null) {
                predicates.add(cb.equal(root.get("recipientUserId"), query.recipientUserId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private boolean has(String action) {
        return PermissionGuard.has(new Permission(CommunicationsFeature.CODE + "." + action));
    }

    private void require(String action) {
        if (!has(action)) {
            throw new ForbiddenException(
                    "This operation requires " + CommunicationsFeature.CODE + "." + action + ".");
        }
    }

    private record NormalizedQuery(
            Instant from,
            Instant until,
            com.hiveapp.shared.email.delivery.EmailDeliveryStatus status,
            com.hiveapp.identity.domain.constant.CredentialTokenPurpose purpose,
            UUID accountId,
            UUID recipientUserId
    ) {}
}
