import { ArrowRightIcon, PlusIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { ProductPriceOwnerType } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { StatusText } from "@/components/patterns/status-text";
import { Button } from "@/components/ui/button";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { formatExactMoney } from "@/lib/exact-decimal";
import { productPriceCycle, productPriceStatus } from "./product-price-rules";

const money = formatExactMoney;

export function ProductPricePanel({ ownerType, ownerId }: { ownerType: ProductPriceOwnerType; ownerId: string }) {
  const session = useAdminSession();
  const prices = useQuery({
    queryKey: adminCommercialKeys.priceBooks.owner(ownerType, ownerId),
    queryFn: () =>
      adminApi.productPrices({ ownerType, ownerId, page: 0, size: 6, sort: "createdAt", direction: "desc" }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.priceBooksList),
  });
  if (!session.can(adminPermissions.priceBooksList)) {
    return <PermissionState description="Votre rôle ne permet pas de consulter les tarifs de ce produit." />;
  }
  if (prices.isLoading) return <LoadingState rows={3} />;
  if (prices.isError) return <ErrorState retry={() => void prices.refetch()} />;
  const entries = prices.data?.content ?? [];
  const total = prices.data?.totalElements ?? 0;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <header className="flex flex-col gap-3 border-b p-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-sm font-semibold">Tarifs</h2>
          <p className="mt-1 text-xs text-muted-foreground">Conditions mensuelles et annuelles de cette révision.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {session.can(adminPermissions.priceBooksCreate) ? (
            <Button asChild size="sm" variant="outline">
              <Link to={`/admin/price-books/new?ownerType=${ownerType}&ownerId=${ownerId}`}>
                <PlusIcon />
                Ajouter
              </Link>
            </Button>
          ) : null}
          <Button asChild size="sm" variant="ghost">
            <Link to={`/admin/price-books?ownerType=${ownerType}&ownerId=${ownerId}`}>
              {total ? `Gérer ${total === 1 ? "le tarif" : `les ${total} tarifs`}` : "Voir la grille tarifaire"}
              <ArrowRightIcon className="rtl:rotate-180" />
            </Link>
          </Button>
        </div>
      </header>
      {!entries.length ? (
        <EmptyState description="Créez un brouillon mensuel ou annuel pour cette révision." title="Aucun tarif" />
      ) : (
        <div className="divide-y">
          {entries.map((price) => {
            const status = productPriceStatus[price.status];
            const content = (
              <>
                <span>
                  <span className="block font-medium tabular-nums">{money(price.amount, price.currencyCode)}</span>
                  <span className="block text-xs text-muted-foreground">
                    {productPriceCycle[price.billingCycle]} · tarif R{price.revisionNumber}
                  </span>
                </span>
                <StatusText tone={status.tone === "neutral" ? "neutral" : status.tone}>{status.label}</StatusText>
                {session.can(adminPermissions.priceBooksRead) ? (
                  <ArrowRightIcon className="hidden size-4 text-muted-foreground rtl:rotate-180 sm:block" />
                ) : null}
              </>
            );
            return session.can(adminPermissions.priceBooksRead) ? (
              <Link
                className="grid gap-2 px-4 py-3 hover:bg-muted/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring sm:grid-cols-[1fr_auto_auto] sm:items-center"
                key={price.id}
                to={`/admin/price-books/${price.id}`}
              >
                {content}
              </Link>
            ) : (
              <div className="grid gap-2 px-4 py-3 sm:grid-cols-[1fr_auto] sm:items-center" key={price.id}>
                {content}
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
