# Permission System — Specification

## 1. Philosophy and Architecture

**Status: CONFIRMED**

The Permission System is a pure, structural authorization library designed for compile-time safety and automatic synchronization between the codebase and the database. It follows a strict separation of concerns, focusing entirely on structural identity and boolean access control.

The system deliberately avoids any knowledge of business rules, billing, subscription plans, quotas, contextual limits, database access, or ORM frameworks. By defining hierarchical paths, it generates safe string constants (dot-paths) that the application uses for runtime authorization checks. Any business logic regarding feature limits or plan entitlements is pushed to the host application, which uses these generated permission paths as generic keys to enforce its own rules.

The library provides four things and nothing more: the `@Permission` annotation for defining the tree, the annotation processor for generating companion classes with string constants, the `PermissionCollector` for reflecting over generated classes and returning all paths as data, and the `PermissionGuard` for prefix-matching against the current user's authorities. Everything else — database tables, seeding, role assignment, plan management, admin UI — is the host application's responsibility.


## 2. The @Permission Annotation

**Status: CONFIRMED**

The system uses a single `@Permission` annotation for declaring permissions at every level of the hierarchy. The annotation targets packages, classes, and methods. It carries three fields: `key` (the node identifier used in the dot-path), `description` (optional, human-readable label for UI representation), and `parent` (optional class reference to explicitly override structural hierarchy).

There is no separate annotation for roots, branches, or leaves. The position in the tree is determined by context, not by annotation type. This keeps the API surface minimal and consistent.

When placed on a `package-info.java`, it declares a module-level or group-level permission node. When placed on a class (typically a Spring `@Service`), it declares a branch in the permission tree. When placed on a method, it declares a leaf permission representing a specific protectable operation.

```java
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {
    String key();
    String description() default "";
    Class<?> parent() default Void.class;
}
```


## 3. Hierarchical Resolution and Parent Resolution

**Status: CONFIRMED**

Every permission must have a parent except root permissions. The parent is resolved using a single priority chain that applies uniformly to class-level and method-level annotations.

If the `parent` field is explicitly specified on the annotation, that parent is used unconditionally. If the annotation is on a method and no parent is specified, the system checks whether the enclosing class carries a `@Permission` annotation. If it does, the enclosing class is the parent. If the enclosing class has no `@Permission`, or if the annotation is on a class with no explicit parent, the system walks up the Java package hierarchy starting from the annotated element's package. It inspects each ancestor package's `package-info.java` for a `@Permission` annotation. The first one found becomes the parent. If no `@Permission` is found in any ancestor package, the annotation processor emits a compile error.

This means a method inside a non-annotated service class can still have its parent resolved via the package hierarchy. It also means a method can override its parent explicitly, attaching itself to any permission node in the tree regardless of where the enclosing class lives.

```java
// Package-level permission
// com/erp/hr/package-info.java
@Permission(key = "hr", description = "Human Resources")
package com.erp.hr;

// Class inherits from package
// com/erp/hr/payroll/PayrollService.java
@Permission(key = "payroll", description = "Payroll Operations")
@Service
public class PayrollService {

    // Method inherits from class
    @Permission(key = "export", description = "Export report")
    public byte[] export(String format) {}

    // Method with explicit parent override
    @Permission(key = "audit", description = "Audit", parent = HRModule.class)
    public void audit() {}
}

// Method in non-annotated class — walks up packages
@Service
public class SomeService {

    @Permission(key = "something", description = "Something")
    public void doSomething() {}
    // enclosing class has no @Permission
    // walks up packages to find parent
}
```

Resolution rules summarized: explicit `parent` field wins. Then enclosing class `@Permission` if present. Then walk up packages. If nothing found, compile error.


## 4. Dot-Path Generation and Prefix Matching

**Status: CONFIRMED**

As the tree is resolved, the system concatenates keys using dot notation to form hierarchical permission paths. For example, a method annotated with `key = "export"` inside a class with `key = "payroll"` under a package with `key = "hr"` under a group with `key = "erp"` produces the path `erp.hr.payroll.export`.

These paths act as both the unique identifier and the evaluation string for the permission. They are also the keys that the host application uses to link its own business logic (plans, configs, quotas) to specific permissions.

Authorization uses prefix matching. When a permission requires `erp.hr.payroll.export`, the system checks whether any of the user's granted permission strings is a prefix of (or equal to) the required path. If the user holds `erp.hr`, that is a prefix of `erp.hr.payroll.export`, so access is granted.

Examples of prefix matching:

