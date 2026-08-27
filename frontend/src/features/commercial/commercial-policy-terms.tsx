import { CaretDownIcon, WarningCircleIcon } from "@phosphor-icons/react";
import type {
  ClientCommercialPolicyConflict,
  ClientCommercialPolicyDecision,
  ClientCommercialPolicyEvaluation,
  CommercialPolicyConflict,
  CommercialPolicyDecisionSnapshot,
  SubscriptionCommercialPolicyEvaluation,
} from "@/api/contracts";
import { StatusText } from "@/components/patterns/status-text";
import { compareExactDecimals, formatExactMoney } from "@/lib/exact-decimal";
import { cn } from "@/lib/utils";

type PolicyEvaluationView = Omit<ClientCommercialPolicyEvaluation, "decisions" | "conflicts"> & {
  decisions: readonly ClientCommercialPolicyDecision[];
  conflicts: readonly ClientCommercialPolicyConflict[];
};

const outcomeLabel = {
  APPLIED: "Appliqué",
  AVAILABLE: "Disponible",
  REJECTED_LOWER_PRECEDENCE: "Écarté par priorité",
  REJECTED_INCOMPATIBLE: "Non applicable",
  BLOCKED_SELECTION: "Bloquant",
} as const;

const targetLabel = {
  ACCOUNT: "Compte",
  ACCOUNT_SET: "Ensemble de comptes",
  PLAN_REVISION_SUBSCRIBERS: "Abonnés d’une révision",
  SEGMENT: "Segment",
} as const;

export function isPolicyGrantedProduct(decisions: readonly ClientCommercialPolicyDecision[] | undefined) {
  return Boolean(
    decisions?.some(
      (decision) =>
        (decision.effectType === "GRANT_ADD_ON" || decision.effectType === "GRANT_QUOTA_PACKAGE") &&
        (decision.outcome === "AVAILABLE" || decision.outcome === "APPLIED"),
    ),
  );
}

export function isPolicyBlockedProduct(decisions: readonly ClientCommercialPolicyDecision[] | undefined) {
  return Boolean(
    decisions?.some(
      (decision) =>
        decision.effectType === "BLOCK_PRODUCT_SELECTION" &&
        (decision.outcome === "AVAILABLE" || decision.outcome === "BLOCKED_SELECTION"),
    ),
  );
}

export function hasAvailableCommercialPolicyTerms(decisions: readonly ClientCommercialPolicyDecision[] | undefined) {
  return Boolean(decisions?.some((decision) => decision.outcome === "AVAILABLE" || decision.outcome === "APPLIED"));
}

export function commercialPolicyConflictText(conflict: ClientCommercialPolicyConflict) {
  switch (conflict.code) {
    case "POLICY_PRODUCT_BLOCKED":
      return conflict.productCode
        ? `Le produit ${conflict.productCode} n’est pas disponible pour ce compte.`
        : "Un produit de cette sélection n’est pas disponible pour ce compte.";
    case "POLICY_FEATURE_BLOCKED":
      return conflict.featureCode
        ? `La fonctionnalité ${conflict.featureCode} n’est pas disponible pour ce compte.`
        : "Une fonctionnalité de cette sélection n’est pas disponible pour ce compte.";
    case "POLICY_QUOTA_OVERFLOW":
      return "Le bonus de capacité dépasse la limite prise en charge. Modifiez la sélection ou la condition commerciale.";
    default:
      return "Une condition commerciale empêche cette sélection.";
  }
}

