package com.hiveapp.platform.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PlatformObservabilityModels {
    private PlatformObservabilityModels() {}

    public enum ComponentState {
        UP,
        CONFIGURED,
        SUPPRESSED,
        DISABLED,
        DEGRADED,
        UNAVAILABLE
    }

    public record Component(
            String key,
            String label,
            ComponentState state,
            String guidance
    ) {}

    public record Health(Instant generatedAt, List<Component> components) {}

    public record Backlog(
            String key,
            String label,
            Map<String, Long> counts,
            Instant oldestAttentionAt,
            boolean attentionRequired,
            String destination
    ) {}

    public record Backlogs(Instant generatedAt, List<Backlog> components) {}

    public record LogAccess(
            boolean configured,
            String provider,
            String destination,
            String guidance
    ) {}
}
