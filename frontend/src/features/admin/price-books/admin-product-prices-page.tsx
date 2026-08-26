import { ArrowRightIcon, MagnifyingGlassIcon, PlusIcon } from "@phosphor-icons/react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import type {
  ProductPrice,
  ProductPriceBillingCycle,
  ProductPriceOwnerType,
  ProductPriceStatus,
} from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable, SortHeader } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { StatusBadge } from "@/components/patterns/status-badge";
import { TableActionsCell, tableActionsColumnMeta } from "@/components/patterns/table-actions-cell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { productPriceCycle, productPriceOwner, productPriceStatus } from "./product-price-rules";

const PAGE_SIZE = 20;
const column = createDataColumns<ProductPrice>();
const sortableFields = new Set([
  "createdAt",
  "updatedAt",
  "effectiveFrom",
  "amount",
  "currencyCode",
  "billingCycle",
  "status",
  "revisionNumber",
]);

function money(amount: number, currency: string) {
  return new Intl.NumberFormat("fr-MA", { style: "currency", currency }).format(amount);
}

function date(value: string | null) {
  return value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium" }).format(new Date(value)) : "Sans fin";
}

function RowActions({ price }: { price: ProductPrice }) {
  const session = useAdminSession();
  const canRead = session.can(adminPermissions.priceBooksRead);
  return (
    <TableActionsCell label={`Actions pour ${price.productName}`}>
      <RowAction
        disabled={!canRead}
        disabledLabel="Votre rôle ne permet pas d’ouvrir ce tarif"
        icon={<ArrowRightIcon className="rtl:rotate-180" />}
        label="Ouvrir le tarif"
        to={canRead ? `/admin/price-books/${price.id}` : undefined}
      />
    </TableActionsCell>
  );
}

const columns = column.columns([
  column.accessor("productName", {
    meta: { headerClassName: "min-w-[260px]" },
    header: "Produit",
    cell: ({ row }) => (
      <span className="block min-w-0">
        <span className="block truncate font-medium">{row.original.productName}</span>
        <span className="block truncate text-xs text-muted-foreground">
          {productPriceOwner[row.original.productType]} · tarif R{row.original.revisionNumber}
        </span>
      </span>
    ),
  }),
  column.accessor("amount", {
    header: ({ column: current }) => <SortHeader column={current}>Montant</SortHeader>,
    cell: ({ row }) => (
      <span className="font-medium tabular-nums">{money(row.original.amount, row.original.currencyCode)}</span>
    ),
  }),
  column.accessor("billingCycle", {
    header: ({ column: current }) => <SortHeader column={current}>Cycle</SortHeader>,
    cell: ({ row }) => productPriceCycle[row.original.billingCycle],
  }),
  column.accessor("effectiveFrom", {
    header: ({ column: current }) => <SortHeader column={current}>Validité</SortHeader>,
    cell: ({ row }) => (
      <span className="text-sm">
        <span className="block">{date(row.original.effectiveFrom)}</span>
        <span className="block text-xs text-muted-foreground">jusqu’au {date(row.original.effectiveUntil)}</span>
      </span>
    ),
  }),
  column.accessor("status", {
    header: ({ column: current }) => <SortHeader column={current}>Statut</SortHeader>,
    cell: ({ row }) => {
      const status = productPriceStatus[row.original.status];
      return <StatusBadge tone={status.tone}>{status.label}</StatusBadge>;
    },
  }),
  column.display({
    id: "actions",
    meta: tableActionsColumnMeta(1),
    header: "Actions",
    cell: ({ row }) => <RowActions price={row.original} />,
  }),
]);

