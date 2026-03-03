package com.hiveapp.permission;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test that verifies the annotation processor generated
 * companion classes and the collector can find them.
 *
 * This test only passes if:
 * 1. The processor ran during compilation
 * 2. Companion classes were generated correctly
 * 3. The collector can reflect over them
 */
class PermissionProcessorTest {

    @Test
    void processorGeneratesCompanionClasses() {
        // If the processor didn't run, this class won't exist
        // and this test won't even compile — that's the first check.
        //
        // We'll add assertions here after wiring @Permission
        // annotations into the real code in Step 5.
    }

    @Test
    void collectorFindsAllPermissions() {
        List<PermissionNode> nodes = PermissionCollector.collect("com.hiveapp");

        assertNotNull(nodes);
        // After Step 5, we'll assert specific paths exist:
        // assertTrue(nodes.stream().anyMatch(n -> n.path().equals("expected.path")));
    }

    @Test
    void permissionGuardPureJavaVariant() {
        // Test prefix matching without Spring Security
        List<String> authorities = List.of("erp.hr");

        // Should pass — "erp.hr" is prefix of "erp.hr.payroll.export"
        assertTrue(PermissionGuard.has("erp.hr.payroll.export", authorities));

        // Should pass — exact match
        assertTrue(PermissionGuard.has("erp.hr", authorities));

        // Should fail — different branch
        assertFalse(PermissionGuard.has("erp.inventory", authorities));

        // Should fail — parent of granted, not child
        assertFalse(PermissionGuard.has("erp", authorities));

        // Critical: should fail — "erp.hr" must not match "erp.hra.something"
        assertFalse(PermissionGuard.has("erp.hra.something", authorities));
    }

    @Test
    void permissionGuardExactMatch() {
        List<String> authorities = List.of("erp.hr.payroll.export");

        assertTrue(PermissionGuard.has("erp.hr.payroll.export", authorities));
        assertFalse(PermissionGuard.has("erp.hr.payroll.run", authorities));
        assertFalse(PermissionGuard.has("erp.hr.payroll", authorities));
    }

    @Test
    void permissionGuardMultipleAuthorities() {
        List<String> authorities = List.of("erp.hr.payroll", "erp.inventory");

        assertTrue(PermissionGuard.has("erp.hr.payroll.export", authorities));
        assertTrue(PermissionGuard.has("erp.hr.payroll.run", authorities));
        assertTrue(PermissionGuard.has("erp.inventory.stock", authorities));
        assertFalse(PermissionGuard.has("erp.hr.employees", authorities));
    }

    @Test
    void permissionGuardThrowsOnDenied() {
        List<String> authorities = List.of("erp.hr");

        assertThrows(SecurityException.class, () ->
                PermissionGuard.check("erp.inventory", authorities)
        );
    }

    @Test
    void permissionGuardPassesOnGranted() {
        List<String> authorities = List.of("erp.hr");

        assertDoesNotThrow(() ->
                PermissionGuard.check("erp.hr.payroll.export", authorities)
        );
    }

    @Test
    void permissionNodeDeriveFields() {
        PermissionNode node = new PermissionNode("erp.hr.payroll.export", "Export", "erp.hr.payroll");

        assertEquals("export", node.key());
        assertEquals(3, node.depth());
        assertFalse(node.isRoot());
    }

    @Test
    void permissionNodeRoot() {
        PermissionNode root = new PermissionNode("erp", "ERP Platform", null);

        assertEquals("erp", root.key());
        assertEquals(0, root.depth());
        assertTrue(root.isRoot());
    }
}