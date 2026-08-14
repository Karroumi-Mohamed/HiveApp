package com.hiveapp.platform.admin.service;

/**
 * The write/read contract for role-assignment audit entries. The assignment writer stores the
 * subject operator under these keys and the role-history reader parses them back out of the
 * stored payload; a drifted key would not fail anywhere visible — the subject would simply
 * vanish from the history page.
 */
public final class AdminRoleAssignmentAudit {

    public static final String ASSIGN_ACTION = "platform.roles.assign_operator";
    public static final String REMOVE_ACTION = "platform.roles.remove_operator";
    /** Wrapper node the audit trail places around every success payload. */
    public static final String AFTER_NODE = "after";
    public static final String ADMIN_USER_ID_KEY = "adminUserId";
    public static final String ASSIGNED_KEY = "assigned";

    private AdminRoleAssignmentAudit() {}
}
