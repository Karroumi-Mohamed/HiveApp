package com.hiveapp.platform.admin.service;

public class StaleAdminRoleImpactException extends RuntimeException {
    public StaleAdminRoleImpactException() {
        super("The role or its assignments changed after the impact preview. Refresh and try again.");
    }
}
