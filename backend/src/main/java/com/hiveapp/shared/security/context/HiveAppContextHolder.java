package com.hiveapp.shared.security.context;

public class HiveAppContextHolder {
    private static final ThreadLocal<HiveAppPermissionContext> contextHolder = new ThreadLocal<>();

    public static void setContext(HiveAppPermissionContext context) {
        contextHolder.set(context);
    }

    public static HiveAppPermissionContext getContext() {
        return contextHolder.get();
    }

    public static void clearContext() {
        contextHolder.remove();
    }
}