export function commercialPolicyDecisionText(decision: ClientCommercialPolicyDecision) {
  const product = decision.productCode ? ` ${decision.productCode}` : "";
  const feature = decision.featureCode ? ` ${decision.featureCode}` : "";
  const quota = decision.quotaResource ? ` / ${decision.quotaResource}` : "";
  switch (decision.effectType) {
    case "FIXED_SUBSCRIPTION_PRICE":
      return decision.outcome === "AVAILABLE" ? "Tarif contractuel disponible" : "Tarif contractuel appliqué";
    case "FIXED_DISCOUNT":
      return decision.outcome === "AVAILABLE" ? "Remise fixe disponible" : "Remise fixe appliquée";
    case "PERCENTAGE_DISCOUNT":
      return decision.outcome === "AVAILABLE" ? "Remise en pourcentage disponible" : "Remise en pourcentage appliquée";
    case "ALLOW_PRODUCT_SELECTION":
      return `Produit${product} rendu disponible pour ce compte`;
    case "BLOCK_PRODUCT_SELECTION":
      return `Produit${product} indisponible pour ce compte`;
    case "ADDITIVE_QUOTA_BONUS":
      return decision.quantityDelta
        ? `Capacité${feature}${quota} augmentée de ${decision.quantityDelta.toLocaleString("fr-MA")}`
        : `Capacité${feature}${quota} supplémentaire`;
    case "GRANT_ADD_ON":
      return `Add-on${product} inclus sans coût récurrent`;
    case "GRANT_QUOTA_PACKAGE":
      return `Pack de capacité${product} inclus sans coût récurrent`;
    case "BLOCK_FEATURE":
      return `Fonctionnalité${feature} indisponible pour ce compte`;
  }
}

export function PolicyGrantedProductText({ className }: { className?: string }) {
  return (
    <StatusText className={cn("text-info", className)} tone="info">
      Inclus par condition commerciale
    </StatusText>
  );
}

function PriceRow({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-5 py-2.5 text-sm">
      <dt className={strong ? "font-medium" : "text-muted-foreground"}>{label}</dt>
      <dd className={cn("text-end tabular-nums", strong && "text-base font-semibold")}>{value}</dd>
    </div>
  );
}

export function CommercialPriceBreakdown({ evaluation }: { evaluation: PolicyEvaluationView }) {
  const fixedPriceApplied = evaluation.decisions.some(
    (decision) =>
      decision.effectType === "FIXED_SUBSCRIPTION_PRICE" &&
      (decision.outcome === "APPLIED" || decision.outcome === "AVAILABLE"),
  );
  const fixedChangesPrice =
    fixedPriceApplied || compareExactDecimals(evaluation.catalogueRecurringPrice, evaluation.fixedRecurringPrice) !== 0;
  const hasDiscount = compareExactDecimals(evaluation.discountAmount, "0") > 0;
  return (
    <dl aria-label="Calcul du prix récurrent" className="divide-y border-y">
      <PriceRow
        label="Sous-total catalogue"
        value={formatExactMoney(evaluation.catalogueRecurringPrice, evaluation.currencyCode)}
      />
      {fixedChangesPrice ? (
        <PriceRow
          label="Tarif fixe retenu"
          value={formatExactMoney(evaluation.fixedRecurringPrice, evaluation.currencyCode)}
        />
      ) : null}
      {hasDiscount ? (
        <PriceRow
          label="Remise non cumulable"
          value={`− ${formatExactMoney(evaluation.discountAmount, evaluation.currencyCode)}`}
        />
      ) : null}
      <PriceRow
        label="Prix récurrent final"
        strong
        value={formatExactMoney(evaluation.finalRecurringPrice, evaluation.currencyCode)}
      />
    </dl>
  );
}

function PolicyAdjustments({ decisions }: { decisions: readonly ClientCommercialPolicyDecision[] }) {
  const visible = decisions.filter((decision) => decision.outcome === "APPLIED" || decision.outcome === "AVAILABLE");
  if (!visible.length) return null;
  return (
    <section>
      <h4 className="text-sm font-semibold">Ajustements appliqués</h4>
      <ul className="mt-2 divide-y border-y text-sm">
        {visible.map((decision, index) => (
          <li
            className="flex items-start justify-between gap-4 py-2.5"
            key={`${decision.effectType}-${decision.productCode ?? decision.featureCode ?? index}`}
          >
            <span>{commercialPolicyDecisionText(decision)}</span>
            {decision.evaluatedAmount && decision.evaluatedCurrencyCode ? (
              <span className="shrink-0 tabular-nums text-muted-foreground">
                {formatExactMoney(decision.evaluatedAmount, decision.evaluatedCurrencyCode)}
              </span>
            ) : null}
          </li>
        ))}
      </ul>
    </section>
  );
}

