import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { CommercialProductType, ExtensionCompatibility, RegistryFeature } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { useCurrentProductPrices } from "@/features/admin/price-books/product-price-summary";
import { ComparisonPriceList } from "./plan-comparison-prices";

const reasons: Record<string, [string, string]> = {
  PLAN_EXTENSIONS_CLOSED: ["Extensions fermées sur ce forfait", "الإضافات مغلقة لهذه الخطة"],
  NOT_ALLOW_LISTED: ["Hors liste autorisée", "خارج القائمة المسموحة"],
  PLAN_EXPLICITLY_BLOCKED: ["Extension bloquée pour ce forfait", "الإضافة محظورة لهذه الخطة"],
  OPTIONAL_TARGETING_EXCLUDED: ["Ce forfait n’est pas ciblé par cette extension", "هذه الإضافة لا تستهدف هذه الخطة"],
  PRODUCT_NOT_FOUND: ["Produit introuvable", "المنتج غير موجود"],
  PRODUCT_NOT_ACTIVE: ["Version non active", "الإصدار غير نشط"],
  DIRECT_ONLY: ["Attribution directe uniquement", "للإسناد المباشر فقط"],
  FEATURE_DEFINITION_MISSING: ["Définition de fonctionnalité manquante", "تعريف الميزة مفقود"],
  FEATURE_NOT_PLAN_ASSIGNABLE: ["Fonctionnalité non commercialisable", "الميزة غير قابلة للبيع"],
  FEATURE_NOT_CLIENT_FACING: ["Fonctionnalité réservée à la plateforme", "الميزة مخصصة للمنصة"],
  FEATURE_NEW_SALES_DISABLED: ["Vente de la fonctionnalité suspendue", "بيع الميزة معلق"],
  FEATURE_RUNTIME_DISABLED: ["Fonctionnalité désactivée", "الميزة معطلة"],
  PLAN_FEATURE_NOT_OPTIONAL: [
    "Fonctionnalité non proposée en option par ce forfait",
    "الخطة لا تتيح الميزة كخيار إضافي",
  ],
  DEPENDENCY_MISSING: ["Add-on requis introuvable", "الإضافة المطلوبة غير موجودة"],
  PRICE_UNAVAILABLE: ["Aucun tarif compatible", "لا يوجد سعر متوافق"],
  DEPENDENCY_UNAVAILABLE: ["Dépendance indisponible", "تبعية غير متاحة"],
  DEPENDENCY_CYCLE: ["Dépendances circulaires", "تبعيات دائرية"],
  DEPENDENCY_NOT_SELECTED: ["Un add-on requis doit aussi être sélectionné", "يجب اختيار الإضافة المطلوبة أيضا"],
  EXCLUDED_SELECTION: ["Sélections incompatibles", "اختيارات غير متوافقة"],
  DUPLICATE_PAID_CAPABILITY: ["Fonctionnalité déjà fournie par une autre sélection", "الميزة متاحة بالفعل باختيار آخر"],
  QUOTA_OWNER_MISSING: ["Aucune capacité compatible à étendre", "لا توجد سعة متوافقة لتوسيعها"],
  QUOTA_NOT_FINITE: ["La capacité de base n’a pas de limite finie", "السعة الأساسية ليست محدودة"],
  INVALID_QUANTITY: ["Quantité non autorisée", "الكمية غير مسموحة"],
};

