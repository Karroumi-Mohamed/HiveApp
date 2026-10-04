import { adminApi as api } from "@/api/admin-api";
import { communicationApi } from "@/api/communication-api";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
import { read } from "@/data/gateway";
import type { Resource, Field, Action } from "./types";
import { text, select, permissionField, compactHistory } from "./fields";
const roleField: Field = {
  key: "roleIds",
  label: "Roles",
  type: "choices",
  full: true,
  load: async (search, page) => {
    const x = await read(p.rolesRead, () =>
      api.roles({ search, page, size: 20 }),
    );
    return {
      options: x.content.map((r) => ({ value: r.id, label: r.name })),
      totalPages: x.totalPages,
    };
  },
};
const operators: Resource = {
  key: "operators",
  title: "Operators",
  singular: "Operator",
  base: "/settings/operators",
  listPermission: p.usersRead,
  readPermission: p.usersRead,
  list: (q) => api.users(q),
  detail: api.user,
  normalize: (d) => ({ ...d, name: d.firstName + " " + d.lastName }),
  columns: [
    { key: "firstName", label: "First name" },
    { key: "lastName", label: "Last name" },
    { key: "email", label: "Email" },
    { key: "isActive", label: "Active", format: "boolean" },
    { key: "credentialState", label: "Access state" },
    { key: "roles", label: "Roles" },
  ],
  createPermission: p.usersCreate,
  fields: [
    text("firstName", "First name", true),
    text("lastName", "Last name", true),
    text("email", "Email", true),
    select("initialAccessMethod", "Initial access", [
      "EMAIL_LINK",
      "TEMPORARY_PASSWORD",
    ]),
    { key: "isSuperAdmin", label: "Platform administrator", type: "checkbox" },
  ],
  defaults: { initialAccessMethod: "EMAIL_LINK", isSuperAdmin: false },
  save: (_, i) => api.createUser(i as any),
  saveDestination: (r) => "/settings/operators/" + r.operator.id,
  bulkActions: [
    ...([true, false] as const).map<Action>((active) => ({
      key: String(active),
      label: active ? "Activate" : "Deactivate",
      permission: p.usersBulkSetActive,
      reason: false,
      destructive: !active,
      execute: (d) => api.bulkSetOperatorsActive(d.ids, active),
    })),
    {
      key: "assign-role",
      label: "Assign role",
      permission: p.usersBulkAssignRole,
      reason: false,
      fields: [
        { ...roleField, key: "adminRoleId", type: "choice", required: true },
      ],
      execute: (d, i) => api.bulkAssignOperatorRole(d.ids, i.adminRoleId),
    },
    {
      key: "activation",
      label: "Resend activation",
      permission: p.usersBulkResendActivation,
      reason: false,
      execute: (d) => api.bulkResendOperatorActivation(d.ids),
    },
  ],
  actions: [
    {
      key: "rename",
      label: "Edit name",
      permission: p.usersRename,
      reason: false,
      fields: [
        text("firstName", "First name", true),
        text("lastName", "Last name", true),
      ],
      defaults: (d) => d,
      execute: (d, i) => api.renameOperator(d.id, i as any),
    },
    {
      key: "email",
      label: "Change email",
      permission: p.usersChangeEmail,
      reason: false,
      fields: [text("email", "Email", true)],
      defaults: (d) => d,
      execute: (d, i) => api.changeOperatorEmail(d.id, i.email),
    },
    {
      key: "roles",
      label: "Edit roles",
      permission: p.usersAssignRole,
      reason: false,
      visible: () => can(p.usersRemoveRole),
      fields: [roleField],
      defaults: (d) => ({ roleIds: d.roles.map((r: any) => r.id) }),
      execute: (d, i) => api.replaceUserRoles(d.id, i.roleIds),
    },
    {
      key: "add-role",
      label: "Add role",
      permission: p.usersAssignRole,
      reason: false,
      fields: [{ ...roleField, key: "roleId", type: "choice", required: true }],
      execute: (d, i) => api.assignUserRole(d.id, i.roleId),
    },
    {
      key: "active",
      label: "Change access",
      permission: p.usersMutate,
      reason: false,
      destructive: true,
      execute: (d) => api.toggleUser(d.id),
    },
    {
      key: "activation",
      label: "Send activation",
      permission: p.usersResendActivation,
      reason: false,
      execute: (d) => api.resendOperatorActivation(d.id),
    },
    {
      key: "verification",
      label: "Verify email",
      permission: p.usersSendEmailVerification,
      reason: false,
      execute: (d) => api.sendOperatorEmailVerification(d.id),
    },
    {
      key: "temporary",
      label: "Temporary access",
      permission: p.usersTemporaryAccess,
      reason: false,
      execute: (d) => api.generateOperatorTemporaryAccess(d.id),
    },
  ],
  sections: [
    {
      key: "roles",
      label: "Roles",
      permission: p.usersRead,
      load: (d) => Promise.resolve(d.roles),
      columns: [
        { key: "name", label: "Role", link: (r) => "/settings/roles/" + r.id },
        { key: "status", label: "Status", format: "status" },
      ],
      actions: (r, d) => [
        {
          key: "remove",
          label: "Remove",
          permission: p.usersRemoveRole,
          reason: false,
          destructive: true,
          execute: () => api.removeUserRole(d.id, r.id),
        },
      ],
    },
    {
      key: "permissions",
      label: "Effective permissions",
      permission: p.usersReadPermissions,
      load: (d) => api.operatorPermissions(d.id),
      columns: [
        { key: "name", label: "Permission" },
        { key: "code", label: "Code" },
      ],
    },
  ],
};
const roles: Resource = {
  key: "roles",
  title: "Access roles",
  singular: "Role",
  base: "/settings/roles",
  listPermission: p.rolesRead,
  readPermission: p.rolesReadDetail,
  list: (q) => api.roles(q),
  detail: api.role,
  columns: [
    { key: "name", label: "Name" },
    { key: "description", label: "Description" },
    { key: "status", label: "Status", format: "status" },
  ],
  createPermission: p.rolesCreate,
  editPermission: p.rolesUpdate,
  fields: [
    {
      ...select("creationMode", "Create from", ["CUSTOM", "PRESET"]),
      show: (d) =>
        !d.id && can(p.rolesListPresets) && can(p.rolesCreateFromPreset),
    },
    {
      key: "presetCode",
      label: "Role preset",
      type: "choice",
      required: true,
      full: true,
      show: (d) => !d.id && d.creationMode === "PRESET",
      load: async (search) => {
        const presets = await read(p.rolesListPresets, api.rolePresets);
        return {
          options: presets
            .filter((x) =>
              (x.name + " " + x.description)
                .toLowerCase()
                .includes(search.toLowerCase()),
            )
            .map((x) => ({ value: x.code, label: x.name, record: x })),
          totalPages: 1,
        };
      },
      onSelect: (d, c) => {
        d.permissionIds = c.record?.permissions.map((x: any) => x.id) || [];
        if (!d.name) d.name = c.record?.name;
        if (!d.description) d.description = c.record?.description;
      },
    },
    text("name", "Name", true),
    { key: "description", label: "Description", type: "textarea" },
    { ...permissionField(), show: (d) => !d.id },
  ],
  defaults: { permissionIds: [], creationMode: "CUSTOM" },
  bulkActions: [
    ...([true, false] as const).map<Action>((active) => ({
      key: String(active),
      label: active ? "Activate" : "Deactivate",
      permission: p.rolesBulkSetActive,
      destructive: !active,
      execute: (d) => api.bulkSetRolesActive(d.ids, active),
    })),
  ],
  save: (id, i, d) =>
    id
      ? api.updateRole(id, {
          name: i.name,
          description: i.description,
          expectedVersion: d!.version,
        })
      : i.creationMode === "PRESET"
        ? api.createRoleFromPreset(i as any)
        : api.createRole(i as any),
  actions: [
    {
      key: "permissions",
      label: "Edit permissions",
      permission: p.rolesReplacePermissions,
      fields: [permissionField()],
      defaults: (d) => ({
        permissionIds: d.permissions?.map((r: any) => r.id) || [],
      }),
      reason: false,
      preview: (d, i) =>
        read(p.rolesPreviewImpact, () =>
          api.previewRoleImpact(d.id, { permissionIds: i.permissionIds }),
        ),
      execute: (d, i, r) =>
        api.replaceRolePermissions(d.id, {
          permissionIds: i.permissionIds,
          expectedVersion: d.version,
          confirmedAssignmentCount: r!.assignmentCount,
        }),
    },
    {
      key: "status",
      label: "Change status",
      permission: p.rolesTransitionStatus,
      fields: [select("status", "Status", ["ACTIVE", "INACTIVE", "ARCHIVED"])],
      defaults: (d) => ({ status: d.status }),
      preview: (d, i) =>
        read(p.rolesPreviewImpact, () =>
          api.previewRoleImpact(d.id, { status: i.status }),
        ),
      execute: (d, i, r) =>
        api.transitionRoleStatus(d.id, {
          status: i.status,
          expectedVersion: d.version,
          confirmedAssignmentCount: r!.assignmentCount,
        }),
    },
    {
      key: "duplicate",
      label: "Duplicate",
      permission: p.rolesDuplicate,
      fields: [text("name", "Name", true)],
      defaults: (d) => ({ name: d.name + " copy" }),
      reason: false,
      execute: (d, i) => api.duplicateRole(d.id, i as any),
      destination: (r) => "/settings/roles/" + r.id,
    },
    {
      key: "delete",
      label: "Delete role",
      permission: p.rolesDelete,
      visible: (d) => d.deletable,
      destructive: true,
      execute: (d) => api.deleteRole(d.id),
      destination: () => "/settings?view=roles",
    },
  ],
  sections: [
    {
      key: "holders",
      label: "Operators",
      permission: p.rolesReadHolders,
      load: (d) => api.roleHolders(d.id),
      columns: [
        {
          key: "email",
          label: "Operator",
          link: (r) => "/settings/operators/" + (r.adminUserId || r.id),
        },
        { key: "firstName", label: "First name" },
        { key: "lastName", label: "Last name" },
      ],
    },
    {
      key: "history",
      label: "History",
      permission: p.rolesReadHistory,
      load: (d) => api.roleHistory(d.id),
      columns: compactHistory,
    },
  ],
};
const features: Resource = {
  key: "features",
  title: "Feature controls",
  singular: "Feature",
  base: "/settings/features",
  listPermission: p.registryRead,
  readPermission: p.registryRead,
  list: async (q) => {
    const all = (await api.registryInventory())
      .flatMap((x) => x.features)
      .filter((x) =>
        (x.displayName + " " + x.code)
          .toLowerCase()
          .includes((q.search || "").toLowerCase()),
      );
    return {
      content: all.slice(q.page * 20, (q.page + 1) * 20),
      page: q.page,
      size: 20,
      totalPages: Math.ceil(all.length / 20),
      totalElements: all.length,
    };
  },
  detail: async (id) => {
    const feature = (await api.registryInventory())
      .flatMap((x) => x.features)
      .find((x) => x.id === id);
    if (!feature) throw Error("Feature not found.");
    return feature;
  },
  normalize: (d) => ({ ...d, name: d.displayName }),
  detailKeys: [
    "code",
    "description",
    "surface",
    "publicVisible",
    "newSalesEnabled",
    "newGrantsEnabled",
    "runtimeEnabled",
    "quotaSchema",
  ],
  columns: [
    { key: "displayName", label: "Feature" },
    { key: "moduleCode", label: "Module" },
    { key: "surface", label: "Surface" },
    { key: "publicVisible", label: "Public", format: "boolean" },
    { key: "newSalesEnabled", label: "Sales", format: "boolean" },
    { key: "runtimeEnabled", label: "Runtime", format: "boolean" },
  ],
  actions: [
    ...(
      [
        {
          key: "public-visibility",
          label: "Public visibility",
          permission: p.registryPublicVisibility,
          field: "publicVisible",
          toggle: "publicVisibilityToggleable",
        },
        {
          key: "new-sales",
          label: "New sales",
          permission: p.registryNewSales,
          field: "newSalesEnabled",
          toggle: "newSalesToggleable",
        },
        {
          key: "new-grants",
          label: "New grants",
          permission: p.registryNewGrants,
          field: "newGrantsEnabled",
          toggle: "newGrantsToggleable",
        },
      ] as const
    ).map<Action>((x) => ({
      key: x.key,
      label: x.label,
      permission: x.permission,
      visible: (d) => d[x.toggle],
      fields: [{ key: "enabled", label: "Enabled", type: "checkbox" }],
      defaults: (d) => ({ enabled: d[x.field] }),
      execute: (d, i) =>
        api.updateFeatureControl(d.id, x.key, !!i.enabled, i.reason),
    })),
    {
      key: "runtime",
      label: "Emergency runtime",
      permission: p.registryRuntime,
      visible: (d) => d.emergencyRuntimeToggleable,
      destructive: true,
      fields: [
        { key: "enabled", label: "Runtime enabled", type: "checkbox" },
        {
          key: "impactConfirmed",
          label: "Impact reviewed",
          type: "checkbox",
          required: true,
        },
        {
          key: "communicationConfirmed",
          label: "Customer communication arranged",
          type: "checkbox",
          required: true,
        },
      ],
      defaults: (d) => ({ enabled: d.runtimeEnabled }),
      execute: (d, i) =>
        api.updateEmergencyRuntime(
          d.id,
          !!i.enabled,
          i.reason,
          !!i.impactConfirmed,
          !!i.communicationConfirmed,
        ),
    },
  ],
  sections: [
    {
      key: "history",
      label: "Control history",
      permission: p.registryControlHistory,
      load: (d) => api.featureHistory(d.id),
      columns: [
        { key: "control", label: "Control" },
        { key: "previousValue", label: "Before", format: "boolean" },
        { key: "newValue", label: "After", format: "boolean" },
        { key: "reason", label: "Reason" },
        { key: "createdAt", label: "Date", format: "date" },
      ],
    },
  ],
};
const preferences: Resource = {
  key: "preferences",
  title: "Notification preferences",
  singular: "Notification preference",
  base: "/settings/preferences",
  listPermission: p.notificationsPreferences,
  readPermission: p.notificationsPreferences,
  list: async () =>
    (await communicationApi.settings(true)).map((r) => ({ ...r, id: r.topic })),
  detail: async (id) => {
    const value = (await communicationApi.settings(true)).find(
      (r) => r.topic === id,
    );
    if (!value) throw Error("Preference not found.");
    return { ...value, id: value.topic, name: value.topic };
  },
  columns: [
    { key: "topic", label: "Topic" },
    { key: "inAppEnabled", label: "In-app", format: "boolean" },
    { key: "emailEnabled", label: "Email", format: "boolean" },
  ],
  editPermission: p.notificationsPreferences,
  fields: [
    { key: "inAppEnabled", label: "In-app notifications", type: "checkbox" },
    { key: "emailEnabled", label: "Email notifications", type: "checkbox" },
  ],
  save: (id, i) =>
    communicationApi.setting(
      {
        topic: id,
        inAppEnabled: i.inAppEnabled,
        emailEnabled: i.emailEnabled,
      } as any,
      true,
    ),
  saveDestination: (r) => "/settings?view=preferences",
};
export const settingsResources = [operators, roles, features, preferences];
