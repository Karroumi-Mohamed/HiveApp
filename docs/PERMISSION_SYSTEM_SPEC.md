# Permission System — Specification

## 1. Philosophy and Architecture

**Status: CONFIRMED**

The Permission System is a pure, structural authorization library designed for compile-time safety and automatic synchronization between the codebase and the database. It follows a strict separation of concerns, focusing entirely on structural identity and boolean access control.

The system is **Logic-Centric**: security settings are tied strictly to where the code is defined. There is no hidden inheritance magic between classes or interfaces. This ensures that security is visible, auditable, and predictable.

The library provides four things:
1.  The @PermissionNode annotation for defining the tree.
2.  The annotation processor for generating type-safe companion classes.
3.  The PermissionCollector for multi-module permission discovery.
4.  The PermissionGuard with a pluggable **Policy Engine** for complex business logic.

Everything else — database tables, role assignment, plan management — is the host application's responsibility.


## 2. The @PermissionNode Annotation

**Status: CONFIRMED**

The system uses a single @PermissionNode annotation for declaring permissions at every level.

```java
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionNode {
    String key() default "";
    String description() default "";
    Class<?> parent() default Void.class;
    boolean autoDiscover() default false;
    Guard guard() default Guard.INHERIT;

    enum Guard { INHERIT, ON, OFF }
}
```

*   **key**: The node identifier. If empty on a method, the method name is used.
*   **autoDiscover**: If true on a class, all public methods automatically become leaf nodes.
*   **guard**: Controls runtime enforcement. ON activates automatic checking.


## 3. Hierarchical Resolution (Logic-Centric)

**Status: CONFIRMED**

Permission paths are resolved using a strict priority chain:
1.  **Explicit parent**: The parent field in the annotation always wins.
2.  **Enclosing Class**: If the method is not annotated, it checks its declaring class.
3.  **Package Walk**: If the class is not annotated, it walks up the package hierarchy to find an annotated package-info.java.

**Strict Rule**: Subclasses do **not** inherit annotations from parent classes. Security follows the **definition**, not the **instance**. A method defined in BaseService is always governed by BaseService settings, even when called on a SubService.


## 4. Generated Code & Type Safety

**Status: CONFIRMED**

The processor generates a type-safe tree of classes in a .generated sub-package relative to the source.

```java
// Usage in code
PermissionGuard.check(PlatformPermissions.Account.Read.permission());
```

*   **permission()**: Returns the Permission object for any node.
*   **all()**: Returns all leaf descendants of a branch.
*   **except()**: Returns all leaves except specific exclusions.

This eliminates typo bugs at compile-time. If you rename a key, the code using it will not compile.


## 5. PermissionGuard & Policy Engine

**Status: CONFIRMED**

The PermissionGuard uses a **Successive Filtering (Sieve)** model. It evaluates a chain of pluggable policies until a definitive decision is reached.

### 5.1 The Policy Sieve
The host application (HiveApp) registers policies in priority order:
1.  **Collaboration Policy**: Checks B2B grants (Overrides Plan).
2.  **Plan Policy**: Checks Subscription Plan limits.
3.  **User Policy**: Checks the user's explicit Roles and Overrides.

### 5.2 Context-Aware Checks
The guard supports passing a context object (e.g., CompanyID) to policies:
```java
PermissionGuard.check(Permissions.HR.Read.permission(), targetCompanyId);
```


## 6. Database Integration (Host Application)

**Status: CONFIRMED**

HiveApp implements the following database structure to support the "Trio" relationship (**Member + Permission + Company**):

### 6.1 Company-Scoped Roles
Roles are not global. A Role is owned by a Company.
*   **Role**: id, name, company_id.
*   **MemberRole**: Pivot linking Member to a Role.

### 6.2 Explicit User Overrides
Specific permissions can be granted or denied directly to a member for a specific company.
*   **MemberPermissionOverride**: member_id, permission_id, company_id, decision (ALLOW/DENY).

### 6.3 Automatic Seeding
At startup, PermissionCollector.collect() scans all JARs and updates the Permission registry table automatically.


## 7. Effective Permission Calculation

**Status: CONFIRMED**

The system resolves access for a Member in Company X using this order:

1.  **B2B**: Is there an active Collaboration granting this permission for Company X? (If yes, GRANT).
2.  **Override**: Does a MemberPermissionOverride exist for this user in Company X? (If yes, follow the decision).
3.  **Role**: Does the user have a MemberRole in Company X that contains this permission? (If yes, GRANT).
4.  **Default**: DENY.

## 8. Quota & Entitlement Integration

**Status: DESIGNED**

Permissions in HiveApp define **Boolean Actions** (Can I do this?), while Quotas define **Quantitative Limits** (How many times can I do this?). The two systems are linked via the permission dot-path.

### 8.1 Polymorphic Quota Objects
Quotas are stored as JSONB in the database but mapped to a sealed hierarchy of Java Records at runtime. 

```java
public sealed interface QuotaConfig permits CountQuota, StorageQuota { ... }
public record CountQuota(int max) implements QuotaConfig {}
```

### 8.2 The Linking Rule
Every Quota record must be keyed by a valid Permission path generated by the library. This ensures that business limits are always attached to verifiable security nodes.


## 9. The Effective Sieve Model

**Status: DESIGNED**

The Final Access decision for any request is an intersection of multiple layers.

### 9.1 The Intersection Formula
`EffectiveAccess = (UserRoles ∪ UserOverrides) ∩ (PlanCeiling ∪ PlanOverrides) ∩ ContextGrants`

### 9.2 Priority Order
The PermissionGuard Policy Engine evaluates the sieve in this order:
1. **Collaboration Policy**: If B2B context, check grantor permissions.
2. **Plan Policy**: Verify the account subscription allows the feature.
3. **User Policy**: Check the individual member's roles and manual overrides.
