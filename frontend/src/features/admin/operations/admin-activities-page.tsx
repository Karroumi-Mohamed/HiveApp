import { EyeIcon } from "@phosphor-icons/react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { adminApi } from "@/api/admin-api";
import type { AuditActorSurface, AuditOutcome, PlatformActivity } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { StatusText } from "@/components/patterns/status-text";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import {
  actorSurfaceLabel,
  auditOutcomePresentation,
  formatEvidence,
  operationDateTime,
  technicalActionLabel,
} from "./operations-presentation";

const columns = createDataColumns<PlatformActivity>();
const ALL = "ALL";

function ActorCell({ activity }: { activity: PlatformActivity }) {
  if (activity.actorIdentity)
    return (
      <div>
        <div className="font-medium">{activity.actorIdentity.displayName}</div>
        <div className="text-xs text-muted-foreground">{activity.actorIdentity.email}</div>
      </div>
    );
  return (
    <div>
      <div>{actorSurfaceLabel(activity.actorSurface)}</div>
      <div className="font-mono text-xs text-muted-foreground">{activity.actorUserId ?? "—"}</div>
    </div>
  );
}

function ActivitySheet({ id, onClose }: { id: string | null; onClose: () => void }) {
  const session = useAdminSession();
  const canPayload = session.can(adminPermissions.activitiesReadPayload);
  const detail = useQuery({
    queryKey: ["admin", "activities", id],
    queryFn: () => {
      if (!id) throw new Error("Activity id is required");
      return adminApi.activity(id);
    },
    enabled: Boolean(id),
  });
  const payload = useQuery({
    queryKey: ["admin", "activities", id, "payload"],
    queryFn: () => {
      if (!id) throw new Error("Activity id is required");
      return adminApi.activityPayload(id);
    },
    enabled: Boolean(id && canPayload && detail.data?.payloadAvailable),
  });
  const item = detail.data;
  return (
    <Sheet onOpenChange={(open) => !open && onClose()} open={Boolean(id)}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-xl" closeLabel="Fermer">
        <SheetHeader className="border-b">
          <SheetTitle>{item ? technicalActionLabel(item.action) : "Activité"}</SheetTitle>
          <SheetDescription>{item ? operationDateTime(item.occurredAt) : "Chargement…"}</SheetDescription>
        </SheetHeader>
        {detail.isError ? <ErrorState retry={() => detail.refetch()} /> : null}
        {item ? (
          <div className="space-y-6 px-4 pb-8 text-sm">
            <dl className="grid grid-cols-[9rem_1fr] gap-x-4 gap-y-3">
              <dt className="text-muted-foreground">Action technique</dt>
              <dd className="font-mono text-xs break-all">{item.action}</dd>
              <dt className="text-muted-foreground">Résultat</dt>
              <dd>
                <StatusText tone={auditOutcomePresentation(item.outcome).tone}>
                  {auditOutcomePresentation(item.outcome).label}
                </StatusText>
              </dd>
              <dt className="text-muted-foreground">Acteur</dt>
              <dd>
                <ActorCell activity={item} />
              </dd>
              <dt className="text-muted-foreground">Ressource</dt>
              <dd>
                {item.resourceType}
                {item.resourceId ? ` · ${item.resourceId}` : ""}
              </dd>
              <dt className="text-muted-foreground">Requête</dt>
              <dd>{[item.requestMethod, item.requestPath].filter(Boolean).join(" ") || "—"}</dd>
              <dt className="text-muted-foreground">Corrélation</dt>
              <dd className="font-mono text-xs break-all">{item.requestId ?? "—"}</dd>
            </dl>
            {canPayload && item.payloadAvailable ? (
              payload.isError ? (
                <ErrorState
                  description="Les données d’audit n’ont pas pu être chargées."
                  retry={() => payload.refetch()}
                />
              ) : payload.data ? (
                <div className="space-y-4">
                  <section>
                    <h3 className="mb-2 font-semibold">Entrée assainie</h3>
                    <pre className="max-h-72 overflow-auto rounded-md border bg-muted/35 p-3 text-xs whitespace-pre-wrap break-all">
                      {formatEvidence(payload.data.requestData)}
                    </pre>
                  </section>
                  <section>
                    <h3 className="mb-2 font-semibold">Résultat assaini</h3>
                    <pre className="max-h-72 overflow-auto rounded-md border bg-muted/35 p-3 text-xs whitespace-pre-wrap break-all">
                      {formatEvidence(payload.data.resultData)}
                    </pre>
                  </section>
                  {payload.data.failureType ? (
                    <p>
                      <span className="text-muted-foreground">Type d’échec : </span>
                      {payload.data.failureType}
                    </p>
                  ) : null}
                </div>
              ) : null
            ) : item.payloadAvailable ? (
              <p className="border-t pt-4 text-muted-foreground">Le contenu détaillé exige une autorisation séparée.</p>
            ) : null}
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

function MobileActivities({
  activities,
  isLoading,
  onOpen,
}: {
  activities: PlatformActivity[];
  isLoading: boolean;
  onOpen: (id: string) => void;
}) {
  if (isLoading)
    return (
      <div className="md:hidden">
        <LoadingState />
      </div>
    );
  if (!activities.length)
    return (
      <div className="md:hidden">
        <EmptyState title="Aucune activité" />
      </div>
    );
  return (
    <div className="divide-y md:hidden">
      {activities.map((activity) => {
        const outcome = auditOutcomePresentation(activity.outcome);
        return (
          <article className="space-y-3 p-4" key={activity.id}>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <p className="font-medium">{technicalActionLabel(activity.action)}</p>
                <p className="mt-0.5 break-all font-mono text-[0.7rem] text-muted-foreground">{activity.action}</p>
              </div>
              <TableActionsCell label="Actions sur l’activité">
                <RowAction icon={<EyeIcon />} label="Examiner" onClick={() => onOpen(activity.id)} />
              </TableActionsCell>
            </div>
            <dl className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <dt className="text-muted-foreground">Date</dt>
                <dd className="mt-0.5 font-medium">{operationDateTime(activity.occurredAt)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Résultat</dt>
                <dd className="mt-0.5">
                  <StatusText tone={outcome.tone}>{outcome.label}</StatusText>
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Acteur</dt>
                <dd className="mt-0.5 font-medium">
                  {activity.actorIdentity?.displayName ?? actorSurfaceLabel(activity.actorSurface)}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Ressource</dt>
                <dd className="mt-0.5 truncate font-medium">{activity.resourceType}</dd>
              </div>
            </dl>
          </article>
        );
      })}
    </div>
  );
}

export function AdminActivitiesPage() {
  const [page, setPage] = useState(0);
  const [outcome, setOutcome] = useState<string>(ALL);
  const [surface, setSurface] = useState<string>(ALL);
  const [actionPrefix, setActionPrefix] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const query = useQuery({
    queryKey: ["admin", "activities", page, outcome, surface, actionPrefix],
    queryFn: () =>
      adminApi.activities({
        page,
        size: 25,
        outcome: outcome === ALL ? undefined : (outcome as AuditOutcome),
        actorSurface: surface === ALL ? undefined : (surface as AuditActorSurface),
        actionPrefix: actionPrefix.trim() || undefined,
      }),
    placeholderData: keepPreviousData,
  });
  const tableColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("occurredAt", {
          header: "Date",
          cell: ({ getValue }) => <span className="whitespace-nowrap text-sm">{operationDateTime(getValue())}</span>,
        }),
        columns.display({
          id: "action",
          header: "Action",
          cell: ({ row }) => (
            <div>
              <div className="font-medium">{technicalActionLabel(row.original.action)}</div>
              <div className="font-mono text-xs text-muted-foreground">{row.original.action}</div>
            </div>
          ),
        }),
        columns.display({ id: "actor", header: "Acteur", cell: ({ row }) => <ActorCell activity={row.original} /> }),
        columns.display({
          id: "resource",
          header: "Ressource",
          cell: ({ row }) => (
            <div>
              <div>{row.original.resourceType}</div>
              <div className="max-w-52 truncate font-mono text-xs text-muted-foreground">
                {row.original.resourceId ?? "—"}
              </div>
            </div>
          ),
        }),
        columns.accessor("outcome", {
          header: "Résultat",
          cell: ({ getValue }) => {
            const view = auditOutcomePresentation(getValue());
            return <StatusText tone={view.tone}>{view.label}</StatusText>;
          },
        }),
        columns.display({
          id: "actions",
          header: "Actions",
          meta: tableActionsColumnMeta(1),
          cell: ({ row }) => (
            <TableActionsCell label="Actions sur l’activité">
              <RowAction icon={<EyeIcon />} label="Examiner" onClick={() => setSelectedId(row.original.id)} />
            </TableActionsCell>
          ),
        }),
      ]),
    [],
  );
  return (
    <div className="space-y-6">
      <PageHeader title="Activités" />
      <section aria-label="Filtres" className="flex flex-wrap items-end gap-3 border-y py-4">
        <label className="space-y-1 text-xs font-medium" htmlFor="activity-action">
          <span className="text-muted-foreground">Action</span>
          <Input
            className="h-8 w-64"
            id="activity-action"
            onChange={(event) => {
              setActionPrefix(event.target.value);
              setPage(0);
            }}
            placeholder="platform.plans"
            value={actionPrefix}
          />
        </label>
        <div className="space-y-1 text-xs font-medium">
          <span className="text-muted-foreground">Résultat</span>
          <Select
            onValueChange={(value) => {
              setOutcome(value);
              setPage(0);
            }}
            value={outcome}
          >
            <SelectTrigger className="w-40" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Tous</SelectItem>
              <SelectItem value="SUCCEEDED">Réussies</SelectItem>
              <SelectItem value="FAILED">Échouées</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1 text-xs font-medium">
          <span className="text-muted-foreground">Surface</span>
          <Select
            onValueChange={(value) => {
              setSurface(value);
              setPage(0);
            }}
            value={surface}
          >
            <SelectTrigger className="w-44" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Toutes</SelectItem>
              <SelectItem value="PLATFORM_ADMIN">Administration</SelectItem>
              <SelectItem value="CLIENT_WORKSPACE">Espace client</SelectItem>
              <SelectItem value="SYSTEM">Système</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </section>
      <section className="overflow-hidden rounded-xl border">
        {query.isError ? (
          <ErrorState retry={() => query.refetch()} />
        ) : (
          <>
            <div className="hidden md:block">
              <DataTable
                columns={tableColumns}
                data={query.data?.content ?? []}
                emptyState={<EmptyState title="Aucune activité" />}
                getRowId={(row) => row.id}
                isLoading={query.isLoading}
              />
            </div>
            <MobileActivities
              activities={query.data?.content ?? []}
              isLoading={query.isLoading}
              onOpen={setSelectedId}
            />
          </>
        )}
        {query.data ? (
          <PaginationBar
            onPageChange={setPage}
            page={page}
            totalElements={query.data.totalElements}
            totalPages={query.data.totalPages}
          />
        ) : null}
      </section>
      <ActivitySheet id={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}
