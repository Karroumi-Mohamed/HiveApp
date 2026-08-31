import { describe, expect, test } from "bun:test";
import type { MemberRoleAssignment } from "@/api/contracts";
import {
  isAssignmentHeld,
  isRoleAssignableTo,
  reconcileRoleSelection,
  resolveOrganizationCompanyId,
} from "./role-assignment-rules";

const assignment = (roleId: string, scope: "ACCOUNT" | "COMPANY", companyId: string | null): MemberRoleAssignment => ({
  assignmentId: `${roleId}-${scope}-${companyId ?? "none"}`,
  roleId,
  roleName: "Support",
  roleStatus: "ACTIVE",
  scope,
  companyId,
  companyName: companyId ? "Company" : null,
});

describe("member role assignment identity", () => {
  const held = [assignment("role-1", "ACCOUNT", null)];

  test("the same role at account scope is already held", () => {
    expect(isAssignmentHeld(held, "role-1", { scope: "ACCOUNT", companyId: null })).toBe(true);
  });

  test("the same role at a company is a different assignment", () => {
    // The regression: excluding by role id alone made this impossible to express.
    expect(isAssignmentHeld(held, "role-1", { scope: "COMPANY", companyId: "company-a" })).toBe(false);
  });

  test("the same role at a different company is a different assignment", () => {
    const withCompany = [...held, assignment("role-1", "COMPANY", "company-a")];
    expect(isAssignmentHeld(withCompany, "role-1", { scope: "COMPANY", companyId: "company-b" })).toBe(false);
    expect(isAssignmentHeld(withCompany, "role-1", { scope: "COMPANY", companyId: "company-a" })).toBe(true);
  });

  test("a company assignment does not block the account-wide one", () => {
    const onlyCompany = [assignment("role-1", "COMPANY", "company-a")];
    expect(isAssignmentHeld(onlyCompany, "role-1", { scope: "ACCOUNT", companyId: null })).toBe(false);
  });

  test("an unrelated role is never held", () => {
    expect(isAssignmentHeld(held, "role-2", { scope: "ACCOUNT", companyId: null })).toBe(false);
  });
});

describe("role boundary compatibility", () => {
  const accountRole = { templateBoundary: "ACCOUNT" as const, boundaryCompanyId: null };
  const companyRole = { templateBoundary: "COMPANY" as const, boundaryCompanyId: "company-a" };

  test("an account-bound role is offered at both scopes", () => {
    expect(isRoleAssignableTo(accountRole, { scope: "ACCOUNT", companyId: null })).toBe(true);
    expect(isRoleAssignableTo(accountRole, { scope: "COMPANY", companyId: "company-a" })).toBe(true);
  });

  test("a company-bound role cannot be granted account-wide", () => {
    expect(isRoleAssignableTo(companyRole, { scope: "ACCOUNT", companyId: null })).toBe(false);
  });

  test("a company-bound role cannot be granted at another company", () => {
    expect(isRoleAssignableTo(companyRole, { scope: "COMPANY", companyId: "company-b" })).toBe(false);
  });

  test("a company-bound role is offered only at its own company", () => {
    expect(isRoleAssignableTo(companyRole, { scope: "COMPANY", companyId: "company-a" })).toBe(true);
  });

  test("a company-bound role is not offered before a company is chosen", () => {
    expect(isRoleAssignableTo(companyRole, { scope: "COMPANY", companyId: null })).toBe(false);
  });
});

describe("role picker selection", () => {
  test("keeps a role that remains available", () => {
    expect(reconcileRoleSelection("role-1", ["role-1", "role-2"])).toBe("role-1");
  });

  test("clears a role that becomes incompatible or already assigned", () => {
    expect(reconcileRoleSelection("role-1", ["role-2"])).toBe("");
  });
});

describe("organization company context", () => {
  const companies = [
    { id: "company-a", isActive: true },
    { id: "company-b", isActive: false },
  ];

  test("no selection resolves to nothing rather than the first company", () => {
    // The regression: account context silently fell back to the first active company, showing a
    // structure nobody chose.
    expect(resolveOrganizationCompanyId(null, companies)).toBeNull();
  });

  test("an explicit active selection resolves to itself", () => {
    expect(resolveOrganizationCompanyId("company-a", companies)).toBe("company-a");
  });

  test("an inactive selection resolves to nothing", () => {
    expect(resolveOrganizationCompanyId("company-b", companies)).toBeNull();
  });

  test("a selection that no longer exists resolves to nothing, not a stale id", () => {
    expect(resolveOrganizationCompanyId("deleted-company", companies)).toBeNull();
  });

  test("an empty company list never invents a selection", () => {
    expect(resolveOrganizationCompanyId("company-a", [])).toBeNull();
  });
});
