# Platform Admin Role Management — V1

**Decision date:** 2026-08-14  
**Status:** Approved for implementation

This specification is separate from client roles and client-role templates. Platform admin roles control HiveApp operators only.

## 1. Product model

### Platform operator

A platform operator is an admin user who operates HiveApp itself. An operator may hold multiple admin roles. Their effective permissions are the union of all active assigned roles.

### SuperAdmin

SuperAdmin is the protected root authority:

- It is not represented by an ordinary role.
- It cannot be created from a preset.
- Ordinary operators cannot modify, deactivate, or remove SuperAdmins.
- SuperAdmin can manage every platform role and operator.
- The system must prevent removal of the final active SuperAdmin.

### Admin role

An admin role is a reusable collection of platform-admin permissions. It has:

- A unique identifier.
- A unique name.
- An optional description.
- A status of `INACTIVE`, `ACTIVE`, or `ARCHIVED`.
- A permission set.
- An optimistic-lock version.
- Created and updated actors and timestamps.

It has no Account, Company, Plan, Add-on, or organizational scope.

## 2. Role-name rules

Admin role names are globally unique. Before comparison, the backend trims leading and trailing whitespace, collapses repeated internal whitespace, and compares case-insensitively. Therefore, `Support Manager` and ` support   manager ` collide.

The backend must enforce this with a normalized-name unique constraint. Concurrent attempts must return a stable `409 ROLE_NAME_CONFLICT`, never a `500`. The display name preserves the user's chosen formatting after whitespace normalization.

## 3. Seeded admin-role presets

Use the term **preset** rather than template to distinguish these from client-role templates.

Presets are:

- Defined and seeded by HiveApp.
- Immutable through the admin UI.
- Not shown in the roles table.
- Available only inside the Create Role flow.
- Validated against the code-declared permission catalogue at startup.
- Never synchronized with roles created from them.

Creating from a preset copies its current permissions into an ordinary independent role. The operator may keep the preset's suggested name, change the name before creation, edit the copied permissions, or cancel without creating anything. If the suggested name already exists, the form requires another name.

No preset versions, provenance, updates, upgrades, merging, or synchronization are required.

### Initial preset families

The exact permission codes must be built from the real `PLATFORM_ADMIN_ROLE_GRANTABLE` catalogue, not invented in frontend code.

1. **Platform Observer** — Read-only platform permissions; no mutation, access-management, credential, or registry-sync actions.
2. **Customer Operations** — Account, Company, member, and subscription inspection plus approved operational support actions; no pricing configuration or platform access management.
3. **Commercial Operations** — Plans, Add-ons, quota packages, subscriptions, and checkout operations; no platform-operator or role management.
4. **Access Administrator** — Platform operator and admin-role management; no unrelated commercial permissions.

Do not create an all-permissions preset. SuperAdmin already represents that authority. Before implementing preset contents, produce a preset-to-permission matrix using actual permission codes for review.

## 4. Creating roles

An authorized operator can create a role from blank, from a seeded preset, or by duplicating an existing role.

Every new role:

- Starts `INACTIVE`.
- Has no assigned operators.
- Has a unique name.
- Contains only platform-admin-grantable permissions.
- Contains no permission beyond the creator's delegation ceiling.

### Create from blank

The operator enters a name, optional description, and permissions.

### Create from preset

The system pre-populates the suggested role name, description, and permission selection. The operator may edit all fields before creating.

### Duplicate role

Duplication copies the description and permission set. It does not copy the name, status, assigned operators, or history. The UI suggests a unique name, but the operator must confirm it. The new role starts `INACTIVE`.

Archived roles may be duplicated if the actor is still authorized to grant every copied permission.

## 5. Permission management

Permission choices come from the backend catalogue. The frontend never hardcodes permission codes.

The permission picker shows only permissions that are marked `PLATFORM_ADMIN_ROLE_GRANTABLE` and fall within the current operator's delegation ceiling. Permissions unavailable to the actor are not offered in the picker.

When viewing an existing role containing permissions beyond the actor's authority, the actual permissions remain visible for transparency, but the permission editor is read-only. The operator cannot indirectly remove or reproduce permissions beyond their authority. SuperAdmin may select every platform-admin-grantable permission.

