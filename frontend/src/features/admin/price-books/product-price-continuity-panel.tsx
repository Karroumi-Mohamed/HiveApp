import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { ProductPrice } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { Button } from "@/components/ui/button";
import { adminCommercialKeys } from "@/features/commercial/commercial-query";
import { formatExactMoney } from "@/lib/exact-decimal";
import { ProductPriceChangeDialog, priceChangeAction, priceChangeLabel } from "./product-price-change-dialog";
import { canUseProductPriceAction, productPriceCycle } from "./product-price-rules";

export function ProductPriceContinuityPanel({ price }: { price: ProductPrice }) {
  const session = useAdminSession();
  const query = useQuery({
    queryKey: [...adminCommercialKeys.priceBooks.all(), "successor", price.id],
    queryFn: () => adminApi.productPrices({ sourcePriceId: price.id, status: "ACTIVE", size: 2 }),
    enabled: price.status === "ACTIVE" && price.effectiveUntil !== null && session.can(adminPermissions.priceBooksList),
    refetchInterval: 30_000,
  });
  if (price.status !== "ACTIVE") return null;
  const scheduled = query.data?.content[0];
  const current =
    Date.parse(price.effectiveFrom) <= Date.now() &&
    (!price.effectiveUntil || Date.parse(price.effectiveUntil) > Date.now());
  const operations = current
    ? price.effectiveUntil
      ? (["RESCHEDULE", "CANCEL"] as const)
      : (["CHANGE"] as const)
    : [];
  return (
    <section className="rounded-xl border bg-card p-5" aria-label="Continuité du tarif">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
        <div className="space-y-2">
          <h2 className="font-semibold">
            {current
              ? "Tarif actuel"
              : Date.parse(price.effectiveFrom) > Date.now()
                ? "Changement programmé"
                : "Ancien tarif"}
          </h2>
          <p className="text-sm text-muted-foreground">
            {current
              ? "Ce tarif s’applique aux prochaines souscriptions. Les abonnés existants gardent leurs conditions."
              : "Ces conditions restent conservées dans l’historique et les abonnements qui les ont acceptées."}
          </p>
          {current && !price.effectiveUntil && (
            <p className="text-sm">Il continue jusqu’à la confirmation d’un nouveau tarif, sans interruption.</p>
          )}
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          {operations.map((operation) => (
            <ProductPriceChangeDialog
              key={operation}
              price={price}
              scheduled={scheduled}
              operation={operation}
              trigger={
                <Button
                  variant={operation === "CANCEL" ? "ghost" : "default"}
                  disabled={
                    (operation !== "CHANGE" && (query.isPending || query.isError || !scheduled)) ||
                    !session.can(adminPermissions.priceBooksPreviewChange) ||
                    !canUseProductPriceAction(price, priceChangeAction[operation], session.can)
                  }
                >
                  {priceChangeLabel[operation]}
                </Button>
              }
            />
          ))}
        </div>
      </div>
      {price.effectiveUntil && (
        <div className="mt-4 border-t pt-4 text-sm">
          {!session.can(adminPermissions.priceBooksList) ? (
            <p>Les détails du tarif suivant ne sont pas accessibles avec votre rôle.</p>
          ) : query.isError ? (
            <p role="alert">
              Le tarif suivant n’a pas pu être chargé.{" "}
              <Button variant="ghost" size="sm" onClick={() => void query.refetch()}>
                Réessayer
              </Button>
            </p>
          ) : query.isLoading ? (
            <p role="status">Chargement du tarif suivant…</p>
          ) : scheduled ? (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p>
                <strong>
                  {Date.parse(scheduled.effectiveFrom) > Date.now() ? "Programmé" : "Tarif suivant"} :{" "}
                  {formatExactMoney(scheduled.amount, scheduled.currencyCode)}
                </strong>{" "}
                · {productPriceCycle[scheduled.billingCycle]}
                <span className="mt-1 block text-muted-foreground">
                  À partir du {new Date(scheduled.effectiveFrom).toLocaleString("fr-MA")}, au moment exact où ce tarif
                  se termine.
                </span>
              </p>
              {session.can(adminPermissions.priceBooksRead) && (
                <Button asChild size="sm" variant="outline">
                  <Link to={`/admin/price-books/${scheduled.id}`}>Voir ce tarif</Link>
                </Button>
              )}
            </div>
          ) : (
            <p role="alert">Aucun tarif suivant trouvé. Cette ancienne configuration doit être vérifiée.</p>
          )}
        </div>
      )}
      {!current && price.sourcePriceId && session.can(adminPermissions.priceBooksRead) && (
        <Button asChild size="sm" variant="outline" className="mt-4">
          <Link to={`/admin/price-books/${price.sourcePriceId}`}>Voir le tarif précédent et ses changements</Link>
        </Button>
      )}
    </section>
  );
}
