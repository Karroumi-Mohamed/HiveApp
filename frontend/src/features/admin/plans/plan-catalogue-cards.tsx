import { ArrowRightIcon, CopyIcon, GitBranchIcon, StackIcon, UsersIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { Link } from "react-router";
import type { CommercialProductBlocker, PlanOperationalItem } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { EmptyState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import {
  canOpenCommercialProduct,
  canReviseCommercialProduct,
} from "@/features/admin/commercial/commercial-permission-rules";
import { BlockerSummary, LifecycleText } from "@/features/admin/commercial/commercial-presentation";
import { ProductPriceOptions, useCurrentProductPrices } from "@/features/admin/price-books/product-price-summary";
import { cn } from "@/lib/utils";

// Routine lifecycle/delete prerequisites belong in the detail page, not the catalogue summary.
const cardWarningBlockers = new Set<CommercialProductBlocker>([
  "NO_ACTIVE_PRICE",
  "NO_PRICE_STARTING_POINT",
  "NO_FEATURES",
  "NO_INCLUDED_FEATURES",
]);

function PlanCardAction({
  label,
  children,
  to,
  unavailableReason,
  className,
}: {
  label: string;
  children: ReactNode;
  to?: string;
  unavailableReason?: string;
  className?: string;
}) {
  const styles = cn("min-h-11 gap-1.5 text-xs text-muted-foreground [&_svg]:size-3.5", className);
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        {to && !unavailableReason ? (
          <Button asChild variant="ghost" size="sm" className={styles}>
            <Link to={to} aria-label={label}>
              {children}
            </Link>
          </Button>
        ) : (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label={label}
            aria-disabled="true"
            className={cn(styles, "cursor-not-allowed opacity-45 hover:bg-transparent")}
          >
            {children}
          </Button>
        )}
      </TooltipTrigger>
      <TooltipContent>{unavailableReason ?? label}</TooltipContent>
    </Tooltip>
  );
}

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
  const warnings = plan.blockers.filter((blocker) => cardWarningBlockers.has(blocker));
  return (
    <article aria-label={`Forfait ${plan.name}`} className="flex min-w-0 flex-col rounded-xl border bg-card">
      <div className="p-5 sm:px-6 sm:pt-6">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="min-w-0 break-words text-xl font-semibold tracking-tight">{plan.name}</h2>
          <LifecycleText status={plan.status} />
        </div>
        <div className="my-5 min-h-9">{pricing}</div>
        <dl className="flex flex-wrap gap-x-5 gap-y-2 text-[13px]">
          <div className="flex items-center gap-1.5">
            <dt className="order-2 text-muted-foreground">
              fonctionnalités<span className="sr-only"> incluses</span>
            </dt>
            <dd className="order-1 flex items-center gap-2 font-semibold tabular-nums">
              <StackIcon aria-hidden="true" className="size-4 text-muted-foreground" />
              {plan.includedFeatureCount}
            </dd>
          </div>
          <div className="flex items-center gap-1.5">
            <dt className="order-2 text-muted-foreground">abonnés</dt>
            <dd className="order-1 flex items-center gap-2 font-semibold tabular-nums">
              <UsersIcon aria-hidden="true" className="size-4 text-muted-foreground" />
              {plan.currentSubscriberCount}
            </dd>
          </div>
        </dl>
        {warnings.length > 0 && (
          <div className="mt-4">
            <BlockerSummary blockers={warnings} />
          </div>
        )}
      </div>
      <fieldset
        className="mt-auto flex flex-wrap items-center gap-1 border-t px-2 py-1.5 sm:px-3"
        aria-label={`Actions pour ${plan.name}`}
      >
        <PlanCardAction
          label={`Dupliquer le forfait ${plan.name}`}
          unavailableReason={canDuplicate ? undefined : "Duplication non autorisée"}
          to={canDuplicate ? `/admin/plans/new?from=${plan.id}` : undefined}
        >
          <CopyIcon aria-hidden="true" />
          Dupliquer
        </PlanCardAction>
        <PlanCardAction
          label={`Réviser le forfait ${plan.name}`}
          unavailableReason={
            !canRevise
              ? "Révision non autorisée"
              : !revise
                ? "La révision n’est pas disponible dans cet état"
                : undefined
          }
          to={revise ? path : undefined}
        >
          <GitBranchIcon aria-hidden="true" />
          Réviser
        </PlanCardAction>
        <PlanCardAction
          label={`Ouvrir le forfait ${plan.name}`}
          unavailableReason={canOpen ? undefined : "Consultation non autorisée"}
          to={canOpen ? path : undefined}
          className="ms-auto text-[13px] text-primary hover:text-primary"
        >
          Ouvrir
          <ArrowRightIcon aria-hidden="true" className="rtl:rotate-180" />
        </PlanCardAction>
      </fieldset>
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
    <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,20rem),1fr))] gap-5 p-4 sm:p-5">
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
                    <ProductPriceOptions prices={prices} variant="catalogue" />
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
