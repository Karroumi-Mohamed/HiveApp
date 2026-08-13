# HiveApp UI Design System

Status: implemented baseline and current UI source of truth as of 2026-08-12.

This document defines the reusable visual language, interaction rules, and application boundaries. It does not replace product-flow decisions or backend authorization.

## 1. Product character

HiveApp is an operational business system. Its interface should feel calm, precise, and trustworthy under dense workloads.

- Prefer clear hierarchy over decoration.
- Keep common actions close to the data they affect.
- Reveal advanced controls progressively.
- Explain consequences before a destructive or wide-impact action.
- Show identifiers and technical details when they help an operator verify a decision, but never let them dominate the page.
- Avoid dashboard-card overload, oversized headings, glass effects, and decorative gradients in working screens.
- Do not add explanatory copy by default. A title is enough when the page or section is self-explanatory.
- Do not place decorative eyebrow labels, chips, or icons above page titles. Use a label there only when it communicates necessary product state or context.
- Do not put icons in colored rounded boxes by default. Icon containers require a functional reason such as selection, status, or a touch target.
- Do not turn every region or metric into a card. Prefer whitespace, alignment, and separators until a boundary is needed for interaction or comprehension.

Visual direction: **quiet control surface**. Neutral surfaces carry most of the interface; deep indigo identifies primary actions and selection; a restrained honey accent gives HiveApp a recognizable signature without turning the product yellow.

## 2. Application boundaries

HiveApp uses one React application with two deliberately isolated shells:

| Shell | Routes | Identity | Context |
|---|---|---|---|
| Platform administration | `/admin/*` | Admin session and ADMIN token | No client workspace headers |
| Client workspace | `/app/*` | Member session and CLIENT token | Account, selected Company, and B2B mode |

The shells may share visual primitives and generic patterns. They must not share an authentication store, permission bootstrap model, or configured API client.

Client query keys include the selected Company and B2B context whenever results or permissions depend on them. `X-Company-ID` and `X-Is-B2B` are attached centrally by the client API layer, never manually at screen call sites.

## 3. Language and direction

- French is the default language and initial document direction is LTR.
- Arabic is optional and switches the document to `lang="ar"` and `dir="rtl"`.
- Use Inter Variable for French and Latin text; use Noto Sans Arabic Variable for Arabic.
- Layout uses logical properties (`start`, `end`, `ps`, `pe`, `ms`, `me`) rather than physical left/right rules.
- Directional icons must mirror when their meaning depends on direction. Phosphor is the application icon set.
- IDs, codes, email addresses, currency values, and other direction-sensitive fragments may use an isolated LTR span inside Arabic UI.
- Translation keys describe meaning, not the current French copy.

## 4. Token architecture

Tokens have three layers:

1. Primitive tokens: raw color, spacing, radius, shadow, typography, and motion values.
2. Semantic tokens: purpose such as background, foreground, primary, danger, success, warning, selected, and focus.
3. Component tokens: exceptions required by a specific reusable component or pattern.

Components use semantic or component tokens. Raw color values remain inside the token definitions.

### 4.1 Color roles

| Role | Intent |
|---|---|
| Canvas | Quiet warm-neutral page background |
| Surface | Primary content and form surface |
| Raised surface | Menus, dialogs, and selected inspectors |
| Ink | Primary readable content |
| Muted ink | Supporting labels and metadata |
| Indigo primary | Primary action, current selection, and focus identity |
| Honey accent | Brand detail, counts, and restrained highlights |
| Success | Completed or healthy state |
| Warning | Attention required but operation remains possible |
| Danger | Destructive, failed, revoked, or blocked state |
| Info | Neutral operational notice |

Status is never communicated by color alone. Pair it with a label and, when useful, an icon.

### 4.2 Spacing, shape, and density

- Base spacing unit: 4px.
- Standard controls: 40px; compact controls: 32px; touch-oriented controls: 44–48px.
- Standard table row: 48px; compact operational row: 40px; comfortable mobile row: 56px.
- Default page padding: 16px mobile, 24px tablet, 32px desktop.
- Cards and panels use modest 10–14px radii. Pills are reserved for statuses, filters, and compact metadata.
- Shadows indicate elevation, not decoration. Bordered surfaces are preferred for normal page structure.
- Dashboard metrics appear only when the backend exposes decision-supporting aggregates or trends. Do not download full lists to derive counts, and do not show raw record totals with no clear action. Omit the region when useful data is unavailable.

### 4.3 Typography

| Usage | Treatment |
|---|---|
| Page title | 24–30px, semibold, compact line height |
| Section title | 18–20px, semibold |
| Body | 14–16px, regular |
| Labels and table headers | 12–14px, medium |
| Codes and identifiers | 12–14px, monospaced or tabular where useful |

Sentence case is the default. Avoid all-caps except short technical badges.

### 4.4 Motion

- Color and opacity: 150ms.
- Small layout or transform changes: 200ms.
- Panels and dialogs: 200–250ms.
- Honor `prefers-reduced-motion`.
- No continuous decorative motion in application shells.

