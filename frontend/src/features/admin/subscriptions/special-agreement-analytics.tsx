import { ArrowRightIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { adminApi } from "@/api/admin-api";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { adminCommercialKeys } from "@/features/commercial/commercial-query";
import { formatExactMoney } from "@/lib/exact-decimal";

export function SpecialAgreementAnalytics({ canOpen }: { canOpen: boolean }) {
  const analytics = useQuery({
    queryKey: adminCommercialKeys.subscriptions.agreementAnalytics(),
    queryFn: adminApi.specialAgreementAnalytics,
    retry: false,
  });
  if (analytics.isLoading) return <LoadingState rows={2} />;
  if (analytics.isError || !analytics.data) return <ErrorState retry={() => void analytics.refetch()} />;
  const data = analytics.data;
  return (
    <section aria-labelledby="special-agreement-analytics-title" className="border-y py-5">
      <div className="flex flex-col gap-5">
        <div>
          <div className="flex items-center justify-between gap-4">
            <h2 className="text-sm font-semibold" id="special-agreement-analytics-title">
              Accords spéciaux
            </h2>
            {canOpen ? (
              <Button asChild size="sm" variant="ghost">
                <Link to="/admin/subscriptions/agreements">
                  Voir les accords <ArrowRightIcon className="rtl:rotate-180" />
                </Link>
              </Button>
            ) : null}
          </div>
          <dl className="mt-4 grid grid-cols-2 gap-x-7 gap-y-4 sm:grid-cols-3 lg:grid-cols-5">
            {[
              ["En cours", data.active],
              ["Planifiés", data.scheduled],
              ["À régler", data.awaitingSettlement],
              ["Décision requise", data.needsAttention],
              ["Total enregistré", data.total],
            ].map(([label, value]) => (
              <div key={label}>
                <dt className="text-xs text-muted-foreground">{label}</dt>
                <dd className="mt-1 text-xl font-semibold tabular-nums">{value}</dd>
              </div>
            ))}
          </dl>
        </div>
        {data.currencies.length ? (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] text-sm">
              <thead className="text-start text-xs text-muted-foreground">
                <tr className="border-b">
                  <th className="py-2 text-start font-medium">Devise</th>
                  <th className="py-2 text-end font-medium">Valeur catalogue</th>
                  <th className="py-2 text-end font-medium">Montants convenus</th>
                  <th className="py-2 text-end font-medium">Facturé</th>
                  <th className="py-2 text-end font-medium">Encaissé</th>
                  <th className="py-2 text-end font-medium">Valeur offerte</th>
                </tr>
              </thead>
              <tbody>
                {data.currencies.map((row) => (
                  <tr className="border-b last:border-0" key={row.currencyCode}>
                    <td className="py-2 font-medium">{row.currencyCode}</td>
                    <td className="py-2 text-end tabular-nums">
                      {formatExactMoney(row.catalogueValue, row.currencyCode)}
                    </td>
                    <td className="py-2 text-end tabular-nums">
                      {formatExactMoney(row.agreedValue, row.currencyCode)}
                    </td>
                    <td className="py-2 text-end tabular-nums">
                      {formatExactMoney(row.invoicedValue, row.currencyCode)}
                    </td>
                    <td className="py-2 text-end tabular-nums">
                      {formatExactMoney(row.collectedValue, row.currencyCode)}
                    </td>
                    <td className="py-2 text-end tabular-nums">
                      {formatExactMoney(row.complimentaryValue, row.currencyCode)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </div>
    </section>
  );
}
