package com.hiveapp.permission.engine;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Defines the context in which permissions are being resolved.
 */
@Getter
@Builder
public class PermissionContext {

    private final ContextType contextType;
    private final UUID accountId;
    private final UUID companyId;
    private final UUID collaborationId;

    public boolean isAdminContext() {
        return contextType == ContextType.ADMIN_PLATFORM;
    }

    public boolean isOwnAccountContext() {
        return contextType == ContextType.CLIENT_OWN_ACCOUNT;
    }

    public boolean isCollaborationContext() {
        return contextType == ContextType.CLIENT_COLLABORATION;
    }

    public boolean hasCompanyScope() {
        return companyId != null;
    }

    public static PermissionContext forOwnAccount(UUID accountId) {
        return PermissionContext.builder()
                .contextType(ContextType.CLIENT_OWN_ACCOUNT)
                .accountId(accountId)
                .build();
    }

    public static PermissionContext forOwnAccountCompany(UUID accountId, UUID companyId) {
        return PermissionContext.builder()
                .contextType(ContextType.CLIENT_OWN_ACCOUNT)
                .accountId(accountId)
                .companyId(companyId)
                .build();
    }

    public static PermissionContext forCollaboration(UUID accountId, UUID companyId, UUID collaborationId) {
        return PermissionContext.builder()
                .contextType(ContextType.CLIENT_COLLABORATION)
                .accountId(accountId)
                .companyId(companyId)
                .collaborationId(collaborationId)
                .build();
    }

    public static PermissionContext forAdmin() {
        return PermissionContext.builder()
                .contextType(ContextType.ADMIN_PLATFORM)
                .build();
    }
}
