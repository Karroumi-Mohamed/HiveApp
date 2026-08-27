import { ArrowRightIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import type { SubscriptionChangeOperation } from "@/api/contracts";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { subscriptionChangeStatusPresentation } from "./subscription-presentation";

const effectDate = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium" }).format(new Date(value)) : "—";

const timingLabel = (timing: SubscriptionChangeOperation["timing"]) =>
  timing === "IMMEDIATE" ? "Immédiat" : "Au renouvellement";

function OperationStatus({ operation }: { operation: SubscriptionChangeOperation }) {
  const presentation = subscriptionChangeStatusPresentation[operation.status];
  return <StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge>;
}

export function SubscriptionChangeList({
  operations,
  renderAction,
}: {
  operations: SubscriptionChangeOperation[];
  renderAction?: (operation: SubscriptionChangeOperation) => ReactNode;
}) {
  return (
    <>
      <div className="divide-y md:hidden">
        {operations.map((operation) => (
          <article className="space-y-4 p-4" key={operation.id}>
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="inline-flex items-center gap-1.5 font-medium">
                  <span>{operation.sourcePlanCode}</span>
                  <ArrowRightIcon aria-hidden="true" className="size-3.5 rtl:rotate-180" />
                  <span>{operation.targetPlanCode}</span>
                </p>
                {operation.attentionReason ? (
                  <p className="mt-1 text-xs text-destructive">{operation.attentionReason}</p>
                ) : null}
              </div>
              <OperationStatus operation={operation} />
            </div>
            <dl className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <dt className="text-muted-foreground">Moment</dt>
                <dd className="mt-0.5 font-medium">{timingLabel(operation.timing)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Effet</dt>
                <dd className="mt-0.5 font-medium">{effectDate(operation.effectiveAt)}</dd>
              </div>
            </dl>
            {renderAction ? <div className="flex justify-end">{renderAction(operation)}</div> : null}
          </article>
        ))}
      </div>
      <div className="hidden overflow-x-auto md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Changement</TableHead>
              <TableHead>Moment</TableHead>
              <TableHead>Statut</TableHead>
              <TableHead>Effet</TableHead>
              {renderAction ? <TableHead>Actions</TableHead> : null}
            </TableRow>
          </TableHeader>
          <TableBody>
            {operations.map((operation) => (
              <TableRow key={operation.id}>
                <TableCell>
                  <span className="inline-flex items-center gap-1.5 text-sm font-medium">
                    <span>{operation.sourcePlanCode}</span>
                    <ArrowRightIcon aria-hidden="true" className="size-3.5 rtl:rotate-180" />
                    <span>{operation.targetPlanCode}</span>
                  </span>
                  {operation.attentionReason ? (
                    <p className="mt-1 max-w-sm text-xs text-destructive">{operation.attentionReason}</p>
                  ) : null}
                </TableCell>
                <TableCell>{timingLabel(operation.timing)}</TableCell>
                <TableCell>
                  <OperationStatus operation={operation} />
                </TableCell>
                <TableCell>{effectDate(operation.effectiveAt)}</TableCell>
                {renderAction ? <TableCell>{renderAction(operation)}</TableCell> : null}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </>
  );
}
