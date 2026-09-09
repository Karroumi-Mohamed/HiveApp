import {
  ArchiveIcon,
  ArrowLeftIcon,
  CopyIcon,
  GitBranchIcon,
  PencilSimpleIcon,
  PlayIcon,
  TrashIcon,
  WarningCircleIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { Link, Navigate, useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  CommercialSegmentAction,
  CommercialSegmentAudienceIdentity,
  CommercialSegmentAudienceReference,
  CommercialSegmentDetail,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
  invalidateCommercialPolicyTargeting,
} from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import {
  actionBlockers,
  billingCycleLabel,
  reviewedSegmentActivationReady,
  segmentActionLabel,
  segmentBlocker,
  segmentHistoryAction,
  segmentKind,
  segmentMutationMessage,
  segmentProductType,
  segmentStatus,
  subscriptionStatusLabel,
  validSegmentId,
} from "./commercial-segment-rules";

function isAudienceIdentity(
  item: CommercialSegmentAudienceReference | CommercialSegmentAudienceIdentity,
): item is CommercialSegmentAudienceIdentity {
  return "accountName" in item;
}

function dateTime(value: string | null) {
  return value
    ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
    : "—";
}

function boundedPage(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 0;
  return Math.min(Number(value), 10_000);
}

function boundedResponsePage(requested: number, totalPages: number) {
  return totalPages <= 0 ? 0 : Math.min(requested, totalPages - 1);
}

function withParam(current: URLSearchParams, key: string, value: string | number | null) {
  const next = new URLSearchParams(current);
  if (value === null || value === "" || value === 0) next.delete(key);
  else next.set(key, String(value));
  return next;
}

function readable(value: string) {
  return value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (letter) => letter.toUpperCase());
}

const changedFieldLabel: Record<string, string> = {
  NAME: "Nom",
  DESCRIPTION: "Description",
  KIND: "Méthode de définition",
  SOURCE: "Origine",
  REASON: "Motif",
  DEFINITION: "Audience",
  OWNER: "Responsable",
};

