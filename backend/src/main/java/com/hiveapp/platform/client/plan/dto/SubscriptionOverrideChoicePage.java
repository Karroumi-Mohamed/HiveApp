package com.hiveapp.platform.client.plan.dto;

import java.util.List;

/**
 * Honest bounded chooser slice. The database candidate page may contain rows rejected by exact
 * compatibility checks, so this contract deliberately exposes {@code hasMoreCandidates} instead
 * of claiming a filtered total it cannot derive without scanning the catalogue.
 */
public record SubscriptionOverrideChoicePage<T>(
        List<T> content,
        List<T> retainedSelections,
        int page,
        int size,
        boolean hasMoreCandidates
) {
    public SubscriptionOverrideChoicePage {
        content = List.copyOf(content == null ? List.of() : content);
        retainedSelections = List.copyOf(retainedSelections == null ? List.of() : retainedSelections);
    }
}