### Permission editor UX

Permissions are displayed as rows grouped by domain, not chips or decorative cards. It provides:

- Search by permission name or description.
- Domain filtering.
- Group expansion and collapse.
- Select or clear a domain.
- A selected-permission count.
- A clear indication of selections copied from a preset or duplicate during creation.
- Sticky save and cancel actions when changes exist.

Permissions are identified by stable code, not localized label.

## 6. Editing and impact preview

Metadata-only changes to name and description can save directly. Changes to permissions or lifecycle status require an impact preview showing:

- Number of assigned operators.
- Permissions being added.
- Permissions being removed.
- Operators losing their last source of a removed permission.
- Whether the current actor may lose management access.

The update request must reference the preview and role version. If the role or its assignments changed afterward, return `409 STALE_IMPACT_PREVIEW` and require a new preview. No fallback update without a successful preview is allowed.

## 7. Lifecycle

### `INACTIVE`

- Grants no permissions.
- Cannot be newly assigned.
- Existing assignments remain recorded but ineffective.
- Can be activated or archived.

### `ACTIVE`

- Grants permissions through its assignments.
- Can be assigned to operators.
- Can be deactivated or archived after impact confirmation.

### `ARCHIVED`

- Grants no permissions.
- Cannot receive new assignments.
- Is hidden from the default roles list.
- Retains history and existing assignment records.
- Can be restored to `INACTIVE`.

Archiving does not delete the role.

## 8. Deletion

Permanent deletion is allowed only when the role has never been assigned, is not active, and is not referenced by required audit or security records. Otherwise, the operator must archive it.

Deletion requires a confirmation dialog containing the exact role name. The backend makes the final decision; the UI must not infer deletability alone.

## 9. Assigning roles to operators

An authorized operator can view an operator's roles, assign an active role, remove a role assignment, and view all operators holding a role.

Rules:

- The role must be `ACTIVE`.
- Duplicate operator-role assignments are rejected.
- The actor cannot assign a role containing permissions beyond their delegation ceiling.
- Ordinary operators cannot manage SuperAdmin assignments.
- A non-SuperAdmin cannot modify their own role assignments; another authorized operator must do it.
- Removing one role does not affect the operator's other roles.
- Effective permissions are recalculated from all remaining active roles.

Assignment and removal actions must be audited.

## 10. Authorization actions

Reuse existing code-declared permission nodes. Add missing nodes only if the current catalogue cannot separately protect:

- Viewing admin roles.
- Creating admin roles.
- Editing admin roles.
- Managing role lifecycle.
- Deleting admin roles.
- Assigning roles to operators.
- Viewing role history.

Having edit-role authority does not bypass the delegation ceiling.

## 11. Backend APIs

Use stable DTOs and `PageResponse<T>`, never raw Spring `Page`.

### Presets

```http
GET  /api/admin/role-presets
GET  /api/admin/role-presets/{presetCode}
POST /api/admin/roles/from-preset
```

The list returns only presets whose complete permission set the actor may grant.

Creation request:

```json
{
  "presetCode": "PLATFORM_OBSERVER",
  "name": "Platform Observer",
  "description": "Optional edited description"
}
```

### Roles

```http
GET    /api/admin/roles
POST   /api/admin/roles
GET    /api/admin/roles/{roleId}
PATCH  /api/admin/roles/{roleId}/metadata
POST   /api/admin/roles/{roleId}/duplicate
POST   /api/admin/roles/{roleId}/impact-preview
PUT    /api/admin/roles/{roleId}/permissions
POST   /api/admin/roles/{roleId}/status
DELETE /api/admin/roles/{roleId}
```

### Assignments

Retain one canonical assignment mutation API:

```http
POST   /api/admin/users/{userId}/roles/{roleId}
DELETE /api/admin/users/{userId}/roles/{roleId}
GET    /api/admin/roles/{roleId}/operators
```

### Permission picker

```http
GET /api/admin/roles/grantable-permissions
```

It returns only actor-grantable permissions with domain and localized display metadata.

## 12. Role-list DTO

Each table row contains:

```text
id
name
description
status
permissionCount
assignedOperatorCount
updatedAt
version
availableActions
```

