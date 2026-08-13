package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** A selection of operators. Bounded so one request cannot start an unbounded amount of work. */
public record BulkOperatorIdsRequest(
        @NotEmpty @Size(max = 100) List<UUID> ids
) {}
