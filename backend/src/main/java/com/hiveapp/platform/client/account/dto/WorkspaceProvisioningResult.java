package com.hiveapp.platform.client.account.dto;

import java.util.UUID;

public record WorkspaceProvisioningResult(
        UUID accountId,
        String slug,
        boolean created
) {
}
