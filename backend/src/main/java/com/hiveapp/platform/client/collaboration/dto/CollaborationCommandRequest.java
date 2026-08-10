package com.hiveapp.platform.client.collaboration.dto;

import com.hiveapp.platform.client.collaboration.domain.constant.SuspensionScheduleAction;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CollaborationCommandRequest(
        @PositiveOrZero long expectedVersion,
        @Size(max = 1000) String reason,
        Instant suspensionScheduledAt,
        SuspensionScheduleAction suspensionScheduleAction
) {}
