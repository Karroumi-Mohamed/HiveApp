import { ArrowRightIcon } from "@phosphor-icons/react";
import type { SortingState } from "@tanstack/react-table";
import { type ReactNode, useMemo } from "react";
import type { AdminSubscriptionChangeOperation, SubscriptionChangeOperation } from "@/api/contracts";
import { createDataColumns, DataTable, DataTableExpander, SortHeader } from "@/components/patterns/data-table";
import { StatusBadge } from "@/components/patterns/status-badge";
import { subscriptionChangeStatusPresentation } from "./subscription-presentation";

const date = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium" }).format(new Date(value)) : "—";

const timingLabel = (timing: SubscriptionChangeOperation["timing"]) =>
  timing === "IMMEDIATE" ? "Immédiat" : "Au renouvellement";

type SubscriptionChangeListOperation = SubscriptionChangeOperation | AdminSubscriptionChangeOperation;

function OperationStatus({ operation }: { operation: SubscriptionChangeListOperation }) {
  const presentation = subscriptionChangeStatusPresentation[operation.status];
  return <StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge>;
}

function attentionText(operation: SubscriptionChangeListOperation) {
  if ("attentionReason" in operation) return operation.attentionReason;
  return operation.attentionCode === "OPERATOR_ASSISTANCE_REQUIRED"
    ? "Ce changement nécessite l’aide d’un opérateur. Contactez le support."
    : null;
}

function ChangeIdentity({ operation }: { operation: SubscriptionChangeListOperation }) {
  const attention = attentionText(operation);
  return (
    <div className="min-w-0">
      <p className="inline-flex items-center gap-1.5 font-medium">
        <span>{operation.sourcePlanCode}</span>
        <ArrowRightIcon aria-hidden="true" className="size-3.5 shrink-0 rtl:rotate-180" />
        <span>{operation.targetPlanCode}</span>
      </p>
      <p className="mt-1 text-xs text-muted-foreground">Demandé le {date(operation.createdAt)}</p>
      {attention ? (
        <p className="mt-1 max-w-sm whitespace-pre-wrap break-words text-xs text-destructive">{attention}</p>
      ) : null}
    </div>
  );
}

export function SubscriptionChangeList<T extends SubscriptionChangeListOperation>({
  operations,
  sorting,
  onSortingChange,
  renderAction,
  renderDetails,
}: {
  operations: T[];
  sorting: SortingState;
  onSortingChange: (next: SortingState) => void;
  renderAction?: (operation: T) => ReactNode;
  renderDetails?: (operation: T) => ReactNode;
}) {
  const column = useMemo(() => createDataColumns<T>(), []);
  const columns = useMemo(
    () =>
      column.columns([
        ...(renderDetails
          ? [
              column.display({
                id: "disclosure",
                meta: { headerClassName: "w-12", cellClassName: "w-12" },
                header: () => <span className="sr-only">Détails</span>,
                cell: ({ row }) => (
                  <DataTableExpander
                    collapseLabel={`Masquer la traçabilité de ${row.original.sourcePlanCode} vers ${row.original.targetPlanCode}`}
                    expandLabel={`Afficher la traçabilité de ${row.original.sourcePlanCode} vers ${row.original.targetPlanCode}`}
                    row={row}
                  />
                ),
              }),
            ]
          : []),
        column.accessor((operation) => operation.createdAt, {
          id: "createdAt",
          meta: { headerClassName: "min-w-52" },
          header: ({ column: item }) => <SortHeader column={item}>Changement</SortHeader>,
          cell: ({ row }) => <ChangeIdentity operation={row.original} />,
        }),
        column.accessor((operation) => operation.timing, {
          id: "timing",
          meta: { headerClassName: "w-44", cellClassName: "w-44" },
          header: ({ column: item }) => <SortHeader column={item}>Moment</SortHeader>,
          cell: ({ row }) => timingLabel(row.original.timing),
        }),
        column.accessor((operation) => operation.status, {
          id: "status",
          meta: { headerClassName: "w-48", cellClassName: "w-48" },
          header: ({ column: item }) => <SortHeader column={item}>Statut</SortHeader>,
          cell: ({ row }) => <OperationStatus operation={row.original} />,
        }),
        column.accessor((operation) => operation.effectiveAt, {
          id: "effectiveAt",
          meta: { headerClassName: "w-40", cellClassName: "w-40" },
          header: ({ column: item }) => <SortHeader column={item}>Effet</SortHeader>,
          cell: ({ row }) => date(row.original.effectiveAt),
        }),
        ...(renderAction
          ? [
              column.display({
                id: "actions",
                meta: { headerClassName: "w-36 text-end", cellClassName: "w-36" },
                header: "Actions",
                cell: ({ row }) => <div className="flex justify-end">{renderAction(row.original)}</div>,
              }),
            ]
          : []),
      ]),
    [column, renderAction, renderDetails],
  );

  return (
    <>
      <div className="divide-y md:hidden">
        {operations.map((operation) => (
          <article className="space-y-4 p-4" key={operation.id}>
            <div className="flex items-start justify-between gap-3">
              <ChangeIdentity operation={operation} />
              <OperationStatus operation={operation} />
            </div>
            <dl className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <dt className="text-muted-foreground">Moment</dt>
                <dd className="mt-0.5 font-medium">{timingLabel(operation.timing)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Effet</dt>
                <dd className="mt-0.5 font-medium">{date(operation.effectiveAt)}</dd>
              </div>
            </dl>
            {renderDetails ? (
              <details className="border-t pt-3">
                <summary className="cursor-pointer text-sm font-medium">Traçabilité</summary>
                <div className="pt-3">{renderDetails(operation)}</div>
              </details>
            ) : null}
            {renderAction ? <div className="flex justify-end">{renderAction(operation)}</div> : null}
          </article>
        ))}
      </div>
      <div className="hidden md:block">
        <DataTable
          columns={columns}
          data={operations}
          getRowCanExpand={() => Boolean(renderDetails)}
          getRowId={(operation) => operation.id}
          onSortingChange={onSortingChange}
          renderExpandedRow={
            renderDetails
              ? (operation) => <div className="border-s-2 border-primary/30 px-5 py-4">{renderDetails(operation)}</div>
              : undefined
          }
          sorting={sorting}
        />
      </div>
    </>
  );
}
