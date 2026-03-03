package com.hiveapp.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a permission node in the hierarchical permission tree.
 *
 * <p>Can be placed on packages ({@code package-info.java}), classes, or methods.
 * The annotation processor resolves the full dot-path by walking the parent chain
 * and generates companion classes with {@code public static final String} constants.</p>
 *
 * <h3>Parent Resolution Order:</h3>
 * <ol>
 *   <li>Explicit {@link #parent()} if specified</li>
 *   <li>Enclosing class's {@code @Permission} (for method-level annotations)</li>
 *   <li>Walk up package hierarchy until a {@code @Permission} on {@code package-info.java} is found</li>
 *   <li>Compile error if no parent is resolvable (except for root nodes)</li>
 * </ol>
 *
 * <h3>Examples:</h3>
 * <pre>
 * // Root — package-info.java
 * {@literal @}Permission(key = "erp", description = "ERP Platform")
 * package com.erp;
 *
 * // Branch — on a service class
 * {@literal @}Permission(key = "payroll", description = "Payroll Operations")
 * {@literal @}Service
 * public class PayrollService {
 *
 *     // Leaf — on a method, parent is the enclosing class
 *     {@literal @}Permission(key = "export", description = "Export to CSV")
 *     public void exportPayroll() {}
 *
 *     // Leaf — explicit parent override
 *     {@literal @}Permission(key = "audit", description = "Audit", parent = HRModule.class)
 *     public void audit() {}
 * }
 * </pre>
 */
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {

    /**
     * The key for this node in the permission tree.
     * Combined with parent keys using dot notation to form the full path.
     * Must be non-empty. Should use lowercase, hyphen-separated identifiers.
     *
     * <p>Example: {@code "payroll"} under parent {@code "hr"} produces {@code "hr.payroll"}</p>
     */
    String key();

    /**
     * Human-readable description for admin UI representation.
     * Optional. Defaults to empty string.
     */
    String description() default "";

    /**
     * Explicit parent class reference. Overrides structural hierarchy resolution.
     * The referenced class must itself carry a {@code @Permission} annotation.
     * Defaults to {@code Void.class} meaning "not specified — resolve structurally".
     */
    Class<?> parent() default Void.class;
}