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
}