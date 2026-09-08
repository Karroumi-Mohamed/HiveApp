import {
  ArchiveIcon,
  ArrowLeftIcon,
  CalendarPlusIcon,
  GitBranchIcon,
  PauseIcon,
  PencilSimpleIcon,
  PlayIcon,
  TrashIcon,
} from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { Link, Navigate, useParams, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { ProductPrice } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { formatExactMoney } from "@/lib/exact-decimal";
import { ProductPriceActionButton } from "./product-price-action-button";
import {
  DeleteProductPriceDialog,
  EditProductPriceDialog,
  ProductPriceActivationDialog,
  ProductPriceReasonDialog,
} from "./product-price-dialogs";
import { ProductPriceReplacementDialog } from "./product-price-replacement-dialog";
import {
  isProductPriceReplacementDraft,
  productPriceBlocker,
  productPriceCycle,
  productPriceHistoryLabel,
  productPriceOwner,
  productPriceOwnerReadPermission,
  productPriceStatus,
} from "./product-price-rules";

const money = formatExactMoney;

function dateTime(value: string | null) {
  return value
    ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
    : "Sans date de fin";
}

function productLink(price: ProductPrice) {
  if (price.productType === "PLAN") return `/admin/plans/${price.productId}`;
  if (price.productType === "ADD_ON") return `/admin/add-ons/${price.productId}`;
  return `/admin/quota-packages/${price.productId}`;
}

function Terms({ price }: { price: ProductPrice }) {
  return (
    <div className="grid gap-5 lg:grid-cols-[1.05fr_0.95fr]">
      <section className="rounded-xl border bg-card p-5">
        <h2 className="text-sm font-semibold">Conditions enregistrées</h2>
        <dl className="mt-5 grid gap-5 sm:grid-cols-2">
          <div>
            <dt className="text-xs text-muted-foreground">Montant</dt>
            <dd className="mt-1 text-xl font-semibold tabular-nums">{money(price.amount, price.currencyCode)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Cycle</dt>
            <dd className="mt-1 font-medium">{productPriceCycle[price.billingCycle]}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Valide à partir du</dt>
            <dd className="mt-1 text-sm font-medium">{dateTime(price.effectiveFrom)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Valide jusqu’au</dt>
            <dd className="mt-1 text-sm font-medium">{dateTime(price.effectiveUntil)}</dd>
          </div>
        </dl>
      </section>
      <section className="rounded-xl border bg-card p-5">
        <h2 className="text-sm font-semibold">Identité tarifaire</h2>
        <dl className="mt-5 space-y-4 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-muted-foreground">Révision</dt>
            <dd className="font-medium tabular-nums">R{price.revisionNumber}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-muted-foreground">Produit</dt>
            <dd className="text-end font-medium">{productPriceOwner[price.productType]}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-muted-foreground">Créé le</dt>
            <dd className="text-end font-medium">{dateTime(price.createdAt)}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-muted-foreground">Dernière modification</dt>
            <dd className="text-end font-medium">{dateTime(price.updatedAt)}</dd>
          </div>
        </dl>
        {price.compatibilityDefault ? (
          <p className="mt-5 border-t pt-4 text-xs leading-5 text-muted-foreground">
            Tarif de compatibilité créé à partir des anciennes conditions du produit. Il ne peut pas être supprimé.
          </p>
        ) : null}
      </section>
    </div>
  );
}

function Lifecycle({ price }: { price: ProductPrice }) {
  const session = useAdminSession();
  return (
    <div className="space-y-5">
      {price.blockers.length ? (
        <section className="rounded-xl border border-warning/30 bg-warning/5 p-5">
          <h2 className="text-sm font-semibold">Points à traiter</h2>
          <ul className="mt-3 list-disc space-y-1 ps-5 text-sm text-muted-foreground">
            {price.blockers.map((blocker) => (
              <li key={blocker}>{productPriceBlocker[blocker]}</li>
            ))}
          </ul>
        </section>
      ) : null}
      <section className="rounded-xl border bg-card p-5">
        <h2 className="text-sm font-semibold">Opérations</h2>
        <div className="mt-4 flex flex-wrap gap-2">
          {isProductPriceReplacementDraft(price) ? (
            <ProductPriceReplacementDialog
              successor={price}
              trigger={
                <Button
                  disabled={!session.can(adminPermissions.priceBooksPreviewReplacement)}
                  title={
                    session.can(adminPermissions.priceBooksPreviewReplacement)
                      ? undefined
                      : "Votre rôle ne permet pas de vérifier un remplacement tarifaire."
                  }
                >
                  <CalendarPlusIcon />
                  Programmer le remplacement
                </Button>
              }
            />
          ) : null}
          {price.status === "DRAFT" || price.status === "INACTIVE" ? (
            <ProductPriceActivationDialog
              price={price}
              trigger={
                <ProductPriceActionButton action="PREVIEW_ACTIVATION" price={price} variant="default">
                  <PlayIcon />
                  Vérifier et mettre en vente
                </ProductPriceActionButton>
              }
            />
          ) : null}
          {price.status === "ACTIVE" ? (
            <ProductPriceReasonDialog
              action="PAUSE"
              price={price}
              trigger={
                <ProductPriceActionButton action="PAUSE" price={price}>
                  <PauseIcon />
                  Suspendre la vente
                </ProductPriceActionButton>
              }
            />
          ) : null}
          {price.status === "ACTIVE" || price.status === "INACTIVE" ? (
            <ProductPriceReasonDialog
              action="REVISE"
              price={price}
              trigger={
                <ProductPriceActionButton action="REVISE" price={price}>
                  <GitBranchIcon />
                  Créer une révision
                </ProductPriceActionButton>
              }
            />
          ) : null}
          {price.status !== "ACTIVE" && price.status !== "ARCHIVED" ? (
            <ProductPriceReasonDialog
              action="ARCHIVE"
              price={price}
              trigger={
                <ProductPriceActionButton action="ARCHIVE" price={price} variant="destructive">
                  <ArchiveIcon />
                  Archiver
                </ProductPriceActionButton>
              }
            />
          ) : null}
          {price.status === "DRAFT" ? (
            <DeleteProductPriceDialog
              price={price}
              trigger={
                <ProductPriceActionButton action="DELETE_DRAFT" price={price} variant="ghost">
                  <TrashIcon />
                  Supprimer le brouillon
                </ProductPriceActionButton>
              }
            />
          ) : null}
        </div>
        {price.status === "ARCHIVED" ? (
          <p className="mt-4 text-sm text-muted-foreground">Ce tarif est conservé en lecture seule.</p>
        ) : null}
        {!session.can(adminPermissions.priceBooksPreviewActivation) && price.status === "DRAFT" ? (
          <p className="mt-4 text-xs text-muted-foreground">Votre rôle ne permet pas de prévisualiser l’activation.</p>
        ) : null}
      </section>
    </div>
  );
}

function History({ priceId }: { priceId: string }) {
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const history = useQuery({
    queryKey: adminCommercialKeys.priceBooks.history(priceId, page),
    queryFn: () => adminApi.productPriceHistory(priceId, page),
    enabled: commercialQueryEnabled(session.can, adminPermissions.priceBooksReadHistory),
  });
  if (!session.can(adminPermissions.priceBooksReadHistory)) return <PermissionState />;
  if (history.isLoading) return <LoadingState />;
  if (history.isError) return <ErrorState retry={() => void history.refetch()} />;
  if (!history.data?.totalElements) return <EmptyState title="Aucun événement" />;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <ol className="divide-y">
        {history.data.content.map((entry) => (
          <li className="grid gap-2 p-4 sm:grid-cols-[1fr_auto] sm:items-start" key={entry.id}>
            <div>
              <p className="text-sm font-medium">{productPriceHistoryLabel(entry.action)}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                {entry.outcome === "SUCCEEDED"
                  ? "Réussi"
                  : `Échec${entry.failureType ? ` · ${entry.failureType}` : ""}`}{" "}
                · par {entry.actorEmail ?? "Système"}
              </p>
              {entry.reason ? <p className="mt-2 text-sm">{entry.reason}</p> : null}
            </div>
            <time className="text-xs text-muted-foreground" dateTime={entry.occurredAt}>
              {dateTime(entry.occurredAt)}
            </time>
          </li>
        ))}
        {!history.data.content.length ? (
          <li className="p-5">
            <EmptyState description="Revenez à une page précédente." title="Aucun événement sur cette page" />
          </li>
        ) : null}
      </ol>
      <PaginationBar
        onPageChange={(next) => setParams(next > 0 ? { page: String(next) } : {}, { replace: true })}
        page={history.data.page}
        totalElements={history.data.totalElements}
        totalPages={history.data.totalPages}
      />
    </section>
  );
}

export function AdminProductPriceDetailPage() {
  const session = useAdminSession();
  const { priceId, tab } = useParams();
  const id = priceId ?? "";
  const canRead = session.can(adminPermissions.priceBooksRead);
  const canReadHistory = session.can(adminPermissions.priceBooksReadHistory);
  const price = useQuery({
    queryKey: adminCommercialKeys.priceBooks.detail(id),
    queryFn: () => adminApi.productPrice(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.priceBooksRead, Boolean(id)),
  });
  if (!canRead && !canReadHistory) return <PermissionState />;
  if (!canRead) {
    if (tab !== "history") return <Navigate replace to={`/admin/price-books/${id}/history`} />;
    return (
      <div className="space-y-6">
        <Button asChild className="-ms-2" size="sm" variant="ghost">
          <Link to={session.can(adminPermissions.priceBooksList) ? "/admin/price-books" : "/admin"}>
            <ArrowLeftIcon className="rtl:rotate-180" />
            {session.can(adminPermissions.priceBooksList) ? "Grille tarifaire" : "Administration"}
          </Link>
        </Button>
        <PageHeader title="Historique du tarif" />
        <History priceId={id} />
      </div>
    );
  }
  if (price.isLoading) return <LoadingState />;
  if (price.isError || !price.data) return <ErrorState retry={() => void price.refetch()} title="Tarif introuvable" />;
  const data = price.data;
  const status = productPriceStatus[data.status];
  return (
    <div className="space-y-6">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to="/admin/price-books">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Grille tarifaire
        </Link>
      </Button>
      <PageHeader
        actions={
          <>
            {data.status !== "DRAFT" &&
            session.can(adminPermissions.repricingPreview) &&
            session.can(adminPermissions.repricingResults) ? (
              <Button asChild variant="outline">
                <Link to={`/admin/repricing/new?from=${id}`}>Appliquer aux abonnés existants</Link>
              </Button>
            ) : null}
            {data.status === "DRAFT" ? (
              <EditProductPriceDialog
                price={data}
                trigger={
                  <ProductPriceActionButton action="EDIT_DRAFT" price={data} variant="ghost">
                    <PencilSimpleIcon />
                    Modifier
                  </ProductPriceActionButton>
                }
              />
            ) : null}
          </>
        }
        description={
          <span className="flex flex-wrap items-center gap-2">
            <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
            <span>{productPriceCycle[data.billingCycle]}</span>
            <span>· R{data.revisionNumber}</span>
          </span>
        }
        title={data.productName}
      />
      <SectionTabs
        ariaLabel="Sections du tarif"
        tabs={[
          { label: "Synthèse", to: `/admin/price-books/${id}`, end: true },
          { label: "Conditions et cycle de vie", to: `/admin/price-books/${id}/terms` },
          ...(session.can(adminPermissions.priceBooksReadHistory)
            ? [{ label: "Historique", to: `/admin/price-books/${id}/history` }]
            : []),
        ]}
      />
      {tab === "history" ? (
        <History priceId={id} />
      ) : tab === "terms" ? (
        <div className="space-y-5">
          <Terms price={data} />
          <Lifecycle price={data} />
        </div>
      ) : (
        <div className="grid gap-5 lg:grid-cols-[1.05fr_0.95fr]">
          <section className="rounded-xl border bg-card p-5">
            <p className="text-xs text-muted-foreground">Tarif</p>
            <p className="mt-1 text-3xl font-semibold tracking-tight tabular-nums">
              {money(data.amount, data.currencyCode)}
            </p>
            <p className="mt-1 text-sm text-muted-foreground">{productPriceCycle[data.billingCycle]}</p>
            <dl className="mt-6 space-y-4 border-t pt-4 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Début</dt>
                <dd className="text-end font-medium">{dateTime(data.effectiveFrom)}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Fin</dt>
                <dd className="text-end font-medium">{dateTime(data.effectiveUntil)}</dd>
              </div>
            </dl>
          </section>
          <section className="rounded-xl border bg-card p-5">
            <p className="text-xs text-muted-foreground">Produit lié</p>
            <p className="mt-1 text-lg font-semibold">{data.productName}</p>
            <p className="mt-1 text-sm text-muted-foreground">{productPriceOwner[data.productType]}</p>
            {session.can(productPriceOwnerReadPermission(data.productType)) ? (
              <Button asChild className="mt-5" size="sm" variant="outline">
                <Link to={productLink(data)}>Ouvrir la révision du produit</Link>
              </Button>
            ) : null}
          </section>
          {data.blockers.length ? (
            <section className="rounded-xl border border-warning/30 bg-warning/5 p-5 lg:col-span-2">
              <h2 className="text-sm font-semibold">État opérationnel</h2>
              <ul className="mt-2 list-disc space-y-1 ps-5 text-sm text-muted-foreground">
                {data.blockers.map((blocker) => (
                  <li key={blocker}>{productPriceBlocker[blocker]}</li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      )}
    </div>
  );
}