function ExtensionList({
  items,
  type,
  catalog,
}: {
  items: ExtensionCompatibility[];
  type: CommercialProductType;
  catalog: RegistryFeature[];
}) {
  const { t, i18n } = useTranslation();
  const session = useAdminSession();
  const prices = useCurrentProductPrices(
    type,
    items.map((item) => item.productId),
  );
  const canReadPrices = session.can(adminPermissions.priceBooksList);
  const partial = Boolean(prices.data && prices.data.totalElements > prices.data.content.length);
  return (
    <ul className="divide-y">
      {items.map((item) => {
        const unit =
          catalog
            .find((feature) => feature.code === item.quotaFeatureCode)
            ?.quotaSchema.find((quota) => quota.resource === item.quotaResource)?.unit || item.quotaResource;
        const applicable = prices.data?.content.filter((price) => price.productId === item.productId) ?? [];
        return (
          <li className="space-y-3 py-4 first:pt-0" key={item.productId}>
            <div>
              <p className="font-semibold">{item.name}</p>
              <p className={item.operatorSelectable ? "text-xs text-success" : "text-xs text-warning"}>
                {t(item.operatorSelectable ? "planComparison.available" : "planComparison.unavailable")}
              </p>
            </div>
            {item.capacityPerUnit !== null && (
              <p className="text-sm font-medium">
                +{item.capacityPerUnit} {unit}
              </p>
            )}
            {item.repeatable !== null && (
              <p className="text-xs text-muted-foreground">
                {item.repeatable
                  ? t("planComparison.quantity", { count: item.maximumQuantity })
                  : t("planComparison.once")}
              </p>
            )}
            <p className="text-xs text-muted-foreground">
              {t(item.clientCatalogVisible ? "planComparison.clientVisible" : "planComparison.directOnly")}
            </p>
            {item.requiredAddOnCodes.length > 0 && (
              <p className="text-xs text-warning">
                {t("planComparison.required", { names: item.requiredAddOnCodes.join(", ") })}
              </p>
            )}
            {item.issues.length > 0 && (
              <ul className="space-y-1 text-xs text-warning">
                {item.issues.map((issue) => (
                  <li key={`${issue.reason}:${issue.sourceCode}:${issue.source}`}>
                    {reasons[issue.reason]?.[i18n.language.startsWith("ar") ? 1 : 0] ??
                      `${t("planComparison.conditions")} : ${issue.reason}`}
                    {issue.sourceCode ? ` · ${issue.sourceCode}` : ""}
                  </li>
                ))}
              </ul>
            )}
            {!canReadPrices ? (
              <p className="text-xs text-muted-foreground">{t("planComparison.priceHidden")}</p>
            ) : prices.isLoading ? (
              <p role="status">{t("planComparison.loading")}</p>
            ) : prices.isError ? (
              <ErrorState retry={() => void prices.refetch()} />
            ) : (
              <>
                {applicable.length ? (
                  <ComparisonPriceList prices={applicable} />
                ) : (
                  <p className="text-xs text-muted-foreground">
                    {t(partial ? "planComparison.pricingPartial" : "planComparison.noPrice")}
                  </p>
                )}
                {partial && <p className="text-xs text-warning">{t("planComparison.pricingPartial")}</p>}
                <Link
                  className="inline-block min-h-6 text-xs text-primary underline"
                  to={`/admin/price-books?ownerType=${type}&ownerId=${item.productId}`}
                >
                  {t("planComparison.morePrices")}
                </Link>
              </>
            )}
          </li>
        );
      })}
    </ul>
  );
}

export function ComparisonExtensions({
  planId,
  featureCode,
  catalog,
}: {
  planId: string;
  featureCode?: string;
  catalog: RegistryFeature[];
}) {
  const { t } = useTranslation();
  const session = useAdminSession();
  const [page, setPage] = useState(0);
  const [type, setType] = useState<CommercialProductType>("ADD_ON");
  const allowed = session.can(adminPermissions.commercialInspectCompatibility);
  const query = useQuery({
    queryKey: ["admin", "commercial", "comparison-extensions", planId, featureCode, type, page],
    queryFn: () => adminApi.inspectPlanCompatibility(planId, { type, featureCode, page, size: 10 }),
    enabled: allowed,
  });
  if (!allowed) return <p className="text-sm text-muted-foreground">{t("planComparison.extensionsHidden")}</p>;
  return (
    <div className="space-y-4">
      <fieldset className="flex flex-wrap gap-2" aria-label={t("planComparison.extensions")}>
        {(["ADD_ON", "QUOTA_PACKAGE"] as const).map((value) => (
          <Button
            key={value}
            size="sm"
            variant={type === value ? "secondary" : "ghost"}
            aria-pressed={type === value}
            onClick={() => {
              setType(value);
              setPage(0);
            }}
          >
            {t(value === "ADD_ON" ? "planComparison.addon" : "planComparison.pack")}
          </Button>
        ))}
      </fieldset>
      {query.isLoading ? (
        <LoadingState rows={2} />
      ) : query.isError || !query.data ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <>
          {query.data.content.length ? (
            <ExtensionList items={query.data.content} type={type} catalog={catalog} />
          ) : (
            <p className="text-sm text-muted-foreground">{t("planComparison.none")}</p>
          )}
          {query.data.totalPages > 1 && (
            <PaginationBar
              page={page}
              totalPages={query.data.totalPages}
              totalElements={query.data.totalElements}
              onPageChange={setPage}
            />
          )}
        </>
      )}
    </div>
  );
}
