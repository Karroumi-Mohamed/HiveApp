import {
  ArchiveIcon,
  ArrowLeftIcon,
  CopyIcon,
  EyeIcon,
  PauseIcon,
  PencilSimpleIcon,
  PlayIcon,
  StopIcon,
  TrashIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { Link, Navigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  CommercialCampaignAction,
  CommercialCampaignComparison,
  CommercialCampaignDetail,
  CommercialCampaignFrozenAudience,
  CommercialCampaignOperationState,
  CommercialCampaignOwnerChoice,
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
import { Textarea } from "@/components/ui/textarea";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateCommercialCampaignTargeting,
} from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import {
  boundedCampaignPage,
  boundedCampaignResponsePage,
  campaignChangedField,
  withCampaignSearchParam,
} from "./commercial-campaign-detail-state";
import {
  CommercialCampaignReasonDialog,
  CommercialCampaignScheduleDialog,
  campaignActionVisible,
} from "./commercial-campaign-dialogs";
import {
  campaignActionLabel,
  campaignActionPermission,
  campaignAudienceMode,
  campaignBlocker,
  campaignFrozenAudienceLabel,
  campaignHistoryAction,
  campaignMutationMessage,
  campaignSource,
  campaignStatus,
  validCampaignId,
} from "./commercial-campaign-rules";

const dateTime = (value: string | null | undefined) =>
  value ? new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";

function actionBlockReason(campaign: CommercialCampaignOperationState, action: CommercialCampaignAction) {
  return campaign.blockedActions[action]?.map((blocker) => campaignBlocker[blocker]).join(" ");
}

function CampaignActions({ campaign }: { campaign: CommercialCampaignOperationState }) {
  const session = useAdminSession();
  const canShow = (action: CommercialCampaignAction) =>
    campaignActionVisible(campaign, action) && session.can(campaignActionPermission[action]);
  const trigger = (action: CommercialCampaignAction, label: string, icon: ReactNode, destructive = false) => {
    const disabled = !campaign.availableActions.includes(action);
    return (
      <Button
        aria-describedby={disabled ? `campaign-block-${action}` : undefined}
        className={destructive ? "text-destructive hover:text-destructive" : undefined}
        disabled={disabled}
        size="sm"
        title={disabled ? actionBlockReason(campaign, action) : undefined}
        variant="ghost"
      >
        {icon}
        {label}
        {disabled ? (
          <span className="sr-only" id={`campaign-block-${action}`}>
            {actionBlockReason(campaign, action)}
          </span>
        ) : null}
      </Button>
    );
  };
  return (
    <div className="flex flex-wrap justify-end gap-1">
      {campaign.availableActions.includes("UPDATE") && session.can(adminPermissions.campaignsUpdate) ? (
        <Button asChild size="sm" variant="ghost">
          <Link to={`/admin/campaigns/${campaign.id}/edit`}>
            <PencilSimpleIcon />
            Modifier
          </Link>
        </Button>
      ) : null}
      {canShow("SCHEDULE") && !session.can(adminPermissions.campaignsPreviewSchedule) ? (
        <Button
          disabled
          size="sm"
          title="La planification exige aussi l’autorisation de vérifier l’audience."
          variant="ghost"
        >
          <PlayIcon />
          Planifier
        </Button>
      ) : canShow("SCHEDULE") ? (
        <CommercialCampaignScheduleDialog
          campaign={campaign}
          trigger={trigger("SCHEDULE", "Planifier", <PlayIcon />)}
        />
      ) : null}
      {(["PAUSE", "RESUME", "END", "ARCHIVE", "DUPLICATE", "REVISE", "DELETE_DRAFT"] as const).map((action) => {
        const config = {
          PAUSE: { label: "Pause", icon: <PauseIcon /> },
          RESUME: { label: "Reprendre", icon: <PlayIcon /> },
          END: { label: "Terminer", icon: <StopIcon />, destructive: true },
          ARCHIVE: { label: "Archiver", icon: <ArchiveIcon />, destructive: true },
          DUPLICATE: { label: "Dupliquer", icon: <CopyIcon /> },
          REVISE: { label: "Réviser", icon: <CopyIcon /> },
          DELETE_DRAFT: { label: "Supprimer", icon: <TrashIcon />, destructive: true },
        }[action];
        return canShow(action) ? (
          <CommercialCampaignReasonDialog
            action={action}
            campaign={campaign}
            key={action}
            trigger={trigger(action, config.label, config.icon, config.destructive)}
          />
        ) : null;
      })}
    </div>
  );
}

function OperationsPanel({ campaign }: { campaign: CommercialCampaignOperationState }) {
  const status = campaignStatus[campaign.status];
  const blockers = Object.entries(campaign.blockedActions).flatMap(([action, reasons]) =>
    (reasons ?? []).map((reason) => ({ action: action as CommercialCampaignAction, reason })),
  );
  return (
    <section className="space-y-5 border-y py-5" aria-label="État opérationnel">
      <dl className="grid gap-4 sm:grid-cols-3">
        <div>
          <dt className="text-xs text-muted-foreground">Statut</dt>
          <dd className="mt-1">
            <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Version</dt>
          <dd className="mt-1 font-medium tabular-nums">{campaign.version}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Actions disponibles</dt>
          <dd className="mt-1 font-medium tabular-nums">{campaign.availableActions.length}</dd>
        </div>
      </dl>
      {blockers.length ? (
        <div>
          <h2 className="text-sm font-semibold">Blocages actuels</h2>
          <ul className="mt-2 space-y-2 text-sm">
            {blockers.map(({ action, reason }) => (
              <li className="flex gap-2" key={`${action}-${reason}`}>
                <span className="font-medium">{campaignActionLabel[action]}</span>
                <span className="text-muted-foreground">— {campaignBlocker[reason]}</span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}

function SummaryPanel({ campaign }: { campaign: CommercialCampaignDetail }) {
  const status = campaignStatus[campaign.summary.status];
  const events = [
    ["Créée", campaign.summary.createdAt],
    ["Planifiée", campaign.scheduledAt],
    ["Activée", campaign.activatedAt],
    ["Mise en pause", campaign.pausedAt],
    ["Reprise", campaign.resumedAt],
    ["Terminée", campaign.endedAt],
    ["Archivée", campaign.archivedAt],
  ].filter((event): event is [string, string] => Boolean(event[1]));
  const blockers = Object.entries(campaign.summary.blockedActions).filter(([, values]) => values?.length);
  return (
    <div className="grid gap-8 lg:grid-cols-[1.2fr_0.8fr]">
      <section className="space-y-6">
        <dl className="grid gap-x-8 gap-y-5 border-y py-5 sm:grid-cols-2">
          <div>
            <dt className="text-xs text-muted-foreground">État</dt>
            <dd className="mt-1">
              <StatusBadge dot={false} tone={status.tone}>
                {status.label}
              </StatusBadge>
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Origine</dt>
            <dd className="mt-1 font-medium">{campaignSource[campaign.summary.source]}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Audience configurée</dt>
            <dd className="mt-1 font-medium">{campaignAudienceMode[campaign.audience.mode]}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Audience figée</dt>
            <dd className="mt-1 font-medium tabular-nums">
              {campaignFrozenAudienceLabel(campaign.audience.mode, campaign.summary.frozenAccountCount)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Début</dt>
            <dd className="mt-1 font-medium">{dateTime(campaign.summary.startsAt)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Fin</dt>
            <dd className="mt-1 font-medium">{dateTime(campaign.summary.endsAt)}</dd>
          </div>
          <div className="sm:col-span-2">
            <dt className="text-xs text-muted-foreground">Motif</dt>
            <dd className="mt-1 whitespace-pre-wrap text-sm">{campaign.reason}</dd>
          </div>
          {campaign.description ? (
            <div className="sm:col-span-2">
              <dt className="text-xs text-muted-foreground">Description</dt>
              <dd className="mt-1 whitespace-pre-wrap text-sm">{campaign.description}</dd>
            </div>
          ) : null}
        </dl>
        {blockers.length ? (
          <section>
            <h2 className="text-sm font-semibold">Opérations bloquées</h2>
            <ul className="mt-3 divide-y border-y">
              {blockers.map(([action, values]) => (
                <li className="py-3 text-sm" key={action}>
                  <span className="font-medium">{campaignActionLabel[action as CommercialCampaignAction]}</span>
                  <span className="mt-1 block text-muted-foreground">
                    {values?.map((value) => campaignBlocker[value]).join(" ")}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ) : null}
      </section>
      <section>
        <h2 className="text-sm font-semibold">Chronologie</h2>
        <ol className="mt-3 border-s ps-5">
          {events.map(([label, at]) => (
            <li className="relative pb-5 last:pb-0" key={`${label}-${at}`}>
              <span className="absolute -start-[1.43rem] top-1 size-2 rounded-full bg-border ring-4 ring-background" />
              <p className="text-sm font-medium">{label}</p>
              <time className="text-xs text-muted-foreground" dateTime={at}>
                {dateTime(at)}
              </time>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}

function FrozenEvidence({ audience }: { audience: CommercialCampaignFrozenAudience }) {
  return (
    <dl className="grid gap-3 border-y py-3 text-xs sm:grid-cols-2">
      <div>
        <dt className="text-muted-foreground">Preuve examinée par</dt>
        <dd className="mt-1 font-mono">{audience.reviewedByActorUserId}</dd>
      </div>
      <div>
        <dt className="text-muted-foreground">Expiration de la preuve</dt>
        <dd className="mt-1">{dateTime(audience.evidenceExpiresAt)}</dd>
      </div>
      <div>
        <dt className="text-muted-foreground">Version vérifiée</dt>
        <dd className="mt-1">
          Campagne {audience.campaignVersion} · catalogue R{audience.catalogRevision}
          <span className="mt-1 block break-all font-mono text-xs text-muted-foreground" dir="ltr">
            Registre {audience.registryVersion}
          </span>
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">Empreinte d’audience</dt>
        <dd className="mt-1 break-all font-mono">{audience.audienceFingerprint}</dd>
      </div>
      <div className="sm:col-span-2">
        <dt className="text-muted-foreground">Motif enregistré</dt>
        <dd className="mt-1 text-foreground">{audience.reason}</dd>
      </div>
    </dl>
  );
}

function AudiencePanel({ campaignId }: { campaignId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = boundedCampaignPage(params.get("page"));
  const canReadOpaque = session.can(adminPermissions.campaignsReadAudience);
  const canReadIdentities = session.can(adminPermissions.campaignsReadAudienceIdentities);
  const revealed = params.get("identified") === "true" && canReadIdentities;
  const opaque = useQuery({
    queryKey: adminCommercialKeys.campaigns.audience(campaignId, page, false),
    queryFn: () => adminApi.commercialCampaignAudience(campaignId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsReadAudience, canReadOpaque),
  });
  const identities = useQuery({
    queryKey: adminCommercialKeys.campaigns.audience(campaignId, page, true),
    queryFn: () => adminApi.commercialCampaignAudienceIdentities(campaignId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsReadAudienceIdentities, revealed),
  });
  const response = revealed ? identities.data?.accounts : opaque.data?.accounts;
  useEffect(() => {
    if (!response) return;
    const bounded = boundedCampaignResponsePage(page, response.totalPages);
    if (bounded !== page) setParams(withCampaignSearchParam(params, "page", bounded), { replace: true });
  }, [page, params, response, setParams]);
  const toggleReveal = () =>
    setParams(
      withCampaignSearchParam(withCampaignSearchParam(params, "identified", revealed ? null : "true"), "page", null),
      { replace: true },
    );
  if (!canReadOpaque && !canReadIdentities) return <PermissionState />;
  if (!canReadOpaque && !revealed) {
    return (
      <Alert>
        <AlertTitle>Identités protégées</AlertTitle>
        <AlertDescription className="space-y-3">
          <p>
            Cette autorisation ne donne pas accès à la preuve opaque. La liste d’identités ne sera chargée qu’après
            votre confirmation.
          </p>
          <Button onClick={toggleReveal} size="sm" variant="outline">
            <EyeIcon />
            Révéler les identités
          </Button>
        </AlertDescription>
      </Alert>
    );
  }
  if ((canReadOpaque && opaque.isLoading) || (revealed && identities.isLoading)) return <LoadingState />;
  if (canReadOpaque && (opaque.isError || !opaque.data))
    return <ErrorState retry={() => void opaque.refetch()} title="Audience indisponible" />;
  if (opaque.data?.publicAudience) {
    return (
      <div className="space-y-5">
        <Alert>
          <AlertTitle>Audience publique</AlertTitle>
          <AlertDescription>
            La preuve enregistre un périmètre public dynamique. Aucune liste d’identités n’est figée.
          </AlertDescription>
        </Alert>
        <FrozenEvidence audience={opaque.data} />
      </div>
    );
  }
  if (revealed && (identities.isError || !identities.data))
    return <ErrorState retry={() => void identities.refetch()} title="Identités indisponibles" />;
  const accounts = revealed ? identities.data?.accounts : opaque.data?.accounts;
  return (
    <section className="overflow-hidden border-y">
      <header className="flex flex-col justify-between gap-3 border-b py-4 sm:flex-row sm:items-center">
        <div>
          <h2 className="text-sm font-semibold">Audience figée</h2>
          {opaque.data ? (
            <>
              <p className="mt-1 text-xs text-muted-foreground">
                {opaque.data.immutableAccountCount} compte(s) · évaluée {dateTime(opaque.data.evaluatedAt)} · catalogue
                R{opaque.data.catalogRevision}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Version de campagne {opaque.data.campaignVersion}
                <span className="mt-1 block break-all font-mono" dir="ltr">
                  Registre {opaque.data.registryVersion}
                </span>
              </p>
            </>
          ) : (
            <p className="mt-1 text-xs text-muted-foreground">Identités révélées explicitement</p>
          )}
        </div>
        {session.can(adminPermissions.campaignsReadAudienceIdentities) ? (
          <Button onClick={toggleReveal} size="sm" variant="outline">
            <EyeIcon />
            {revealed ? "Masquer les identités" : "Révéler les identités"}
          </Button>
        ) : null}
      </header>
      {opaque.data ? <FrozenEvidence audience={opaque.data} /> : null}
      <ul className="divide-y">
        {revealed
          ? identities.data?.accounts.content.map((account) => (
              <li className="grid gap-1 py-3 sm:grid-cols-[1fr_auto]" key={account.accountId}>
                <div>
                  <p className="text-sm font-medium">{account.accountName ?? "Compte supprimé"}</p>
                  <p className="text-xs text-muted-foreground">{account.accountSlug ?? account.accountId}</p>
                </div>
                <div className="text-start text-xs text-muted-foreground sm:text-end">
                  <p>{account.ownerEmail ?? "Email indisponible"}</p>
                  <p>{account.active ? "Actif" : "Inactif"}</p>
                </div>
              </li>
            ))
          : opaque.data?.accounts.content.map((account) => (
              <li className="py-3 font-mono text-sm" key={account.accountId}>
                {account.accountId}
              </li>
            ))}
      </ul>
      {accounts && !accounts.totalElements ? <EmptyState title="Audience vide" /> : null}
      {accounts ? (
        <PaginationBar
          onPageChange={(next) => setParams(withCampaignSearchParam(params, "page", next), { replace: true })}
          page={accounts.page}
          totalElements={accounts.totalElements}
          totalPages={accounts.totalPages}
        />
      ) : null}
    </section>
  );
}

function ComparisonDetails({ comparison }: { comparison: CommercialCampaignComparison }) {
  const candidates = [comparison.source, comparison.compared];
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-sm font-semibold">Différences</h2>
        <p className="mt-1 text-xs text-muted-foreground">
          {comparison.sameLineage ? "Même lignée" : "Lignées indépendantes"}
          {comparison.directSuccessor ? " · succession directe" : ""}
        </p>
      </div>
      {comparison.changedFields.length ? (
        <ul className="divide-y border-y">
          {comparison.changedFields.map((field) => (
            <li className="py-2 text-sm" key={field}>
              {campaignChangedField[field] ?? field}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted-foreground">Aucune différence.</p>
      )}
      <div className="grid gap-6 border-t pt-5 sm:grid-cols-2">
        {candidates.map((candidate) => (
          <dl className="space-y-3" key={candidate.summary.id}>
            <div>
              <dt className="text-xs text-muted-foreground">Campagne</dt>
              <dd className="font-semibold">{candidate.summary.name}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Révision</dt>
              <dd>R{candidate.summary.revisionNumber}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Audience</dt>
              <dd>{campaignAudienceMode[candidate.audience.mode]}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Fenêtre</dt>
              <dd className="text-sm">
                {dateTime(candidate.summary.startsAt)} → {dateTime(candidate.summary.endsAt)}
              </dd>
            </div>
          </dl>
        ))}
      </div>
    </div>
  );
}

function RevisionsPanel({ campaignId }: { campaignId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = boundedCampaignPage(params.get("page"));
  const against = validCampaignId(params.get("against") ?? undefined) ? (params.get("against") as string) : "";
  const [compareCandidate, setCompareCandidate] = useState(against);
  const canReadRevisions = session.can(adminPermissions.campaignsRevisions);
  const revisions = useQuery({
    queryKey: adminCommercialKeys.campaigns.revisions(campaignId, page),
    queryFn: () => adminApi.commercialCampaignRevisions(campaignId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsRevisions),
  });
  const comparison = useQuery({
    queryKey: adminCommercialKeys.campaigns.comparison(campaignId, against),
    queryFn: () => adminApi.compareCommercialCampaigns(campaignId, against),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsCompare, Boolean(against)),
  });
  useEffect(() => {
    if (!revisions.data) return;
    const bounded = boundedCampaignResponsePage(page, revisions.data.totalPages);
    if (bounded !== page) setParams(withCampaignSearchParam(params, "page", bounded), { replace: true });
  }, [page, params, revisions.data, setParams]);
  useEffect(() => setCompareCandidate(against), [against]);
  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(300px,0.7fr)_1.3fr]">
      <section className="overflow-hidden border-y">
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
                const status = campaignStatus[revision.status];
                return (
                  <li className="flex items-center gap-3 py-3" key={revision.id}>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium">Révision {revision.revisionNumber}</p>
                      <p className="truncate text-xs text-muted-foreground">{revision.code}</p>
                    </div>
                    <StatusBadge dot={false} tone={status.tone}>
                      {status.label}
                    </StatusBadge>
                    {session.can(adminPermissions.campaignsCompare) && revision.id !== campaignId ? (
                      <Button
                        onClick={() =>
                          setParams(withCampaignSearchParam(params, "against", revision.id), { replace: true })
                        }
                        size="sm"
                        variant="ghost"
                      >
                        Comparer
                      </Button>
                    ) : null}
                  </li>
                );
              })}
            </ul>
            <PaginationBar
              onPageChange={(next) => setParams(withCampaignSearchParam(params, "page", next), { replace: true })}
              page={revisions.data.page}
              totalElements={revisions.data.totalElements}
              totalPages={revisions.data.totalPages}
            />
          </>
        )}
      </section>
      <section className="min-h-56 border-y py-4">
        {!session.can(adminPermissions.campaignsCompare) ? (
          <PermissionState />
        ) : (
          <div className="space-y-5">
            <div className="space-y-2 border-b pb-4">
              <Label htmlFor="campaign-compare-id">Révision à comparer</Label>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Input
                  id="campaign-compare-id"
                  maxLength={36}
                  onChange={(event) => setCompareCandidate(event.target.value)}
                  placeholder={
                    canReadRevisions ? "Choisissez à gauche ou collez un identifiant" : "Collez un identifiant connu"
                  }
                  value={compareCandidate}
                />
                <Button
                  disabled={!validCampaignId(compareCandidate) || compareCandidate === campaignId}
                  onClick={() =>
                    setParams(withCampaignSearchParam(params, "against", compareCandidate), { replace: true })
                  }
                  variant="outline"
                >
                  Comparer
                </Button>
              </div>
            </div>
            {!against ? (
              <EmptyState title="Choisissez une révision à comparer" />
            ) : comparison.isLoading ? (
              <LoadingState />
            ) : comparison.isError || !comparison.data ? (
              <ErrorState retry={() => void comparison.refetch()} />
            ) : (
              <ComparisonDetails comparison={comparison.data} />
            )}
          </div>
        )}
      </section>
    </div>
  );
}

function HistoryPanel({ campaignId }: { campaignId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = boundedCampaignPage(params.get("page"));
  const history = useQuery({
    queryKey: adminCommercialKeys.campaigns.history(campaignId, page),
    queryFn: () => adminApi.commercialCampaignHistory(campaignId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsHistory),
  });
  useEffect(() => {
    if (history.data) {
      const bounded = boundedCampaignResponsePage(page, history.data.totalPages);
      if (bounded !== page) setParams(withCampaignSearchParam(params, "page", bounded), { replace: true });
    }
  }, [history.data, page, params, setParams]);
  if (!session.can(adminPermissions.campaignsHistory)) return <PermissionState />;
  if (history.isLoading) return <LoadingState />;
  if (history.isError || !history.data) return <ErrorState retry={() => void history.refetch()} />;
  if (!history.data.totalElements) return <EmptyState title="Aucun événement" />;
  return (
    <section className="overflow-hidden border-y">
      <ol className="divide-y">
        {history.data.content.map((entry) => (
          <li className="grid gap-2 py-4 sm:grid-cols-[1fr_auto]" key={entry.id}>
            <div>
              <p className="text-sm font-medium">{campaignHistoryAction(entry.action)}</p>
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
        onPageChange={(next) => setParams(withCampaignSearchParam(params, "page", next), { replace: true })}
        page={history.data.page}
        totalElements={history.data.totalElements}
        totalPages={history.data.totalPages}
      />
    </section>
  );
}

function OwnerDialog({ campaignId, version, trigger }: { campaignId: string; version: number; trigger: ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [page, setPage] = useState(0);
  const [ownerId, setOwnerId] = useState("");
  const [reason, setReason] = useState("");
  const expectedVersion = useRef(version);
  const choices = useQuery({
    queryKey: adminCommercialKeys.campaigns.ownerChoices({ query: debounced, page }),
    queryFn: () => adminApi.commercialCampaignOwnerChoices({ query: debounced || undefined, page, size: 15 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsChooseOwners, open),
  });
  const selectedChoice = useQuery({
    queryKey: adminCommercialKeys.campaigns.ownerChoices({ selected: ownerId }),
    queryFn: () => adminApi.resolveCommercialCampaignOwnerChoices([ownerId]),
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.campaignsResolveOwnerChoices,
      open && Boolean(ownerId),
    ),
  });
  const selectedOwner =
    selectedChoice.data?.[0] ?? choices.data?.content.find((choice) => choice.adminUserId === ownerId);
  const mutation = useMutation({
    mutationFn: () =>
      adminApi.reassignCommercialCampaignOwner(campaignId, {
        version: expectedVersion.current,
        ownerAdminUserId: ownerId,
        reason: reason.trim(),
      }),
    onSuccess: async () => {
      setOpen(false);
      await invalidateCommercialCampaignTargeting(
        queryClient,
        adminCommercialKeys.campaigns.detail(campaignId),
        adminCommercialKeys.campaigns.owner(campaignId),
      );
      toast.success("Responsable réassigné");
    },
    onError: async (error) => {
      await invalidateCommercialCampaignTargeting(
        queryClient,
        adminCommercialKeys.campaigns.detail(campaignId),
        adminCommercialKeys.campaigns.owner(campaignId),
      );
      toast.error(campaignMutationMessage(error));
    },
  });
  const changeOpen = (next: boolean) => {
    setOpen(next);
    if (next) expectedVersion.current = version;
    else {
      setSearch("");
      setPage(0);
      setOwnerId("");
      setReason("");
    }
  };
  return (
    <Dialog onOpenChange={changeOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Réassigner le responsable</DialogTitle>
          <DialogDescription>
            Choisissez uniquement parmi les opérateurs exposés par le catalogue dédié aux campagnes.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <Label htmlFor="campaign-owner-search">Rechercher</Label>
          <Input
            id="campaign-owner-search"
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            value={search}
          />
          {ownerId ? (
            <div className="border-y py-3 text-sm">
              <p className="text-xs text-muted-foreground">Sélection</p>
              <p className="mt-1 font-medium">{selectedOwner?.displayName || selectedOwner?.username || ownerId}</p>
              {selectedOwner?.email ? <p className="text-xs text-muted-foreground">{selectedOwner.email}</p> : null}
            </div>
          ) : null}
          {choices.isLoading ? (
            <LoadingState rows={3} />
          ) : choices.isError || !choices.data ? (
            <ErrorState retry={() => void choices.refetch()} />
          ) : (
            <>
              <fieldset className="max-h-64 overflow-y-auto border-y">
                <legend className="sr-only">Responsable</legend>
                {choices.data.content.map((choice: CommercialCampaignOwnerChoice) => (
                  <Label
                    className="flex cursor-pointer items-center gap-3 border-b py-3 last:border-0"
                    htmlFor={`campaign-owner-${choice.adminUserId}`}
                    key={choice.adminUserId}
                  >
                    <input
                      checked={ownerId === choice.adminUserId}
                      id={`campaign-owner-${choice.adminUserId}`}
                      name="campaign-owner"
                      onChange={() => setOwnerId(choice.adminUserId)}
                      type="radio"
                    />
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium">
                        {choice.displayName || choice.username}
                      </span>
                      <span className="block truncate text-xs text-muted-foreground">{choice.email}</span>
                    </span>
                  </Label>
                ))}
              </fieldset>
              <PaginationBar
                onPageChange={setPage}
                page={choices.data.page}
                totalElements={choices.data.totalElements}
                totalPages={choices.data.totalPages}
              />
            </>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="campaign-owner-reason">Motif obligatoire</Label>
          <Textarea
            id="campaign-owner-reason"
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            value={reason}
          />
        </div>
        <DialogFooter>
          <Button onClick={() => changeOpen(false)} variant="outline">
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

function OwnerPanel({ campaignId }: { campaignId: string }) {
  const session = useAdminSession();
  const owner = useQuery({
    queryKey: adminCommercialKeys.campaigns.owner(campaignId),
    queryFn: () => adminApi.commercialCampaignOwner(campaignId),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsOwner),
  });
  if (!session.can(adminPermissions.campaignsOwner)) return <PermissionState />;
  if (owner.isLoading) return <LoadingState />;
  if (owner.isError || !owner.data) return <ErrorState retry={() => void owner.refetch()} />;
  return (
    <section className="flex flex-col justify-between gap-5 border-y py-5 sm:flex-row sm:items-start">
      <dl className="grid gap-4 sm:grid-cols-2">
        <div>
          <dt className="text-xs text-muted-foreground">Responsable</dt>
          <dd className="mt-1 font-semibold">{owner.data.displayName || owner.data.username}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Email</dt>
          <dd className="mt-1 text-sm">{owner.data.email}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">État</dt>
          <dd className="mt-1 text-sm">{owner.data.active ? "Actif" : "Inactif"}</dd>
        </div>
      </dl>
      {owner.data.status === "DRAFT" && session.can(adminPermissions.campaignsReassignOwner) ? (
        <OwnerDialog
          campaignId={campaignId}
          trigger={<Button variant="outline">Réassigner</Button>}
          version={owner.data.version}
        />
      ) : null}
    </section>
  );
}

export function AdminCommercialCampaignDetailPage() {
  const session = useAdminSession();
  const { campaignId, tab } = useParams();
  const id = validCampaignId(campaignId) ? campaignId : "";
  const campaign = useQuery({
    queryKey: adminCommercialKeys.campaigns.detail(id),
    queryFn: () => adminApi.commercialCampaign(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsRead, Boolean(id)),
  });
  const operations = useQuery({
    queryKey: adminCommercialKeys.campaigns.operations(id),
    queryFn: () => adminApi.commercialCampaignOperations(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsReadOperations, Boolean(id)),
  });
  if (!id)
    return (
      <ErrorState
        description="L’identifiant présent dans l’adresse est invalide."
        title="Adresse de campagne invalide"
      />
    );
  const tabs = [
    session.can(adminPermissions.campaignsRead) ? { label: "Synthèse", to: `/admin/campaigns/${id}`, end: true } : null,
    session.can(adminPermissions.campaignsReadOperations)
      ? { label: "Opérations", to: `/admin/campaigns/${id}/operations` }
      : null,
    session.can(adminPermissions.campaignsReadAudience) || session.can(adminPermissions.campaignsReadAudienceIdentities)
      ? { label: "Audience", to: `/admin/campaigns/${id}/audience` }
      : null,
    session.can(adminPermissions.campaignsRevisions) || session.can(adminPermissions.campaignsCompare)
      ? { label: "Révisions", to: `/admin/campaigns/${id}/revisions` }
      : null,
    session.can(adminPermissions.campaignsHistory)
      ? { label: "Historique", to: `/admin/campaigns/${id}/history` }
      : null,
    session.can(adminPermissions.campaignsOwner) ? { label: "Responsable", to: `/admin/campaigns/${id}/owner` } : null,
  ].filter((candidate): candidate is NonNullable<typeof candidate> => Boolean(candidate));
  const validTabs = new Set([undefined, "operations", "audience", "revisions", "history", "owner"]);
  if (!validTabs.has(tab)) return <Navigate replace to={`/admin/campaigns/${id}`} />;
  if (!tab && !session.can(adminPermissions.campaignsRead) && session.can(adminPermissions.campaignsReadOperations))
    return <Navigate replace to={`/admin/campaigns/${id}/operations`} />;
  if (session.can(adminPermissions.campaignsRead) && campaign.isLoading) return <LoadingState />;
  if (session.can(adminPermissions.campaignsRead) && (campaign.isError || !campaign.data))
    return <ErrorState retry={() => void campaign.refetch()} title="Campagne introuvable" />;
  if (!campaign.data && session.can(adminPermissions.campaignsReadOperations) && operations.isLoading)
    return <LoadingState />;
  if (
    !campaign.data &&
    session.can(adminPermissions.campaignsReadOperations) &&
    (operations.isError || !operations.data)
  )
    return <ErrorState retry={() => void operations.refetch()} title="État opérationnel indisponible" />;
  const detail = campaign.data;
  const operationState = operations.data ?? detail?.summary;
  return (
    <section className="space-y-6">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to={session.can(adminPermissions.campaignsList) ? "/admin/campaigns" : "/admin"}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          {session.can(adminPermissions.campaignsList) ? "Campagnes" : "Administration"}
        </Link>
      </Button>
      <PageHeader
        actions={operationState ? <CampaignActions campaign={operationState} /> : undefined}
        description={
          operationState ? (
            <span className="font-mono text-xs">
              {operationState.code} · R{operationState.revisionNumber}
            </span>
          ) : undefined
        }
        title={operationState?.name ?? "Campagne"}
      />
      <SectionTabs ariaLabel="Sections de la campagne" tabs={tabs} />
      {tab === "operations" && operationState ? (
        <OperationsPanel campaign={operationState} />
      ) : tab === "audience" ? (
        <AudiencePanel campaignId={id} />
      ) : tab === "revisions" ? (
        <RevisionsPanel campaignId={id} />
      ) : tab === "history" ? (
        <HistoryPanel campaignId={id} />
      ) : tab === "owner" ? (
        <OwnerPanel campaignId={id} />
      ) : detail ? (
        <SummaryPanel campaign={detail} />
      ) : (
        <PermissionState />
      )}
    </section>
  );
}
