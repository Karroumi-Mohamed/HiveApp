import type { AdminRolePreset } from "@/api/contracts";

export function draftFromPreset(preset: AdminRolePreset) {
  return {
    name: preset.name,
    description: preset.description,
    permissionIds: new Set(preset.permissions.map((permission) => permission.id)),
  };
}

export function permissionSetsDiffer(current: ReadonlySet<string>, proposed: ReadonlySet<string>) {
  return current.size !== proposed.size || [...proposed].some((permissionId) => !current.has(permissionId));
}
