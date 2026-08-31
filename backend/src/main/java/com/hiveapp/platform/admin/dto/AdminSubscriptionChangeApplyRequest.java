package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Operator-authored application of an exact, recently reviewed subscription change. */
public record AdminSubscriptionChangeApplyRequest(
        @NotNull @Valid SubscriptionChangeRequest selection,
        @NotBlank @Size(max = 2048) String previewToken,
        @NotBlank @Size(max = 2000) String reason
) {
    public SubscriptionChangeApplyRequest reviewedSelection() {
        return new SubscriptionChangeApplyRequest(selection, previewToken);
    }
}
