import type { ProductPrice } from "@/api/contracts";
import type { NoticeDelivery, RepricingPreview, RepricingState } from "@/api/repricing-api";

export const repricingStates: Record<RepricingState, string> = {
  READY: "Prêt à confirmer",
  PENDING: "Planifié",
  AWAITING_PAYMENT: "Renouvellement en cours",
  APPLIED: "Appliqué",
  CONFLICT: "À revoir",
  CANCELLED: "Annulé",
};
export const noticeDeliveries: Record<NoticeDelivery, string> = {
  NOT_REQUESTED: "Non demandé",
  PENDING: "En attente",
  SENDING: "Envoi en cours",
  SENT: "Envoyé",
  SUPPRESSED: "Non envoyé (adresse ou transport indisponible)",
  FAILED: "Échec d’envoi",
  CANCELLED: "Annulé",
};
const blockers: Record<string, string> = {
  NO_CURRENT_SUBSCRIPTION: "Aucun abonnement en cours",
  ACCOUNT_INACTIVE: "Compte inactif",
  NOT_RENEWING: "Abonnement non renouvelable",
  SOURCE_TARIFF_CHANGED: "L’ancien tarif n’est plus utilisé",
  TARGET_TARIFF_UNAVAILABLE: "Nouveau tarif indisponible à cette date",
  PROTECTED_TERMS: "Accord particulier ou remise protégée",
  PENDING_CHANGE: "Un autre changement est en cours",
  PENDING_REPRICING: "Un changement de tarif est déjà planifié",
  SUBSCRIPTION_CHANGED: "Conditions de l’abonnement modifiées",
  RENEWAL_DATE_CHANGED: "Date de renouvellement modifiée",
  TARGET_TARIFF_CHANGED: "Nouveau tarif modifié",
  EXECUTION_FAILED: "Échec technique — réessai possible sans modifier les conditions",
  ACTIVATION_CONFLICT: "Renouvellement bloqué — consulter l’abonnement",
  PAYMENT_FAILED: "Paiement échoué — reprendre depuis la facturation",
};
export const repricingBlocker = (value: string | null) => (value ? (blockers[value] ?? "Conditions à revoir") : null);
export const repricingDate = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";
export const repricingCycle = (value: string) => (value === "YEARLY" ? "an" : "mois");
export function compatibleTariff(source: ProductPrice, target: ProductPrice) {
  return (
    source.id !== target.id &&
    source.productType === target.productType &&
    source.productId === target.productId &&
    source.currencyCode === target.currencyCode &&
    source.billingCycle === target.billingCycle &&
    target.status === "ACTIVE"
  );
}
export function currentRepricingPreview(
  preview: RepricingPreview | null,
  reviewedKey: string,
  currentKey: string,
  now = Date.now(),
) {
  return Boolean(
    preview && reviewedKey === currentKey && Date.parse(preview.expiresAt) > now && preview.summary.readyCount > 0,
  );
}
