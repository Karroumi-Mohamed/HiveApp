import { GitBranchIcon, MagnifyingGlassIcon, PlusIcon, UsersIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { PlanVersion } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { ProductPriceOptions } from "@/features/admin/price-books/product-price-summary";
import { adminCommercialKeys } from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { PlanStatusTag } from "./plan-version-controls";

export function PlanFamilyCatalogue() {
  const { t } = useTranslation();
  const session = useAdminSession();
  const [params, setParams] = useSearchParams();
  const [comparing, setComparing] = useState(params.get("compare") === "1");
  const [selection, setSelection] = useState<PlanVersion[]>([]);
  const canCompare = session.can(adminPermissions.plansCompare);
  const search = params.get("search") ?? "";
  const page = Math.max(0, Number(params.get("page")) || 0);
  const debounced = useDebouncedValue(search);
  const query = useQuery({
    queryKey: [...adminCommercialKeys.plans.all(), "families", debounced, page],
    queryFn: () => adminApi.planFamilies({ search: debounced, page, size: 12 }),
    enabled: session.can(adminPermissions.plansListFamilies),
  });
  return (
    <div className="space-y-6">
      <PageHeader
        title={t("planVersions.plans")}
        actions={
          <>
            {canCompare && (
              <Button
                variant="outline"
                onClick={() => {
                  setComparing(!comparing);
                  setSelection([]);
                }}
              >
                {t(comparing ? "planComparison.cancel" : "planComparison.title")}
              </Button>
            )}
            {session.can(adminPermissions.plansCreate) && (
              <Button asChild>
                <Link to="/admin/plans/new">
                  <PlusIcon />
                  {t("planVersions.create")}
                </Link>
              </Button>
            )}
          </>
        }
      />
      {comparing && canCompare && (
        <section
          className="sticky top-0 z-20 flex flex-wrap items-center gap-3 rounded-xl border bg-card p-4 shadow-sm"
          aria-label={t("planComparison.choose")}
        >
          <p className="text-sm font-medium">{t("planComparison.selected", { count: selection.length })}</p>
          {selection.map((plan) => (
            <Button
              key={plan.id}
              size="sm"
              variant="secondary"
              aria-label={t("planComparison.remove", { name: plan.name })}
              onClick={() => setSelection(selection.filter((item) => item.id !== plan.id))}
            >
              {plan.name} · V{plan.productVersionNumber} ×
            </Button>
          ))}
          <div className="ms-auto">
            {selection.length >= 2 ? (
              <Button asChild>
                <Link to={`/admin/plans/compare?ids=${selection.map((plan) => plan.id).join(",")}`}>
                  {t("planComparison.compare")}
                </Link>
              </Button>
            ) : (
              <Button disabled>{t("planComparison.choose")}</Button>
            )}
          </div>
        </section>
      )}
      <div className="relative max-w-md">
        <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-3 size-4 text-muted-foreground" />
        <Input
          className="ps-9"
          aria-label={t("planVersions.search")}
          placeholder={t("planVersions.search")}
          value={search}
          onChange={(event) =>
            setParams(
              (previous) => {
                const next = new URLSearchParams(previous);
                next.set("search", event.target.value);
                next.delete("page");
                return next;
              },
              { replace: true },
            )
          }
        />
      </div>
      {query.isLoading ? (
        <LoadingState rows={3} />
      ) : query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : query.data?.content.length ? (
        <>
          <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,20rem),1fr))] gap-5">
            {query.data.content.map((family) => {
              const plan = family.publicVersion;
              return (
                <article
                  key={family.lineageId}
                  aria-label={plan.name}
                  className="flex min-w-0 flex-col rounded-xl border bg-card"
                >
                  <div className="space-y-5 p-6">
                    {comparing && canCompare && (
                      <label
                        htmlFor={`compare-${plan.id}`}
                        className="flex min-h-11 cursor-pointer items-center gap-3 text-sm"
                      >
                        <Checkbox
                          id={`compare-${plan.id}`}
                          checked={selection.some((item) => item.id === plan.id)}
                          disabled={selection.length >= 3 && !selection.some((item) => item.id === plan.id)}
                          onCheckedChange={(checked) =>
                            setSelection((current) =>
                              checked
                                ? current.some((item) => item.id === plan.id) || current.length >= 3
                                  ? current
                                  : [...current, plan]
                                : current.filter((item) => item.id !== plan.id),
                            )
                          }
                        />
                        {t("planComparison.select", { name: plan.name })}
                      </label>
                    )}
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <h2 className="text-xl font-semibold tracking-tight">{plan.name}</h2>
                      <PlanStatusTag status={plan.status} />
                    </div>
                    {!family.pricesVisible ? (
                      <p className="text-sm text-muted-foreground">{t("planVersions.pricesHidden")}</p>
                    ) : family.currentPrices.length ? (
                      <ProductPriceOptions prices={family.currentPrices} variant="catalogue" />
                    ) : (
                      <p className="text-sm text-warning">{t("planVersions.noPrice")}</p>
                    )}
                    <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                      {family.currentSubscriberCount !== null && (
                        <span className="inline-flex items-center gap-2">
                          <UsersIcon aria-hidden="true" />
                          {t("planVersions.subscribersCount", { count: family.currentSubscriberCount })}
                        </span>
                      )}
                      {session.can(adminPermissions.plansListVersions) && (
                        <Link
                          className="inline-flex min-h-6 items-center gap-2 text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                          to={`/admin/plans/${plan.id}/versions`}
                        >
                          <GitBranchIcon aria-hidden="true" />
                          {t("planVersions.versionsCount", { count: family.versionCount })}
                        </Link>
                      )}
                    </div>
                  </div>
                  <div className="mt-auto flex flex-wrap items-center justify-end gap-2 border-t px-4 py-3">
                    {family.draft && session.can(adminPermissions.plansReadDetail) && (
                      <Button asChild size="sm" variant="ghost">
                        <Link to={`/admin/plans/${family.draft.id}`}>{t("planVersions.continueDraft")}</Link>
                      </Button>
                    )}
                    {session.can(adminPermissions.plansReadDetail) && (
                      <Button asChild size="sm" variant="outline">
                        <Link to={`/admin/plans/${plan.id}`}>{t("planVersions.open")}</Link>
                      </Button>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
          <PaginationBar
            page={page}
            totalPages={query.data.totalPages}
            totalElements={query.data.totalElements}
            onPageChange={(next) =>
              setParams((previous) => {
                const value = new URLSearchParams(previous);
                value.set("page", String(next));
                return value;
              })
            }
          />
        </>
      ) : (
        <EmptyState title={t("planVersions.empty")} description={t("planVersions.emptyHint")} />
      )}
    </div>
  );
}
