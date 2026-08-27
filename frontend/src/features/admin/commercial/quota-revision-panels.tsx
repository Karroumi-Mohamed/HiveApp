import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
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
    queryKey: ["admin", "commercial", "quota", product.lineageId, "revisions", revisionPage],
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
  const comparison = useQuery({
    queryKey: ["admin", "commercial", "quota", product.id, "comparison", candidateId],
    queryFn: () => adminApi.compareQuotaPackage(product.id, candidateId),
    enabled: session.can(adminPermissions.quotaPackagesCompare) && Boolean(candidateId),
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
  const candidates = (revisions.data?.content ?? []).filter((candidate) => candidate.id !== product.id);
  const canRevise = session.can(adminPermissions.quotaPackagesRevise);
  const canCompare = session.can(adminPermissions.quotaPackagesCompare);
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
              onChange={(event) => setReason(event.target.value)}
              placeholder="Pourquoi cette nouvelle révision est-elle nécessaire ?"
              value={reason}
            />
          </div>
          <Button className="mt-4" disabled={!reason.trim() || revise.isPending} onClick={() => revise.mutate()}>
            Créer R{product.revisionNumber + 1}
          </Button>
        </section>
      ) : null}
      {canCompare ? (
        <section className="rounded-xl border bg-card p-5">
          <h2 className="text-sm font-semibold">Comparer les révisions</h2>
          <div className="mt-5 space-y-2">
            <Label>Révision à comparer</Label>
            <Select onValueChange={setCandidateId} value={candidateId}>
              <SelectTrigger aria-label="Révision à comparer">
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
          </div>
          {revisions.isError ? <ErrorState retry={() => void revisions.refetch()} /> : null}
          {revisions.data && revisions.data.totalPages > 1 ? (
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
          {comparison.isLoading ? <LoadingState rows={2} /> : null}
          {comparison.isError ? <ErrorState retry={() => void comparison.refetch()} /> : null}
          {comparison.data ? (
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
    queryKey: ["admin", "commercial", "quota", productId, "history", page],
    queryFn: () => adminApi.quotaPackageHistory(productId, page, 20),
    enabled: session.can(adminPermissions.quotaPackagesHistory),
  });
  if (!session.can(adminPermissions.quotaPackagesHistory)) return <PermissionState />;
  if (history.isLoading) return <LoadingState rows={4} />;
  if (history.isError) return <ErrorState retry={() => void history.refetch()} />;
  if (!history.data?.content.length)
    return <p className="border-y py-10 text-center text-sm text-muted-foreground">Aucun événement enregistré.</p>;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <ol className="divide-y px-5">
        {history.data.content.map((entry) => (
          <li className="grid gap-1 py-4 sm:grid-cols-[minmax(0,1fr)_auto]" key={entry.id}>
            <div>
              <p className="text-sm font-medium">{historyActionLabel(entry.action, entry.lifecycleAction)}</p>
              <p className="text-xs text-muted-foreground">
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
