package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.member.service.MemberAccessSessionService;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeOrigin;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionLifecycleEvent;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionLifecycleEventRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionLifecycleModels;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.audit.AuditedMutation;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleAdminService {
    private static final List<SubscriptionStatus> CURRENT = List.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.TRIALING,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.SUSPENDED);
    private static final List<SubscriptionChangeStatus> OUTSTANDING = List.of(
            SubscriptionChangeStatus.PENDING,
            SubscriptionChangeStatus.AWAITING_CONFIRMATION,
            SubscriptionChangeStatus.NEEDS_ATTENTION);

    private final SubscriptionRepository subscriptions;
    private final SubscriptionLifecycleEventRepository events;
    private final SubscriptionChangeOperationRepository operations;
    private final SubscriptionCheckoutService checkouts;
    private final SubscriptionLifecycleManager lifecycle;
    private final MemberAccessSessionService memberSessions;
    private final CommercialPreviewTokenService evidence;
    private final CommercialCatalogVersionService catalogVersions;
    private final RegistryCatalogVersionService registryVersions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SubscriptionLifecycleModels.Actions actions(UUID accountId) {
        Subscription subscription = current(accountId);
        return new SubscriptionLifecycleModels.Actions(
                subscription.getId(),
                subscription.getVersion(),
                subscription.getStatus(),
                subscription.getSuspensionCause(),
                SubscriptionLifecycleRules.availableActions(subscription, clock.instant()));
    }

    @Transactional(readOnly = true)
    public SubscriptionLifecycleModels.Preview preview(
            UUID accountId,
            UUID actorUserId,
            SubscriptionLifecycleModels.PreviewRequest request) {
        Subscription subscription = current(accountId);
        Instant now = clock.instant();
        List<String> blockers = blockers(subscription, request.action(), request.graceEndsAt(), now);
        return issuePreview(subscription, actorUserId, request.action(), request.graceEndsAt(), blockers, now);
    }

    @Transactional
    @AuditedMutation(action = "platform.subscription.lifecycle.cancel_at_period_end", resourceType = "SUBSCRIPTION")
    public SubscriptionLifecycleModels.Mutation cancelAtPeriodEnd(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return apply(accountId, actorUserId, SubscriptionLifecycleAction.CANCEL_AT_PERIOD_END, request);
    }

    @Transactional
    @AuditedMutation(action = "platform.subscription.lifecycle.keep_renewing", resourceType = "SUBSCRIPTION")
    public SubscriptionLifecycleModels.Mutation keepRenewing(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return apply(accountId, actorUserId, SubscriptionLifecycleAction.KEEP_RENEWING, request);
    }

    @Transactional
    @AuditedMutation(action = "platform.subscription.lifecycle.cancel_immediately", resourceType = "SUBSCRIPTION")
    public SubscriptionLifecycleModels.Mutation cancelImmediately(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return apply(accountId, actorUserId, SubscriptionLifecycleAction.CANCEL_IMMEDIATELY, request);
    }

    @Transactional
    @AuditedMutation(action = "platform.subscription.lifecycle.suspend", resourceType = "SUBSCRIPTION")
    public SubscriptionLifecycleModels.Mutation suspend(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return apply(accountId, actorUserId, SubscriptionLifecycleAction.SUSPEND, request);
    }

    @Transactional
    @AuditedMutation(action = "platform.subscription.lifecycle.restore", resourceType = "SUBSCRIPTION")
    public SubscriptionLifecycleModels.Mutation restore(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return apply(accountId, actorUserId, SubscriptionLifecycleAction.RESTORE, request);
    }

    @Transactional
    @AuditedMutation(action = "platform.subscription.lifecycle.extend_grace", resourceType = "SUBSCRIPTION")
    public SubscriptionLifecycleModels.Mutation extendGrace(
            UUID accountId, UUID actorUserId, SubscriptionLifecycleModels.ApplyRequest request) {
        return apply(accountId, actorUserId, SubscriptionLifecycleAction.EXTEND_GRACE, request);
    }

    private SubscriptionLifecycleModels.Mutation apply(
            UUID accountId,
            UUID actorUserId,
            SubscriptionLifecycleAction action,
            SubscriptionLifecycleModels.ApplyRequest request) {
        Subscription snapshot = current(accountId);
        Subscription subscription = subscriptions.findCurrentForLifecycleUpdate(
                        snapshot.getId(), accountId, CURRENT)
                .orElseThrow(() -> new StaleResourceVersionException(
                        "Subscription lifecycle review is stale. Review the command again."));
        Instant now = clock.instant();
        List<String> blockers = blockers(subscription, action, request.graceEndsAt(), now);
        if (!blockers.isEmpty()) {
            throw new InvalidStateException(String.join(" ", blockers));
        }
        long catalogRevision = catalogVersions.currentRevision();
        String registryVersion = registryVersions.currentVersion();
        evidence.requireValid(
                request.previewToken(),
                CommercialPreviewKind.SUBSCRIPTION_LIFECYCLE,
                subscription.getId(),
                subscription.getVersion(),
                actorUserId,
                catalogRevision,
                registryVersion,
                fingerprint(subscription, action, request.graceEndsAt(), blockers),
                () -> new StaleResourceVersionException(
                        "Subscription lifecycle review is stale. Review the command again."));

        SubscriptionStatus before = subscription.getStatus();
        Instant previousGrace = subscription.getGraceEndsAt();
        Instant effectiveAt = effectiveAt(subscription, action, now);
        mutate(subscription, action, request.graceEndsAt(), request.reason().trim(), actorUserId, now);
        subscriptions.saveAndFlush(subscription);

        SubscriptionLifecycleEvent event = new SubscriptionLifecycleEvent();
        event.setAccount(subscription.getAccount());
        event.setSubscription(subscription);
        event.setAction(action);
        event.setBeforeStatus(before);
        event.setAfterStatus(subscription.getStatus());
        event.setEffectiveAt(effectiveAt);
        event.setPreviousGraceEndsAt(previousGrace);
        event.setNextGraceEndsAt(subscription.getGraceEndsAt());
        event.setActorUserId(actorUserId);
        event.setReason(request.reason().trim());
        event = events.saveAndFlush(event);
        return new SubscriptionLifecycleModels.Mutation(
                event.getId(), subscription.getId(), subscription.getVersion(), action,
                subscription.getStatus(), subscription.isCancelAtPeriodEnd(),
                subscription.getGraceEndsAt(), subscription.getSuspendedAt());
    }

    private void mutate(
            Subscription subscription,
            SubscriptionLifecycleAction action,
            Instant requestedGrace,
            String reason,
            UUID actorUserId,
            Instant now) {
        switch (action) {
            case CANCEL_AT_PERIOD_END -> subscription.setCancelAtPeriodEnd(true);
            case KEEP_RENEWING -> subscription.setCancelAtPeriodEnd(false);
            case CANCEL_IMMEDIATELY -> {
                cancelOutstanding(subscription.getAccount().getId(), actorUserId, reason, now);
                lifecycle.cancelImmediately(subscription, now);
                memberSessions.revokeAllForAccount(subscription.getAccount().getId());
            }
            case SUSPEND -> {
                SubscriptionStatus before = subscription.getStatus();
                subscription.setStatus(SubscriptionStatus.SUSPENDED);
                subscription.setSuspendedAt(now);
                subscription.setSuspensionCause(SubscriptionSuspensionCause.OPERATOR);
                subscription.setSuspendedFromStatus(before);
                subscription.setSuspensionReason(reason);
                memberSessions.revokeAllForAccount(subscription.getAccount().getId());
            }
            case RESTORE -> {
                subscription.setStatus(subscription.getSuspendedFromStatus());
                subscription.setSuspendedAt(null);
                subscription.setSuspensionCause(null);
                subscription.setSuspendedFromStatus(null);
                subscription.setSuspensionReason(null);
            }
            case EXTEND_GRACE -> {
                subscription.setStatus(SubscriptionStatus.PAST_DUE);
                subscription.setGraceEndsAt(requestedGrace);
                subscription.setSuspendedAt(null);
                subscription.setSuspensionCause(null);
                subscription.setSuspendedFromStatus(null);
                subscription.setSuspensionReason(null);
            }
        }
    }

    private void cancelOutstanding(UUID accountId, UUID actorUserId, String reason, Instant now) {
        operations.findOutstandingForLifecycleUpdate(accountId, OUTSTANDING).forEach(operation -> {
            checkouts.cancelFor(operation);
            operation.setStatus(SubscriptionChangeStatus.CANCELLED);
            operation.setCancellationOrigin(SubscriptionChangeOrigin.PLATFORM_ADMIN);
            operation.setCancelledByUserId(actorUserId);
            operation.setCancellationReason(reason);
            operation.setCancelledAt(now);
            operations.save(operation);
        });
    }

    private List<String> blockers(
            Subscription subscription,
            SubscriptionLifecycleAction action,
            Instant graceEndsAt,
            Instant now) {
        List<String> blockers = SubscriptionLifecycleRules.blockers(
                subscription, action, graceEndsAt, now);
        if (blockers.isEmpty()
                && action == SubscriptionLifecycleAction.SUSPEND
                && operations.existsByAccountIdAndStatusIn(subscription.getAccount().getId(), OUTSTANDING)) {
            return List.of("Cancel the pending subscription change before suspending access.");
        }
        return blockers;
    }

    private SubscriptionLifecycleModels.Preview issuePreview(
            Subscription subscription,
            UUID actorUserId,
            SubscriptionLifecycleAction action,
            Instant graceEndsAt,
            List<String> blockers,
            Instant now) {
        long catalogRevision = catalogVersions.currentRevision();
        String registryVersion = registryVersions.currentVersion();
        var token = evidence.issue(
                CommercialPreviewKind.SUBSCRIPTION_LIFECYCLE,
                subscription.getId(),
                subscription.getVersion(),
                actorUserId,
                catalogRevision,
                registryVersion,
                fingerprint(subscription, action, graceEndsAt, blockers),
                now);
        return new SubscriptionLifecycleModels.Preview(
                subscription.getId(), subscription.getVersion(), action,
                subscription.getStatus(), resultingStatus(subscription, action),
                effectiveAt(subscription, action, now), subscription.getGraceEndsAt(), graceEndsAt,
                blockers, token.evaluatedAt(), token.expiresAt(), token.token());
    }

    private Subscription current(UUID accountId) {
        return subscriptions.findCurrentByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));
    }

    private SubscriptionStatus resultingStatus(
            Subscription subscription, SubscriptionLifecycleAction action) {
        return switch (action) {
            case CANCEL_IMMEDIATELY -> SubscriptionStatus.CANCELLED;
            case SUSPEND -> SubscriptionStatus.SUSPENDED;
            case RESTORE -> subscription.getSuspendedFromStatus();
            case EXTEND_GRACE -> SubscriptionStatus.PAST_DUE;
            case CANCEL_AT_PERIOD_END, KEEP_RENEWING -> subscription.getStatus();
        };
    }

    private Instant effectiveAt(
            Subscription subscription, SubscriptionLifecycleAction action, Instant now) {
        return action == SubscriptionLifecycleAction.CANCEL_AT_PERIOD_END
                ? subscription.getCurrentPeriodEnd() : now;
    }

    private String fingerprint(
            Subscription subscription,
            SubscriptionLifecycleAction action,
            Instant graceEndsAt,
            List<String> blockers) {
        String material = String.join("|",
                subscription.getId().toString(),
                Long.toString(subscription.getVersion()),
                subscription.getStatus().name(),
                Boolean.toString(subscription.isCancelAtPeriodEnd()),
                action.name(),
                graceEndsAt == null ? "" : graceEndsAt.toString(),
                String.join(";", blockers));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

}
