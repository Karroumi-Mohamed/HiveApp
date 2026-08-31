import type { MemberRoleAssignment } from "@/api/contracts";

export type AssignmentTarget = { scope: string; companyId: string | null };

/**
 * Whether this exact assignment already exists.
 *
 * <p>The backend's identity for an assignment is role + scope + company. Treating the role alone
 * as the identity made a legitimate second assignment impossible to express — the same role held
 * account-wide and again at one company are two different grants, not a duplicate.
 */
export function isAssignmentHeld(
  assignments: MemberRoleAssignment[],
  roleId: string,
  target: AssignmentTarget,
): boolean {
  const targetCompanyId = target.scope === "COMPANY" ? (target.companyId ?? null) : null;
  return assignments.some(
    (assignment) =>
      assignment.roleId === roleId &&
      assignment.scope === target.scope &&
      (assignment.companyId ?? null) === targetCompanyId,
  );
}

/**
 * Whether a role template may be assigned at this scope and company.
 *
 * <p>A company-bound role exists for one company: it cannot be granted account-wide, nor at a
 * different company. Offering it there produced choices the backend was always going to refuse.
 * An account-bound role carries no company of its own, so the backend's permitted scopes govern
 * it and both scopes stay on offer here.
 */
export function isRoleAssignableTo(
  role: { templateBoundary: "ACCOUNT" | "COMPANY"; boundaryCompanyId: string | null },
  target: AssignmentTarget,
): boolean {
  if (role.templateBoundary !== "COMPANY") return true;
  return target.scope === "COMPANY" && Boolean(target.companyId) && role.boundaryCompanyId === target.companyId;
}

/** Removes a stored picker choice once it is no longer one of the choices currently offered. */
export function reconcileRoleSelection(selectedRoleId: string, availableRoleIds: string[]): string {
  return availableRoleIds.includes(selectedRoleId) ? selectedRoleId : "";
}

/**
 * The company whose organization structure should be shown.
 *
 * <p>Returns null unless a company is explicitly selected *and* still valid. Structure is
 * company-specific, so falling back to "the first active one" showed a structure nobody chose and
 * applied edits to it; a selection that has since been deactivated or removed must not be used to
 * build a request from a stale id.
 */
export function resolveOrganizationCompanyId(
  selectedCompanyId: string | null,
  companies: Array<{ id: string; isActive: boolean }>,
): string | null {
  if (!selectedCompanyId) return null;
  const selected = companies.find((company) => company.id === selectedCompanyId);
  return selected?.isActive ? selected.id : null;
}
