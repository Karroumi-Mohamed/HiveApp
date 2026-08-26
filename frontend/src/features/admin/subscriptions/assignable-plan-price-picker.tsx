import { CheckIcon, MagnifyingGlassIcon } from "@phosphor-icons/react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { adminApi } from "@/api/admin-api";
import type { AssignablePlanPrice, ProductPriceBillingCycle } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { formatExactMoney } from "@/lib/exact-decimal";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { cn } from "@/lib/utils";

function money(price: AssignablePlanPrice) {
  return formatExactMoney(price.amount, price.currencyCode);
}

function cycle(value: ProductPriceBillingCycle) {
  return value === "MONTHLY" ? "Mensuel" : "Annuel";
}

export function AssignablePlanPricePicker({
  value,
  onChange,
  enabled,
}: {
  value: AssignablePlanPrice | null;
  onChange: (price: AssignablePlanPrice) => void;
  enabled: boolean;
}) {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search);
  const [currency, setCurrency] = useState("");
  const [billingCycle, setBillingCycle] = useState<ProductPriceBillingCycle | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const filters = { search: debouncedSearch, currency, billingCycle, page } as const;
  const prices = useQuery({
    queryKey: adminCommercialKeys.subscriptions.assignablePrices(filters),
    queryFn: () =>
      adminApi.assignablePlanPrices({
        search: debouncedSearch || undefined,
        currencyCode: currency || undefined,
        billingCycle: billingCycle === "ALL" ? undefined : billingCycle,
        page,
        size: 8,
        sort: "planName",
        direction: "asc",
      }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.subscriptionsListAssignablePrices, enabled),
    placeholderData: keepPreviousData,
  });

  if (!session.can(adminPermissions.subscriptionsListAssignablePrices)) {
    return <PermissionState description="Votre rôle ne permet pas de consulter les tarifs attribuables." />;
  }

  return (
    <section aria-busy={prices.isFetching} aria-label="Tarif du forfait" className="overflow-hidden rounded-lg border">
      <div className="grid gap-2 border-b p-3 sm:grid-cols-[minmax(180px,1fr)_130px_130px]">
        <div className="relative">
          <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            aria-label="Rechercher un forfait attribuable"
            className="ps-9"
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Rechercher un forfait…"
            value={search}
          />
        </div>
        <Select
          onValueChange={(next) => {
            setBillingCycle(next as ProductPriceBillingCycle | "ALL");
            setPage(0);
          }}
          value={billingCycle}
        >
          <SelectTrigger aria-label="Cycle du tarif">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Tous les cycles</SelectItem>
            <SelectItem value="MONTHLY">Mensuel</SelectItem>
            <SelectItem value="YEARLY">Annuel</SelectItem>
          </SelectContent>
        </Select>
        <Input
          aria-label="Devise du tarif"
          maxLength={3}
          onChange={(event) => {
            setCurrency(event.target.value.toUpperCase());
            setPage(0);
          }}
          placeholder="Devise"
          value={currency}
        />
      </div>
      {prices.isLoading ? (
        <div className="p-4">
          <LoadingState rows={4} />
        </div>
      ) : prices.isError ? (
        <ErrorState retry={() => void prices.refetch()} title="Tarifs indisponibles" />
      ) : prices.data?.content.length ? (
        <div className="divide-y">
          {prices.data.content.map((price) => {
            const selected = value?.priceEntryId === price.priceEntryId;
            return (
              <button
                aria-pressed={selected}
                className={cn(
                  "grid w-full gap-2 px-4 py-3 text-start hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center",
                  selected && "bg-muted",
                )}
                key={price.priceEntryId}
                onClick={() => onChange(price)}
                type="button"
              >
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium">{price.planName}</span>
                  <span className="block text-xs text-muted-foreground">Forfait R{price.planRevisionNumber}</span>
                </span>
                <span className="text-sm font-medium tabular-nums">{money(price)}</span>
                <span className="flex items-center justify-end gap-2 text-xs text-muted-foreground">
                  {cycle(price.billingCycle)}
                  {selected ? <CheckIcon aria-hidden="true" className="size-4 text-primary" weight="bold" /> : null}
                </span>
              </button>
            );
          })}
        </div>
      ) : (
        <EmptyState description="Modifiez les filtres ou publiez un tarif actif." title="Aucun tarif attribuable" />
      )}
      {prices.data ? (
        <PaginationBar
          onPageChange={setPage}
          page={prices.data.page}
          totalElements={prices.data.totalElements}
          totalPages={prices.data.totalPages}
        />
      ) : null}
      {value ? (
        <p aria-live="polite" className="border-t bg-muted/30 px-4 py-3 text-sm">
          <span className="font-medium">Sélection : {value.planName}</span>
          <span className="ms-2 text-muted-foreground">
            {cycle(value.billingCycle)} · {money(value)}
          </span>
        </p>
      ) : null}
    </section>
  );
}
