import type { CommercialAnalyticsProductType, CommercialAttentionType, SubscriptionStatus } from "@/api/contracts";

export const subscriptionStatusLabel: Record<SubscriptionStatus, string> = {
  TRIALING: "En essai",
  ACTIVE: "Actifs",
  PAST_DUE: "Impayés",
  SUSPENDED: "Suspendus",
  CANCELLED: "Annulés",
  EXPIRED: "Expirés",
};

export const attentionTypeLabel: Record<CommercialAttentionType, string> = {
  PAST_DUE: "Impayé",
  SUSPENDED: "Suspendu",
  OPEN_INVOICE: "Facture ouverte",
  CHANGE_NEEDS_ATTENTION: "Changement bloqué",
};

export const productTypeLabel: Record<CommercialAnalyticsProductType, string> = {
  PLAN: "Forfait",
  ADD_ON: "Add-on",
  QUOTA_PACKAGE: "Pack de capacité",
};

export const lifecycleActionLabel: Record<string, string> = {
  CANCEL_AT_PERIOD_END: "Annulation planifiée",
  KEEP_RENEWING: "Renouvellement conservé",
  CANCEL_IMMEDIATELY: "Annulation immédiate",
  SUSPEND: "Suspension",
  RESTORE: "Restauration",
  ENTER_PAST_DUE: "Passage en impayé",
  EXTEND_GRACE: "Délai prolongé",
  RECOVER: "Paiement récupéré",
  EXPIRE: "Expiration",
};

export const offerOutcomeLabel = {
  RESERVED: "Réservées",
  APPLIED: "Appliquées",
  CANCELLED: "Annulées",
  FAILED: "Échouées",
} as const;

export const billingCycleShortLabel = {
  MONTHLY: "mensuel",
  YEARLY: "annuel",
  FOREVER: "permanent",
} as const;

export function bucketLabel(value: string, interval: "DAY" | "WEEK" | "MONTH", timezone: string) {
  return new Intl.DateTimeFormat("fr-MA", {
    timeZone: timezone,
    ...(interval === "MONTH"
      ? { month: "short", year: "numeric" }
      : { day: "2-digit", month: "short", year: interval === "WEEK" ? "numeric" : undefined }),
  }).format(new Date(value));
}

export function dateTime(value: string | null) {
  return value
    ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
    : "—";
}
