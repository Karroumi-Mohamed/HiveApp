package com.hiveapp.platform.client.plan.service;

import com.hiveapp.shared.exception.ForbiddenException;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionGuard;
import org.springframework.stereotype.Component;

/** Enforces the complete Permissionizer policy chain for composed commercial commands. */
@Component
public class CrossFeatureCommercialAuthorizer {

    public void require(String permissionCode, String operation) {
        if (!PermissionGuard.has(new Permission(permissionCode))) {
            throw new ForbiddenException(operation + " requires " + permissionCode + ".");
        }
    }
}