export function CommercialPolicyConflicts({
  conflicts,
  title = "Conditions commerciales à résoudre",
}: {
  conflicts: readonly ClientCommercialPolicyConflict[];
  title?: string;
}) {
  if (!conflicts.length) return null;
  return (
    <section className="border-s-2 border-destructive ps-4" role="alert">
      <h4 className="flex items-center gap-2 text-sm font-semibold text-destructive">
        <WarningCircleIcon aria-hidden="true" />
        {title}
      </h4>
      <ul className="mt-2 list-disc space-y-1 ps-5 text-sm">
        {conflicts.map((conflict, index) => (
          <li key={`${conflict.code}-${conflict.productCode ?? conflict.featureCode ?? index}`}>
            {commercialPolicyConflictText(conflict)}
          </li>
        ))}
      </ul>
    </section>
  );
}

function EvaluationTime({ value }: { value: string }) {
  return (
    <p className="text-xs text-muted-foreground">
      Conditions évaluées le{" "}
      <time dateTime={value}>
        {new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))}
      </time>
    </p>
  );
}

export function ClientCommercialPolicyTerms({
  evaluation,
  title = "Conditions commerciales",
}: {
  evaluation: ClientCommercialPolicyEvaluation;
  title?: string;
}) {
  return (
    <section aria-label={title} className="space-y-4">
      <h3 className="text-sm font-semibold">{title}</h3>
      <CommercialPriceBreakdown evaluation={evaluation} />
      <PolicyAdjustments decisions={evaluation.decisions} />
      <CommercialPolicyConflicts conflicts={evaluation.conflicts} />
      <EvaluationTime value={evaluation.evaluatedAt} />
    </section>
  );
}

function OptionalValue({ value }: { value: string | number | null }) {
  return <>{value ?? "—"}</>;
}

function ExactAmount({ amount, currency }: { amount: string | null; currency: string | null }) {
  return <>{amount ? `${amount}${currency ? ` ${currency}` : ""}` : "—"}</>;
}

function AdminDecisionDetails({ decision }: { decision: CommercialPolicyDecisionSnapshot }) {
  return (
    <article className="py-3 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <p className="text-sm font-medium">
            {decision.policyName} · révision {decision.policyRevisionNumber}
          </p>
          <p className="mt-0.5 text-xs text-muted-foreground">{commercialPolicyDecisionText(decision)}</p>
        </div>
        <StatusText
          tone={
            decision.outcome.startsWith("REJECTED")
              ? "neutral"
              : decision.outcome === "BLOCKED_SELECTION"
                ? "danger"
                : "info"
          }
        >
          {outcomeLabel[decision.outcome]}
        </StatusText>
      </div>
      <dl className="mt-3 grid gap-x-6 gap-y-2 text-xs sm:grid-cols-2 lg:grid-cols-3">
        <div>
          <dt className="text-muted-foreground">Cible</dt>
          <dd className="mt-0.5 font-medium">{targetLabel[decision.targetKind]}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Priorité</dt>
          <dd className="mt-0.5 font-medium tabular-nums">{decision.priority}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Ordre de l’effet</dt>
          <dd className="mt-0.5 font-medium tabular-nums">{decision.effectOrder}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Type d’effet</dt>
          <dd className="mt-0.5 break-all font-mono">{decision.effectType}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Code de la politique</dt>
          <dd className="mt-0.5 break-all font-mono">
            <OptionalValue value={decision.policyCode} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Politique / activation</dt>
          <dd className="mt-0.5 break-all font-mono">
            {decision.policyId} / {decision.activationId}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Lignée</dt>
          <dd className="mt-0.5 break-all font-mono">{decision.lineageId}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Effet</dt>
          <dd className="mt-0.5 break-all font-mono">{decision.effectId}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Produit</dt>
          <dd className="mt-0.5 break-all font-mono">
            <OptionalValue value={decision.productType} /> · <OptionalValue value={decision.productCode} /> ·{" "}
            <OptionalValue value={decision.productId} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Fonctionnalité / quota</dt>
          <dd className="mt-0.5 break-all font-mono">
            <OptionalValue value={decision.featureCode} /> / <OptionalValue value={decision.quotaResource} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Quantité ajoutée</dt>
          <dd className="mt-0.5 font-mono tabular-nums">
            <OptionalValue value={decision.quantityDelta} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Montant configuré</dt>
          <dd className="mt-0.5 font-mono tabular-nums">
            <ExactAmount amount={decision.configuredAmount} currency={decision.configuredCurrencyCode} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Pourcentage / plafond</dt>
          <dd className="mt-0.5 font-mono tabular-nums">
            <OptionalValue value={decision.percentage} /> /{" "}
            <ExactAmount amount={decision.maximumAmount} currency={decision.maximumCurrencyCode} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Montant évalué</dt>
          <dd className="mt-0.5 font-mono tabular-nums">
            <ExactAmount amount={decision.evaluatedAmount} currency={decision.evaluatedCurrencyCode} />
          </dd>
        </div>
        <div className="sm:col-span-2 lg:col-span-3">
          <dt className="text-muted-foreground">Explication enregistrée</dt>
          <dd className="mt-0.5 whitespace-pre-wrap break-words">
            <OptionalValue value={decision.explanation} />
          </dd>
        </div>
      </dl>
    </article>
  );
}

