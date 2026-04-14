# HiveApp Platform — Master Business & Architecture Specification

**Version:** 5.0
**Status:** Approved Master Blueprint (Final Hierarchy Logic)
**Date:** 2026-04-19

---

## 1. Executive Summary & Vision

HiveApp is a multi-tenant, collaborative Enterprise Resource Planning (ERP) ecosystem. Its core philosophy is **"Deterministic Security and Explicit Delegation."** The platform is designed to ensure that access control is driven by structured, code-first definitions rather than brittle manual database configurations.

---

## 2. Business Domains & Functional Capabilities

### 2.1 Platform Administration (The Governance Layer)
Admins never create structural entities. They **refine** what the code provides.
*   **The "Trio" of Admin Decisions**:
    1.  **Visibility Control**: Toggling features through their lifecycle (`INTERNAL` -> `BETA` -> `PUBLIC`).
    2.  **Plan Composition**: Bundling code-spawned Features into marketable subscription tiers.
    3.  **Quota Value Management**: Setting the numeric limits (e.g., "5 Users" vs "50 Users") for each feature within a plan.
*   **Zero Data Entry**: Because existence is defined in code, admins never click "New Module" or "New Feature." They only manage the business properties of discovered entities.

### 2.2 Client Workspace (The Tenant Layer)
*   **Absolute Boundary**: The `Company` is the unit of isolation.
*   **Delegated RBAC**: Managers create scoped roles and assign permissions (Bricks) to members.

---

## 3. The Permission & Quota Philosophy

### 3.1 The "Code-Spawns-Existence" Principle
The skeletal structure of the ERP is declared in the Java codebase via `@PermissionNode`.
*   **Modules (Buildings)**: Declared on packages. Automatically spawned in the DB at startup.
*   **Features (Rooms)**: Declared on classes. Automatically linked to modules in the DB at startup.
*   **Permissions (Bricks)**: Declared on methods. Automatically linked to features in the DB at startup.

### 3.2 The Hierarchical Sieve
1.  **Collaboration Sieve (B2B)**: External grants.
2.  **Entitlement Sieve (Subscription)**: Plan-level allowed features.
3.  **Delegated Sieve (Roles & Overrides)**: User-specific grants within a Company.

### 3.3 Type-Safe Polymorphic Quotas
Quotas are linked to Permissions. Code defines the **Type** (e.g., `MaxStorage`), while the Admin defines the **Value** (e.g., `10GB`). Values are stored as JSONB but evaluated via Java Sealed Records.

---

## 4. Lifecycle & Performance

### 4.1 Feature Visibility
*   **INTERNAL**: Default state. Hidden from clients, used for dev/testing.
*   **PUBLIC**: Market-ready. Visible in the client catalog.
*   **BETA**: Restricted access for invited accounts.

### 4.2 Expansion Logic & Snapshots
When an Admin composes a Plan, the system snapshots the specific **Feature IDs** into the database. This ensures that if a Module grows in the code later, existing plans remain stable and do not "leak" new features to customers who haven't paid for them.

---

## 5. Technical Architecture & UX Standards

### 5.1 System Architecture
*   **Monolithic Agility**: Structured monolith with clean internal domain boundaries.
*   **Code-First Seeding**: The database registry is a mirror of the code's permission tree.

### 5.2 User Experience (The "Lean Backend" Rule)
To keep the database clean and the system fast, **Marketing Metadata** is handled by the Frontend:
*   **Names & Icons**: The backend only stores technical codes (e.g., `HR_PAYROLL`). The Frontend Code contains a mapping dictionary that translates these codes into "Automated Payroll Engine" and attaches the correct SVG icon.
*   **Visual Abstraction**: The UI groups features into modules automatically to keep the interface clean for the user.

### 5.3 Contextual Execution
The infrastructure reliably establishes identity and scope (Account/Company/B2B) before any business logic is executed.
