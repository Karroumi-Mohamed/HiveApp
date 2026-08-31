import { describe, expect, test } from "bun:test";
import type { RoleImpact } from "@/api/contracts";
import { isRolePreviewReady } from "./role-edit-rules";

const preview = { version: 3, assignmentCount: 2 } as RoleImpact;

describe("role edit impact confirmation", () => {
  test("creation and duplication do not require an impact preview", () => {
    expect(isRolePreviewReady(false, { data: undefined, isFetching: false, isError: false })).toBe(true);
  });

  test("an edit cannot be submitted before a preview succeeds", () => {
    expect(isRolePreviewReady(true, { data: undefined, isFetching: false, isError: false })).toBe(false);
  });

  test("retained preview data cannot be confirmed while it is being refreshed", () => {
    expect(isRolePreviewReady(true, { data: preview, isFetching: true, isError: false })).toBe(false);
  });

  test("retained preview data cannot be confirmed after a failed refresh", () => {
    expect(isRolePreviewReady(true, { data: preview, isFetching: false, isError: true })).toBe(false);
  });

  test("a current successful preview can be confirmed", () => {
    expect(isRolePreviewReady(true, { data: preview, isFetching: false, isError: false })).toBe(true);
  });
});
