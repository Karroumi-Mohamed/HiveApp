package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SubscriptionLifecycleModels {
    private SubscriptionLifecycleModels() {}

    public record PreviewRequest(
            @NotNull SubscriptionLifecycleAction action,
            Instant graceEndsAt) {}

    public record Actions(
            UUID subscriptionId,
            long subscriptionVersion,
            SubscriptionStatus status,
            SubscriptionSuspensionCause suspensionCause,
            Set<SubscriptionLifecycleAction> availableActions) {
        public Actions {
            availableActions = availableActions == null ? Set.of() : Set.copyOf(availableActions);
        }
    }

    public record ApplyRequest(
            @NotBlank @Size(max = 4096) String previewToken,
            @NotBlank @Size(max = 2000) String reason,
            Instant graceEndsAt) {}

    public record Preview(
            UUID subscriptionId,
            long expectedVersion,
            SubscriptionLifecycleAction action,
            SubscriptionStatus beforeStatus,
            SubscriptionStatus afterStatus,
            Instant effectiveAt,
            Instant previousGraceEndsAt,
            Instant nextGraceEndsAt,
            List<String> blockers,
            Instant evaluatedAt,
            Instant expiresAt,
            String previewToken) {
        public Preview {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }

    public record Mutation(
            UUID eventId,
            UUID subscriptionId,
            long subscriptionVersion,
            SubscriptionLifecycleAction action,
            SubscriptionStatus status,
            boolean cancelAtPeriodEnd,
            Instant graceEndsAt,
            Instant suspendedAt) {}

    public record Event(
            UUID id,
            UUID subscriptionId,
            SubscriptionLifecycleAction action,
            SubscriptionStatus beforeStatus,
            SubscriptionStatus afterStatus,
            Instant effectiveAt,
            Instant previousGraceEndsAt,
            Instant nextGraceEndsAt,
            UUID actorUserId,
            String actorEmail,
            String reason,
            Instant createdAt) {}
}
