package com.hiveapp.shared.security.context;

import java.util.UUID;

public record HiveAppPermissionContext(
    UUID actorUserId,
    UUID clientAccountId,
    UUID currentAccountId,
    UUID targetCompanyId,
    UUID collaborationId,
    boolean isB2B
) {}
