package com.hiveapp.platform.admin.service;

public class AdminRoleNameConflictException extends RuntimeException {
    public AdminRoleNameConflictException(String name) {
        super("An admin role named '" + name + "' already exists.");
    }
}