function MobilePriceList({ prices }: { prices: ProductPrice[] }) {
  if (!prices.length) {
    return (
      <div className="md:hidden">
        <EmptyState description="Modifiez les filtres ou créez un tarif." title="Aucun tarif" />
      </div>
    );
  }
  return (
    <div className="divide-y md:hidden">
      {prices.map((price) => {
        const status = productPriceStatus[price.status];
        return (
          <article className="space-y-3 p-4" key={price.id}>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{price.productName}</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {productPriceOwner[price.productType]} · R{price.revisionNumber}
                </p>
              </div>
              <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
              <RowActions price={price} />
            </div>
            <dl className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <dt className="text-muted-foreground">Montant</dt>
                <dd className="mt-0.5 font-medium tabular-nums">{money(price.amount, price.currencyCode)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Cycle</dt>
                <dd className="mt-0.5 font-medium">{productPriceCycle[price.billingCycle]}</dd>
              </div>
            </dl>
          </article>
        );
      })}
    </div>
  );
}

function filterValue<T extends string>(value: string | null, allowed: readonly T[]): T | "ALL" {
  return allowed.includes(value as T) ? (value as T) : "ALL";
}

export function AdminProductPricesPage() {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const urlSearch = params.get("search") ?? "";
  const [search, setSearch] = useState(urlSearch);
  const debouncedSearch = useDebouncedValue(search);
  const ownerType = filterValue(params.get("ownerType"), ["PLAN", "ADD_ON", "QUOTA_PACKAGE"] as const);
  const status = filterValue(params.get("status"), ["DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"] as const);
  const billingCycle = filterValue(params.get("billingCycle"), ["MONTHLY", "YEARLY"] as const);
  const currencyCode = params.get("currency") ?? "";
  const page = Math.max(0, Number(params.get("page")) || 0);
  const requestedSort = params.get("sort") ?? "createdAt";
  const sort = sortableFields.has(requestedSort) ? requestedSort : "createdAt";
  const direction = params.get("direction") === "asc" ? "asc" : "desc";
  const sorting: SortingState = [{ id: sort, desc: direction === "desc" }];

  useEffect(() => setSearch(urlSearch), [urlSearch]);

  const update = (changes: Record<string, string | number | null>, resetPage = true) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === "" || value === "ALL") next.delete(key);
      else next.set(key, String(value));
    }
    if (resetPage && !("page" in changes)) next.delete("page");
    setParams(next, { replace: true });
  };

  useEffect(() => {
    setParams(
      (current) => {
        const currentSearch = current.get("search") ?? "";
        if (currentSearch === debouncedSearch) return current;
        const next = new URLSearchParams(current);
        if (debouncedSearch) next.set("search", debouncedSearch);
        else next.delete("search");
        next.delete("page");
        return next;
      },
      { replace: true },
    );
  }, [debouncedSearch, setParams]);

  const filters = {
    search: debouncedSearch,
    ownerType,
    ownerId: params.get("ownerId") ?? "",
    status,
    currencyCode,
    billingCycle,
    page,
    sort,
    direction,
  } as const;
  const prices = useQuery({
    queryKey: adminCommercialKeys.priceBooks.list(filters),
    queryFn: () =>
      adminApi.productPrices({
        search: debouncedSearch || undefined,
        ownerType: ownerType === "ALL" ? undefined : (ownerType as ProductPriceOwnerType),
        ownerId: ownerType === "ALL" ? undefined : (params.get("ownerId") ?? undefined),
        status: status === "ALL" ? undefined : (status as ProductPriceStatus),
        currencyCode: currencyCode || undefined,
        billingCycle: billingCycle === "ALL" ? undefined : (billingCycle as ProductPriceBillingCycle),
        page,
        size: PAGE_SIZE,
        sort,
        direction,
      }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.priceBooksList),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          session.can(adminPermissions.priceBooksCreate) ? (
            <Button asChild>
              <Link to="/admin/price-books/new">
                <PlusIcon />
                Créer un tarif
              </Link>
            </Button>
          ) : undefined
        }
        title="Grille tarifaire"
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="grid gap-3 border-b p-4 sm:grid-cols-2 xl:grid-cols-[minmax(220px,1fr)_repeat(4,180px)]">
          <div className="relative">
            <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              aria-label="Rechercher les tarifs"
              className="ps-9"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Produit, code ou devise…"
              value={search}
            />
          </div>
          <Select onValueChange={(value) => update({ ownerType: value, ownerId: null })} value={ownerType}>
            <SelectTrigger aria-label="Type de produit">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Tous les produits</SelectItem>
              <SelectItem value="PLAN">Forfaits</SelectItem>
              <SelectItem value="ADD_ON">Add-ons</SelectItem>
              <SelectItem value="QUOTA_PACKAGE">Packs de capacité</SelectItem>
            </SelectContent>
          </Select>
          <Select onValueChange={(value) => update({ status: value })} value={status}>
            <SelectTrigger aria-label="Statut du tarif">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Tous les statuts</SelectItem>
              {Object.entries(productPriceStatus).map(([value, item]) => (
                <SelectItem key={value} value={value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select onValueChange={(value) => update({ billingCycle: value })} value={billingCycle}>
            <SelectTrigger aria-label="Cycle de facturation">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Tous les cycles</SelectItem>
              <SelectItem value="MONTHLY">Mensuels</SelectItem>
              <SelectItem value="YEARLY">Annuels</SelectItem>
            </SelectContent>
          </Select>
          <Input
            aria-label="Filtrer par devise"
            maxLength={3}
            onChange={(event) => update({ currency: event.target.value.toUpperCase() })}
            placeholder="Devise"
            value={currencyCode}
          />
        </div>
        {ownerType !== "ALL" && params.get("ownerId") ? (
          <div className="flex flex-wrap items-center justify-between gap-3 border-b bg-muted/25 px-4 py-2 text-sm">
            <span>
              Révision filtrée
              {prices.data?.content[0]?.productName ? ` : ${prices.data.content[0].productName}` : ""}
            </span>
            <Button onClick={() => update({ ownerId: null })} size="sm" variant="ghost">
              Retirer le filtre produit
            </Button>
          </div>
        ) : null}

        {prices.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : prices.isError ? (
          <ErrorState retry={() => void prices.refetch()} />
        ) : (
          <>
            <MobilePriceList prices={prices.data?.content ?? []} />
            <div className="hidden md:block">
              <DataTable
                columns={columns}
                data={prices.data?.content ?? []}
                emptyState={<EmptyState description="Modifiez les filtres ou créez un tarif." title="Aucun tarif" />}
                getRowId={(price) => price.id}
                onSortingChange={(next) => {
                  const value = next[0];
                  update(
                    value
                      ? { sort: value.id, direction: value.desc ? "desc" : "asc" }
                      : { sort: null, direction: null },
                  );
                }}
                sorting={sorting}
              />
            </div>
          </>
        )}
        {prices.data ? (
          <PaginationBar
            onPageChange={(next) => update({ page: next }, false)}
            page={prices.data.page}
            totalElements={prices.data.totalElements}
            totalPages={prices.data.totalPages}
          />
        ) : null}
      </section>
    </div>
  );
}