A user holding `erp.hr` can access `erp.hr.payroll.export` because `erp.hr` is a prefix. A user holding `erp.hr.payroll.export` can access only that specific leaf. A user holding `erp.hr.payroll.run` cannot access `erp.hr.payroll.export` because neither is a prefix of the other. A user holding `erp` can access anything under the entire ERP tree.


## 5. Permission Path Immutability

**Status: CONFIRMED**

A permission path is immutable once deployed to production. Renaming a key changes the dot-path, which breaks every role assignment, plan reference, and config entry that used the old path. The system does not attempt rename detection, migration logic, or versioning.

If a permission needs to change, the professional approach is a two-phase migration. First, the developer adds the new permission in code alongside the old one. After deployment, the host application's seeder detects the new path and inserts it into the database. The admin then migrates role assignments and plan references from the old path to the new path through the admin UI. Once no role, plan, or config references the old path, the developer removes it from code. On the next deployment, the seeder detects the orphaned path and marks it accordingly.

This is the same principle as database column migrations: add new, migrate data, drop old. No magic renames, no silent breakage. The library provides the data (via `PermissionCollector`) that makes orphan detection possible. The host application implements the detection logic, the admin UI warnings, and the cleanup decisions.


## 6. Compile-Time Processing

**Status: CONFIRMED**

The annotation processor runs during the Java compilation phase and performs two primary actions: validation and code generation.

For validation, the processor ensures every `@Permission` on a method or class has a resolvable parent through the resolution chain. If no parent can be found, it emits a compile error. It also validates that the tree has no duplicate keys at the same level (two siblings with the same key would produce identical dot-paths).

For code generation, the processor generates companion Java classes containing the fully resolved dot-paths as `public static final String` constants. For a service class `PayrollService` with method-level permissions `export` and `run`, the processor generates `PayrollServicePermissions` with fields `EXPORT` and `RUN`. This allows developers to reference permissions programmatically without relying on fragile string literals, entirely eliminating typo-related authorization bugs.

```java
// Source: PayrollService.java
@Permission(key = "payroll", description = "Payroll Operations")
@Service
public class PayrollService {

    @Permission(key = "export", description = "Export report")
    public byte[] export(String format) {}

    @Permission(key = "run", description = "Execute payroll")
    public void processPayroll() {}
}

// Generated: PayrollServicePermissions.java
public final class PayrollServicePermissions {
    public static final String EXPORT = "erp.hr.payroll.export";
    public static final String RUN = "erp.hr.payroll.run";

    private PayrollServicePermissions() {}
}
```

For class-level and package-level permissions, the processor also generates companion classes so their paths can be referenced as string constants by the host application's business logic.

```java
// Source: package-info.java
@Permission(key = "hr", description = "Human Resources")
package com.erp.hr;

// Generated: HrPermissions.java
public final class HrPermissions {
    public static final String HR = "erp.hr";

    private HrPermissions() {}
}
```


## 7. PermissionCollector — Data Extraction via Reflection

**Status: CONFIRMED**

The `PermissionCollector` is a utility class that reflects over the generated companion classes and returns a structured list of all permission paths that exist in the current build. It is the bridge between the compile-time generated code and whatever the host application wants to do with the data at runtime.

The collector scans for generated companion classes (by naming convention, marker annotation, or a configured base package), reads their `public static final String` fields, and returns a list of `PermissionNode` objects containing the path, description, and parent path.

```java
public class PermissionCollector {

    public static List<PermissionNode> collect(String basePackage) {
        // scans for *Permissions classes in the given package tree
        // reads static final String fields
        // returns list of PermissionNode(path, description, parentPath)
    }
}
```

The collector has zero dependencies. It uses standard Java reflection. It does not touch a database, does not require Spring, and does not perform any side effects. It simply reads and returns data.

The host application uses this data however it needs: seeding a database, building an admin UI tree, generating documentation, validating role assignments, detecting orphaned paths. The library does not prescribe any specific use.

This approach uses the generated companion classes as the single source of truth. There is no intermediate JSON file or registry that could go out of sync. The same constants that developers reference in code are the same values that the collector returns. Impossible to drift.


## 8. Library Boundary — What the Library Does and Does Not Do

**Status: CONFIRMED**

The library provides exactly four components:

`@Permission` annotation — declares permission nodes on packages, classes, and methods.

`PermissionAnnotationProcessor` — runs at compile time, validates the tree, generates companion classes with `public static final String` constants.

`PermissionCollector` — reflects over generated companion classes at runtime, returns all permission paths as structured data. Zero dependencies. No side effects.

`PermissionGuard` — reads the current user's granted authorities, performs prefix matching against a required permission path, throws `AccessDeniedException` on failure. The Spring-integrated version reads from `SecurityContextHolder`. A pure-Java version accepts authorities as a parameter.

