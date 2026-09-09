import { ArrowRightIcon, CopyIcon, GitBranchIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { Link } from "react-router";
import type { PlanOperationalItem } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { EmptyState } from "@/components/patterns/remote-state";
import { RowAction } from "@/components/patterns/row-action";
import { Button } from "@/components/ui/button";
import {
  canOpenCommercialProduct,
  canReviseCommercialProduct,
} from "@/features/admin/commercial/commercial-permission-rules";
import { availabilityLabel, BlockerSummary, LifecycleText } from "@/features/admin/commercial/commercial-presentation";
import { ProductPriceOptions, useCurrentProductPrices } from "@/features/admin/price-books/product-price-summary";

export function PlanCatalogueCard({
  plan,
  pricing,
  canOpen,
  canDuplicate,
  canRevise,
}: {
  plan: PlanOperationalItem;
  pricing: ReactNode;
  canOpen: boolean;
  canDuplicate: boolean;
  canRevise: boolean;
}) {
  const path = `/admin/plans/${plan.id}`;
  const revise = canRevise && plan.availableActions.includes("REVISE");
  return (
    <article
      aria-label={`Forfait ${plan.name}`}
      className="flex min-w-0 flex-col rounded-xl border bg-card p-5 shadow-sm sm:p-6"
    >
      <div className="flex items-start justify-between gap-3">
        <h2 className="min-w-0 break-words text-xl font-semibold tracking-tight">{plan.name}</h2>
        <LifecycleText status={plan.status} />
      </div>
      <p className="mt-2 text-sm text-muted-foreground">
        {availabilityLabel[plan.salesVisibility]} · Version {plan.revisionNumber}
      </p>
      <div className="my-6 min-h-20 border-y py-4">
        <h3 className="mb-3 text-xs font-medium uppercase tracking-wide text-muted-foreground">Tarifs actuels</h3>
        {pricing}
      </div>
      <dl className="grid grid-cols-2 gap-4 text-sm">
        <div>
          <dt className="text-muted-foreground">Fonctionnalités incluses</dt>
          <dd className="mt-1 text-lg font-semibold tabular-nums">{plan.includedFeatureCount}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Abonnements actuels</dt>
          <dd className="mt-1 text-lg font-semibold tabular-nums">{plan.currentSubscriberCount}</dd>
        </div>
      </dl>
      {plan.featureCount > plan.includedFeatureCount && (
        <p className="mt-3 text-sm text-muted-foreground">
          {plan.featureCount - plan.includedFeatureCount} autres fonctionnalités configurées
        </p>
      )}
      <div className="mt-4">
        <BlockerSummary blockers={plan.blockers} />
      </div>
      <div className="mt-auto flex flex-wrap items-center justify-between gap-3 pt-6">
        <fieldset className="flex gap-1" aria-label={`Actions pour ${plan.name}`}>
          <RowAction
            icon={<CopyIcon />}
            label="Dupliquer le forfait"
            disabled={!canDuplicate}
            disabledLabel="Duplication non autorisée"
            to={canDuplicate ? `/admin/plans/new?from=${plan.id}` : undefined}
          />
          <RowAction
            icon={<GitBranchIcon />}
            label="Ouvrir pour réviser"
            disabled={!revise}
            disabledLabel={!canRevise ? "Révision non autorisée" : "La révision n’est pas disponible dans cet état"}
            to={revise ? path : undefined}
          />
        </fieldset>
        {canOpen ? (
          <Button asChild variant="outline">
            <Link to={path}>
              Ouvrir le forfait
              <ArrowRightIcon className="rtl:rotate-180" />
            </Link>
          </Button>
        ) : (
          <Button variant="outline" disabled>
            Consultation non autorisée
          </Button>
        )}
      </div>
    </article>
  );
}

export function PlanCatalogueCards({ plans }: { plans: PlanOperationalItem[] }) {
  const session = useAdminSession();
  const query = useCurrentProductPrices(
    "PLAN",
    plans.map((plan) => plan.id),
  );
  const canReadPrices = session.can(adminPermissions.priceBooksList);
  if (!plans.length)
    return <EmptyState title="Aucun forfait" description="Modifiez les filtres ou créez votre premier forfait." />;
  const truncated = Boolean(query.data && query.data.totalElements > query.data.content.length);
  return (
    <div className="grid gap-5 p-4 sm:grid-cols-2 sm:p-5 xl:grid-cols-3">
      {plans.map((plan) => {
        const prices = query.data?.content.filter((price) => price.productId === plan.id) ?? [];
        return (
          <PlanCatalogueCard
            key={plan.id}
            plan={plan}
            canOpen={canOpenCommercialProduct(session.can, "PLAN")}
            canDuplicate={session.can(adminPermissions.plansDuplicate)}
            canRevise={canReviseCommercialProduct(session.can, "PLAN")}
            pricing={
              !canReadPrices ? (
                <p className="text-sm text-muted-foreground">Tarifs non accessibles avec votre rôle.</p>
              ) : query.isLoading ? (
                <p role="status" className="text-sm text-muted-foreground">
                  Chargement des tarifs…
                </p>
              ) : query.isError ? (
                <div role="alert" className="text-sm">
                  Tarifs indisponibles.{" "}
                  <Button variant="ghost" size="sm" onClick={() => void query.refetch()}>
                    Réessayer
                  </Button>
                </div>
              ) : (
                <div className="space-y-2">
                  {prices.length ? (
                    <ProductPriceOptions prices={prices} />
                  ) : (
                    <p className="text-sm text-muted-foreground">
                      {truncated ? "Aperçu des tarifs incomplet." : "Aucun tarif applicable actuellement."}
                    </p>
                  )}
                  {truncated && (
                    <Link
                      className="text-sm text-primary underline"
                      to={`/admin/price-books?ownerType=PLAN&ownerId=${plan.id}`}
                    >
                      Voir tous les tarifs
                    </Link>
                  )}
                </div>
              )
            }
          />
        );
      })}
    </div>
  );
}
