import { WarningCircleIcon } from "@phosphor-icons/react";
import type {
  CommercialChoiceState,
  CommercialProductBlocker,
  PlanExtensionPolicy,
  ProductSalesVisibility,
} from "@/api/contracts";
import { StatusText } from "@/components/patterns/status-text";

export const lifecycleLabel: Record<string, string> = {
  DRAFT: "Brouillon",
  ACTIVE: "Actif",
  INACTIVE: "Suspendu",
  ARCHIVED: "Archivé",
};

export const blockerLabel: Record<CommercialProductBlocker, string> = {
  NOT_PUBLISHED: "Révision non publiée",
  PAUSED: "Nouvelles ventes suspendues",
  ARCHIVED_TERMINAL: "Révision archivée",
  NO_ACTIVE_PRICE: "Aucun tarif actif",
  NO_PRICE_STARTING_POINT: "Aucun tarif de départ",
  PUBLISHED_PRICE_HISTORY: "Historique tarifaire publié",
  REFERENCED_BY_ADD_ON: "Référencé par un add-on",
  REFERENCED_BY_QUOTA_PACKAGE: "Référencé par un pack de capacité",
  NO_FEATURES: "Aucune fonctionnalité",
  NO_INCLUDED_FEATURES: "Aucune fonctionnalité incluse",
  DRAFT_SUCCESSOR_EXISTS: "Une révision brouillon existe déjà",
  NOT_LATEST_REVISION: "Une révision plus récente existe",
  DEFAULT_PLAN_LOCKED: "Forfait par défaut protégé",
};

export const availabilityLabel: Record<ProductSalesVisibility, string> = {
  PUBLIC: "Catalogue public",
  DIRECT_ONLY: "Attribution directe",
};

export const extensionPolicyLabel: Record<PlanExtensionPolicy, string> = {
  CLOSED: "Extensions fermées",
  ALLOW_LIST: "Liste autorisée",
  OPEN_COMPATIBLE: "Extensions compatibles",
};

export const choiceStateLabel: Record<CommercialChoiceState, string> = {
  SELECTABLE: "Sélectionnable",
  NO_LONGER_ACTIVE: "Conservé — plus disponible",
  MISSING: "Référence absente",
};

const capacityUnit: Record<string, string> = {
  members: "membres",
  persons: "personnes",
  people: "personnes",
  companies: "entreprises",
  documents: "documents",
};

export function capacityUnitLabel(resource: string): string {
  return capacityUnit[resource.toLowerCase()] ?? "unités";
}

export function LifecycleText({ status }: { status: string }) {
  return (
    <StatusText
      tone={
        status === "ACTIVE" ? "success" : status === "DRAFT" ? "info" : status === "ARCHIVED" ? "neutral" : "warning"
      }
    >
      {lifecycleLabel[status] ?? status}
    </StatusText>
  );
}

export function BlockerSummary({ blockers }: { blockers: CommercialProductBlocker[] }) {
  if (!blockers.length) return <StatusText tone="success">Prêt pour la vente</StatusText>;
  const first = blockers[0];
  if (!first) return null;
  return (
    <span className="inline-flex max-w-64 items-start gap-1.5 text-xs text-warning">
      <WarningCircleIcon aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
      <span>
        {blockerLabel[first]}
        {blockers.length > 1 ? ` +${blockers.length - 1}` : ""}
      </span>
    </span>
  );
}
