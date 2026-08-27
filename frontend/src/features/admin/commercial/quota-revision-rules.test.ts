import { describe, expect, test } from "bun:test";
import type { QuotaPackage, QuotaPackageOperationalItem } from "@/api/contracts";
import {
  boundedPaginationPage,
  directQuotaRevisionCandidates,
  retainedQuotaRevisionCandidate,
} from "./quota-revision-rules";

const revision = (id: string, sourceQuotaPackageId: string | null) =>
  ({ id, sourceQuotaPackageId }) as QuotaPackageOperationalItem;

describe("quota revision comparison pagination", () => {
  const current = { id: "r3", sourceQuotaPackageId: "r2" } as QuotaPackage;

  test("offers only the direct predecessor and successor accepted by the backend", () => {
    expect(
      directQuotaRevisionCandidates(current, [
        revision("r1", null),
        revision("r2", "r1"),
        revision("r3", "r2"),
        revision("r4", "r3"),
        revision("r5", "r4"),
      ]).map((candidate) => candidate.id),
    ).toEqual(["r2", "r4"]);
  });

  test("clears a page-local choice permanently when it vanishes", () => {
    const visible = [revision("r2", "r1")];
    expect(retainedQuotaRevisionCandidate("r2", visible)).toBe("r2");
    const cleared = retainedQuotaRevisionCandidate("r2", []);
    expect(cleared).toBe("");
    expect(retainedQuotaRevisionCandidate(cleared, visible)).toBe("");
  });

  test("clamps an empty stale last page instead of trapping the reader there", () => {
    expect(boundedPaginationPage(4, 3)).toBe(2);
    expect(boundedPaginationPage(-1, 3)).toBe(0);
    expect(boundedPaginationPage(2, 0)).toBe(0);
  });
});
