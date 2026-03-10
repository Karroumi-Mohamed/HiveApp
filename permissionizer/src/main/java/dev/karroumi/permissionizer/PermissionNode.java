package dev.karroumi.permissionizer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a node in the hierarchical permission tree.
 *
 * <p>
 * Place on packages ({@code package-info.java}), classes, or methods.
 * The annotation processor resolves the full dot-path by walking the parent
 * chain
 * and generates a nested class tree with type-safe {@link Permission}
 * accessors.
 * </p>
 *
 * <h3>Key resolution:</h3>
 * <p>
 * On methods, {@link #key()} is optional. If empty, the method name is used.
 * On classes and packages, {@link #key()} is required — the processor emits a
 * compile error if missing.
 * </p>
 *
 * <h3>Parent resolution order:</h3>
 * <ol>
 * <li>Explicit {@link #parent()} if specified</li>
 * <li>Enclosing class's {@code @PermissionNode} (for methods)</li>
 * <li>Walk up packages until an annotated {@code package-info.java} is
 * found</li>
 * <li>Compile error if unresolvable</li>
 * </ol>
 *
 * <h3>Guard behavior:</h3>
 * <p>
 * {@link #guard()} controls automatic permission checking at runtime.
 * When set to {@link Guard#ON}, methods under this node are auto-checked
 * by the {@link PermissionResolver}. Children inherit the guard setting
 * unless they override it with {@link Guard#OFF}.
 * </p>
 *
 * <h3>Auto-discovery:</h3>
 * <p>
 * {@link #autoDiscover()} on a class causes the processor to scan all
 * declared public methods without their own {@code @PermissionNode} and
 * generate leaf permissions using the method name as the key.
 * </p>
 *
 * <h3>Examples:</h3>
 * 
 * <pre>
 * // Package — key required
 * {@literal @}PermissionNode(key = "platform", description = "HiveApp Platform", guard = Guard.ON)
 * package com.hiveapp.platform;
 *
 * // Class — key required, autoDiscover generates leaves from methods
 * {@literal @}PermissionNode(key = "operations", description = "Account Ops", autoDiscover = true)
 * public class AccountServiceImpl { ... }
 *
 * // Method — key optional, derived from method name
 * {@literal @}PermissionNode(description = "Create account")
 * public AccountDto createAccount() { ... }
 *
 * // Method — explicit key overrides method name
 * {@literal @}PermissionNode(key = "create", description = "Create account")
 * public AccountDto createAccount() { ... }
 *
 * // Method — opt out of auto-guarding
 * {@literal @}PermissionNode(key = "internal", guard = Guard.OFF)
 * public void helperMethod() { ... }
 * </pre>
 */
@Target({ ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionNode {

    /**
     * The key for this node in the permission tree.
     * Combined with parent keys via dot notation to form the full path.
     *
     * <p>
     * Required on packages and classes. Optional on methods —
     * if empty, the method name is used as the key.
     * </p>
     */
    String key()

    default "";

    /**
     * Human-readable description for admin UI representation
     * and database seeding.
     */
    String description()

    default "";

    /**
     * Explicit parent class override. The referenced class must carry
     * {@code @PermissionNode}. Defaults to Void meaning resolve structurally
     * via enclosing class or package hierarchy.
     */
    Class<?> parent()

    default Void.class;

    /**
     * Controls automatic permission checking at runtime.
     *
     * <p>
     * {@link Guard#ON} enables auto-checking for this node and all
     * children that don't override with {@link Guard#OFF}.
     * {@link Guard#OFF} disables auto-checking for this node.
     * {@link Guard#INHERIT} (default) inherits from the nearest ancestor
     * that specifies ON or OFF.
     * </p>
     */
    Guard guard()

    default Guard.INHERIT;

    /**
     * When true on a class, the processor scans all declared public
     * methods without their own {@code @PermissionNode} and generates
     * leaf permissions using the method name as the key.
     *
     * <p>
     * Only effective on classes. Ignored on packages and methods.
     * </p>
     *
     * <p>
     * Skips inherited methods (from Object, superclasses),
     * static methods, and non-public methods.
     * </p>
     */
    boolean autoDiscover() default false;

    /**
     * Controls whether permission checking is enforced automatically
     * at runtime by the {@link PermissionResolver} and interceptors.
     */
    enum Guard {
        /** Inherit guard setting from the nearest ancestor with ON or OFF. */
        INHERIT,
        /** Enable automatic permission checking for this node and descendants. */
        ON,
        /** Disable automatic permission checking for this node. */
        OFF
    }
}
