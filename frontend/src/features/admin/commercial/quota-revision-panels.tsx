import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { QuotaPackage } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { adminCommercialKeys, invalidateAdminCommercial } from "@/features/commercial/commercial-query";
import {
  boundedPaginationPage,
  directQuotaRevisionCandidates,
  retainedQuotaRevisionCandidate,
} from "./quota-revision-rules";

const fieldLabel: Record<string, string> = {
  name: "Nom",
  description: "Description",
  featureCode: "Fonctionnalité",
  resource: "Ressource",
  capacityPerUnit: "Capacité par unité",
  repeatable: "Répétition",
  maximumQuantity: "Quantité maximale",
  allowedPlanCodes: "Forfaits compatibles",
  allowedAddOnCodes: "Add-ons compatibles",
  salesVisibility: "Disponibilité commerciale",
};

function historyActionLabel(action: string, lifecycleAction: string | null) {
  if (lifecycleAction === "ACTIVATE") return "Mise en vente";
  if (lifecycleAction === "DEACTIVATE") return "Ventes suspendues";
  if (lifecycleAction === "ARCHIVE") return "Révision archivée";
  if (action.includes("revise")) return "Nouvelle révision";
  if (action.includes("create")) return "Pack créé";
  if (action.includes("update")) return "Configuration modifiée";
  if (action.includes("delete")) return "Brouillon supprimé";
  return "Modification du pack";
}