## 5. Component layers

### 5.1 Primitives — `src/components/ui`

Copied shadcn/Radix components. They own accessibility behavior, focus handling, variants, and basic styling. Product logic does not belong here.

### 5.2 Patterns — `src/components/patterns`

Reusable HiveApp workflows composed from primitives:

- `ProductShell` / `AppShell`: responsive sidebar, mobile sheet, top bar, navigation, and content boundary.
- `PageHeader`: breadcrumb when needed, title, and actions. Description is optional and reserved for ambiguity or important operating context.
- `PaginationBar`: stable server-pagination controls used with operational tables.
- `SectionTabs`: page and subpage navigation without duplicating shell state.
- `StatusBadge`: canonical lifecycle and operation statuses with text labels.
- `OperationalCard`: decision-supporting aggregate with an action or meaningful state; not a decorative count box.
- `PermissionInventory`: progressively disclosed effective-permission list.
- `QuotaEditor`: reusable resource/limit editor for plan and add-on composition.
- Feature-level catalogue pickers and impact dialogs: backend-driven choices, counts, warnings, timing, and acknowledgement.
- `EmptyState`, `ErrorState`, and `LoadingState`.

### 5.3 Features — `src/features`

Feature components own business-specific queries, commands, copy, and composition. They consume patterns and primitives but do not restyle them ad hoc.

## 6. Layout system

Desktop uses a persistent collapsible sidebar, a compact contextual top bar, and a content column capped where reading benefits from it. Data-heavy pages may use the full available width.

Sidebar selection uses restrained background contrast and stronger typography. Decorative rails, glowing indicators, and ornamental active-state markers are not used.

Mobile uses a sheet-based navigation drawer. Toolbars wrap into a primary action plus an overflow menu. Wide tables change into prioritized rows or cards; horizontal scrolling is a last resort.

Preferred information structures:

- List + inspector for roles, plans, and platform features.
- Table for peer records with comparable columns, such as admin users.
- Stacked comparison cells for plan-feature coverage.
- Dedicated danger zone at the end of detail surfaces.

## 7. Interaction and state rules

Every remote-data surface defines loading, empty, error, success, unavailable, and permission-denied behavior.

- Loading uses stable skeleton geometry; unauthorized content must never flash.
- Empty means the actor can read the resource but no records exist. Show a permission-aware next action.
- Hidden means the actor lacks discovery/read access. Do not tease inaccessible navigation.
- Disabled is used when an action is visible and the reason is useful: state conflict, quota, missing prerequisite, or insufficient mutation permission on an otherwise readable page.
- Backend errors branch on stable `ApiError.code`, never on human-readable messages.
- A 403 triggers permission refresh for the active shell and rolls back optimistic state.
- Mutations that affect many records show an impact preview and timing choice when the backend supports one.
- Toasts confirm transient outcomes; persistent warnings remain inline near the affected data.

## 8. Authorization UX

The backend is authoritative. UI authorization improves clarity but is never a security boundary.

- Effective permissions control route, navigation, section, and action visibility.
- SuperAdmin and Account Owner root behavior is represented explicitly in each shell's session model.
- Permission picker options come from backend catalogue endpoints.
- Permission identifiers used for route/action checks are centralized typed identifiers, not scattered string literals.
- Client permissions are reloaded when Company or B2B context changes.
- The current delegation ceiling answers **what** an actor may grant.
- Target-aware management will later answer **whom** an actor may manage. The UI must not imply department/group-based target authority until that backend model exists.

## 9. Forms and consequential operations

- Labels are persistent; placeholders are examples, not labels.
- Validation appears next to the field and the first invalid field receives focus after submit.
- Save buttons state the operation: `Créer le rôle`, `Enregistrer les quotas`, `Remplacer le forfait`.
- Destructive dialogs name the entity and summarize consequences.
- Plan/subscription operations distinguish normal revision, exceptional immediate enforcement, and scheduled-at-renewal effects.
- Backend-generated warnings and impact counts are preferred over frontend inference.
- Passwords and temporary credentials are never revealed after their defined one-time delivery moment.

## 10. Accessibility baseline

- WCAG 2.2 AA target.
- Keyboard access and visible `:focus-visible` states for every interactive element.
- Minimum 4.5:1 contrast for normal text and 3:1 for large text and UI boundaries.
- Icon-only actions have accessible names and tooltips when the action is not universally obvious.
- Dialogs have labelled titles/descriptions and restore focus on close.
- Async changes use appropriate live-region announcements without excessive noise.
- Tables retain semantic headers; responsive alternatives preserve labels and relationships.

## 11. Governance

- Add a primitive only when the value is reusable.
- Add a pattern only after the workflow repeats or is clearly cross-domain.
- New variants require a named use case and all interaction states.
- Screens do not introduce raw brand colors, arbitrary radii, or one-off shadows.
- French and Arabic behavior is reviewed together for shared primitives and patterns.
- A visual decision that changes these rules updates this document in the same batch.
