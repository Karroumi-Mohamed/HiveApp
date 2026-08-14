import { describe, expect, test } from "bun:test";
import type { AdminRolePreset } from "@/api/contracts";
import { draftFromPreset, permissionSetsDiffer } from "@/features/admin/roles/admin-role-rules";

const preset: AdminRolePreset = {
  code: "OBSERVER",
  name: "Observateur",
  description: "Lecture seule",
  permissions: [
    { id: "p-1", code: "platform.roles.read", name: "Read", description: "", action: "read", resource: "roles" },
    { id: "p-2", code: "platform.plans.list", name: "List", description: "", action: "list", resource: "plans" },
  ],
};

describe("admin role preset draft", () => {
  test("copies the suggested metadata and exact permission set", () => {
    const draft = draftFromPreset(preset);

    expect(draft.name).toBe("Observateur");
    expect(draft.description).toBe("Lecture seule");
    expect([...draft.permissionIds]).toEqual(["p-1", "p-2"]);
  });

  test("creates an independent set that cannot mutate the preset", () => {
    const draft = draftFromPreset(preset);
    draft.permissionIds.delete("p-1");

    expect(preset.permissions).toHaveLength(2);
  });
});

describe("admin role permission edits", () => {
  test("permission order alone is not a change", () => {
    expect(permissionSetsDiffer(new Set(["p-1", "p-2"]), new Set(["p-2", "p-1"]))).toBeFalse();
  });

  test("addition and removal both require a preview", () => {
    expect(permissionSetsDiffer(new Set(["p-1"]), new Set(["p-1", "p-2"]))).toBeTrue();
    expect(permissionSetsDiffer(new Set(["p-1", "p-2"]), new Set(["p-1"]))).toBeTrue();
  });
});
