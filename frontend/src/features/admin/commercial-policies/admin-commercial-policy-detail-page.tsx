import {
  ArchiveIcon,
  ArrowLeftIcon,
  CopyIcon,
  GitBranchIcon,
  PauseIcon,
  PencilSimpleIcon,
  PlayIcon,
  StopIcon,
  TrashIcon,
  WarningCircleIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { Link, Navigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  CommercialPolicyAction,
  CommercialPolicyDetail,
  CommercialPolicyEffect,
  CommercialPolicyHistory,
} from "@/api/contracts";
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
} from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { CommercialPolicyActivationDialog, CommercialPolicyReasonDialog } from "./commercial-policy-dialogs";
import {
  effectLabel,
  executionBlocker,
  policyBlocker,
  policyMutationMessage,
  policySource,
  policyStatus,
  policyTarget,
} from "./commercial-policy-rules";

function dateTime(value: string | null) {
  return value
    ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
    : "Sans fin";
}

function readableAction(action: string) {
  const key = (action.split(".").at(-1) ?? action).replaceAll("-", "_").toUpperCase();
  const labels: Record<string, string> = {
    CREATE: "Brouillon créé",
    UPDATE: "Brouillon modifié",
    UPDATE_DRAFT: "Brouillon modifié",
    DUPLICATE: "Copie indépendante créée",
    REVISE: "Révision créée",
    ACTIVATE: "Définition activée",
    RESUME: "Définition reprise",
    PAUSE: "Définition suspendue",
    END: "Définition terminée",
    ARCHIVE: "Définition archivée",
    DELETE: "Brouillon supprimé",
    DELETE_DRAFT: "Brouillon supprimé",
    REASSIGN_OWNER: "Responsable réassigné",
  };
  return labels[key] ?? key.replaceAll("_", " ").toLocaleLowerCase("fr");
}

function effectValue(effect: CommercialPolicyEffect) {
  if (effect.type === "FIXED_SUBSCRIPTION_PRICE")
    return `${effect.amount} ${effect.currencyCode} · ${effect.billingCycle === "YEARLY" ? "annuel" : "mensuel"}`;
  if (effect.type === "FIXED_DISCOUNT") return `−${effect.amount} ${effect.currencyCode}`;
  if (effect.type === "PERCENTAGE_DISCOUNT")
    return `−${effect.percentage}% · plafond ${effect.maximumAmount} ${effect.maximumCurrencyCode}`;
  if (effect.type === "ADDITIVE_QUOTA_BONUS")
    return `${effect.featureCode} · +${effect.quantityDelta} ${effect.quotaResource}`;
  if (effect.type === "BLOCK_FEATURE") return effect.featureCode ?? "Fonctionnalité manquante";
  return `${effect.productType ? { PLAN: "Forfait", ADD_ON: "Add-on", QUOTA_PACKAGE: "Pack de capacité" }[effect.productType] : "Produit"} · ${effect.productCode ?? effect.productRevisionId ?? "révision manquante"}`;
}

function TargetDefinition({ policy }: { policy: CommercialPolicyDetail }) {
  const target = policy.target;
  return (
    <section className="border-y py-5">
      <h2 className="text-sm font-semibold">Audience définie</h2>
      <dl className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div>
          <dt className="text-xs text-muted-foreground">Type</dt>
          <dd className="mt-1 font-medium">{policyTarget[target.kind]}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Cible</dt>
          <dd className="mt-1 font-medium">
            {target.kind === "ACCOUNT"
              ? (target.accountName ?? target.accountId)
              : target.kind === "ACCOUNT_SET"
                ? `${target.accountIds.length} compte(s)`
                : target.kind === "PLAN_REVISION_SUBSCRIBERS"
                  ? `${target.planName ?? target.planCode} · ${target.planCode}`
                  : target.segmentReference}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Dernière audience activée</dt>
          <dd className="mt-1 font-medium tabular-nums">
            {policy.summary.latestAffectedAccountCount ?? "Jamais activée"}
          </dd>
        </div>
      </dl>
    </section>
  );
}

