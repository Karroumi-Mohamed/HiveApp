package com.hiveapp.shared.security.context;

import java.util.UUID;

public record HiveAppPermissionContext(
    UUID actorUserId,
    UUID currentAccountId,
    UUID targetCompanyId,
    boolean isB2B
) {}
