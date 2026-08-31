package com.hiveapp.platform.client.plan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Creates the global row before registry synchronization and all commercial seeders run. */
@Component
@RequiredArgsConstructor
public class CommercialCatalogRevisionStartupInitializer {

    private final CommercialCatalogRevisionInitializer initializer;

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void initialize() {
        try {
            initializer.ensureExists();
        } catch (DataIntegrityViolationException ignoredConcurrentFirstInsert) {
            // Another application node committed the unique singleton first.
        }
    }
}