The library does NOT provide:

Database tables or schema — the host application defines its own tables.

Seeding or synchronization logic — the host application writes its own seeder using data from `PermissionCollector`.

Orphan detection or cleanup — the host application compares collector output against its own DB and decides what to do with stale paths.

Role or user management — the host application assigns permissions to roles and loads them into Spring Security's `Authentication` object.

Admin UI — the host application builds its own UI using data from `PermissionCollector` and its own DB.

Plan management, feature configuration, quota enforcement, usage tracking — all host application concerns that use permission paths as generic keys.


## 9. PermissionGuard — Runtime Evaluation

**Status: CONFIRMED**

The `PermissionGuard` enhances Spring Security rather than replacing it. Spring Security remains responsible for user authentication, session management, and holding the user's granted authorities in its standard security context.

The guard reads the active user's granted authorities from `SecurityContextHolder` and performs prefix matching against the requested permission path. If any of the user's granted authority strings is a prefix of (or equal to) the requested permission path, access is granted. Otherwise, the guard throws Spring Security's standard `AccessDeniedException`, which is handled by the existing `ExceptionTranslationFilter` in the security filter chain.

No custom filters are added to the security chain. No Spring Security internals are modified. The guard simply reads what Spring Security already provides and applies prefix logic instead of exact matching.

The guard exists in two forms. The Spring-aware version reads from `SecurityContextHolder` and depends on `spring-security-core`. The pure-Java version accepts a collection of authority strings as a parameter and has zero dependencies, allowing it to be used outside of Spring.

```java
public final class PermissionGuard {

    // Spring-aware version
    public static void check(String requiredPermission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }

        boolean granted = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(authority -> requiredPermission.startsWith(authority));

        if (!granted) {
            throw new AccessDeniedException("Required permission: " + requiredPermission);
        }
    }

    // Pure-Java version
    public static void check(String requiredPermission, Collection<String> authorities) {
        boolean granted = authorities.stream()
            .anyMatch(authority -> requiredPermission.startsWith(authority));

        if (!granted) {
            throw new AccessDeniedException("Required permission: " + requiredPermission);
        }
    }

    // Boolean variants
    public static boolean has(String permission) { /* ... */ }
    public static boolean has(String permission, Collection<String> authorities) { /* ... */ }
}
```


## 10. PermissionGuard Usage in Service Methods

**Status: CONFIRMED**

The `PermissionGuard` is called inside service methods where the protected operation lives. The developer calls `PermissionGuard.check()` with the generated string constant. If the current user lacks the required permission, execution stops with an `AccessDeniedException` before any business logic runs.

The permission check and the business logic live in the same method. The `@Permission` annotation defines the tree structure and drives code generation. The guard enforces access at runtime. They are complementary — the annotation is for compile-time processing, the guard is for runtime enforcement.

```java
@Service
@Permission(key = "payroll", description = "Payroll Operations")
public class PayrollService {

    private final QuotaService quotaService;

    @Permission(key = "export", description = "Export to CSV")
    public void exportPayroll(String format, int employeeCount) {
        // Permission check (library)
        PermissionGuard.check(PayrollServicePermissions.EXPORT);

        // Business logic (host application)
        quotaService.consume(PayrollServicePermissions.EXPORT, employeeCount);

        // Domain logic
        // ...
    }

    @Permission(key = "run", description = "Execute payroll")
    public void processPayroll() {
        PermissionGuard.check(PayrollServicePermissions.RUN);
        // ...
    }

    @Permission(key = "rollback", description = "Rollback payroll")
    public void rollbackPayroll() {
        PermissionGuard.check(PayrollServicePermissions.ROLLBACK);
        // ...
    }
}
```


## 11. Host Application Responsibilities

**Status: CONFIRMED**

The host application is responsible for everything beyond structural permission definition and boolean access control. The permission library generates dot-path strings and enforces prefix-matching authorization. Everything else is the host application's domain.

Database schema — the host application defines its own tables for storing permissions, role assignments, plans, configs, and any other data. The library has no opinion on table structure.

Seeding — the host application writes its own startup routine (e.g., a Spring `ApplicationRunner`) that calls `PermissionCollector.collect()` to get the current set of permission paths from code, then compares against its DB and performs inserts and updates as needed. The seeder never deletes paths automatically. Paths found in DB but not in code are marked as orphaned, not removed, to avoid silently breaking role assignments.

Orphan detection — the host application compares the collector output against its DB to find paths that exist in the DB but no longer exist in code. It marks these as orphaned or deprecated. The admin handles migration and cleanup through the admin UI. The library provides the data that makes this comparison possible but does not implement the comparison itself.