`availableActions` is computed by the backend so the frontend does not recreate security policy. List queries must use aggregate or bulk counts and avoid per-role queries.

## 13. UI architecture

### Navigation

Under the platform administration sidebar:

**Access management**

- Operators
- Roles

Presets do not receive their own navigation page.

### Roles page

Use a table, not a role-card grid.

The header contains the title `Roles`, a compact result count, and one primary `Create role` action. Do not add an eyebrow chip, decorative description paragraph, or analytics-card row.

Table columns:

- Role
- Status
- Permissions
- Assigned operators
- Last modified
- Actions

Controls:

- Search.
- Status filters: All, Active, Inactive, Archived.
- Sort by name, operator count, or last modification.
- Paginated results.
- Row actions through an overflow menu.

Clicking the role name opens its detail page.

### Create-role flow

Opening `Create role` presents `Start blank` and the seeded presets. Presets use a compact selectable list or flat two-column selector on wide screens, not large promotional cards.

Each option shows only its name, short purpose, and permission count. Selection opens the normal role form with fields pre-populated.

### Role detail

The header contains the role name, status, assigned-operator count, primary contextual action, and an overflow menu for destructive or secondary actions.

Tabs:

1. Overview
2. Permissions
3. Operators
4. History

Avoid placing every section inside bordered cards. Use dividers, tables, definition rows, and whitespace for structure.

### Responsive behaviour

- Desktop uses the full table.
- Tablet hides secondary columns before introducing horizontal scrolling.
- Mobile uses accessible stacked list rows with the same actions.
- Do not turn the desktop experience into a decorative card dashboard.

## 14. Feedback and accessibility

- French is primary; Arabic and RTL remain supported.
- Use logical spacing and direction-aware icons.
- Phosphor remains the single icon system.
- Icon-only buttons require accessible labels and tooltips.
- All actions are keyboard accessible.
- Focus returns to the correct trigger after dialogs close.
- Destructive actions use explicit text and confirmation.
- Loading prevents duplicate submission.
- API errors branch on stable error codes, never localized messages.
- Name conflicts display next to the name field.
- Interactive targets are at least 44 pixels.

## 15. Audit history

Record:

- Role creation method: blank, preset, or duplicate.
- Metadata changes.
- Permission additions and removals.
- Status transitions.
- Assignment and removal.
- Archive, restore, and delete.
- Actor and timestamp.

The Role entity does not retain preset or duplicate provenance. The audit event may record the creation source for investigation only; it creates no synchronization relationship.

## 16. Performance requirements

- Preset definitions and validated permission sets may be cached in memory.
- Validate every mutation again server-side.
- Use a unique database constraint for normalized role names.
- Use optimistic locking for role edits.
- Use bulk assignment counts on role lists.
- No controller maps lazy entities.
- Open-in-view remains disabled.
- Update the current base SQL directly; no Flyway history is required before production.

## 17. Explicitly out of scope

- Admin-created or editable presets.
- Preset versions.
- Template synchronization.
- Upgrade notifications.
- Automatic merging.
- Provenance stored on roles.
- Plan or Add-on compatibility.
- Account or Company scope.
- Role hierarchy.
- Direct per-operator permission overrides.
- Bulk role assignment.
- Duplicate-role analysis.
- Charts or role analytics.
- Automatic role recommendations.

## 18. Required tests

Backend:

- Normalized name collisions on create, rename, preset creation, and duplication.
- Concurrent duplicate-name attempts return one success and one `409`.
- A preset copies the exact permission set.
- Preset creation produces an inactive role without assignments.
- Duplication does not copy assignments or status.
- Actor delegation ceiling enforcement.
- Rejection of non-grantable permissions.
- SuperAdmin protections.
- Inactive and archived roles grant nothing.
- Stale impact-preview rejection.
- Safe-delete rules.
- Assignment union behaviour.
- Constant-query role listing.
- Audit-event creation.
- Stable `PageResponse`.

Frontend:

- Roles render as a table.
- Preset selection pre-populates the form.
- The preset name remains editable.
- Name-conflict errors appear beside the name field.
- The permission picker contains only returned permissions.
- Saving is impossible without a valid impact preview.
- Duplication creates no copied assignments.
- RTL layout and directional icons work.
- Keyboard navigation and dialog focus restoration work.
