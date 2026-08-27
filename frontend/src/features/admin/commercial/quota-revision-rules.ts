import type { QuotaPackage, QuotaPackageOperationalItem } from "@/api/contracts";

type RevisionIdentity = Pick<QuotaPackage, "id" | "sourceQuotaPackageId">;
type RevisionCandidate = Pick<QuotaPackageOperationalItem, "id" | "sourceQuotaPackageId">;

/** The backend compares only a direct predecessor or successor, never any arbitrary lineage row. */
export function directQuotaRevisionCandidates<T extends RevisionCandidate>(
  product: RevisionIdentity,
  candidates: readonly T[],
): T[] {
  return candidates.filter(
    (candidate) =>
      candidate.id !== product.id &&
      (candidate.id === product.sourceQuotaPackageId || candidate.sourceQuotaPackageId === product.id),
  );
}

/** A vanished/page-local choice is cleared; returning rows are never selected again implicitly. */
export function retainedQuotaRevisionCandidate(candidateId: string, candidates: readonly RevisionCandidate[]) {
  return candidates.some((candidate) => candidate.id === candidateId) ? candidateId : "";
}

export function boundedPaginationPage(page: number, totalPages: number) {
  if (totalPages <= 0) return 0;
  return Math.min(Math.max(page, 0), totalPages - 1);
}
