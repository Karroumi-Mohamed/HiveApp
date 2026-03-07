package dev.karroumi.permissionizer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a node in the hierarchical permission tree.
 *
 * <p>Place on packages ({@code package-info.java}), classes, or methods.
 * The annotation processor resolves the full dot-path by walking the parent chain
 * and generates a nested class tree with {@code $} constants.</p>
 *
 * <p>Parent resolution order:</p>
 * <ol>
 *   <li>Explicit {@link #parent()} if specified</li>
 *   <li>Enclosing class's {@code @PermissionNode} (for methods)</li>
 *   <li>Walk up packages until an annotated {@code package-info.java} is found</li>
 *   <li>Compile error if unresolvable</li>
 * </ol>
 *
 * <h3>Guard behavior:</h3>
 * <p>{@link #guard()} controls automatic permission checking at runtime.
 * When set to {@link Guard#ON}, the {@link PermissionResolver} will indicate
 * that this node and its children should be auto-checked. Children inherit
 * the guard setting unless they override it.</p>
 *
 * <h3>Auto-discovery:</h3>
 * <p>{@link #autoDiscover()} is only meaningful on classes. When {@code true},
 * the processor scans all declared public methods that don't have their own
 * {@code @PermissionNode} and generates leaf permissions using the method name
 * as the key.</p>
 */
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionNode {

    /**
     * The key for this node. Combined with parent keys via dot notation.
     * Example: "payroll" under parent "hr" produces "hr.payroll".
     */
    String key();

    /**
     * Human-readable description for admin UI representation.
     */
    String description() default "";

    /**
     * Explicit parent class override. The referenced class must carry
     * {@code @PermissionNode}. Defaults to Void (resolve structurally).
     */
    Class<?> parent() default Void.class;

    /**
     * Controls automatic permission checking at runtime.
     *
     * <p>{@link Guard#ON} enables auto-checking for this node and its children.
     * {@link Guard#OFF} disables it for this node.
     * {@link Guard#INHERIT} (default) inherits from the nearest parent
     * that specifies ON or OFF.</p>
     */
    Guard guard() default Guard.INHERIT;

    /**
     * When true on a class, the processor auto-discovers all declared public
     * methods without their own {@code @PermissionNode} and generates leaf
     * permissions using the method name as the key.
     *
     * <p>Only effective on classes. Ignored on packages and methods.</p>
     *
     * <p>Methods annotated with {@code @PermissionNode} are not affected —
     * they keep their explicit key.</p>
     *
     * <p>Inherited methods (from Object, interfaces, superclasses) are skipped.
     * Only methods declared directly in the annotated class are discovered.</p>
     */
    boolean autoDiscover() default false;

    /**
     * Controls whether permission checking is automatic at runtime.
     */
    enum Guard {
        /** Inherit guard setting from the nearest parent that specifies ON or OFF. */
        INHERIT,
        /** Enable automatic permission checking for this node and children. */
        ON,
        /** Disable automatic permission checking for this node. */
        OFF
    }
}