function PolicyEffects({ policy }: { policy: CommercialPolicyDetail }) {
  return (
    <section>
      <h2 className="text-sm font-semibold">Effets typés</h2>
      <ol className="mt-3 divide-y rounded-xl border bg-card">
        {policy.effects.map((effect) => (
          <li
            className="grid gap-2 px-4 py-3 sm:grid-cols-[40px_minmax(180px,0.8fr)_1.2fr_auto] sm:items-center"
            key={effect.id}
          >
            <span className="text-xs text-muted-foreground tabular-nums">{effect.order + 1}</span>
            <span className="text-sm font-medium">{effectLabel[effect.type]}</span>
            <span className="text-sm text-muted-foreground">{effectValue(effect)}</span>
            <span className="text-xs text-muted-foreground tabular-nums">Classe {effect.precedenceClass}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}

function LifecycleOperations({ policy }: { policy: CommercialPolicyDetail }) {
  const has = (action: CommercialPolicyAction) => policy.summary.availableActions.includes(action);
  return (
    <section className="space-y-4 border-t pt-5">
      <div>
        <h2 className="text-sm font-semibold">Opérations</h2>
        <p className="mt-1 text-xs text-muted-foreground">
          Les actions proposées viennent du backend et tiennent compte du cycle de vie et de vos permissions.
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        {has("ACTIVATE") || has("RESUME") ? (
          <CommercialPolicyActivationDialog
            policy={policy}
            trigger={
              <Button>
                <PlayIcon />
                {has("RESUME") ? "Vérifier et reprendre" : "Vérifier et activer"}
              </Button>
            }
          />
        ) : null}
        {has("PAUSE") ? (
          <CommercialPolicyReasonDialog
            action="PAUSE"
            policy={policy}
            trigger={
              <Button variant="outline">
                <PauseIcon />
                Suspendre
              </Button>
            }
          />
        ) : null}
        {has("REVISE") ? (
          <CommercialPolicyReasonDialog
            action="REVISE"
            policy={policy}
            trigger={
              <Button variant="outline">
                <GitBranchIcon />
                Créer une révision
              </Button>
            }
          />
        ) : null}
        {has("DUPLICATE") ? (
          <CommercialPolicyReasonDialog
            action="DUPLICATE"
            policy={policy}
            trigger={
              <Button variant="ghost">
                <CopyIcon />
                Dupliquer
              </Button>
            }
          />
        ) : null}
        {has("END") ? (
          <CommercialPolicyReasonDialog
            action="END"
            policy={policy}
            trigger={
              <Button variant="destructive">
                <StopIcon />
                Terminer
              </Button>
            }
          />
        ) : null}
        {has("ARCHIVE") ? (
          <CommercialPolicyReasonDialog
            action="ARCHIVE"
            policy={policy}
            trigger={
              <Button variant="ghost">
                <ArchiveIcon />
                Archiver
              </Button>
            }
          />
        ) : null}
        {has("DELETE_DRAFT") ? (
          <CommercialPolicyReasonDialog
            action="DELETE_DRAFT"
            policy={policy}
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

function Overview({ policy }: { policy: CommercialPolicyDetail }) {
  return (
    <div className="space-y-6">
      {policy.summary.blockers.length ? (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>Blocages de la définition</AlertTitle>
          <AlertDescription>
            <ul className="list-disc ps-4">
              {policy.summary.blockers.map((blocker) => (
                <li key={blocker}>{policyBlocker[blocker]}</li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      ) : null}
      {!policy.summary.executionSupported ? (
        <Alert className="border-warning/30 bg-warning/5">
          <WarningCircleIcon />
          <AlertTitle>Exécution abonnements indisponible</AlertTitle>
          <AlertDescription>
            <ul className="list-disc ps-4">
              {policy.summary.executionBlockers.map((blocker) => (
                <li key={blocker}>{executionBlocker[blocker]}</li>
              ))}
            </ul>
            <p>
              Une activation enregistre la définition et sa preuve ; elle ne prétend pas avoir appliqué ces effets aux
              abonnements.
            </p>
          </AlertDescription>
        </Alert>
      ) : null}
      <dl className="grid gap-x-8 gap-y-5 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-xs text-muted-foreground">Origine</dt>
          <dd className="mt-1 font-medium">{policySource[policy.summary.source]}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Priorité</dt>
          <dd className="mt-1 font-medium tabular-nums">{policy.summary.priority}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Début</dt>
          <dd className="mt-1 font-medium">{dateTime(policy.summary.effectiveFrom)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Fin</dt>
          <dd className="mt-1 font-medium">{dateTime(policy.summary.effectiveUntil)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Référence d’approbation</dt>
          <dd className="mt-1 font-medium">{policy.approvalReference ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Référence contractuelle</dt>
          <dd className="mt-1 font-medium">{policy.contractReference ?? "—"}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-xs text-muted-foreground">Motif métier</dt>
          <dd className="mt-1 font-medium">{policy.reason}</dd>
        </div>
      </dl>
      {policy.description ? (
        <p className="max-w-3xl text-sm leading-6 text-muted-foreground">{policy.description}</p>
      ) : null}
      <TargetDefinition policy={policy} />
      <PolicyEffects policy={policy} />
      <LifecycleOperations policy={policy} />
    </div>
  );
}

function Audience({ policyId }: { policyId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const audience = useQuery({
    queryKey: adminCommercialKeys.policies.audience(policyId, page),
    queryFn: () => adminApi.commercialPolicyAudience(policyId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesPreviewAudience),
  });
  if (!session.can(adminPermissions.commercialPoliciesPreviewAudience)) return <PermissionState />;
  if (audience.isLoading) return <LoadingState />;
  if (audience.isError || !audience.data) return <ErrorState retry={() => void audience.refetch()} />;
  return (
    <div className="space-y-5">
      <div className="grid gap-5 border-y py-5 sm:grid-cols-3">
        <div>
          <p className="text-xs text-muted-foreground">Audience actuelle</p>
          <p className="mt-1 text-xl font-semibold tabular-nums">{audience.data.totalAccounts}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Limite d’activation</p>
          <p className="mt-1 text-xl font-semibold tabular-nums">{audience.data.activationAccountLimit}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Version vérifiée</p>
          <p className="mt-1 text-xl font-semibold tabular-nums">R{audience.data.expectedVersion}</p>
        </div>
      </div>
      {audience.data.blockers.length ? (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>Audience non activable</AlertTitle>
          <AlertDescription>
            <ul className="list-disc ps-4">
              {audience.data.blockers.map((blocker) => (
                <li key={blocker}>{policyBlocker[blocker]}</li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      ) : null}
      <section className="overflow-hidden rounded-xl border bg-card">
        <ul className="divide-y">
          {audience.data.accounts.content.map((account) => (
            <li className="flex items-center justify-between gap-3 px-4 py-3" key={account.id}>
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium">{account.name ?? account.id}</span>
                <span className="block truncate text-xs text-muted-foreground">{account.slug ?? account.id}</span>
              </span>
              <span className="text-xs text-muted-foreground">{account.active ? "Actif" : "Inactif"}</span>
            </li>
          ))}
        </ul>
        {!audience.data.accounts.content.length ? (
          <EmptyState
            description="La définition reste en brouillon ou nécessite une cible résoluble."
            title="Aucun compte résolu"
          />
        ) : null}
        <PaginationBar
          onPageChange={(next) => setParams(next ? { page: String(next) } : {}, { replace: true })}
          page={audience.data.accounts.page}
          totalElements={audience.data.accounts.totalElements}
          totalPages={audience.data.accounts.totalPages}
        />
      </section>
    </div>
  );
}

const changedFieldLabel: Record<string, string> = {
  NAME: "Nom",
  DESCRIPTION: "Description",
  EFFECTIVE_WINDOW: "Période",
  SOURCE: "Origine",
  PRIORITY: "Priorité",
  REASON: "Motif",
  APPROVAL_REFERENCE: "Approbation",
  CONTRACT_REFERENCE: "Contrat",
  TARGET: "Audience",
  EFFECTS: "Effets",
  OWNER: "Responsable",
};

function Revisions({ policyId }: { policyId: string }) {
  const session = useAdminSession();
  const canReadRevisions = session.can(adminPermissions.commercialPoliciesReadRevisions);
  const canCompare = session.can(adminPermissions.commercialPoliciesCompare);
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const compared = params.get("against") ?? "";
  const [compareCandidate, setCompareCandidate] = useState(compared);
  const chooseComparison = (candidateId: string) => {
    setCompareCandidate(candidateId);
    const next = new URLSearchParams(params);
    next.set("against", candidateId);
    setParams(next, { replace: true });
  };
  const revisions = useQuery({
    queryKey: adminCommercialKeys.policies.revisions(policyId, page),
    queryFn: () => adminApi.commercialPolicyRevisions(policyId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesReadRevisions),
  });
  const comparison = useQuery({
    queryKey: adminCommercialKeys.policies.comparison(policyId, compared),
    queryFn: () => adminApi.compareCommercialPolicies(policyId, compared),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesCompare, Boolean(compared)),
  });
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(320px,0.8fr)_1.2fr]">
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
                const status = policyStatus[revision.status];
                return (
                  <li className="flex items-center gap-3 px-4 py-3" key={revision.id}>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium">Révision {revision.revisionNumber}</p>
                      <p className="truncate text-xs text-muted-foreground">{revision.code}</p>
                    </div>
                    <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                    {canCompare && revision.id !== policyId ? (
                      <Button onClick={() => chooseComparison(revision.id)} size="sm" variant="ghost">
                        Comparer
                      </Button>
                    ) : null}
                  </li>
                );
              })}
            </ul>
            <PaginationBar
              onPageChange={(next) => {
                const value = new URLSearchParams(params);
                next ? value.set("page", String(next)) : value.delete("page");
                setParams(value, { replace: true });
              }}
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
              <Label htmlFor="commercial-policy-compared-id">Révision à comparer</Label>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Input
                  id="commercial-policy-compared-id"
                  onChange={(event) => setCompareCandidate(event.target.value)}
                  placeholder="Choisissez dans la lignée ou collez un identifiant connu"
                  value={compareCandidate}
                />
                <Button
                  disabled={!/^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(compareCandidate) || compareCandidate === policyId}
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
                    {comparison.data.directSuccessor ? " · Succession directe" : ""}
                  </p>
                </div>
                {comparison.data.changedFields.length ? (
                  <ul className="divide-y rounded-lg border">
                    {comparison.data.changedFields.map((field) => (
                      <li className="px-3 py-2 text-sm" key={field}>
                        {changedFieldLabel[field] ?? readableAction(field)}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-muted-foreground">Aucune différence de définition.</p>
                )}
                <div className="grid gap-4 border-t pt-5 sm:grid-cols-2">
                  {[comparison.data.source, comparison.data.compared].map((candidate, index) => (
                    <article className="space-y-3 rounded-lg border p-4" key={candidate.summary.id}>
                      <div>
                        <p className="text-xs text-muted-foreground">
                          {index === 0 ? "Révision ouverte" : "Révision comparée"}
                        </p>
                        <h3 className="mt-1 font-semibold">{candidate.summary.name}</h3>
                        <p className="text-xs text-muted-foreground">
                          {candidate.summary.code} · R{candidate.summary.revisionNumber}
                        </p>
                      </div>
                      <dl className="grid gap-2 text-sm">
                        <div className="flex justify-between gap-3">
                          <dt className="text-muted-foreground">Audience</dt>
                          <dd className="text-end font-medium">{policyTarget[candidate.target.kind]}</dd>
                        </div>
                        <div className="flex justify-between gap-3">
                          <dt className="text-muted-foreground">Origine</dt>
                          <dd className="text-end font-medium">{policySource[candidate.summary.source]}</dd>
                        </div>
                        <div className="flex justify-between gap-3">
                          <dt className="text-muted-foreground">Priorité</dt>
                          <dd className="text-end font-medium tabular-nums">{candidate.summary.priority}</dd>
                        </div>
                        <div className="flex justify-between gap-3">
                          <dt className="text-muted-foreground">Effets</dt>
                          <dd className="text-end font-medium tabular-nums">{candidate.effects.length}</dd>
                        </div>
                      </dl>
                      <ol className="divide-y border-t text-xs">
                        {candidate.effects.map((effect) => (
                          <li className="py-2" key={effect.id}>
                            <span className="font-medium">{effectLabel[effect.type]}</span>
                            <span className="block text-muted-foreground">{effectValue(effect)}</span>
                          </li>
                        ))}
                      </ol>
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

function HistoryList({ policyId }: { policyId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const history = useQuery({
    queryKey: adminCommercialKeys.policies.history(policyId, page),
    queryFn: () => adminApi.commercialPolicyHistory(policyId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesReadHistory),
  });
  if (!session.can(adminPermissions.commercialPoliciesReadHistory)) return <PermissionState />;
  if (history.isLoading) return <LoadingState />;
  if (history.isError || !history.data) return <ErrorState retry={() => void history.refetch()} />;
  if (!history.data.totalElements) return <EmptyState title="Aucun événement" />;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <ol className="divide-y">
        {history.data.content.map((entry: CommercialPolicyHistory) => (
          <li className="grid gap-2 p-4 sm:grid-cols-[1fr_auto]" key={entry.id}>
            <div>
              <p className="text-sm font-medium">{readableAction(entry.action)}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                {entry.outcome === "SUCCEEDED" ? "Réussi" : "Échec"} · par {entry.actorEmail ?? "Système"}
              </p>
              {entry.reason ? <p className="mt-2 text-sm">{entry.reason}</p> : null}
            </div>
            <time className="text-xs text-muted-foreground" dateTime={entry.occurredAt}>
              {dateTime(entry.occurredAt)}
            </time>
          </li>
        ))}
      </ol>
      <PaginationBar
        onPageChange={(next) => setParams(next ? { page: String(next) } : {}, { replace: true })}
        page={history.data.page}
        totalElements={history.data.totalElements}
        totalPages={history.data.totalPages}
      />
    </section>
  );
}

function Activations({ policyId }: { policyId: string }) {
  const session = useAdminSession();
  const canReadActivations = session.can(adminPermissions.commercialPoliciesReadActivations);
  const canReadAccounts = session.can(adminPermissions.commercialPoliciesReadActivationAccounts);
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const selected = params.get("activation") ?? "";
  const accountsPage = Math.max(0, Number(params.get("accountsPage")) || 0);
  const activations = useQuery({
    queryKey: adminCommercialKeys.policies.activations(policyId, page),
    queryFn: () => adminApi.commercialPolicyActivations(policyId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesReadActivations),
  });
  const accounts = useQuery({
    queryKey: adminCommercialKeys.policies.activationAudience(policyId, selected, accountsPage),
    queryFn: () => adminApi.commercialPolicyActivationAudience(policyId, selected, accountsPage),
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.commercialPoliciesReadActivationAccounts,
      Boolean(selected),
    ),
  });
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
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-medium">Activation #{activation.activationNumber}</p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {activation.affectedAccountCount} compte(s) · catalogue R{activation.catalogRevision}
                      </p>
                    </div>
                    {canReadAccounts ? (
                      <Button
                        onClick={() => {
                          const next = new URLSearchParams(params);
                          next.set("activation", activation.id);
                          next.delete("accountsPage");
                          setParams(next, { replace: true });
                        }}
                        size="sm"
                        variant="ghost"
                      >
                        Voir l’audience figée
                      </Button>
                    ) : null}
                  </div>
                  <p className="text-sm">{activation.reason}</p>
                  <time className="block text-xs text-muted-foreground" dateTime={activation.recordedAt}>
                    {dateTime(activation.recordedAt)}
                  </time>
                </li>
              ))}
            </ol>
            {!activations.data.totalElements ? <EmptyState title="Aucune activation" /> : null}
            <PaginationBar
              onPageChange={(next) => {
                const value = new URLSearchParams(params);
                next ? value.set("page", String(next)) : value.delete("page");
                setParams(value, { replace: true });
              }}
              page={activations.data.page}
              totalElements={activations.data.totalElements}
              totalPages={activations.data.totalPages}
            />
          </>
        )}
      </section>
      <section className="min-h-56 overflow-hidden rounded-xl border bg-card">
        {!canReadAccounts ? (
          <PermissionState />
        ) : !selected ? (
          <EmptyState
            description="Une activation conserve la liste exacte des comptes examinés."
            title="Choisissez une activation"
          />
        ) : accounts.isLoading ? (
          <div className="p-4">
            <LoadingState />
          </div>
        ) : accounts.isError || !accounts.data ? (
          <ErrorState retry={() => void accounts.refetch()} />
        ) : (
          <>
            <div className="border-b p-4">
              <h2 className="text-sm font-semibold">Audience figée · activation #{accounts.data.activationNumber}</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                {accounts.data.immutableAccountCount} compte(s) enregistrés
              </p>
            </div>
            <ul className="divide-y">
              {accounts.data.accounts.content.map((account) => (
                <li className="px-4 py-3" key={account.id}>
                  <p className="text-sm font-medium">{account.name ?? account.id}</p>
                  <p className="text-xs text-muted-foreground">{account.slug ?? account.id}</p>
                </li>
              ))}
            </ul>
            <PaginationBar
              onPageChange={(nextPage) => {
                const value = new URLSearchParams(params);
                nextPage ? value.set("accountsPage", String(nextPage)) : value.delete("accountsPage");
                setParams(value, { replace: true });
              }}
              page={accounts.data.accounts.page}
              totalElements={accounts.data.accounts.totalElements}
              totalPages={accounts.data.accounts.totalPages}
            />
          </>
        )}
      </section>
    </div>
  );
}

function ReassignOwnerDialog({ policy, trigger }: { policy: CommercialPolicyDetail; trigger: ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [ownerId, setOwnerId] = useState("");
  const [reason, setReason] = useState("");
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search);
  const [page, setPage] = useState(0);
  const operators = useQuery({
    queryKey: ["admin", "users", "policy-owner-choices", debouncedSearch, page],
    queryFn: () =>
      adminApi.users({
        search: debouncedSearch || undefined,
        active: true,
        page,
        size: 20,
        sort: "email",
        direction: "asc",
      }),
    enabled: open && session.can(adminPermissions.usersRead),
  });
  const selectedOperator = useQuery({
    queryKey: ["admin", "users", "policy-owner-selected", ownerId],
    queryFn: () => adminApi.user(ownerId),
    enabled: open && Boolean(ownerId) && session.can(adminPermissions.usersRead),
  });
  const operatorOptions = [...(operators.data?.content ?? [])];
  if (selectedOperator.data && !operatorOptions.some((operator) => operator.id === selectedOperator.data?.id)) {
    operatorOptions.unshift(selectedOperator.data);
  }
  const mutation = useMutation({
    mutationFn: () =>
      adminApi.reassignCommercialPolicyOwner(policy.summary.id, {
        version: policy.summary.version,
        ownerAdminUserId: ownerId,
        reason: reason.trim(),
      }),
    onSuccess: async () => {
      await invalidateAdminCommercial(
        queryClient,
        adminCommercialKeys.policies.all(),
        adminCommercialKeys.policies.detail(policy.summary.id),
        adminCommercialKeys.policies.owner(policy.summary.id),
      );
      setOpen(false);
      setOwnerId("");
      setReason("");
      toast.success("Responsable réassigné");
    },
    onError: async (error) => {
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.policies.detail(policy.summary.id) });
      toast.error(policyMutationMessage(error));
    },
  });
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Réassigner le responsable</DialogTitle>
          <DialogDescription>
            Cette identité est protégée par une permission distincte et la réassignation n’est possible que sur un
            brouillon.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="policy-owner-choice">Nouvel opérateur</Label>
          {session.can(adminPermissions.usersRead) ? (
            <div className="space-y-2">
              <Input
                aria-label="Rechercher un responsable"
                onChange={(event) => {
                  setSearch(event.target.value);
                  setPage(0);
                }}
                placeholder="Rechercher un opérateur actif…"
                value={search}
              />
              {operators.isError ? (
                <Button onClick={() => void operators.refetch()} size="sm" variant="outline">
                  Réessayer la recherche
                </Button>
              ) : (
                <Select onValueChange={setOwnerId} value={ownerId || undefined}>
                  <SelectTrigger className="w-full" id="policy-owner-choice">
                    <SelectValue placeholder={operators.isLoading ? "Chargement…" : "Choisir un opérateur actif"} />
                  </SelectTrigger>
                  <SelectContent>
                    {operatorOptions.map((operator) => (
                      <SelectItem key={operator.id} value={operator.id}>
                        {operator.email}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              {operators.data ? (
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span>{operators.data.totalElements} opérateur(s)</span>
                  <div className="flex gap-1">
                    <Button
                      disabled={operators.data.first}
                      onClick={() => setPage((value) => value - 1)}
                      size="sm"
                      variant="ghost"
                    >
                      Précédent
                    </Button>
                    <Button
                      disabled={operators.data.last}
                      onClick={() => setPage((value) => value + 1)}
                      size="sm"
                      variant="ghost"
                    >
                      Suivant
                    </Button>
                  </div>
                </div>
              ) : null}
            </div>
          ) : (
            <div className="space-y-2">
              <Input
                id="policy-owner-choice"
                onChange={(event) => setOwnerId(event.target.value)}
                placeholder="Identifiant administrateur"
                value={ownerId}
              />
              <p className="text-xs text-muted-foreground">
                La recherche d’opérateurs est protégée séparément. Collez ici un identifiant administrateur connu.
              </p>
            </div>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="policy-owner-reason">Motif obligatoire</Label>
          <Textarea
            id="policy-owner-reason"
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
          <Button disabled={!ownerId || !reason.trim() || mutation.isPending} onClick={() => mutation.mutate()}>
            Réassigner
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Owner({ policy }: { policy?: CommercialPolicyDetail }) {
  const session = useAdminSession();
  const { policyId } = useParams();
  const id = policy?.summary.id ?? policyId ?? "";
  const owner = useQuery({
    queryKey: adminCommercialKeys.policies.owner(id),
    queryFn: () => adminApi.commercialPolicyOwner(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesReadOwner, Boolean(id)),
  });
  if (!session.can(adminPermissions.commercialPoliciesReadOwner)) return <PermissionState />;
  if (owner.isLoading) return <LoadingState />;
  if (owner.isError || !owner.data) return <ErrorState retry={() => void owner.refetch()} />;
  return (
    <section className="space-y-5">
      <div className="border-y py-5">
        <dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <dt className="text-xs text-muted-foreground">Nom</dt>
            <dd className="mt-1 font-medium">{owner.data.displayName ?? owner.data.username}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Email</dt>
            <dd className="mt-1 font-medium">{owner.data.email}</dd>
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
      </div>
      {policy?.summary.availableActions.includes("REASSIGN_OWNER") ? (
        <ReassignOwnerDialog policy={policy} trigger={<Button variant="outline">Réassigner le responsable</Button>} />
      ) : null}
    </section>
  );
}

function standaloneSurface(tab: string | undefined, policyId: string) {
  if (tab === "history") return <HistoryList policyId={policyId} />;
  if (tab === "revisions") return <Revisions policyId={policyId} />;
  if (tab === "activations") return <Activations policyId={policyId} />;
  if (tab === "owner") return <Owner />;
  if (tab === "audience") return <Audience policyId={policyId} />;
  return null;
}

export function AdminCommercialPolicyDetailPage() {
  const session = useAdminSession();
  const { policyId, tab } = useParams();
  const id = policyId ?? "";
  const canRead = session.can(adminPermissions.commercialPoliciesRead);
  const policy = useQuery({
    queryKey: adminCommercialKeys.policies.detail(id),
    queryFn: () => adminApi.commercialPolicy(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesRead, Boolean(id)),
  });
  if (!canRead) {
    const surface = standaloneSurface(tab, id);
    if (!surface) return <Navigate replace to="/admin" />;
    return (
      <div className="space-y-6">
        <Button asChild className="-ms-2" size="sm" variant="ghost">
          <Link to={session.can(adminPermissions.commercialPoliciesList) ? "/admin/commercial-policies" : "/admin"}>
            <ArrowLeftIcon className="rtl:rotate-180" />
            {session.can(adminPermissions.commercialPoliciesList) ? "Politiques commerciales" : "Administration"}
          </Link>
        </Button>
        <PageHeader title="Politique commerciale" />
        {surface}
      </div>
    );
  }
  if (policy.isLoading) return <LoadingState />;
  if (policy.isError || !policy.data)
    return <ErrorState retry={() => void policy.refetch()} title="Politique introuvable" />;
  const data = policy.data;
  const status = policyStatus[data.summary.status];
  const tabs = [
    { label: "Synthèse", to: `/admin/commercial-policies/${id}`, end: true },
    ...(session.can(adminPermissions.commercialPoliciesPreviewAudience)
      ? [{ label: "Audience", to: `/admin/commercial-policies/${id}/audience` }]
      : []),
    ...(session.can(adminPermissions.commercialPoliciesReadRevisions) ||
    session.can(adminPermissions.commercialPoliciesCompare)
      ? [{ label: "Révisions", to: `/admin/commercial-policies/${id}/revisions` }]
      : []),
    ...(session.can(adminPermissions.commercialPoliciesReadActivations) ||
    session.can(adminPermissions.commercialPoliciesReadActivationAccounts)
      ? [{ label: "Activations", to: `/admin/commercial-policies/${id}/activations` }]
      : []),
    ...(session.can(adminPermissions.commercialPoliciesReadHistory)
      ? [{ label: "Historique", to: `/admin/commercial-policies/${id}/history` }]
      : []),
    ...(session.can(adminPermissions.commercialPoliciesReadOwner)
      ? [{ label: "Responsable", to: `/admin/commercial-policies/${id}/owner` }]
      : []),
  ];
  return (
    <div className="space-y-6">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to="/admin/commercial-policies">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Politiques commerciales
        </Link>
      </Button>
      <PageHeader
        actions={
          data.summary.availableActions.includes("EDIT_DRAFT") ? (
            <Button asChild variant="ghost">
              <Link to={`/admin/commercial-policies/${id}/edit`}>
                <PencilSimpleIcon />
                Modifier
              </Link>
            </Button>
          ) : undefined
        }
        description={
          <span className="flex flex-wrap items-center gap-2">
            <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
            <span>{data.summary.code}</span>
            <span>· R{data.summary.revisionNumber}</span>
          </span>
        }
        title={data.summary.name}
      />
      <SectionTabs ariaLabel="Sections de la politique" tabs={tabs} />
      {tab === "audience" ? (
        <Audience policyId={id} />
      ) : tab === "revisions" ? (
        <Revisions policyId={id} />
      ) : tab === "activations" ? (
        <Activations policyId={id} />
      ) : tab === "history" ? (
        <HistoryList policyId={id} />
      ) : tab === "owner" ? (
        <Owner policy={data} />
      ) : (
        <Overview policy={data} />
      )}
    </div>
  );
}
