# HiveApp Platform — Master Business & Architecture Specification

**Version:** 4.1
**Status:** Approved Master Blueprint
**Date:** 2026-04-19

---

## 1. Executive Summary & Vision

HiveApp is a multi-tenant, collaborative Enterprise Resource Planning (ERP) ecosystem. Its primary mission is to provide businesses with a highly granular, secure, and scalable platform to manage their operations, while enabling seamless, strictly controlled B2B (Business-to-Business) collaboration. 

The core philosophy of HiveApp is **"Deterministic Security and Explicit Delegation."** The platform is designed from the ground up to ensure that access control is never ambiguous, that permissions cannot magically leak across boundaries, and that the system's capabilities are driven by structured, code-first definitions rather than brittle manual configurations.

---

## 2. Business Domains & Functional Capabilities

The HiveApp ecosystem is divided into two logical halves: The Administrative Governance layer and the Client Operational Workspace.

### 2.1 Platform Administration (The Governance Layer)
This domain is restricted to the creators and operators of the HiveApp platform.
*   **Module & Feature Registry Management**: The platform acts as a catalog of capabilities. Admins define these capabilities, creating the "Blueprint" of what the ERP can do.
*   **Plan Definition (Templating)**: Admins create subscription tiers (e.g., Free, Professional, Enterprise). 
*   **Registry APIs**:
    *   **Public Catalog API**: Exposes only `PUBLIC` modules and features for clients to browse and purchase.
    *   **Admin Inventory API**: Exposes the complete inventory (including `INTERNAL` and `BETA` features) for platform management.

### 2.2 Client Workspace (The Tenant Layer)
This domain is where businesses operate.
*   **Account Ownership**: A central Account represents the billing and ownership entity.
*   **Multi-Company Architecture**: An Account can operate multiple isolated Companies. The Company is the absolute boundary for data and access.
*   **Organizational Structuring**: Companies can organize their personnel into informational Departments.
*   **Granular Delegation (RBAC)**: Business owners and managers can define highly specific Roles, assigning collections of permissions to members, scoped strictly to a specific Company.

### 2.3 B2B Collaboration Engine
*   **The Trust Link**: Business A can invite Business B to collaborate on a specific Company owned by Business A.
*   **The "Sliver" Grant**: Business A grants a specific, limited subset of permissions (e.g., "Only manage Payroll") to Business B.
*   **Delegated Management**: Business B distributes that "sliver" among its own employees.

---

## 3. The Permission & Quota Philosophy

### 3.1 The "Code is Law" Principle
Permissions are defined by engineers using `@PermissionNode`. The system automatically discovers these at startup, ensuring the database is a perfect reflection of the code.

### 3.2 The Hierarchical Sieve
1.  **The Collaboration Sieve (B2B)**: Checks if the action is granted via a B2B link.
2.  **The Entitlement Sieve (Subscription)**: Checks if the Account's subscription (Plan + Overrides) allows this feature.
3.  **The Delegation Sieve (Roles & Overrides)**: Checks user-specific grants within the target company.

### 3.3 Structural Mapping (Bricks, Rooms, Buildings)
*   **Permissions (Bricks)**: Atomic code-level actions.
*   **Features (Rooms)**: Marketable units composed of multiple Permissions. **The Plan only contains Features.**
*   **Modules (Buildings)**: Visual folders grouping multiple Features for clean UI and bulk-buying shortcuts.

### 3.4 Polymorphic Quotas
*   **Linkage**: Quotas are tightly coupled to **Permissions** (Bricks).
*   **Storage**: Quotas are defined in the `PlanFeature.config` (JSONB) or `Subscription.custom_overrides` (JSONB).
*   **Type-Safety**: Quotas are mapped to **Java Sealed Records** (e.g., `CountQuota`, `StorageQuota`) for strict runtime evaluation.

---

## 4. Product & Lifecycle Management

### 4.1 Feature Visibility & Lifecycle
To support development and staged rollouts, every Feature in the Registry has a `status`:
*   **PUBLIC**: Visible to all clients in the catalog.
*   **INTERNAL**: Only visible/accessible to Platform Admins (for development/testing).
*   **BETA**: Visible only to specific invited Accounts.
*   **DEPRECATED**: Hidden from the catalog, but active for existing subscribers.

### 4.2 Plan Composition & Overrides
*   **Expansion Logic**: While an Admin might **compose** a Plan or a Client **subscribe to** a Module, the system expands this into its constituent **Feature IDs** when saving to the Plan or Subscription Overrides. This ensures snapshot integrity.
*   **Marketing Metadata**: The `Plan` entity contains a `marketing_data` (JSONB) field for UI highlights, descriptions, and localized labels.

---

## 5. Technical Architecture & UX Standards

### 5.1 System Architecture
*   **Monolithic Agility**: Structured monolith with clean domain boundaries.
*   **Data Persistence**: Relational (PostgreSQL) for rigid entities; JSONB for dynamic/polymorphic configs.

### 5.2 User Experience (Visual Abstraction)
*   **Packaging**: The UI must simplify complexity. If an Account has purchased all features of a module, the UI should represent this as a single "Module Active" state rather than listing individual features.
*   **Drill-Down**: Granular feature management is only exposed when required (e.g., custom plan construction).

### 5.3 Contextual Execution
The platform infrastructure reliably establishes the actor's identity and verified scope (Account/Company/Capacity) before any business logic is executed.