function ReasonDialog({
  segment,
  action,
  trigger,
}: {
  segment: CommercialSegmentDetail;
  action: "DUPLICATE" | "REVISE" | "ARCHIVE" | "DELETE_DRAFT";
  trigger: ReactNode;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [name, setName] = useState(`${segment.summary.name} — copie`);
  const mutation = useMutation({
    mutationFn: async () => {
      if (action === "DUPLICATE") {
        return adminApi.duplicateCommercialSegment(segment.summary.id, {
          version: segment.summary.version,
          name,
          reason,
        });
      }
      if (action === "REVISE") {
        return adminApi.reviseCommercialSegment(segment.summary.id, { version: segment.summary.version, reason });
      }
      if (action === "ARCHIVE") {
        return adminApi.archiveCommercialSegment(segment.summary.id, { version: segment.summary.version, reason });
      }
      await adminApi.deleteCommercialSegment(segment.summary.id, { version: segment.summary.version, reason });
      return null;
    },
    onSuccess: async (result) => {
      if (action === "ARCHIVE") await invalidateCommercialPolicyTargeting(queryClient);
      else await invalidateAdminCommercial(queryClient, adminCommercialKeys.segments.all());
      setOpen(false);
      toast.success(
        action === "DUPLICATE"
          ? "Segment dupliqué"
          : action === "REVISE"
            ? "Révision créée"
            : action === "ARCHIVE"
              ? "Segment archivé"
              : "Brouillon supprimé",
      );
      if (action === "DELETE_DRAFT") navigate("/admin/segments");
      else if (result) navigate(`/admin/segments/${result.summary.id}`);
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.code === "STALE_RESOURCE_VERSION") {
        setOpen(false);
        setReason("");
      }
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.segments.detail(segment.summary.id) });
      toast.error(segmentMutationMessage(error));
    },
  });
  const labels = {
    DUPLICATE: ["Dupliquer le segment", "Créer une copie indépendante"],
    REVISE: ["Créer une révision", "Créer un brouillon successeur"],
    ARCHIVE: ["Archiver le segment", "Retirer cette révision des usages futurs"],
    DELETE_DRAFT: ["Supprimer le brouillon", "Supprimer définitivement ce brouillon inutilisé"],
  } as const;
  const blockers = actionBlockers(segment.summary, action);
  return (
    <Dialog
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setReason("");
      }}
      open={open}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{labels[action][0]}</DialogTitle>
          <DialogDescription>{labels[action][1]}.</DialogDescription>
        </DialogHeader>
        {blockers.length ? (
          <Alert variant="destructive">
            <WarningCircleIcon />
            <AlertTitle>Action bloquée</AlertTitle>
            <AlertDescription>
              <ul className="list-disc ps-4">
                {blockers.map((blocker) => (
                  <li key={blocker}>{segmentBlocker[blocker]}</li>
                ))}
              </ul>
            </AlertDescription>
          </Alert>
        ) : null}
        {action === "DUPLICATE" ? (
          <div className="space-y-2">
            <Label htmlFor="segment-duplicate-name">Nom de la copie</Label>
            <Input
              id="segment-duplicate-name"
              maxLength={180}
              onChange={(event) => setName(event.target.value)}
              value={name}
            />
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor={`segment-${action}-reason`}>Motif obligatoire</Label>
          <Textarea
            id={`segment-${action}-reason`}
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            value={reason}
          />
        </div>
        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="outline">
            Annuler
          </Button>
          <Button
            disabled={
              Boolean(blockers.length) ||
              !reason.trim() ||
              (action === "DUPLICATE" && !name.trim()) ||
              mutation.isPending
            }
            onClick={() => mutation.mutate()}
            variant={action === "DELETE_DRAFT" ? "destructive" : "default"}
          >
            {mutation.isPending ? "Traitement…" : labels[action][0]}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function CommercialSegmentActivationDialog({ segment }: { segment: CommercialSegmentDetail }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const openRef = useRef(false);
  const [reason, setReason] = useState("");
  const [clock, setClock] = useState(() => Date.now());
  const queryKey = adminCommercialKeys.segments.preview(segment.summary.id, segment.summary.version);
  const preview = useQuery({
    queryKey,
    queryFn: () => adminApi.previewCommercialSegment(segment.summary.id),
    enabled: open && session.can(adminPermissions.segmentsPreview),
    gcTime: 0,
    staleTime: 0,
  });
  useEffect(
    () => () => {
      queryClient.removeQueries({
        queryKey: adminCommercialKeys.segments.preview(segment.summary.id, segment.summary.version),
        exact: true,
      });
    },
    [queryClient, segment.summary.id, segment.summary.version],
  );
  const expiresAt = preview.data?.expiresAt;
  useEffect(() => {
    if (!open || !expiresAt) return;
    const expires = Date.parse(expiresAt);
    if (!Number.isFinite(expires)) return;
    const timer = window.setTimeout(() => setClock(Date.now()), Math.max(0, expires - Date.now() + 1));
    return () => window.clearTimeout(timer);
  }, [expiresAt, open]);
  const changeOpen = (next: boolean) => {
    openRef.current = next;
    setOpen(next);
    if (next) setClock(Date.now());
    else {
      setReason("");
      queryClient.removeQueries({ queryKey, exact: true });
    }
  };
  const mutation = useMutation({
    mutationFn: () => {
      if (!reviewedSegmentActivationReady(segment, preview.data, Date.now())) {
        throw new Error("La vérification signée n’est plus actuelle.");
      }
      return adminApi.activateCommercialSegment(segment.summary.id, {
        version: preview.data?.criteriaVersion ?? segment.summary.version,
        reason: reason.trim(),
        previewToken: preview.data?.previewToken ?? "",
      });
    },
    onSuccess: async () => {
      changeOpen(false);
      await invalidateCommercialPolicyTargeting(queryClient);
      toast.success("Segment activé");
    },
    onError: async (error) => {
      queryClient.removeQueries({ queryKey, exact: true });
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.segments.detail(segment.summary.id) });
      if (openRef.current) {
        await preview.refetch();
        setClock(Date.now());
      }
      toast.error(segmentMutationMessage(error));
    },
  });
  const ready = reviewedSegmentActivationReady(segment, preview.data, clock);
  const previewExpiry = preview.data ? Date.parse(preview.data.expiresAt) : Number.NaN;
  const expired = Boolean(preview.data && (!Number.isFinite(previewExpiry) || previewExpiry <= clock));
  const refreshPreview = async () => {
    queryClient.removeQueries({ queryKey, exact: true });
    await preview.refetch();
    setClock(Date.now());
  };
  return (
    <Dialog onOpenChange={changeOpen} open={open}>
      <DialogTrigger asChild>
        <Button>
          <PlayIcon />
          Vérifier et activer
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Activer cette audience ?</DialogTitle>
          <DialogDescription>
            Une nouvelle vérification signée est calculée à chaque ouverture. Elle n’est jamais réutilisée après
            fermeture.
          </DialogDescription>
        </DialogHeader>
        {preview.isLoading || preview.isFetching ? (
          <LoadingState rows={3} />
        ) : preview.isError || !preview.data ? (
          <ErrorState retry={() => void refreshPreview()} />
        ) : (
          <div className="space-y-4">
            <dl className="grid gap-4 border-y py-4 sm:grid-cols-2">
              <div>
                <dt className="text-xs text-muted-foreground">Comptes</dt>
                <dd className="mt-1 text-xl font-semibold tabular-nums">{preview.data.totalAccounts}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Valide jusqu’au</dt>
                <dd className="mt-1 text-sm font-medium">{dateTime(preview.data.expiresAt)}</dd>
              </div>
            </dl>
            {!preview.data.activatable ? (
              <Alert variant="destructive">
                <WarningCircleIcon />
                <AlertTitle>Activation impossible</AlertTitle>
                <AlertDescription>
                  <ul className="list-disc ps-4">
                    {preview.data.blockers.length ? (
                      preview.data.blockers.map((blocker) => <li key={blocker}>{segmentBlocker[blocker]}</li>)
                    ) : (
                      <li>Le backend refuse cette activation sans fournir de blocage détaillé.</li>
                    )}
                  </ul>
                </AlertDescription>
              </Alert>
            ) : expired ? (
              <Alert>
                <WarningCircleIcon />
                <AlertTitle>Vérification expirée</AlertTitle>
                <AlertDescription>
                  <Button onClick={() => void refreshPreview()} size="sm" variant="outline">
                    Vérifier de nouveau
                  </Button>
                </AlertDescription>
              </Alert>
            ) : null}
            {preview.data.sample.length ? (
              <div>
                <p className="text-xs text-muted-foreground">Échantillon sans identité</p>
                <ul className="mt-2 space-y-1 font-mono text-xs">
                  {preview.data.sample.map((item) => (
                    <li className="break-all" dir="ltr" key={item.accountId}>
                      {item.accountId}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
            <div className="space-y-2">
              <Label htmlFor="segment-activation-reason">Motif obligatoire</Label>
              <Textarea
                id="segment-activation-reason"
                maxLength={500}
                onChange={(event) => setReason(event.target.value)}
                rows={3}
                value={reason}
              />
            </div>
          </div>
        )}
        <DialogFooter>
          <Button onClick={() => changeOpen(false)} variant="outline">
            Annuler
          </Button>
          <Button disabled={!ready || !reason.trim() || mutation.isPending} onClick={() => mutation.mutate()}>
            Activer
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function LifecycleOperations({ segment }: { segment: CommercialSegmentDetail }) {
  const session = useAdminSession();
  const has = (action: CommercialSegmentAction) => segment.summary.availableActions.includes(action);
  return (
    <section className="space-y-4 border-t pt-5">
      <h2 className="text-sm font-semibold">Opérations</h2>
      <div className="flex flex-wrap gap-2">
        {has("ACTIVATE") &&
        session.can(adminPermissions.segmentsPreview) &&
        session.can(adminPermissions.segmentsActivate) ? (
          <CommercialSegmentActivationDialog segment={segment} />
        ) : null}
        {has("REVISE") && session.can(adminPermissions.segmentsRevise) ? (
          <ReasonDialog
            action="REVISE"
            segment={segment}
            trigger={
              <Button variant="outline">
                <GitBranchIcon />
                Créer une révision
              </Button>
            }
          />
        ) : null}
        {has("DUPLICATE") && session.can(adminPermissions.segmentsDuplicate) ? (
          <ReasonDialog
            action="DUPLICATE"
            segment={segment}
            trigger={
              <Button variant="ghost">
                <CopyIcon />
                Dupliquer
              </Button>
            }
          />
        ) : null}
        {has("ARCHIVE") && session.can(adminPermissions.segmentsArchive) ? (
          <ReasonDialog
            action="ARCHIVE"
            segment={segment}
            trigger={
              <Button variant="ghost">
                <ArchiveIcon />
                Archiver
              </Button>
            }
          />
        ) : null}
        {has("DELETE_DRAFT") && session.can(adminPermissions.segmentsDeleteDraft) ? (
          <ReasonDialog
            action="DELETE_DRAFT"
            segment={segment}
            trigger={
              <Button variant="ghost">
                <TrashIcon />
                Supprimer le brouillon
              </Button>
            }
          />
        ) : null}
      </div>
    </section>
  );
}

function Definition({ segment }: { segment: CommercialSegmentDetail }) {
  const criteria = segment.definition.criteria;
  if (segment.summary.kind === "EXPLICIT_ACCOUNTS") {
    return (
      <section className="space-y-2 border-t pt-5">
        <h2 className="text-sm font-semibold">Comptes sélectionnés</h2>
        <p className="text-sm text-muted-foreground">
          {segment.definition.explicitAccountIds.length} identifiant(s) configuré(s). Les noms sont consultés sur la
          surface d’audience protégée.
        </p>
      </section>
    );
  }
  if (!criteria) return null;
  const rows: Array<[string, string]> = [
    [
      "Forfaits courants",
      criteria.currentPlanRevisionIds.length ? `${criteria.currentPlanRevisionIds.length} révision(s)` : "—",
    ],
    ["Statuts", criteria.subscriptionStatuses.map((value) => subscriptionStatusLabel[value]).join(", ") || "—"],
    ["Devises", criteria.currencyCodes.join(", ") || "—"],
    ["Cycles", criteria.billingCycles.map((value) => billingCycleLabel[value]).join(", ") || "—"],
    [
      "Création du compte",
      criteria.accountCreatedFrom || criteria.accountCreatedUntil
        ? `${dateTime(criteria.accountCreatedFrom)} → ${dateTime(criteria.accountCreatedUntil)}`
        : "—",
    ],
    ["Produits détenus", criteria.productHoldings.length ? `${criteria.productHoldings.length} exigence(s)` : "—"],
  ];
  return (
    <section className="space-y-4 border-t pt-5">
      <div>
        <h2 className="text-sm font-semibold">Critères</h2>
        <p className="mt-1 text-xs text-muted-foreground">{criteria.semantics}</p>
      </div>
      <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt className="text-xs text-muted-foreground">{label}</dt>
            <dd className="mt-1 text-sm font-medium">{value}</dd>
          </div>
        ))}
      </dl>
      {criteria.productHoldings.length ? (
        <ul className="divide-y rounded-lg border">
          {criteria.productHoldings.map((holding) => (
            <li className="flex justify-between gap-3 px-3 py-2 text-sm" key={`${holding.type}:${holding.code}`}>
              <span>{segmentProductType[holding.type]}</span>
              <span className="font-mono text-xs" dir="ltr">
                {holding.code}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

function Overview({ segment }: { segment: CommercialSegmentDetail }) {
  const session = useAdminSession();
  const blocked = Object.entries(segment.summary.blockedActions);
  return (
    <div className="space-y-6">
      <dl className="grid gap-x-8 gap-y-5 border-y py-5 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-xs text-muted-foreground">Définition</dt>
          <dd className="mt-1 font-medium">{segmentKind[segment.summary.kind]}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Révision</dt>
          <dd className="mt-1 font-medium tabular-nums">R{segment.summary.revisionNumber}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Dernière audience activée</dt>
          <dd className="mt-1 font-medium tabular-nums">{segment.summary.latestActivationAccountCount ?? "—"}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-xs text-muted-foreground">Motif métier</dt>
          <dd className="mt-1 font-medium">{segment.reason}</dd>
        </div>
      </dl>
      {segment.description ? (
        <p className="max-w-3xl text-sm leading-6 text-muted-foreground">{segment.description}</p>
      ) : null}
      {blocked.length ? (
        <section className="space-y-3">
          <h2 className="text-sm font-semibold">Contraintes actuelles</h2>
          <ul className="divide-y rounded-lg border">
            {blocked.map(([action, blockers]) => (
              <li className="grid gap-1 px-3 py-2 text-sm sm:grid-cols-[180px_1fr]" key={action}>
                <span className="font-medium">
                  {segmentActionLabel[action as CommercialSegmentAction] ?? readable(action)}
                </span>
                <span className="text-muted-foreground">
                  {blockers?.map((blocker) => segmentBlocker[blocker]).join(" ")}
                </span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      <Definition segment={segment} />
      {segment.summary.availableActions.includes("EDIT_DRAFT") && session.can(adminPermissions.segmentsUpdateDraft) ? (
        <Button asChild variant="outline">
          <Link to={`/admin/segments/${segment.summary.id}/edit`}>
            <PencilSimpleIcon />
            Modifier le brouillon
          </Link>
        </Button>
      ) : null}
      <LifecycleOperations segment={segment} />
    </div>
  );
}

function Audience({ segmentId, segment }: { segmentId: string; segment?: CommercialSegmentDetail }) {
  const session = useAdminSession();
  const version = segment?.summary.version ?? 0;
  const count = useQuery({
    queryKey: adminCommercialKeys.segments.count(segmentId, version),
    queryFn: () => adminApi.countCommercialSegment(segmentId),
    enabled: commercialQueryEnabled(session.can, adminPermissions.segmentsCount),
  });
  const preview = useMutation({ mutationFn: () => adminApi.previewCommercialSegment(segmentId) });
  const identities = useMutation({ mutationFn: () => adminApi.commercialSegmentIdentitySample(segmentId) });
  return (
    <div className="space-y-6">
      {session.can(adminPermissions.segmentsCount) ? (
        count.isLoading ? (
          <LoadingState rows={2} />
        ) : count.isError || !count.data ? (
          <ErrorState retry={() => void count.refetch()} />
        ) : (
          <dl className="grid gap-5 border-y py-5 sm:grid-cols-3">
            <div>
              <dt className="text-xs text-muted-foreground">Audience actuelle</dt>
              <dd className="mt-1 text-xl font-semibold tabular-nums">{count.data.totalAccounts}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Limite d’activation</dt>
              <dd className="mt-1 text-xl font-semibold tabular-nums">{count.data.activationAccountLimit}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Évaluée</dt>
              <dd className="mt-1 text-sm font-medium">{dateTime(count.data.evaluatedAt)}</dd>
            </div>
          </dl>
        )
      ) : null}
      <div className="flex flex-wrap gap-2">
        {session.can(adminPermissions.segmentsPreview) ? (
          <Button disabled={preview.isPending} onClick={() => preview.mutate()} variant="outline">
            {preview.isPending ? "Vérification…" : "Vérifier sans identités"}
          </Button>
        ) : null}
        {session.can(adminPermissions.segmentsReadSampleIdentities) ? (
          <Button disabled={identities.isPending} onClick={() => identities.mutate()} variant="ghost">
            {identities.isPending ? "Lecture…" : "Afficher l’échantillon identifié"}
          </Button>
        ) : null}
      </div>
      {preview.isError ? (
        <ErrorState retry={() => preview.mutate()} />
      ) : preview.data ? (
        <section className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-sm font-semibold">Vérification signée</h2>
            <span className="text-xs text-muted-foreground">expire {dateTime(preview.data.expiresAt)}</span>
          </div>
          {preview.data.blockers.length ? (
            <Alert variant="destructive">
              <WarningCircleIcon />
              <AlertTitle>Audience non activable</AlertTitle>
              <AlertDescription>
                <ul className="list-disc ps-4">
                  {preview.data.blockers.map((blocker) => (
                    <li key={blocker}>{segmentBlocker[blocker]}</li>
                  ))}
                </ul>
              </AlertDescription>
            </Alert>
          ) : null}
          <ul className="divide-y rounded-lg border font-mono text-xs">
            {preview.data.sample.map((item) => (
              <li className="break-all px-3 py-2" key={item.accountId}>
                {item.accountId}
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      {identities.isError ? (
        <ErrorState retry={() => identities.mutate()} />
      ) : identities.data ? (
        <section className="space-y-3">
          <h2 className="text-sm font-semibold">Échantillon identifié</h2>
          <ul className="divide-y rounded-lg border">
            {identities.data.sample.map((account) => (
              <li className="flex items-start justify-between gap-3 px-3 py-2" key={account.accountId}>
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium">{account.accountName ?? account.accountId}</span>
                  <span className="block truncate text-xs text-muted-foreground">
                    <span dir="ltr">
                      {account.accountSlug ?? account.accountId}
                      {account.ownerEmail ? ` · ${account.ownerEmail}` : " · Email indisponible"}
                    </span>
                  </span>
                </span>
                <span className="text-xs text-muted-foreground">{account.active ? "Actif" : "Inactif"}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      {!session.can(adminPermissions.segmentsCount) &&
      !session.can(adminPermissions.segmentsPreview) &&
      !session.can(adminPermissions.segmentsReadSampleIdentities) ? (
        <PermissionState />
      ) : null}
    </div>
  );
}

function Revisions({ segmentId }: { segmentId: string }) {
  const session = useAdminSession();
  const canReadRevisions = session.can(adminPermissions.segmentsReadRevisions);
  const canCompare = session.can(adminPermissions.segmentsCompare);
  const [params, setParams] = useSearchParams();
  const page = boundedPage(params.get("page"));
  const compared = validSegmentId(params.get("against"));
  const [compareCandidate, setCompareCandidate] = useState(compared);
  const chooseComparison = (candidateId: string) => {
    const normalized = validSegmentId(candidateId);
    if (!normalized || normalized === segmentId) return;
    setParams(withParam(params, "against", normalized), { replace: true });
  };
  const revisions = useQuery({
    queryKey: adminCommercialKeys.segments.revisions(segmentId, page),
    queryFn: () => adminApi.commercialSegmentRevisions(segmentId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.segmentsReadRevisions),
  });
  const comparison = useQuery({
    queryKey: adminCommercialKeys.segments.comparison(segmentId, compared),
    queryFn: () => adminApi.compareCommercialSegments(segmentId, compared),
    enabled: commercialQueryEnabled(session.can, adminPermissions.segmentsCompare, Boolean(compared)),
  });
  useEffect(() => setCompareCandidate(compared), [compared]);
  useEffect(() => {
    const normalized = revisions.data ? boundedResponsePage(page, revisions.data.totalPages) : page;
    const raw = params.get("page");
    let next = params;
    if ((raw && raw !== String(page)) || normalized !== page) next = withParam(next, "page", normalized);
    if (params.get("against") && !compared) next = withParam(next, "against", null);
    if (next !== params) setParams(next, { replace: true });
  }, [compared, page, params, revisions.data, setParams]);
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(300px,0.8fr)_1.2fr]">
      <section className="overflow-hidden rounded-xl border bg-card">
        {!canReadRevisions ? (
          <PermissionState />
        ) : revisions.isLoading ? (
          <LoadingState />
        ) : revisions.isError || !revisions.data ? (
          <ErrorState retry={() => void revisions.refetch()} />
        ) : (
          <>
            <ul className="divide-y">
              {revisions.data.content.map((revision) => {
                const status = segmentStatus[revision.status];
                return (
                  <li className="flex flex-wrap items-center gap-3 px-4 py-3" key={revision.id}>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-medium">Révision {revision.revisionNumber}</span>
                      <span className="block truncate text-xs text-muted-foreground">{revision.code}</span>
                    </span>
                    <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                    {canCompare && revision.id !== segmentId ? (
                      <Button onClick={() => chooseComparison(revision.id)} size="sm" variant="ghost">
                        Comparer
                      </Button>
                    ) : null}
                  </li>
                );
              })}
            </ul>
            <PaginationBar
              onPageChange={(next) => setParams(withParam(params, "page", next), { replace: true })}
              page={revisions.data.page}
              totalElements={revisions.data.totalElements}
              totalPages={revisions.data.totalPages}
            />
          </>
        )}
      </section>
      <section className="min-h-56 rounded-xl border bg-card p-5">
        {!canCompare ? (
          <PermissionState />
        ) : (
          <div className="space-y-5">
            <div className="space-y-2 border-b pb-5">
              <Label htmlFor="commercial-segment-compared-id">Révision à comparer</Label>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Input
                  className="font-mono"
                  dir="ltr"
                  id="commercial-segment-compared-id"
                  maxLength={36}
                  onChange={(event) => setCompareCandidate(event.target.value)}
                  placeholder={
                    canReadRevisions ? "Choisissez à gauche ou collez un identifiant" : "Collez un identifiant connu"
                  }
                  value={compareCandidate}
                />
                <Button
                  disabled={!validSegmentId(compareCandidate) || validSegmentId(compareCandidate) === segmentId}
                  onClick={() => chooseComparison(compareCandidate)}
                  variant="outline"
                >
                  Comparer
                </Button>
              </div>
            </div>
            {!compared ? (
              <EmptyState
                description="Choisissez une révision de la même lignée ou une copie indépendante."
                title="Aucune comparaison sélectionnée"
              />
            ) : comparison.isLoading ? (
              <LoadingState />
            ) : comparison.isError || !comparison.data ? (
              <ErrorState retry={() => void comparison.refetch()} />
            ) : (
              <>
                <div>
                  <h2 className="text-sm font-semibold">Changements détectés</h2>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {comparison.data.sameLineage ? "Même lignée" : "Lignées indépendantes"}
                    {comparison.data.directSuccessor ? " · succession directe" : ""}
                  </p>
                </div>
                {comparison.data.changedFields.length ? (
                  <ul className="divide-y rounded-lg border">
                    {comparison.data.changedFields.map((field) => (
                      <li className="px-3 py-2 text-sm" key={field}>
                        {changedFieldLabel[field] ?? readable(field)}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-muted-foreground">Aucune différence de définition.</p>
                )}
                <div className="grid gap-4 sm:grid-cols-2">
                  {[comparison.data.source, comparison.data.compared].map((item) => (
                    <article className="border-t pt-4" key={item.summary.id}>
                      <p className="font-medium">{item.summary.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {item.summary.code} · R{item.summary.revisionNumber}
                      </p>
                      <p className="mt-3 text-sm">{segmentKind[item.summary.kind]}</p>
                    </article>
                  ))}
                </div>
              </>
            )}
          </div>
        )}
      </section>
    </div>
  );
}

function Activations({ segmentId }: { segmentId: string }) {
  const session = useAdminSession();
  const canReadActivations = session.can(adminPermissions.segmentsReadActivations);
  const canReadOpaqueAudience = session.can(adminPermissions.segmentsReadActivationAudience);
  const canReadIdentities = session.can(adminPermissions.segmentsReadActivationIdentities);
  const [params, setParams] = useSearchParams();
  const page = boundedPage(params.get("page"));
  const selected = validSegmentId(params.get("activation"));
  const accountsPage = boundedPage(params.get("accountsPage"));
  const [activationCandidate, setActivationCandidate] = useState(selected);
  const [identified, setIdentified] = useState(false);
  const showIdentities = canReadIdentities && (identified || !canReadOpaqueAudience);
  const activations = useQuery({
    queryKey: adminCommercialKeys.segments.activations(segmentId, page),
    queryFn: () => adminApi.commercialSegmentActivations(segmentId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.segmentsReadActivations),
  });
  const audience = useQuery({
    queryKey: adminCommercialKeys.segments.activationAudience(segmentId, selected, accountsPage, showIdentities),
    queryFn: () =>
      showIdentities
        ? adminApi.commercialSegmentActivationIdentities(segmentId, selected, accountsPage)
        : adminApi.commercialSegmentActivationAudience(segmentId, selected, accountsPage),
    enabled: Boolean(selected) && (showIdentities ? canReadIdentities : canReadOpaqueAudience),
  });
  const chooseActivation = (activationId: string) => {
    const normalized = validSegmentId(activationId);
    if (!normalized) return;
    setIdentified(false);
    let next = withParam(params, "activation", normalized);
    next = withParam(next, "accountsPage", null);
    setParams(next, { replace: true });
  };
  useEffect(() => setActivationCandidate(selected), [selected]);
  useEffect(() => {
    let next = params;
    const rawPage = params.get("page");
    const rawAccountsPage = params.get("accountsPage");
    const finalPage = activations.data ? boundedResponsePage(page, activations.data.totalPages) : page;
    const finalAccountsPage = audience.data
      ? boundedResponsePage(accountsPage, audience.data.accounts.totalPages)
      : accountsPage;
    if ((rawPage && rawPage !== String(page)) || finalPage !== page) {
      next = withParam(next, "page", finalPage);
    }
    if ((rawAccountsPage && rawAccountsPage !== String(accountsPage)) || finalAccountsPage !== accountsPage) {
      next = withParam(next, "accountsPage", finalAccountsPage);
    }
    if (!selected) {
      if (params.get("activation")) next = withParam(next, "activation", null);
      if (params.get("accountsPage")) next = withParam(next, "accountsPage", null);
    }
    if (next !== params) setParams(next, { replace: true });
  }, [accountsPage, activations.data, audience.data, page, params, selected, setParams]);
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(340px,0.9fr)_1.1fr]">
      <section className="overflow-hidden rounded-xl border bg-card">
        {!canReadActivations ? (
          <PermissionState />
        ) : activations.isLoading ? (
          <LoadingState />
        ) : activations.isError || !activations.data ? (
          <ErrorState retry={() => void activations.refetch()} />
        ) : (
          <>
            <ol className="divide-y">
              {activations.data.content.map((activation) => (
                <li className="space-y-2 p-4" key={activation.id}>
                  <div className="flex flex-col items-start justify-between gap-3 sm:flex-row">
                    <div>
                      <p className="text-sm font-medium">
                        Activation {activation.activationNumber} · {activation.affectedAccountCount} compte(s)
                      </p>
                      <time className="mt-1 block text-xs text-muted-foreground" dateTime={activation.recordedAt}>
                        {dateTime(activation.recordedAt)}
                      </time>
                    </div>
                    {canReadOpaqueAudience || canReadIdentities ? (
                      <Button onClick={() => chooseActivation(activation.id)} size="sm" variant="ghost">
                        Voir l’audience figée
                      </Button>
                    ) : null}
                  </div>
                  <p className="text-sm">{activation.reason}</p>
                </li>
              ))}
            </ol>
            {!activations.data.content.length ? <EmptyState title="Aucune activation" /> : null}
            <PaginationBar
              onPageChange={(next) => setParams(withParam(params, "page", next), { replace: true })}
              page={activations.data.page}
              totalElements={activations.data.totalElements}
              totalPages={activations.data.totalPages}
            />
          </>
        )}
      </section>
      <section className="min-h-56 overflow-hidden rounded-xl border bg-card">
        {!canReadOpaqueAudience && !canReadIdentities ? (
          <PermissionState />
        ) : (
          <div>
            <div className="space-y-3 border-b p-4">
              <Label htmlFor="commercial-segment-activation-id">Activation à examiner</Label>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Input
                  className="font-mono"
                  dir="ltr"
                  id="commercial-segment-activation-id"
                  maxLength={36}
                  onChange={(event) => setActivationCandidate(event.target.value)}
                  placeholder={
                    canReadActivations ? "Choisissez à gauche ou collez un identifiant" : "Collez un identifiant connu"
                  }
                  value={activationCandidate}
                />
                <Button
                  disabled={!validSegmentId(activationCandidate)}
                  onClick={() => chooseActivation(activationCandidate)}
                  variant="outline"
                >
                  Examiner
                </Button>
              </div>
              {canReadOpaqueAudience && canReadIdentities && selected ? (
                <Button
                  onClick={() => {
                    setIdentified((value) => !value);
                    setParams(withParam(params, "accountsPage", null), { replace: true });
                  }}
                  size="sm"
                  variant="ghost"
                >
                  {showIdentities ? "Masquer les identités" : "Afficher les identités"}
                </Button>
              ) : null}
            </div>
            {!selected ? (
              <EmptyState
                description="Une activation conserve la liste exacte des comptes examinés."
                title="Choisissez une activation"
              />
            ) : audience.isLoading ? (
              <div className="p-4">
                <LoadingState rows={3} />
              </div>
            ) : audience.isError || !audience.data ? (
              <ErrorState retry={() => void audience.refetch()} />
            ) : (
              <>
                <div className="border-b p-4">
                  <h2 className="text-sm font-semibold">
                    Audience figée · activation {audience.data.activationNumber}
                  </h2>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {audience.data.immutableAccountCount} compte(s) enregistrés
                  </p>
                </div>
                <ul className="divide-y">
                  {audience.data.accounts.content.map((item) => {
                    const identity = isAudienceIdentity(item) ? item : null;
                    return (
                      <li className="px-4 py-3" key={item.accountId}>
                        {identity ? (
                          <span>
                            <span className="block text-sm font-medium">{identity.accountName ?? item.accountId}</span>
                            <span className="block text-xs text-muted-foreground" dir="ltr">
                              {identity.accountSlug ?? item.accountId}
                              {identity.ownerEmail ? ` · ${identity.ownerEmail}` : " · Email indisponible"}
                            </span>
                          </span>
                        ) : (
                          <span className="block break-all font-mono text-xs" dir="ltr">
                            {item.accountId}
                          </span>
                        )}
                      </li>
                    );
                  })}
                </ul>
                <PaginationBar
                  onPageChange={(next) => setParams(withParam(params, "accountsPage", next), { replace: true })}
                  page={audience.data.accounts.page}
                  totalElements={audience.data.accounts.totalElements}
                  totalPages={audience.data.accounts.totalPages}
                />
              </>
            )}
          </div>
        )}
      </section>
    </div>
  );
}

function History({ segmentId }: { segmentId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = boundedPage(params.get("page"));
  const history = useQuery({
    queryKey: adminCommercialKeys.segments.history(segmentId, page),
    queryFn: () => adminApi.commercialSegmentHistory(segmentId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.segmentsReadHistory),
  });
  useEffect(() => {
    if (!history.data) return;
    const normalized = boundedResponsePage(page, history.data.totalPages);
    const raw = params.get("page");
    if ((raw && raw !== String(page)) || normalized !== page) {
      setParams(withParam(params, "page", normalized), { replace: true });
    }
  }, [history.data, page, params, setParams]);
  if (!session.can(adminPermissions.segmentsReadHistory)) return <PermissionState />;
  if (history.isLoading) return <LoadingState />;
  if (history.isError || !history.data) return <ErrorState retry={() => void history.refetch()} />;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <ul className="divide-y">
        {history.data.content.map((entry) => (
          <li className="grid gap-1 px-4 py-3 sm:grid-cols-[1fr_auto]" key={entry.id}>
            <span>
              <span className="block text-sm font-medium">{segmentHistoryAction(entry.action)}</span>
              <span className="block text-xs text-muted-foreground">
                {entry.actorEmail ? `par ${entry.actorEmail}` : "acteur système"}
                {entry.reason ? ` · ${entry.reason}` : ""}
              </span>
            </span>
            <span className={`text-xs ${entry.outcome === "FAILED" ? "text-destructive" : "text-muted-foreground"}`}>
              {entry.outcome === "FAILED" ? "Échec" : "Réussi"} · {dateTime(entry.occurredAt)}
            </span>
          </li>
        ))}
      </ul>
      {!history.data.content.length ? <EmptyState title="Aucun événement" /> : null}
      <PaginationBar
        onPageChange={(next) => setParams(withParam(params, "page", next), { replace: true })}
        page={history.data.page}
        totalElements={history.data.totalElements}
        totalPages={history.data.totalPages}
      />
    </section>
  );
}

function ReassignOwnerDialog({ segment }: { segment: CommercialSegmentDetail }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [ownerId, setOwnerId] = useState("");
  const [reason, setReason] = useState("");
  const changeOpen = (next: boolean) => {
    setOpen(next);
    if (!next) {
      setSearch("");
      setOwnerId("");
      setReason("");
    }
  };
  const operators = useQuery({
    queryKey: ["admin", "users", "segment-owner", debounced],
    queryFn: () => adminApi.users({ search: debounced || undefined, active: true, size: 20 }),
    enabled: open && session.can(adminPermissions.usersRead),
  });
  const mutation = useMutation({
    mutationFn: () =>
      adminApi.reassignCommercialSegmentOwner(segment.summary.id, {
        version: segment.summary.version,
        ownerAdminUserId: ownerId,
        reason,
      }),
    onSuccess: async () => {
      await invalidateAdminCommercial(queryClient, adminCommercialKeys.segments.all());
      changeOpen(false);
      toast.success("Responsable réassigné");
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.code === "STALE_RESOURCE_VERSION") changeOpen(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: adminCommercialKeys.segments.detail(segment.summary.id) }),
        queryClient.invalidateQueries({ queryKey: adminCommercialKeys.segments.owner(segment.summary.id) }),
      ]);
      toast.error(segmentMutationMessage(error));
    },
  });
  return (
    <Dialog onOpenChange={changeOpen} open={open}>
      <DialogTrigger asChild>
        <Button variant="outline">Réassigner le responsable</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Réassigner le responsable</DialogTitle>
          <DialogDescription>La réassignation conserve l’historique et exige un motif.</DialogDescription>
        </DialogHeader>
        {session.can(adminPermissions.usersRead) ? (
          <div className="space-y-2">
            <Label htmlFor="segment-owner-search">Nouvel opérateur actif</Label>
            <Input
              id="segment-owner-search"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Rechercher…"
              value={search}
            />
            {operators.isError ? (
              <Alert variant="destructive">
                <WarningCircleIcon />
                <AlertTitle>Opérateurs indisponibles</AlertTitle>
                <AlertDescription>
                  <Button onClick={() => void operators.refetch()} size="sm" variant="outline">
                    Réessayer
                  </Button>
                </AlertDescription>
              </Alert>
            ) : (
              <Select disabled={operators.isLoading} onValueChange={setOwnerId} value={ownerId || undefined}>
                <SelectTrigger>
                  <SelectValue placeholder={operators.isLoading ? "Chargement…" : "Choisir un opérateur"} />
                </SelectTrigger>
                <SelectContent>
                  {operators.data?.content.map((operator) => (
                    <SelectItem key={operator.id} value={operator.id}>
                      {operator.email}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>
        ) : (
          <div className="space-y-2">
            <Label htmlFor="segment-owner-id">Identifiant administrateur</Label>
            <Input
              className="font-mono"
              dir="ltr"
              id="segment-owner-id"
              maxLength={36}
              onChange={(event) => setOwnerId(event.target.value)}
              value={ownerId}
            />
          </div>
        )}
        <div className="space-y-2">
          <Label htmlFor="segment-owner-reason">Motif obligatoire</Label>
          <Textarea
            id="segment-owner-reason"
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            value={reason}
          />
        </div>
        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="outline">
            Annuler
          </Button>
          <Button
            disabled={!validSegmentId(ownerId) || !reason.trim() || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            Réassigner
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Owner({ segmentId, segment }: { segmentId: string; segment?: CommercialSegmentDetail }) {
  const session = useAdminSession();
  const owner = useQuery({
    queryKey: adminCommercialKeys.segments.owner(segmentId),
    queryFn: () => adminApi.commercialSegmentOwner(segmentId),
    enabled: commercialQueryEnabled(session.can, adminPermissions.segmentsReadOwner),
  });
  if (!session.can(adminPermissions.segmentsReadOwner)) return <PermissionState />;
  if (owner.isLoading) return <LoadingState />;
  if (owner.isError || !owner.data) return <ErrorState retry={() => void owner.refetch()} />;
  return (
    <section className="space-y-5">
      <dl className="grid gap-5 border-y py-5 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-xs text-muted-foreground">Nom</dt>
          <dd className="mt-1 font-medium">{owner.data.displayName ?? owner.data.username}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Email</dt>
          <dd className="mt-1 font-medium" dir="ltr">
            {owner.data.email}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Identifiant</dt>
          <dd className="mt-1 break-all font-mono text-xs">{owner.data.adminUserId}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">État</dt>
          <dd className="mt-1 font-medium">{owner.data.active ? "Actif" : "Inactif"}</dd>
        </div>
      </dl>
      {segment?.summary.availableActions.includes("REASSIGN_OWNER") &&
      session.can(adminPermissions.segmentsReassignOwner) ? (
        <ReassignOwnerDialog segment={segment} />
      ) : null}
    </section>
  );
}

function standalone(tab: string | undefined, id: string) {
  if (tab === "audience") return <Audience segmentId={id} />;
  if (tab === "revisions") return <Revisions segmentId={id} />;
  if (tab === "activations") return <Activations segmentId={id} />;
  if (tab === "history") return <History segmentId={id} />;
  if (tab === "owner") return <Owner segmentId={id} />;
  return null;
}

export function AdminCommercialSegmentDetailPage() {
  const session = useAdminSession();
  const { segmentId, tab } = useParams();
  const id = validSegmentId(segmentId);
  const canRead = session.can(adminPermissions.segmentsRead);
  const segment = useQuery({
    queryKey: adminCommercialKeys.segments.detail(id),
    queryFn: () => adminApi.commercialSegment(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.segmentsRead, Boolean(id)),
  });
  if (!id)
    return (
      <ErrorState
        description="L’identifiant présent dans l’adresse est invalide."
        title="Adresse de segment invalide"
      />
    );
  if (!canRead) {
    const surface = standalone(tab, id);
    if (!surface) return <Navigate replace to="/admin" />;
    return (
      <div className="space-y-6">
        <Button asChild className="-ms-2" size="sm" variant="ghost">
          <Link to={session.can(adminPermissions.segmentsList) ? "/admin/segments" : "/admin"}>
            <ArrowLeftIcon className="rtl:rotate-180" />
            {session.can(adminPermissions.segmentsList) ? "Segments" : "Administration"}
          </Link>
        </Button>
        <PageHeader title="Segment de comptes" />
        {surface}
      </div>
    );
  }
  if (segment.isLoading) return <LoadingState />;
  if (segment.isError || !segment.data)
    return <ErrorState retry={() => void segment.refetch()} title="Segment introuvable" />;
  const data = segment.data;
  const status = segmentStatus[data.summary.status];
  const tabs = [
    { label: "Synthèse", to: `/admin/segments/${id}`, end: true },
    ...(session.can(adminPermissions.segmentsCount) ||
    session.can(adminPermissions.segmentsPreview) ||
    session.can(adminPermissions.segmentsReadSampleIdentities)
      ? [{ label: "Audience", to: `/admin/segments/${id}/audience` }]
      : []),
    ...(session.can(adminPermissions.segmentsReadRevisions) || session.can(adminPermissions.segmentsCompare)
      ? [{ label: "Révisions", to: `/admin/segments/${id}/revisions` }]
      : []),
    ...(session.can(adminPermissions.segmentsReadActivations) ||
    session.can(adminPermissions.segmentsReadActivationAudience) ||
    session.can(adminPermissions.segmentsReadActivationIdentities)
      ? [{ label: "Activations", to: `/admin/segments/${id}/activations` }]
      : []),
    ...(session.can(adminPermissions.segmentsReadHistory)
      ? [{ label: "Historique", to: `/admin/segments/${id}/history` }]
      : []),
    ...(session.can(adminPermissions.segmentsReadOwner)
      ? [{ label: "Responsable", to: `/admin/segments/${id}/owner` }]
      : []),
  ];
  const validTabs = new Set([undefined, "audience", "revisions", "activations", "history", "owner"]);
  if (!validTabs.has(tab)) return <Navigate replace to={`/admin/segments/${id}`} />;
  return (
    <div className="space-y-6">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to={session.can(adminPermissions.segmentsList) ? "/admin/segments" : "/admin"}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          {session.can(adminPermissions.segmentsList) ? "Segments" : "Administration"}
        </Link>
      </Button>
      <PageHeader
        actions={
          data.summary.availableActions.includes("EDIT_DRAFT") && session.can(adminPermissions.segmentsUpdateDraft) ? (
            <Button asChild variant="ghost">
              <Link to={`/admin/segments/${id}/edit`}>
                <PencilSimpleIcon />
                Modifier
              </Link>
            </Button>
          ) : undefined
        }
        description={
          <span className="flex items-center gap-3">
            <span dir="ltr">
              {data.summary.code} · R{data.summary.revisionNumber}
            </span>
            <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          </span>
        }
        title={data.summary.name}
      />
      <SectionTabs ariaLabel="Sections du segment" tabs={tabs} />
      {!tab ? (
        <Overview segment={data} />
      ) : tab === "audience" ? (
        <Audience segment={data} segmentId={id} />
      ) : tab === "revisions" ? (
        <Revisions segmentId={id} />
      ) : tab === "activations" ? (
        <Activations segmentId={id} />
      ) : tab === "history" ? (
        <History segmentId={id} />
      ) : (
        <Owner segment={data} segmentId={id} />
      )}
    </div>
  );
}
