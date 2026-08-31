import { EyeIcon } from "@phosphor-icons/react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { adminApi } from "@/api/admin-api";
import type { CredentialTokenPurpose, PlatformCommunication, PlatformEmailDeliveryStatus } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { StatusText } from "@/components/patterns/status-text";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import {
  communicationPurposeLabel,
  communicationStatusPresentation,
  operationDateTime,
} from "./operations-presentation";

const columns = createDataColumns<PlatformCommunication>();
const ALL = "ALL";

function CommunicationSheet({ id, onClose }: { id: string | null; onClose: () => void }) {
  const session = useAdminSession();
  const canRecipient = session.can(adminPermissions.communicationsReadRecipientIdentity);
  const canFailure = session.can(adminPermissions.communicationsReadFailureEvidence);
  const requiredId = () => {
    if (!id) throw new Error("Communication id is required");
    return id;
  };
  const detail = useQuery({
    queryKey: ["admin", "communications", id],
    queryFn: () => adminApi.communication(requiredId()),
    enabled: Boolean(id),
  });
  const recipient = useQuery({
    queryKey: ["admin", "communications", id, "recipient"],
    queryFn: () => adminApi.communicationRecipient(requiredId()),
    enabled: Boolean(id && canRecipient),
  });
  const failure = useQuery({
    queryKey: ["admin", "communications", id, "failure"],
    queryFn: () => adminApi.communicationFailureEvidence(requiredId()),
    enabled: Boolean(id && canFailure && detail.data?.status === "FAILED"),
  });
  const item = detail.data;
  return (
    <Sheet onOpenChange={(open) => !open && onClose()} open={Boolean(id)}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-lg" closeLabel="Fermer">
        <SheetHeader className="border-b">
          <SheetTitle>{item ? communicationPurposeLabel(item.purpose) : "Communication"}</SheetTitle>
          <SheetDescription>{item ? operationDateTime(item.createdAt) : "Chargement…"}</SheetDescription>
        </SheetHeader>
        {detail.isError ? <ErrorState retry={() => detail.refetch()} /> : null}
        {item ? (
          <div className="space-y-6 px-4 pb-8 text-sm">
            <dl className="grid grid-cols-[8rem_1fr] gap-x-4 gap-y-3">
              <dt className="text-muted-foreground">État</dt>
              <dd>
                <StatusText tone={communicationStatusPresentation(item.status).tone}>
                  {communicationStatusPresentation(item.status).label}
                </StatusText>
              </dd>
              <dt className="text-muted-foreground">Destinataire</dt>
              <dd>
                {recipient.data?.email ?? item.recipientEmail ?? (canRecipient ? "Chargement…" : "Identité protégée")}
              </dd>
              <dt className="text-muted-foreground">Tentative</dt>
              <dd>{operationDateTime(item.attemptedAt)}</dd>
              <dt className="text-muted-foreground">Livraison</dt>
              <dd>{operationDateTime(item.deliveredAt)}</dd>
              <dt className="text-muted-foreground">Compte</dt>
              <dd className="font-mono text-xs break-all">{item.accountId ?? "—"}</dd>
              <dt className="text-muted-foreground">Identifiant</dt>
              <dd className="font-mono text-xs break-all">{item.id}</dd>
            </dl>
            {item.status === "FAILED" ? (
              canFailure ? (
                failure.isError ? (
                  <ErrorState
                    description="La preuve d’échec n’a pas pu être chargée."
                    retry={() => failure.refetch()}
                  />
                ) : failure.data ? (
                  <section className="border-t pt-4">
                    <h3 className="font-semibold">Preuve d’échec</h3>
                    <p className="mt-2">
                      <span className="text-muted-foreground">Code : </span>
                      {failure.data.failureCode ?? "Non classé"}
                    </p>
                    <p>
                      <span className="text-muted-foreground">Tentative : </span>
                      {operationDateTime(failure.data.attemptedAt)}
                    </p>
                  </section>
                ) : null
              ) : (
                <p className="border-t pt-4 text-muted-foreground">La preuve d’échec exige une autorisation séparée.</p>
              )
            ) : null}
            <p className="border-t pt-4 text-xs text-muted-foreground">
              Cette surface conserve la preuve de livraison. Les liens, mots de passe et corps d’email ne sont jamais
              exposés.
            </p>
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

function MobileCommunications({
  communications,
  isLoading,
  onOpen,
}: {
  communications: PlatformCommunication[];
  isLoading: boolean;
  onOpen: (id: string) => void;
}) {
  if (isLoading)
    return (
      <div className="md:hidden">
        <LoadingState />
      </div>
    );
  if (!communications.length)
    return (
      <div className="md:hidden">
        <EmptyState title="Aucune communication" />
      </div>
    );
  return (
    <div className="divide-y md:hidden">
      {communications.map((communication) => {
        const status = communicationStatusPresentation(communication.status);
        return (
          <article className="space-y-3 p-4" key={communication.id}>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <p className="font-medium">{communicationPurposeLabel(communication.purpose)}</p>
                <p className="mt-0.5 truncate text-xs text-muted-foreground">
                  {communication.recipientEmail ?? "Identité protégée"}
                </p>
              </div>
              <TableActionsCell label="Actions sur la communication">
                <RowAction icon={<EyeIcon />} label="Examiner" onClick={() => onOpen(communication.id)} />
              </TableActionsCell>
            </div>
            <dl className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <dt className="text-muted-foreground">Date</dt>
                <dd className="mt-0.5 font-medium">{operationDateTime(communication.createdAt)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">État</dt>
                <dd className="mt-0.5">
                  <StatusText tone={status.tone}>{status.label}</StatusText>
                </dd>
              </div>
            </dl>
          </article>
        );
      })}
    </div>
  );
}

export function AdminCommunicationsPage() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<string>(ALL);
  const [purpose, setPurpose] = useState<string>(ALL);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const summary = useQuery({
    queryKey: ["admin", "communications", "summary"],
    queryFn: adminApi.communicationSummary,
  });
  const list = useQuery({
    queryKey: ["admin", "communications", page, status, purpose],
    queryFn: () =>
      adminApi.communications({
        page,
        size: 25,
        status: status === ALL ? undefined : (status as PlatformEmailDeliveryStatus),
        purpose: purpose === ALL ? undefined : (purpose as CredentialTokenPurpose),
      }),
    placeholderData: keepPreviousData,
  });
  const tableColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("createdAt", {
          header: "Date",
          cell: ({ getValue }) => <span className="whitespace-nowrap">{operationDateTime(getValue())}</span>,
        }),
        columns.accessor("purpose", {
          header: "Objet",
          cell: ({ getValue }) => <span className="font-medium">{communicationPurposeLabel(getValue())}</span>,
        }),
        columns.display({
          id: "recipient",
          header: "Destinataire",
          cell: ({ row }) =>
            row.original.recipientEmail ? (
              <span>{row.original.recipientEmail}</span>
            ) : (
              <span className="text-muted-foreground">Identité protégée</span>
            ),
        }),
        columns.accessor("status", {
          header: "État",
          cell: ({ getValue }) => {
            const view = communicationStatusPresentation(getValue());
            return <StatusText tone={view.tone}>{view.label}</StatusText>;
          },
        }),
        columns.display({
          id: "evidence",
          header: "Dernier événement",
          cell: ({ row }) => operationDateTime(row.original.deliveredAt ?? row.original.attemptedAt),
        }),
        columns.display({
          id: "actions",
          header: "Actions",
          meta: tableActionsColumnMeta(1),
          cell: ({ row }) => (
            <TableActionsCell label="Actions sur la communication">
              <RowAction icon={<EyeIcon />} label="Examiner" onClick={() => setSelectedId(row.original.id)} />
            </TableActionsCell>
          ),
        }),
      ]),
    [],
  );
  const statuses: PlatformEmailDeliveryStatus[] = ["SENT", "PENDING", "FAILED", "SUPPRESSED"];
  return (
    <div className="space-y-6">
      <PageHeader title="Communications" />
      {summary.isError ? (
        <ErrorState retry={() => summary.refetch()} />
      ) : summary.data ? (
        <section aria-label="État des livraisons" className="grid grid-cols-2 border-y sm:grid-cols-5">
          <div className="col-span-2 px-4 py-4 sm:col-span-1">
            <div className="text-xs text-muted-foreground">Total</div>
            <div className="mt-1 text-2xl font-semibold tabular-nums">{summary.data.total}</div>
          </div>
          {statuses.map((item) => {
            const view = communicationStatusPresentation(item);
            return (
              <div className="border-t px-4 py-4 even:border-s sm:border-t-0 sm:border-s" key={item}>
                <StatusText tone={view.tone}>{view.label}</StatusText>
                <div className="mt-1 text-2xl font-semibold tabular-nums">{summary.data.byStatus[item] ?? 0}</div>
              </div>
            );
          })}
        </section>
      ) : null}
      <section aria-label="Filtres" className="flex flex-wrap items-end gap-3 border-b pb-4">
        <div className="space-y-1 text-xs font-medium">
          <span className="text-muted-foreground">État</span>
          <Select
            onValueChange={(value) => {
              setStatus(value);
              setPage(0);
            }}
            value={status}
          >
            <SelectTrigger className="w-44" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Tous</SelectItem>
              <SelectItem value="SENT">Envoyés</SelectItem>
              <SelectItem value="PENDING">En attente</SelectItem>
              <SelectItem value="FAILED">Échecs</SelectItem>
              <SelectItem value="SUPPRESSED">Supprimés</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1 text-xs font-medium">
          <span className="text-muted-foreground">Objet</span>
          <Select
            onValueChange={(value) => {
              setPurpose(value);
              setPage(0);
            }}
            value={purpose}
          >
            <SelectTrigger className="w-64" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Tous</SelectItem>
              <SelectItem value="ACTIVATION">Activation</SelectItem>
              <SelectItem value="PASSWORD_RESET">Réinitialisation du mot de passe</SelectItem>
              <SelectItem value="EMAIL_VERIFICATION">Vérification de l’adresse email</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </section>
      <section className="overflow-hidden rounded-xl border">
        {list.isError ? (
          <ErrorState retry={() => list.refetch()} />
        ) : (
          <>
            <div className="hidden md:block">
              <DataTable
                columns={tableColumns}
                data={list.data?.content ?? []}
                emptyState={<EmptyState title="Aucune communication" />}
                getRowId={(row) => row.id}
                isLoading={list.isLoading}
              />
            </div>
            <MobileCommunications
              communications={list.data?.content ?? []}
              isLoading={list.isLoading}
              onOpen={setSelectedId}
            />
          </>
        )}
        {list.data ? (
          <PaginationBar
            onPageChange={setPage}
            page={page}
            totalElements={list.data.totalElements}
            totalPages={list.data.totalPages}
          />
        ) : null}
      </section>
      <CommunicationSheet id={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}