Authority loading — the host application implements `UserDetailsService` or a custom `AuthenticationProvider` to load the user's granted permission strings into Spring Security's `Authentication` object as `GrantedAuthority` entries. The library reads these at check time via `PermissionGuard`.

Role and permission assignment — the host application provides the UI and logic for assigning permission strings to roles and users. The pool of assignable permissions comes from the seeded DB table, which mirrors the code-defined tree.

Plan management — the host application creates plans, assigns permission paths to plans, and manages plan subscriptions. Permission paths are used as generic keys.

Feature configuration — the host application defines configs, limits, and checks for specific features, keyed by permission paths. This is entirely business logic with no involvement from the permission library.

Usage tracking and quota enforcement — the host application tracks usage in its own domain tables and enforces limits using its own services. The permission path serves as the lookup key.


## 12. Library Module Structure

**Status: CONFIRMED**

The library is structured as two modules for maximum flexibility:

`permission-core` — contains the `@Permission` annotation, the annotation processor, `PermissionCollector`, and the pure-Java `PermissionGuard` overload that accepts authorities as a parameter. Zero external dependencies. Pure Java. Usable in any Java project regardless of framework.

`permission-spring` — contains the Spring-aware `PermissionGuard` overload that reads from `SecurityContextHolder`. Depends on `spring-security-core`. A thin integration layer.

For projects that do not use Spring Security, only `permission-core` is needed. The host application can use the `PermissionCollector` for seeding and implement its own authorization checks using the pure-Java guard.

For Spring Boot projects, both modules are used. The Spring-aware guard provides the convenience of reading authorities from the security context automatically.


## 13. Full Usage Example

**Status: CONFIRMED**

The following demonstrates the complete developer experience from permission definition through runtime enforcement and host application business logic.

```java
// Group level
// com/erp/package-info.java
@Permission(key = "erp", description = "ERP Platform")
package com.erp;
```
```java
// Module level
// com/erp/hr/package-info.java
@Permission(key = "hr", description = "Human Resources")
package com.erp.hr;
```
```java
// Service level — class is a branch, methods are leaves
// com/erp/hr/payroll/PayrollService.java
@Permission(key = "payroll", description = "Payroll Operations")
@Service
public class PayrollService {

    private final QuotaService quotaService;

    @Permission(key = "export", description = "Export to CSV")
    public void exportPayroll(String format, int employeeCount) {
        PermissionGuard.check(PayrollServicePermissions.EXPORT);
        quotaService.consume(PayrollServicePermissions.EXPORT, employeeCount);
        // domain logic...
    }

    @Permission(key = "run", description = "Execute payroll")
    public void processPayroll() {
        PermissionGuard.check(PayrollServicePermissions.RUN);
        // domain logic...
    }

    @Permission(key = "rollback", description = "Rollback payroll")
    public void rollbackPayroll() {
        PermissionGuard.check(PayrollServicePermissions.ROLLBACK);
        // domain logic...
    }
}
```

Generated at compile time:

```java
public final class PayrollServicePermissions {
    public static final String EXPORT = "erp.hr.payroll.export";
    public static final String RUN = "erp.hr.payroll.run";
    public static final String ROLLBACK = "erp.hr.payroll.rollback";

    private PayrollServicePermissions() {}
}
```

Permission tree produced:

```
erp
└── hr
    └── payroll
        ├── export      → erp.hr.payroll.export
        ├── run         → erp.hr.payroll.run
        └── rollback    → erp.hr.payroll.rollback
```

Host application seeder at startup:

```java
@Component
public class PermissionSeeder implements ApplicationRunner {

    private final PermissionRepository permissionRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<PermissionNode> codePermissions = PermissionCollector.collect("com.erp");
        List<String> dbPaths = permissionRepository.findAllPaths();

        Set<String> codePaths = codePermissions.stream()
            .map(PermissionNode::path)
            .collect(Collectors.toSet());

        // Insert new paths from code
        for (PermissionNode node : codePermissions) {
            if (!dbPaths.contains(node.path())) {
                permissionRepository.insert(node.path(), node.description(), node.parentPath());
            }
        }

        // Mark orphaned paths (in DB but not in code)
        for (String dbPath : dbPaths) {
            if (!codePaths.contains(dbPath)) {
                permissionRepository.markOrphaned(dbPath);
            }
        }
    }
}
```

At runtime, when a user calls the export endpoint, `PermissionGuard.check` reads their authorities from Spring Security, applies prefix matching, and grants or denies access. Then the host application's `QuotaService` uses the same permission path string to enforce business limits from its own tables.