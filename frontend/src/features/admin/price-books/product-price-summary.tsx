import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { ProductPrice, ProductPriceOwnerType } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { Button } from "@/components/ui/button";
import { adminCommercialKeys } from "@/features/commercial/commercial-query";
import { formatExactMoney, formatExactMoneyParts } from "@/lib/exact-decimal";
import { productPriceCycle } from "./product-price-rules";

/** One bounded request for the visible product set, never one request per card. */
export function useCurrentProductPrices(ownerType: ProductPriceOwnerType, ownerIds: string[]) {
  const session = useAdminSession();
  const ids = [...new Set(ownerIds)].sort().join(",");
  const filters = {
    ownerType,
    ownerIds: ids,
    currentOnly: true,
    page: 0,
    size: 100,
    sort: "currencyCode",
    direction: "asc",
  };
  return useQuery({
    queryKey: [...adminCommercialKeys.priceBooks.all(), "current", filters],
    queryFn: () => adminApi.productPrices(filters),
    enabled: ids.length > 0 && session.can(adminPermissions.priceBooksList),
    refetchInterval: 30_000,
  });
}

export function ProductPriceOptions({
  prices,
  variant = "summary",
}: {
  prices: ProductPrice[];
  variant?: "summary" | "catalogue";
}) {
  if (variant === "catalogue") {
    return (
      <ul aria-label="Tarifs actuels" className="space-y-4">
        {prices.map((price) => (
          <li key={price.id} className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
            <span dir="ltr" className="min-w-0 break-all text-4xl font-semibold tracking-tight tabular-nums">
              {formatExactMoneyParts(price.amount, price.currencyCode)
                .filter((part) => part.type !== "currency")
                .map((part) => part.value)
                .join("")
                .trim()}
            </span>
            <span className="whitespace-nowrap text-sm text-muted-foreground">
              {price.currencyCode}{" "}
              {price.billingCycle === "MONTHLY"
                ? "/ mois"
                : price.billingCycle === "YEARLY"
                  ? "/ an"
                  : `· ${productPriceCycle[price.billingCycle]}`}
            </span>
          </li>
        ))}
      </ul>
    );
  }
  return (
    <ul className="space-y-2">
      {prices.map((price) => (
        <li className="flex flex-wrap items-baseline justify-between gap-x-5 gap-y-1" key={price.id}>
          <span className="text-sm text-muted-foreground">{productPriceCycle[price.billingCycle]}</span>
          <span className="font-semibold tabular-nums">
            {formatExactMoney(price.amount, price.currencyCode)}{" "}
            <span className="text-xs font-normal">{price.currencyCode}</span>
          </span>
        </li>
      ))}
    </ul>
  );
}

export function ProductPriceSummary({ ownerType, ownerId }: { ownerType: ProductPriceOwnerType; ownerId: string }) {
  const session = useAdminSession();
  const prices = useCurrentProductPrices(ownerType, [ownerId]);
  if (!session.can(adminPermissions.priceBooksList))
    return <p className="text-sm text-muted-foreground">Tarifs non accessibles avec votre rôle.</p>;
  if (prices.isLoading)
    return (
      <p className="text-sm text-muted-foreground" role="status">
        Chargement des tarifs…
      </p>
    );
  if (prices.isError)
    return (
      <div className="text-sm" role="alert">
        Tarifs indisponibles.{" "}
        <Button size="sm" variant="ghost" onClick={() => void prices.refetch()}>
          Réessayer
        </Button>
      </div>
    );
  const entries = prices.data?.content ?? [];
  return (
    <div className="space-y-3">
      {entries.length ? (
        <ProductPriceOptions prices={entries} />
      ) : (
        <p className="text-sm text-muted-foreground">Aucun tarif applicable actuellement.</p>
      )}
      <Link
        className="inline-block text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        to={`/admin/price-books?ownerType=${ownerType}&ownerId=${ownerId}`}
      >
        {prices.data && prices.data.totalElements > entries.length
          ? "Voir tous les tarifs (aperçu partiel)"
          : "Voir les tarifs et changements prévus"}
      </Link>
    </div>
  );
}
