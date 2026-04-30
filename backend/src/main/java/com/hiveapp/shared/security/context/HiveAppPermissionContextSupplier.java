package com.hiveapp.shared.security.context;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class HiveAppPermissionContextSupplier implements Supplier<HiveAppPermissionContext> {

    @Override
    public HiveAppPermissionContext get() {
        return HiveAppContextHolder.getContext();
    }
}
