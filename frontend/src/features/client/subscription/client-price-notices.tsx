import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { repricingApi } from "@/api/repricing-api";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { repricingCycle, repricingDate, repricingStates } from "@/features/admin/repricing/repricing-rules";
import { formatExactMoney } from "@/lib/exact-decimal";

export function ClientPriceNotices() {
  const session = useClientSession();
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const key = ["client", "price-notices", session.selectedCompanyId, session.isB2B];
  const allowed = session.can(clientPermissions.subscriptionReadPriceNotices);
  const query = useQuery({ queryKey: [...key, page], queryFn: () => repricingApi.notices(page), enabled: allowed });
  const mark = useMutation({
    mutationFn: repricingApi.markRead,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: key });
    },
  });
  if (!allowed) return <PermissionState />;
  if (query.isLoading) return <LoadingState />;
  if (query.isError) return <ErrorState retry={() => void query.refetch()} />;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="border-b p-5">
        <h2 className="font-semibold">Notifications tarifaires</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Marquer une notification comme lue ne vaut pas acceptation.
        </p>
      </div>
      {mark.isError ? (
        <p role="alert" className="p-4 text-sm text-destructive">
          Impossible de marquer la notification comme lue. Réessayez.
        </p>
      ) : null}
      <ul className="divide-y">
        {query.data?.content.map((notice) => {
          const item = notice.change;
          return (
            <li key={notice.id} className="space-y-3 p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h3 className="font-medium">{notice.productName}</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {repricingStates[item.status]} · {repricingDate(item.effectiveAt)}
                  </p>
                </div>
                {!notice.read && session.can(clientPermissions.subscriptionMarkPriceNoticeRead) ? (
                  <Button variant="ghost" size="sm" disabled={mark.isPending} onClick={() => mark.mutate(notice.id)}>
                    Marquer comme lue
                  </Button>
                ) : (
                  <span className="text-xs text-muted-foreground">{notice.read ? "Lue" : "Non lue"}</span>
                )}
              </div>
              <dl className="grid gap-4 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-muted-foreground">Tarif unitaire</dt>
                  <dd className="mt-1 font-medium tabular-nums">
                    {formatExactMoney(item.oldUnitPrice, item.currencyCode)} →{" "}
                    {formatExactMoney(item.newUnitPrice, item.currencyCode)} / {repricingCycle(item.billingCycle)}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Total de votre abonnement</dt>
                  <dd className="mt-1 font-medium tabular-nums">
                    {item.oldTotal === null ? "—" : formatExactMoney(item.oldTotal, item.currencyCode)} →{" "}
                    {item.newTotal === null ? "—" : formatExactMoney(item.newTotal, item.currencyCode)} /{" "}
                    {repricingCycle(item.billingCycle)}
                  </dd>
                </div>
              </dl>
              {item.status === "CANCELLED" ? (
                <p className="text-sm">Ce changement a été annulé.</p>
              ) : item.blocker === "PAYMENT_FAILED" ? (
                <p className="text-sm text-warning">
                  Le paiement du renouvellement a échoué. Consultez l’onglet Facturation.
                </p>
              ) : item.status === "CONFLICT" ? (
                <p className="text-sm text-warning">
                  Ce changement est bloqué et doit être revu. Aucun nouveau tarif n’est appliqué par cette notification.
                </p>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Vos fonctionnalités et quantités restent identiques. La période déjà payée ne change pas.
                </p>
              )}
            </li>
          );
        })}
      </ul>
      {!query.data?.content.length ? <EmptyState title="Aucune notification tarifaire" /> : null}
      {query.data ? <PaginationBar {...query.data} onPageChange={setPage} /> : null}
    </section>
  );
}
