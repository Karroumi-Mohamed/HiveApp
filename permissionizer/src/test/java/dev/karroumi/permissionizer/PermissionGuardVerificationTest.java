package dev.karroumi.permissionizer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermissionGuardVerificationTest {

    @BeforeEach
    void setUp() {
        PermissionGuard.reset();
    }

    @AfterEach
    void tearDown() {
        PermissionGuard.reset();
    }

    @Test
    void guardedDefinitionsFailStartupWithoutAnInterceptionMechanism() {
        assertThrows(IllegalStateException.class,
                () -> PermissionGuard.builder().initialize());
    }

    @Test
    void registeredSpringInterceptorSatisfiesStartupAlignment() {
        PermissionGuard.registerSpringInterceptor();

        assertDoesNotThrow(() -> PermissionGuard.builder().initialize());
    }
}
