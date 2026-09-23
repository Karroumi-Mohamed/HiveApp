import { useTranslation } from "react-i18next";
import type { PlanVersionPrice } from "@/api/contracts";
import { formatExactMoney } from "@/lib/exact-decimal";

export function ComparisonPriceList({ prices }: { prices: PlanVersionPrice[] }) {
  const { t, i18n } = useTranslation();
  const date = (value: string) => new Date(value).toLocaleDateString(i18n.language, { dateStyle: "medium" });
  return (
    <ul className="space-y-3">
      {prices.map((price) => (
        <li key={price.id} className="space-y-1">
          <p className="font-semibold tabular-nums" dir="ltr">
            {formatExactMoney(price.amount, price.currencyCode)}
          </p>
          <p className="text-xs text-muted-foreground">{t(`planComparison.${price.billingCycle}`)}</p>
          {new Date(price.effectiveFrom).getTime() > 0 && (
            <p className="text-xs text-muted-foreground">
              {t("planComparison.dateFrom", { date: date(price.effectiveFrom) })}
            </p>
          )}
          {price.effectiveUntil && (
            <p className="text-xs text-muted-foreground">
              {t("planComparison.dateUntil", { date: date(price.effectiveUntil) })}
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}
