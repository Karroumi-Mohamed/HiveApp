package com.hiveapp.shared.security.context;

import java.util.UUID;
import lombok.Value;

@Value
public class HiveAppPermissionContext {
    UUID actorUserId;
    UUID currentAccountId;
    UUID targetCompanyId;
    boolean isB2B;
}
