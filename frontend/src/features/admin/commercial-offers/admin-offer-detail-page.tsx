import {
  ArchiveIcon,
  ArrowLeftIcon,
  ArrowRightIcon,
  CopyIcon,
  EyeIcon,
  PencilSimpleIcon,
  PlayIcon,
  StopIcon,
  TrashIcon,
  UserSwitchIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { Link, Navigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminOfferApi } from "@/api/admin-offer-api";
import { ApiError } from "@/api/http";
import type {
  OfferAction,
  OfferComparison,
  OfferOperationState,
  OfferOwnerChoice,
  OfferRedemption,
  OfferRedemptionIdentity,
} from "@/api/offer-contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
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
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import {
  OfferApplyAccountDialog,
  OfferDuplicateDialog,
  OfferPublicationDialog,
  OfferReasonDialog,
} from "./offer-dialogs";
import {
  offerAcceptance,
  offerActionPermission,
  offerActionReason,
  offerBlocker,
  offerDiscovery,
  offerStatus,
} from "./offer-rules";

const dateTime = (value: string | null | undefined) =>
  value ? new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";
const money = (value: string, currency: string) =>
  Number.isFinite(Number(value))
    ? new Intl.NumberFormat("fr-FR", { style: "currency", currency }).format(Number(value))
    : `${value} ${currency}`;
const pageFrom = (value: string | null) => (value && /^\d+$/.test(value) ? Math.min(10_000, Number(value)) : 0);
const validId = (value?: string | null): value is string => Boolean(value && /^[0-9a-f-]{36}$/i.test(value));

function OfferActions({ offer }: { offer: OfferOperationState }) {
  const session = useAdminSession();
  const visible = (action: OfferAction) => session.can(offerActionPermission[action]);
  const canApplyToAccount = visible("PREVIEW_FOR_ACCOUNT") && visible("APPLY_FOR_ACCOUNT");
  const applyToAccountAvailable =
    offer.availableActions.includes("PREVIEW_FOR_ACCOUNT") && offer.availableActions.includes("APPLY_FOR_ACCOUNT");
  const trigger = (action: OfferAction, label: string, icon: ReactNode, destructive = false) => {
    const available = offer.availableActions.includes(action);
    return (
      <Button
        className={destructive ? "text-destructive hover:text-destructive" : undefined}
        disabled={!available}
        size="sm"
        title={!available ? (offerActionReason(action, offer) ?? undefined) : undefined}
        variant="ghost"
      >
        {icon}
        {label}
      </Button>
    );
  };
  return (
    <div className="flex flex-wrap justify-end gap-1">
      {visible("UPDATE") ? (
        offer.availableActions.includes("UPDATE") &&
        session.can(adminPermissions.offersReadEditableDefinition) &&
        session.can(adminPermissions.offersPreviewUpdateDefinition) ? (
          <Button asChild size="sm" variant="ghost">
            <Link to={`/admin/offers/${offer.id}/edit`}>
              <PencilSimpleIcon />
              Modifier
            </Link>
          </Button>
        ) : (
          trigger("UPDATE", "Modifier", <PencilSimpleIcon />)
        )
      ) : null}
      {visible("PUBLISH") ? (
        <OfferPublicationDialog mode="PUBLISH" offer={offer} trigger={trigger("PUBLISH", "Publier", <PlayIcon />)} />
      ) : null}
      {visible("RESTORE") ? (
        <OfferPublicationDialog mode="RESTORE" offer={offer} trigger={trigger("RESTORE", "Restaurer", <PlayIcon />)} />
      ) : null}
      {visible("RETIRE") ? (
        <OfferReasonDialog action="RETIRE" offer={offer} trigger={trigger("RETIRE", "Retirer", <StopIcon />, true)} />
      ) : null}
      {visible("DUPLICATE") ? (
        <OfferDuplicateDialog offer={offer} trigger={trigger("DUPLICATE", "Dupliquer", <CopyIcon />)} />
      ) : null}
      {visible("REVISE") ? (
        <OfferReasonDialog action="REVISE" offer={offer} trigger={trigger("REVISE", "Réviser", <CopyIcon />)} />
      ) : null}
      {canApplyToAccount ? (
        <OfferApplyAccountDialog
          offer={offer}
          trigger={
            <Button
              disabled={!applyToAccountAvailable}
              size="sm"
              title={
                !applyToAccountAvailable
                  ? (offerActionReason("PREVIEW_FOR_ACCOUNT", offer) ??
                    offerActionReason("APPLY_FOR_ACCOUNT", offer) ??
                    undefined)
                  : undefined
              }
              variant="ghost"
            >
              <UserSwitchIcon />
              Appliquer à un compte
            </Button>
          }
        />
      ) : null}
      {visible("ARCHIVE") ? (
        <OfferReasonDialog
          action="ARCHIVE"
          offer={offer}
          trigger={trigger("ARCHIVE", "Archiver", <ArchiveIcon />, true)}
        />
      ) : null}
      {visible("DELETE_DRAFT") ? (
        <OfferReasonDialog
          action="DELETE_DRAFT"
          offer={offer}
          trigger={trigger("DELETE_DRAFT", "Supprimer", <TrashIcon />, true)}
        />
      ) : null}
    </div>
  );
}

function OperationsPanel({ offer }: { offer: OfferOperationState }) {
  const status = offerStatus[offer.status];
  const blockers = Object.entries(offer.blockedActions).flatMap(([action, values]) =>
    (values ?? []).map((value) => ({ action: action as OfferAction, value })),
  );
  return (
    <section className="space-y-6">
      <OfferActions offer={offer} />
      <dl className="grid gap-5 border-y py-5 sm:grid-cols-3">
        <div>
          <dt className="text-xs text-muted-foreground">Statut</dt>
          <dd className="mt-1">
            <StatusBadge dot={false} tone={status.tone}>
              {status.label}
            </StatusBadge>
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Révision</dt>
          <dd className="mt-1 font-medium">R{offer.revisionNumber}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Version concurrente</dt>
          <dd className="mt-1 font-medium tabular-nums">{offer.version}</dd>
        </div>
      </dl>
      {blockers.length ? (
        <section>
          <h2 className="text-sm font-semibold">Blocages actuels</h2>
          <ul className="mt-3 divide-y border-y">
            {blockers.map(({ action, value }) => (
              <li className="py-3 text-sm" key={`${action}:${value}`}>
                <span className="font-medium">{action.replaceAll("_", " ")}</span>
                <span className="mt-1 block text-muted-foreground">{offerBlocker[value]}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : (
        <p className="text-sm text-muted-foreground">Aucun blocage opérationnel.</p>
      )}
    </section>
  );
}

function OverviewPanel({ offerId }: { offerId: string }) {
  const session = useAdminSession();
  const detail = useQuery({
    queryKey: adminCommercialKeys.offers.detail(offerId),
    queryFn: ({ signal }) => adminOfferApi.detail(offerId, { signal }),
    enabled: session.can(adminPermissions.offersRead),
  });
  if (!session.can(adminPermissions.offersRead)) return <PermissionState />;
  if (detail.isLoading) return <LoadingState />;
  if (detail.isError || !detail.data) return <ErrorState retry={() => void detail.refetch()} />;
  const offer = detail.data;
  const status = offerStatus[offer.status];
  return (
    <div className="grid gap-8 lg:grid-cols-[1.1fr_0.9fr]">
      <section>
        <dl className="grid gap-x-8 gap-y-5 border-y py-5 sm:grid-cols-2">
          <div>
            <dt className="text-xs text-muted-foreground">Statut</dt>
            <dd className="mt-1">
              <StatusBadge dot={false} tone={status.tone}>
                {status.label}
              </StatusBadge>
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Campagne</dt>
            <dd className="mt-1 font-medium">
              {offer.campaign.name} · R{offer.campaign.revisionNumber}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Découverte</dt>
            <dd className="mt-1 font-medium">
              {offerDiscovery[offer.discovery]}
              {offer.customerCodeConfigured ? " · code configuré" : ""}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Acceptation</dt>
            <dd className="mt-1 font-medium">{offerAcceptance[offer.acceptance]}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Début</dt>
            <dd className="mt-1 font-medium">{dateTime(offer.startsAt)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Fin</dt>
            <dd className="mt-1 font-medium">{dateTime(offer.endsAt)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Capacité globale</dt>
            <dd className="mt-1 font-medium">{offer.globalLimit ?? "Sans limite"}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Par compte</dt>
            <dd className="mt-1 font-medium">{offer.perAccountLimit ?? "Sans limite"}</dd>
          </div>
          {offer.description ? (
            <div className="sm:col-span-2">
              <dt className="text-xs text-muted-foreground">Description</dt>
              <dd className="mt-1 whitespace-pre-wrap text-sm">{offer.description}</dd>
            </div>
          ) : null}
        </dl>
      </section>
      <section className="space-y-5">
        <div>
          <h2 className="text-sm font-semibold">Contenu accepté</h2>
          <p className="mt-1 text-xs text-muted-foreground">Révisions et prix exacts figés dans l’offre.</p>
        </div>
        <dl className="divide-y border-y">
          <div className="py-3">
            <dt className="text-xs text-muted-foreground">Forfait</dt>
            <dd className="mt-1 font-medium">{offer.resolvedSelection.plan.name}</dd>
            <dd className="text-xs text-muted-foreground">
              {money(offer.resolvedSelection.plan.amount, offer.resolvedSelection.plan.currencyCode)} ·{" "}
              {offer.resolvedSelection.plan.billingCycle}
            </dd>
          </div>
          <div className="py-3">
            <dt className="text-xs text-muted-foreground">Add-ons</dt>
            <dd className="mt-1 text-sm">
              {offer.resolvedSelection.addOns.length
                ? offer.resolvedSelection.addOns
                    .map((item) => `${item.name}${item.pricingMode === "FREE" ? " (offert)" : ""}`)
                    .join(", ")
                : "Aucun"}
            </dd>
          </div>
          <div className="py-3">
            <dt className="text-xs text-muted-foreground">Packs de capacité</dt>
            <dd className="mt-1 text-sm">
              {offer.resolvedSelection.quotaPackages.length
                ? offer.resolvedSelection.quotaPackages
                    .map(
                      (item) => `${item.quantity ?? 1} × ${item.name}${item.pricingMode === "FREE" ? " (offert)" : ""}`,
                    )
                    .join(", ")
                : "Aucun"}
            </dd>
          </div>
          <div className="py-3">
            <dt className="text-xs text-muted-foreground">Avantage</dt>
            <dd className="mt-1 text-sm">
              {offer.effects.discountType === "NONE"
                ? "Aucune réduction"
                : offer.effects.discountType === "FIXED"
                  ? `${offer.effects.discountAmount} de réduction`
                  : `${offer.effects.percentage}% · plafond ${offer.effects.percentageCap}`}
            </dd>
          </div>
        </dl>
      </section>
    </div>
  );
}

function StatsPanel({ offerId }: { offerId: string }) {
  const session = useAdminSession();
  const stats = useQuery({
    queryKey: adminCommercialKeys.offers.stats(offerId),
    queryFn: ({ signal }) => adminOfferApi.stats(offerId, { signal }),
    enabled: session.can(adminPermissions.offersReadStats),
  });
  if (!session.can(adminPermissions.offersReadStats)) return <PermissionState />;
  if (stats.isLoading) return <LoadingState />;
  if (stats.isError || !stats.data) return <ErrorState retry={() => void stats.refetch()} />;
  const values = [
    ["Réservées", stats.data.reserved],
    ["Appliquées", stats.data.applied],
    ["Annulées", stats.data.cancelled],
    ["Échouées", stats.data.failed],
    ["Capacité restante", stats.data.remainingGlobalCapacity ?? "Sans limite"],
  ] as const;
  return (
    <section>
      <dl className="grid border-y sm:grid-cols-2 lg:grid-cols-5">
        {values.map(([label, value], index) => (
          <div className={`px-5 py-5 ${index ? "border-t sm:border-s sm:border-t-0" : ""}`} key={label}>
            <dt className="text-xs text-muted-foreground">{label}</dt>
            <dd className="mt-2 text-2xl font-semibold tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
      <p className="mt-4 text-sm text-muted-foreground">
        Ces compteurs décrivent le résultat opérationnel de cette lignée d’offre; ils ne représentent pas du revenu
        encaissé.
      </p>
    </section>
  );
}

const redemptionColumn = createDataColumns<OfferRedemption>();
function redemptionColumns(identities: Map<string, OfferRedemptionIdentity>, revealAllowed: boolean) {
  return redemptionColumn.columns([
    redemptionColumn.accessor("reservedAt", {
      header: ({ column }) => <SortHeader column={column}>Réservée</SortHeader>,
      cell: ({ row }) => <span className="text-sm">{dateTime(row.original.reservedAt)}</span>,
    }),
    redemptionColumn.display({
      id: "account",
      header: "Compte",
      cell: ({ row }) =>
        revealAllowed ? (
          (identities.get(row.original.id)?.accountName ?? <span className="text-muted-foreground">Non révélé</span>)
        ) : (
          <span className="font-mono text-xs text-muted-foreground">Identité protégée</span>
        ),
    }),
    redemptionColumn.accessor("surface", {
      header: ({ column }) => <SortHeader column={column}>Origine</SortHeader>,
      cell: ({ row }) => (row.original.surface === "CLIENT" ? "Client" : "Opérateur"),
    }),
    redemptionColumn.accessor("status", {
      header: ({ column }) => <SortHeader column={column}>Statut</SortHeader>,
      cell: ({ row }) => (
        <StatusBadge
          dot={false}
          tone={
            row.original.status === "APPLIED" ? "success" : row.original.status === "FAILED" ? "warning" : "neutral"
          }
        >
          {row.original.status === "RESERVED"
            ? "Réservée"
            : row.original.status === "APPLIED"
              ? "Appliquée"
              : row.original.status === "CANCELLED"
                ? "Annulée"
                : "Échouée"}
        </StatusBadge>
      ),
    }),
    redemptionColumn.display({
      id: "actions",
      meta: tableActionsColumnMeta(1),
      header: "Actions",
      cell: ({ row }) => (
        <TableActionsCell label={`Actions de l’acceptation ${row.original.id}`}>
          <RowAction
            icon={<ArrowRightIcon className="rtl:rotate-180" />}
            label="Ouvrir l’acceptation"
            to={`/admin/offers/${row.original.offerId}/redemptions/${row.original.id}`}
          />
        </TableActionsCell>
      ),
    }),
  ]);
}

function RedemptionsPanel({ offerId }: { offerId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = pageFrom(params.get("page"));
  const status = params.get("status") || undefined;
  const identityKey = `${page}:${status ?? "ALL"}`;
  const [identityState, setIdentityState] = useState<{
    key: string;
    values: Map<string, OfferRedemptionIdentity>;
  }>({ key: "", values: new Map() });
  const identities =
    identityState.key === identityKey ? identityState.values : new Map<string, OfferRedemptionIdentity>();
  const filters = { page, status: status ?? "ALL" };
  const redemptions = useQuery({
    queryKey: adminCommercialKeys.offers.redemptions(offerId, filters),
    queryFn: ({ signal }) =>
      adminOfferApi.redemptions(
        offerId,
        {
          page,
          size: 20,
          status: status as OfferRedemption["status"] | undefined,
          sort: "reservedAt",
          direction: "desc",
        },
        { signal },
      ),
    enabled: session.can(adminPermissions.offersReadRedemptions),
  });
  const reveal = useMutation({
    mutationFn: () =>
      adminOfferApi.resolveRedemptionIdentities(offerId, redemptions.data?.content.map((item) => item.id) ?? []),
    onSuccess: (data) =>
      setIdentityState({ key: identityKey, values: new Map(data.map((item) => [item.redemptionId, item])) }),
    onError: () => toast.error("Les identités n’ont pas pu être révélées."),
  });
  if (!session.can(adminPermissions.offersReadRedemptions)) return <PermissionState />;
  if (redemptions.isLoading) return <LoadingState />;
  if (redemptions.isError || !redemptions.data) return <ErrorState retry={() => void redemptions.refetch()} />;
  return (
    <section className="space-y-4">
      <div className="flex flex-wrap justify-between gap-3">
        <select
          aria-label="Statut des acceptations"
          className="h-9 rounded-md border bg-background px-3 text-sm"
          onChange={(event) => {
            const next = new URLSearchParams(params);
            if (event.target.value) next.set("status", event.target.value);
            else next.delete("status");
            next.delete("page");
            setParams(next, { replace: true });
          }}
          value={status ?? ""}
        >
          <option value="">Tous les statuts</option>
          <option value="RESERVED">Réservées</option>
          <option value="APPLIED">Appliquées</option>
          <option value="CANCELLED">Annulées</option>
          <option value="FAILED">Échouées</option>
        </select>
        {session.can(adminPermissions.offersReadRedemptionIdentities) ? (
          <Button
            disabled={!redemptions.data.content.length || reveal.isPending}
            onClick={() => reveal.mutate()}
            size="sm"
            variant="outline"
          >
            <EyeIcon />
            {reveal.isPending ? "Révélation…" : "Révéler cette page"}
          </Button>
        ) : null}
      </div>
      <DataTable
        columns={redemptionColumns(identities, session.can(adminPermissions.offersReadRedemptionIdentities))}
        data={redemptions.data.content}
        emptyState={<EmptyState title="Aucune acceptation" />}
        getRowId={(item) => item.id}
      />
      <PaginationBar
        onPageChange={(nextPage) => {
          const next = new URLSearchParams(params);
          if (nextPage) next.set("page", String(nextPage));
          else next.delete("page");
          setParams(next, { replace: true });
        }}
        page={redemptions.data.page}
        totalElements={redemptions.data.totalElements}
        totalPages={redemptions.data.totalPages}
      />
    </section>
  );
}

function ComparisonDetails({ comparison }: { comparison: OfferComparison }) {
  return (
    <div className="space-y-5">
      <p className="text-sm text-muted-foreground">
        {comparison.sameLineage ? "Même lignée" : "Lignées indépendantes"}
        {comparison.directSuccessor ? " · succession directe" : ""}
      </p>
      {comparison.changedFields.length ? (
        <ul className="divide-y border-y">
          {comparison.changedFields.map((field) => (
            <li className="py-2 text-sm" key={field}>
              {field.replaceAll(".", " › ")}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted-foreground">Aucune différence.</p>
      )}
      <div className="grid gap-6 border-t pt-5 sm:grid-cols-2">
        {[comparison.left, comparison.right].map((candidate) => (
          <dl className="space-y-3" key={candidate.id}>
            <div>
              <dt className="text-xs text-muted-foreground">Offre</dt>
              <dd className="font-semibold">{candidate.name}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Révision</dt>
              <dd>R{candidate.revisionNumber}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Forfait</dt>
              <dd>{candidate.resolvedSelection.plan.name}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Fenêtre</dt>
              <dd className="text-sm">
                {dateTime(candidate.startsAt)} → {dateTime(candidate.endsAt)}
              </dd>
            </div>
          </dl>
        ))}
      </div>
    </div>
  );
}

function RevisionsPanel({ offerId }: { offerId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = pageFrom(params.get("page"));
  const against = validId(params.get("against")) ? (params.get("against") as string) : "";
  const revisions = useQuery({
    queryKey: adminCommercialKeys.offers.revisions(offerId, page),
    queryFn: ({ signal }) => adminOfferApi.revisions(offerId, page, 20, { signal }),
    enabled: session.can(adminPermissions.offersRevisions),
  });
  const comparison = useQuery({
    queryKey: adminCommercialKeys.offers.comparison(offerId, against),
    queryFn: ({ signal }) => adminOfferApi.compare(offerId, against, { signal }),
    enabled: Boolean(against && session.can(adminPermissions.offersCompare)),
  });
  if (!session.can(adminPermissions.offersRevisions) && !session.can(adminPermissions.offersCompare))
    return <PermissionState />;
  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(280px,0.7fr)_1.3fr]">
      <section className="border-y">
        {!session.can(adminPermissions.offersRevisions) ? (
          <PermissionState />
        ) : revisions.isLoading ? (
          <LoadingState />
        ) : revisions.isError || !revisions.data ? (
          <ErrorState retry={() => void revisions.refetch()} />
        ) : (
          <>
            <ul className="divide-y">
              {revisions.data.content.map((revision) => {
                const status = offerStatus[revision.status];
                return (
                  <li className="flex items-center gap-3 py-3" key={revision.id}>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium">Révision {revision.revisionNumber}</p>
                      <p className="text-xs text-muted-foreground">{dateTime(revision.createdAt)}</p>
                    </div>
                    <StatusBadge dot={false} tone={status.tone}>
                      {status.label}
                    </StatusBadge>
                    {session.can(adminPermissions.offersCompare) && revision.id !== offerId ? (
                      <Button
                        onClick={() => {
                          const next = new URLSearchParams(params);
                          next.set("against", revision.id);
                          setParams(next, { replace: true });
                        }}
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
              onPageChange={(nextPage) => {
                const next = new URLSearchParams(params);
                if (nextPage) next.set("page", String(nextPage));
                else next.delete("page");
                setParams(next, { replace: true });
              }}
              page={revisions.data.page}
              totalElements={revisions.data.totalElements}
              totalPages={revisions.data.totalPages}
            />
          </>
        )}
      </section>
      <section className="min-h-56 border-y py-4">
        {!against ? (
          <EmptyState title="Choisissez une révision à comparer" />
        ) : comparison.isLoading ? (
          <LoadingState />
        ) : comparison.isError || !comparison.data ? (
          <ErrorState retry={() => void comparison.refetch()} />
        ) : (
          <ComparisonDetails comparison={comparison.data} />
        )}
      </section>
    </div>
  );
}

function HistoryPanel({ offerId }: { offerId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = pageFrom(params.get("page"));
  const history = useQuery({
    queryKey: adminCommercialKeys.offers.history(offerId, page),
    queryFn: ({ signal }) => adminOfferApi.history(offerId, page, 20, { signal }),
    enabled: session.can(adminPermissions.offersHistory),
  });
  if (!session.can(adminPermissions.offersHistory)) return <PermissionState />;
  if (history.isLoading) return <LoadingState />;
  if (history.isError || !history.data) return <ErrorState retry={() => void history.refetch()} />;
  if (!history.data.totalElements) return <EmptyState title="Aucun événement" />;
  return (
    <section className="border-y">
      <ol className="divide-y">
        {history.data.content.map((entry) => (
          <li className="grid gap-2 py-4 sm:grid-cols-[1fr_auto]" key={entry.id}>
            <div>
              <p className="text-sm font-medium">{entry.action.replaceAll("_", " ")}</p>
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
        onPageChange={(nextPage) => {
          const next = new URLSearchParams(params);
          if (nextPage) next.set("page", String(nextPage));
          else next.delete("page");
          setParams(next, { replace: true });
        }}
        page={history.data.page}
        totalElements={history.data.totalElements}
        totalPages={history.data.totalPages}
      />
    </section>
  );
}

function OwnerDialog({ owner, trigger }: { owner: import("@/api/offer-contracts").OfferOwner; trigger: ReactNode }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [selected, setSelected] = useState<OfferOwnerChoice | null>(null);
  const [reason, setReason] = useState("");
  const choices = useQuery({
    queryKey: adminCommercialKeys.offers.ownerChoices({ search: debounced }),
    queryFn: ({ signal }) =>
      adminOfferApi.ownerChoices({ search: debounced || undefined, page: 0, size: 20 }, { signal }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.offersChooseOwners, open),
  });
  const mutation = useMutation({
    mutationFn: () =>
      adminOfferApi.reassignOwner(owner.offerId, {
        lineageVersion: owner.lineageVersion,
        ownerAdminUserId: selected?.adminUserId ?? "",
        reason: reason.trim(),
      }),
    onSuccess: async () => {
      setOpen(false);
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.offers.owner(owner.offerId) });
      toast.success("Responsable réassigné");
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "La réassignation a échoué."),
  });
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Réassigner le responsable</DialogTitle>
          <DialogDescription>Le responsable porte la lignée entière, pas seulement cette révision.</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <Input
            aria-label="Rechercher un responsable"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Nom ou email…"
            value={search}
          />
          <div className="max-h-48 overflow-y-auto border-y">
            {choices.data?.content.map((choice) => (
              <label
                className="flex cursor-pointer items-center gap-3 border-b py-2 last:border-0"
                key={choice.adminUserId}
              >
                <input
                  checked={selected?.adminUserId === choice.adminUserId}
                  className="accent-primary"
                  name="offer-owner"
                  onChange={() => setSelected(choice)}
                  type="radio"
                />
                <span>
                  <span className="block text-sm font-medium">{choice.displayName ?? choice.username}</span>
                  <span className="block text-xs text-muted-foreground">{choice.email}</span>
                </span>
              </label>
            ))}
          </div>
          <div className="space-y-2">
            <Label htmlFor="offer-owner-reason">Motif</Label>
            <Textarea
              id="offer-owner-reason"
              maxLength={500}
              onChange={(event) => setReason(event.target.value)}
              rows={3}
              value={reason}
            />
          </div>
        </div>
        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="ghost">
            Annuler
          </Button>
          <Button disabled={!selected || !reason.trim() || mutation.isPending} onClick={() => mutation.mutate()}>
            Réassigner
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function OwnerPanel({ offerId }: { offerId: string }) {
  const session = useAdminSession();
  const owner = useQuery({
    queryKey: adminCommercialKeys.offers.owner(offerId),
    queryFn: ({ signal }) => adminOfferApi.owner(offerId, { signal }),
    enabled: session.can(adminPermissions.offersReadOwner),
  });
  if (!session.can(adminPermissions.offersReadOwner)) return <PermissionState />;
  if (owner.isLoading) return <LoadingState />;
  if (owner.isError || !owner.data) return <ErrorState retry={() => void owner.refetch()} />;
  return (
    <section className="flex flex-col justify-between gap-5 border-y py-5 sm:flex-row sm:items-center">
      <div>
        <h2 className="font-medium">{owner.data.displayName ?? owner.data.username}</h2>
        <p className="text-sm text-muted-foreground">{owner.data.email}</p>
        <p className="mt-1 text-xs text-muted-foreground">
          {owner.data.active ? "Opérateur actif" : "Opérateur inactif"}
        </p>
      </div>
      {session.can(adminPermissions.offersReassignOwner) ? (
        <OwnerDialog
          owner={owner.data}
          trigger={
            <Button variant="outline">
              <UserSwitchIcon />
              Réassigner
            </Button>
          }
        />
      ) : null}
    </section>
  );
}

function RedemptionDetailPanel({ offerId, redemptionId }: { offerId: string; redemptionId: string }) {
  const session = useAdminSession();
  const redemption = useQuery({
    queryKey: adminCommercialKeys.offers.redemption(offerId, redemptionId),
    queryFn: ({ signal }) => adminOfferApi.redemption(offerId, redemptionId, { signal }),
    enabled: session.can(adminPermissions.offersReadRedemptionDetail),
  });
  const [identity, setIdentity] = useState<OfferRedemptionIdentity | null>(null);
  const reveal = useMutation({
    mutationFn: () => adminOfferApi.resolveRedemptionIdentities(offerId, [redemptionId]),
    onSuccess: (data) => setIdentity(data[0] ?? null),
  });
  if (!session.can(adminPermissions.offersReadRedemptionDetail)) return <PermissionState />;
  if (redemption.isLoading) return <LoadingState />;
  if (redemption.isError || !redemption.data) return <ErrorState retry={() => void redemption.refetch()} />;
  const item = redemption.data;
  return (
    <section className="space-y-6">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to={`/admin/offers/${offerId}/redemptions`}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          Acceptations
        </Link>
      </Button>
      <dl className="grid gap-x-8 gap-y-5 border-y py-5 sm:grid-cols-2">
        <div>
          <dt className="text-xs text-muted-foreground">Statut</dt>
          <dd className="mt-1 font-medium">{item.status}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Origine</dt>
          <dd className="mt-1 font-medium">{item.surface === "CLIENT" ? "Client" : "Opérateur"}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Réservée</dt>
          <dd className="mt-1 font-medium">{dateTime(item.reservedAt)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Appliquée</dt>
          <dd className="mt-1 font-medium">{dateTime(item.appliedAt)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Prix accepté</dt>
          <dd className="mt-1 font-medium">{money(item.acceptedTerms.finalPrice, item.acceptedTerms.currencyCode)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Compte</dt>
          <dd className="mt-1 font-medium">{identity?.accountName ?? "Identité protégée"}</dd>
          {session.can(adminPermissions.offersReadRedemptionIdentities) && !identity ? (
            <Button className="mt-2" onClick={() => reveal.mutate()} size="sm" variant="outline">
              <EyeIcon />
              Révéler
            </Button>
          ) : null}
        </div>
        {item.terminalReason ? (
          <div className="sm:col-span-2">
            <dt className="text-xs text-muted-foreground">Raison terminale</dt>
            <dd className="mt-1 text-sm">{item.terminalReason}</dd>
          </div>
        ) : null}
      </dl>
      {item.operation ? (
        <Alert>
          <AlertTitle>Opération d’abonnement</AlertTitle>
          <AlertDescription>
            {item.operation.status} · {item.operation.sourcePlanCode} → {item.operation.targetPlanCode}
          </AlertDescription>
        </Alert>
      ) : null}
    </section>
  );
}

export function AdminOfferDetailPage() {
  const session = useAdminSession();
  const { offerId, tab, redemptionId } = useParams();
  const id = validId(offerId) ? offerId : "";
  const operations = useQuery({
    queryKey: adminCommercialKeys.offers.operations(id),
    queryFn: ({ signal }) => adminOfferApi.operations(id, { signal }),
    enabled: Boolean(id && session.can(adminPermissions.offersReadOperations)),
  });
  if (!id)
    return (
      <ErrorState description="L’identifiant présent dans l’adresse est invalide." title="Adresse d’offre invalide" />
    );
  const tabs = [
    session.can(adminPermissions.offersRead) ? { label: "Vue d’ensemble", to: `/admin/offers/${id}`, end: true } : null,
    session.can(adminPermissions.offersReadOperations)
      ? { label: "Opérations", to: `/admin/offers/${id}/operations` }
      : null,
    session.can(adminPermissions.offersReadStats) ? { label: "Résultats", to: `/admin/offers/${id}/results` } : null,
    session.can(adminPermissions.offersReadRedemptions)
      ? { label: "Acceptations", to: `/admin/offers/${id}/redemptions` }
      : null,
    session.can(adminPermissions.offersRevisions) || session.can(adminPermissions.offersCompare)
      ? { label: "Révisions", to: `/admin/offers/${id}/revisions` }
      : null,
    session.can(adminPermissions.offersHistory) ? { label: "Historique", to: `/admin/offers/${id}/history` } : null,
    session.can(adminPermissions.offersReadOwner) ? { label: "Responsable", to: `/admin/offers/${id}/owner` } : null,
  ].filter((item): item is { label: string; to: string; end?: boolean } => Boolean(item));
  const validTabs = new Set(["operations", "results", "redemptions", "revisions", "history", "owner"]);
  if (tab && !validTabs.has(tab)) return <Navigate replace to={tabs[0]?.to ?? "/admin"} />;
  const title = operations.data?.name ?? "Offre";
  return (
    <div className="space-y-7">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to="/admin/offers">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Offres
        </Link>
      </Button>
      <PageHeader actions={operations.data ? <OfferActions offer={operations.data} /> : undefined} title={title} />
      {tabs.length > 1 ? <SectionTabs ariaLabel="Sections de l’offre" tabs={tabs} /> : null}
      {redemptionId ? (
        <RedemptionDetailPanel offerId={id} redemptionId={redemptionId} />
      ) : !tab ? (
        <OverviewPanel offerId={id} />
      ) : tab === "operations" ? (
        !session.can(adminPermissions.offersReadOperations) ? (
          <PermissionState />
        ) : operations.isLoading ? (
          <LoadingState />
        ) : operations.isError || !operations.data ? (
          <ErrorState retry={() => void operations.refetch()} />
        ) : (
          <OperationsPanel offer={operations.data} />
        )
      ) : tab === "results" ? (
        <StatsPanel offerId={id} />
      ) : tab === "redemptions" ? (
        <RedemptionsPanel offerId={id} />
      ) : tab === "revisions" ? (
        <RevisionsPanel offerId={id} />
      ) : tab === "history" ? (
        <HistoryPanel offerId={id} />
      ) : (
        <OwnerPanel offerId={id} />
      )}
    </div>
  );
}