function AdminConflictDetails({ conflict }: { conflict: CommercialPolicyConflict }) {
  return (
    <article className="py-3 first:pt-0 last:pb-0">
      <p className="text-sm font-medium text-destructive">{commercialPolicyConflictText(conflict)}</p>
      <dl className="mt-2 grid gap-x-6 gap-y-2 text-xs sm:grid-cols-2">
        <div>
          <dt className="text-muted-foreground">Code stable</dt>
          <dd className="mt-0.5 font-mono">{conflict.code}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Produit / fonctionnalité / quota</dt>
          <dd className="mt-0.5 break-all font-mono">
            <OptionalValue value={conflict.productCode} /> / <OptionalValue value={conflict.featureCode} /> /{" "}
            <OptionalValue value={conflict.quotaResource} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Politique</dt>
          <dd className="mt-0.5 break-all font-mono">{conflict.policyId}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Effet</dt>
          <dd className="mt-0.5 break-all font-mono">{conflict.effectId}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-muted-foreground">Message enregistré</dt>
          <dd className="mt-0.5 whitespace-pre-wrap break-words">{conflict.message}</dd>
        </div>
      </dl>
    </article>
  );
}

export function AdminCommercialPolicyTerms({
  evaluation,
  title = "Conditions commerciales",
}: {
  evaluation: SubscriptionCommercialPolicyEvaluation;
  title?: string;
}) {
  return (
    <section aria-label={title} className="space-y-4">
      <h3 className="text-sm font-semibold">{title}</h3>
      <CommercialPriceBreakdown evaluation={evaluation} />
      <PolicyAdjustments decisions={evaluation.decisions} />
      <CommercialPolicyConflicts conflicts={evaluation.conflicts} />
      <EvaluationTime value={evaluation.evaluatedAt} />
      {evaluation.decisions.length || evaluation.conflicts.length ? (
        <details className="group border-y py-3">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
            Traçabilité commerciale complète
            <CaretDownIcon aria-hidden="true" className="shrink-0 transition-transform group-open:rotate-180" />
          </summary>
          <div className="mt-3 space-y-4 border-t pt-3">
            {evaluation.decisions.length ? (
              <section>
                <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Décisions</h4>
                <div className="mt-2 divide-y">
                  {evaluation.decisions.map((decision) => (
                    <AdminDecisionDetails decision={decision} key={decision.effectId} />
                  ))}
                </div>
              </section>
            ) : null}
            {evaluation.conflicts.length ? (
              <section className="border-t pt-4">
                <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Conflits</h4>
                <div className="mt-2 divide-y">
                  {evaluation.conflicts.map((conflict) => (
                    <AdminConflictDetails conflict={conflict} key={`${conflict.code}-${conflict.effectId}`} />
                  ))}
                </div>
              </section>
            ) : null}
          </div>
        </details>
      ) : null}
    </section>
  );
}
