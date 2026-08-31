import type {
  AuditActorSurface,
  AuditOutcome,
  CredentialTokenPurpose,
  PlatformComponentState,
  PlatformEmailDeliveryStatus,
} from "@/api/contracts";

export function operationDateTime(value: string | null | undefined) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("fr", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function toOptionalInstant(value: string) {
  return value ? new Date(value).toISOString() : undefined;
}

const technicalActionLabels: Record<string, string> = {
  activate: "Activation",
  archive: "Archivage",
  assign: "Attribution",
  assign_role: "Attribution du rôle",
  automatic_resume: "Reprise automatique",
  create: "Création",
  deactivate: "Désactivation",
  delete: "Suppression",
  preview_activation: "Aperçu avant activation",
  process_due: "Traitement des échéances",
  remove: "Retrait",
  remove_role: "Retrait du rôle",
  update: "Modification",
};

export function technicalActionLabel(action: string) {
  const tail = action.split(".").at(-1) ?? action;
  if (technicalActionLabels[tail]) return technicalActionLabels[tail];
  return tail.replaceAll("_", " ").replace(/^./, (letter) => letter.toUpperCase());
}

export function actorSurfaceLabel(surface: AuditActorSurface) {
  return { PLATFORM_ADMIN: "Administration", CLIENT_WORKSPACE: "Espace client", SYSTEM: "Système" }[surface];
}

export function auditOutcomePresentation(outcome: AuditOutcome) {
  return outcome === "SUCCEEDED"
    ? ({ label: "Réussie", tone: "success" } as const)
    : ({ label: "Échouée", tone: "danger" } as const);
}

export function communicationStatusPresentation(status: PlatformEmailDeliveryStatus) {
  return {
    PENDING: { label: "En attente", tone: "warning" },
    SENT: { label: "Envoyé", tone: "success" },
    FAILED: { label: "Échec", tone: "danger" },
    SUPPRESSED: { label: "Non envoyé (dev)", tone: "neutral" },
  }[status] as { label: string; tone: "warning" | "success" | "danger" | "neutral" };
}

export function communicationPurposeLabel(purpose: CredentialTokenPurpose) {
  return {
    ACTIVATION: "Activation",
    PASSWORD_RESET: "Réinitialisation du mot de passe",
    EMAIL_VERIFICATION: "Vérification de l’adresse email",
  }[purpose];
}

export function componentStatePresentation(state: PlatformComponentState) {
  return {
    UP: { label: "Opérationnel", tone: "success" },
    CONFIGURED: { label: "Configuré", tone: "success" },
    SUPPRESSED: { label: "Mode test", tone: "warning" },
    DISABLED: { label: "Désactivé", tone: "neutral" },
    DEGRADED: { label: "Dégradé", tone: "warning" },
    UNAVAILABLE: { label: "Indisponible", tone: "danger" },
  }[state] as { label: string; tone: "warning" | "success" | "danger" | "neutral" };
}

export function observabilityComponentLabel(key: string, fallback: string) {
  return (
    {
      database: "Base de données",
      email: "E-mails d’accès",
      billing_provider: "Encaissement",
    }[key] ?? fallback
  );
}

export function observabilityGuidance(key: string, state: PlatformComponentState, fallback: string | null) {
  const messages: Record<string, Partial<Record<PlatformComponentState, string>>> = {
    database: {
      UNAVAILABLE: "La base de données ne répond pas. Vérifiez l’infrastructure.",
    },
    email: {
      SUPPRESSED: "En développement, les envois sont enregistrés sans qu’aucun e-mail soit expédié.",
      UNAVAILABLE: "Aucun service d’envoi d’e-mails d’accès n’est configuré.",
    },
    billing_provider: {
      DISABLED: "L’encaissement automatique par prestataire est désactivé.",
      UNAVAILABLE: "L’encaissement est activé sans prestataire approuvé.",
    },
  };
  return messages[key]?.[state] ?? fallback ?? "—";
}

export function backlogLabel(key: string, fallback: string) {
  return (
    {
      billing_outbox: "Commandes de paiement",
      provider_events: "Événements du prestataire",
      email_delivery: "E-mails d’accès",
    }[key] ?? fallback
  );
}

export function backlogStatusLabel(status: string) {
  return (
    {
      PENDING: "En attente",
      PROCESSING: "En traitement",
      APPLIED: "Appliqués",
      FAILED: "Échecs",
      RECEIVED: "Reçus",
      UNMATCHED: "Non rapprochés",
      MISMATCHED: "Incohérents",
      SENT: "Envoyés",
      SUPPRESSED: "Non envoyés (dev)",
    }[status] ?? status.replaceAll("_", " ").toLocaleLowerCase("fr")
  );
}

export function logAccessGuidance(configured: boolean) {
  return configured
    ? "Utilisez un identifiant de requête HiveApp pour poursuivre l’investigation chez le fournisseur externe."
    : "Aucun fournisseur externe n’est configuré. HiveApp ne conserve pas les journaux bruts de production.";
}

export function formatEvidence(value: string | null) {
  if (!value) return "Aucune donnée enregistrée.";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