export function QuotaRevisionPanel({ product }: { product: QuotaPackage }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [reason, setReason] = useState("");
  const [candidateId, setCandidateId] = useState("");
  const [revisionPage, setRevisionPage] = useState(0);
  const revisions = useQuery({
    queryKey: adminCommercialKeys.quotaPackages.revisions(product.lineageId, revisionPage),
    queryFn: () =>
      adminApi.operationalQuotaPackages({
        lineageId: product.lineageId,
        page: revisionPage,
        size: 20,
        sort: "revisionNumber",
        direction: "desc",
      }),
    enabled: session.can(adminPermissions.quotaPackagesCompare),
  });
  const candidates = directQuotaRevisionCandidates(product, revisions.data?.content ?? []);
  const selectedCandidateId = retainedQuotaRevisionCandidate(candidateId, candidates);
  const comparisonContextReady = Boolean(selectedCandidateId) && !revisions.isFetching && !revisions.isError;
  const comparison = useQuery({
    queryKey: adminCommercialKeys.quotaPackages.comparison(product.id, selectedCandidateId),
    queryFn: () => adminApi.compareQuotaPackage(product.id, selectedCandidateId),
    enabled: session.can(adminPermissions.quotaPackagesCompare) && comparisonContextReady,
  });
  const revise = useMutation({
    mutationFn: () => adminApi.reviseQuotaPackage(product.id, product.version, reason.trim()),
    onSuccess: ({ successor, warnings }) => {
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.quotaPackages.all());
      toast.success(`Révision R${successor.revisionNumber} créée`);
      warnings.forEach((warning) => {
        toast.warning(warning);
      });
      navigate(`/admin/quota-packages/${successor.id}/revisions`);
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Révision impossible"),
  });
  const canRevise = session.can(adminPermissions.quotaPackagesRevise);
  const canCompare = session.can(adminPermissions.quotaPackagesCompare);
  useEffect(() => {
    if (revisions.isFetching || revisions.isError || !revisions.data) return;
    const boundedPage = boundedPaginationPage(revisionPage, revisions.data.totalPages);
    if (boundedPage !== revisionPage) {
      setCandidateId("");
      setRevisionPage(boundedPage);
      return;
    }
    if (candidateId !== selectedCandidateId) setCandidateId(selectedCandidateId);
  }, [candidateId, revisionPage, revisions.data, revisions.isError, revisions.isFetching, selectedCandidateId]);
  if (!canRevise && !canCompare) return <PermissionState />;
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {canRevise ? (
        <section className="rounded-xl border bg-card p-5">
          <h2 className="text-sm font-semibold">Créer la prochaine révision</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Le pack actuel reste inchangé. La copie commence en brouillon et conserve ses tarifs comme brouillons.
          </p>
          <div className="mt-5 space-y-2">
            <Label htmlFor="quota-revision-reason">Motif</Label>
            <Textarea
              id="quota-revision-reason"
              maxLength={500}
              name="revision-reason"
              onChange={(event) => setReason(event.target.value)}
              placeholder="Pourquoi cette nouvelle révision est-elle nécessaire ?"
              rows={4}
              autoComplete="off"
              value={reason}
            />
            <p className="text-end text-xs text-muted-foreground tabular-nums">{reason.trim().length}/500</p>
          </div>
          <Button className="mt-4" disabled={!reason.trim() || revise.isPending} onClick={() => revise.mutate()}>
            Créer R{product.revisionNumber + 1}
          </Button>
        </section>
      ) : null}
      {canCompare ? (
        <section className="rounded-xl border bg-card p-5">
          <h2 className="text-sm font-semibold">Comparer les révisions</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Seules la révision précédente et la révision suivante peuvent être comparées.
          </p>
          {revisions.isLoading ? <LoadingState rows={2} /> : null}
          {revisions.isError ? <ErrorState retry={() => void revisions.refetch()} /> : null}
          {revisions.data && !revisions.isError ? (
            <>
              <div className="mt-5 space-y-2">
                <Label htmlFor="quota-comparison-revision">Révision à comparer</Label>
                <Select onValueChange={setCandidateId} value={selectedCandidateId}>
                  <SelectTrigger className="w-full" id="quota-comparison-revision">
                    <SelectValue placeholder="Choisir une révision" />
                  </SelectTrigger>
                  <SelectContent>
                    {candidates.map((candidate) => (
                      <SelectItem key={candidate.id} value={candidate.id}>
                        {candidate.name} · R{candidate.revisionNumber}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {!candidates.length ? (
                  <p className="text-xs text-muted-foreground">Aucune révision adjacente sur cette page.</p>
                ) : null}
              </div>
              {revisions.data.totalPages > 1 ? (
                <PaginationBar
                  page={revisions.data.page}
                  totalElements={revisions.data.totalElements}
                  totalPages={revisions.data.totalPages}
                  onPageChange={(page) => {
                    setCandidateId("");
                    setRevisionPage(page);
                  }}
                />
              ) : null}
            </>
          ) : null}
          {comparisonContextReady && comparison.isLoading ? <LoadingState rows={2} /> : null}
          {comparisonContextReady && comparison.isError ? <ErrorState retry={() => void comparison.refetch()} /> : null}
          {comparisonContextReady && comparison.data ? (
            <div className="mt-5 border-t pt-4 text-sm">
              <p className="font-medium">
                {comparison.data.directSuccessor ? "Révision suivante directe" : "Révisions de la même lignée"}
              </p>
              {comparison.data.changedFields.length ? (
                <ul className="mt-3 list-disc space-y-1 ps-5 text-muted-foreground">
                  {comparison.data.changedFields.map((field) => (
                    <li key={field}>{fieldLabel[field] ?? "Configuration commerciale"}</li>
                  ))}
                </ul>
              ) : (
                <p className="mt-3 text-muted-foreground">Aucune différence de configuration.</p>
              )}
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

export function QuotaHistoryPanel({ productId }: { productId: string }) {
  const session = useAdminSession();
  const [page, setPage] = useState(0);
  const history = useQuery({
    queryKey: adminCommercialKeys.quotaPackages.history(productId, page),
    queryFn: () => adminApi.quotaPackageHistory(productId, page, 20),
    enabled: session.can(adminPermissions.quotaPackagesHistory),
  });
  const boundedPage = history.data ? boundedPaginationPage(page, history.data.totalPages) : page;
  useEffect(() => {
    if (!history.isFetching && !history.isError && boundedPage !== page) setPage(boundedPage);
  }, [boundedPage, history.isError, history.isFetching, page]);
  if (!session.can(adminPermissions.quotaPackagesHistory)) return <PermissionState />;
  if (history.isLoading) return <LoadingState rows={4} />;
  if (history.isError) return <ErrorState retry={() => void history.refetch()} />;
  if (boundedPage !== page) return <LoadingState rows={1} />;
  if (!history.data?.totalElements)
    return <p className="border-y py-10 text-center text-sm text-muted-foreground">Aucun événement enregistré.</p>;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <ol className="divide-y px-5">
        {!history.data.content.length ? (
          <li className="py-10 text-center text-sm text-muted-foreground">Aucun événement sur cette page.</li>
        ) : null}
        {history.data.content.map((entry) => (
          <li className="grid gap-1 py-4 sm:grid-cols-[minmax(0,1fr)_auto]" key={entry.id}>
            <div className="min-w-0">
              <p className="text-sm font-medium">{historyActionLabel(entry.action, entry.lifecycleAction)}</p>
              <p className="break-words text-xs text-muted-foreground">
                {entry.actorEmail ? `par ${entry.actorEmail}` : "Action système"}
                {entry.reason ? ` · ${entry.reason}` : ""}
              </p>
            </div>
            <div className="text-xs text-muted-foreground sm:text-end">
              <p>
                {new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium", timeStyle: "short" }).format(
                  new Date(entry.occurredAt),
                )}
              </p>
              <p>{entry.outcome === "SUCCEEDED" ? "Réussi" : "Échec"}</p>
            </div>
          </li>
        ))}
      </ol>
      <PaginationBar
        page={history.data.page}
        totalElements={history.data.totalElements}
        totalPages={history.data.totalPages}
        onPageChange={setPage}
      />
    </section>
  );
}
