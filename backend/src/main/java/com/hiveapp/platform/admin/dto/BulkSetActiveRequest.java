package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Explicit end state rather than a toggle: a mixed selection has no coherent toggle, and the
 * caller always knows which state it wants. Boxed so an absent field fails validation instead of
 * silently defaulting to false and deactivating the selection.
 */
public record BulkSetActiveRequest(
        @NotEmpty @Size(max = 100) List<UUID> ids,
        @NotNull Boolean active
) {}
