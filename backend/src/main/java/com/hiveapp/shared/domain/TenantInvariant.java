package com.hiveapp.shared.domain;

public final class TenantInvariant {

    private TenantInvariant() {
    }

    public static void requireSameEntity(BaseEntity left, BaseEntity right, String message) {
        if (!sameEntity(left, right)) {
            throw new IllegalStateException(message);
        }
    }

    public static void requireDifferentEntities(BaseEntity left, BaseEntity right, String message) {
        if (sameEntity(left, right)) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean sameEntity(BaseEntity left, BaseEntity right) {
        if (left == null || right == null) {
            return false;
        }
        if (left == right) {
            return true;
        }
        return left.getId() != null && left.getId().equals(right.getId());
    }
}
