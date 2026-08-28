import type {
  CommercialCampaignAction,
  CommercialCampaignAudienceMode,
  CommercialCampaignBlocker,
  CommercialCampaignDetail,
  CommercialCampaignSchedulePreview,
  CommercialCampaignSegmentChoiceState,
  CommercialCampaignSource,
  CommercialCampaignStatus,
  CommercialCampaignWriteInput,
} from "@/api/contracts";
import { ApiError } from "@/api/http";

export const campaignStatus: Record<
  CommercialCampaignStatus,
  { label: string; tone: "neutral" | "info" | "success" | "warning" }
> = {
  DRAFT: { label: "Brouillon", tone: "info" },
  SCHEDULED: { label: "Planifiée", tone: "info" },
  ACTIVE: { label: "Active", tone: "success" },
  PAUSED: { label: "En pause", tone: "warning" },
  ENDED: { label: "Terminée", tone: "neutral" },
  ARCHIVED: { label: "Archivée", tone: "neutral" },
};

export const campaignAudienceMode: Record<CommercialCampaignAudienceMode, string> = {
  PUBLIC: "Publique",
  EXPLICIT_ACCOUNTS: "Comptes sélectionnés",
  SEGMENT: "Segment figé",
};

export const campaignSource: Record<CommercialCampaignSource, string> = {
  MARKETING: "Marketing",
  SALES: "Ventes",
  RETENTION: "Fidélisation",
  SUPPORT: "Support",
  MANUAL: "Manuelle",
};

export const campaignBlocker: Record<CommercialCampaignBlocker, string> = {
  NOT_DRAFT: "Cette opération exige un brouillon.",
  NOT_SCHEDULED: "Cette opération exige une campagne planifiée.",
  NOT_ACTIVE: "Cette opération exige une campagne active.",
  NOT_PAUSED: "Cette opération exige une campagne en pause.",
  NOT_ENDED: "Cette opération exige une campagne terminée.",
  ALREADY_ARCHIVED: "Cette campagne est déjà archivée.",
  INVALID_WINDOW: "La période de campagne est invalide.",
  WINDOW_ENDED: "La période de campagne est déjà terminée.",
  EMPTY_AUDIENCE: "L’audience ne contient aucun compte.",
  AUDIENCE_TOO_LARGE: "L’audience dépasse la limite de sécurité.",
  SEGMENT_NOT_ACTIVE: "Le segment sélectionné n’est plus actif.",
  SEGMENT_ACTIVATION_STALE: "L’activation du segment a été remplacée.",
  NOT_LATEST_REVISION: "Une révision plus récente existe déjà.",
  DRAFT_SUCCESSOR_EXISTS: "Un brouillon successeur existe déjà.",
  LIVE_LINEAGE_REVISION_EXISTS: "Une autre révision de cette campagne est déjà en cours.",
  HAS_SCHEDULE_HISTORY: "Cette campagne conserve un historique de planification.",
  HAS_DERIVED_CAMPAIGNS: "Une autre campagne a été créée depuis cette campagne.",
};

export const campaignActionPermission: Record<CommercialCampaignAction, string> = {
  UPDATE: "platform.campaigns.update",
  DUPLICATE: "platform.campaigns.duplicate",
  REVISE: "platform.campaigns.revise",
  COMPARE: "platform.campaigns.compare",
  HISTORY: "platform.campaigns.history",
  PREVIEW_SCHEDULE: "platform.campaigns.preview_schedule",
  SCHEDULE: "platform.campaigns.schedule",
  PAUSE: "platform.campaigns.pause",
  RESUME: "platform.campaigns.resume",
  END: "platform.campaigns.end",
  ARCHIVE: "platform.campaigns.archive",
  DELETE_DRAFT: "platform.campaigns.delete_draft",
  OWNER: "platform.campaigns.owner",
  REASSIGN_OWNER: "platform.campaigns.reassign_owner",
  REVISIONS: "platform.campaigns.revisions",
  READ_AUDIENCE: "platform.campaigns.read_audience",
  READ_AUDIENCE_IDENTITIES: "platform.campaigns.read_audience_identities",
};

export const campaignActionLabel: Record<CommercialCampaignAction, string> = {
  UPDATE: "Modifier",
  DUPLICATE: "Dupliquer",
  REVISE: "Créer une révision",
  COMPARE: "Comparer",
  HISTORY: "Lire l’historique",
  PREVIEW_SCHEDULE: "Vérifier la planification",
  SCHEDULE: "Planifier",
  PAUSE: "Mettre en pause",
  RESUME: "Reprendre",
  END: "Terminer",
  ARCHIVE: "Archiver",
  DELETE_DRAFT: "Supprimer le brouillon",
  OWNER: "Lire le responsable",
  REASSIGN_OWNER: "Réassigner le responsable",
  REVISIONS: "Lire les révisions",
  READ_AUDIENCE: "Lire la preuve d’audience",
  READ_AUDIENCE_IDENTITIES: "Révéler les identités de l’audience",
};

export type CommercialCampaignDraft = {
  name: string;
  description: string;
  source: CommercialCampaignSource;
  reason: string;
  startsAt: string;
  endsAt: string;
  audienceMode: CommercialCampaignAudienceMode;
  explicitAccountIds: string[];
  segmentId: string;
  segmentActivationId: string;
};

export type CampaignDraftErrors = Partial<Record<"name" | "reason" | "window" | "audience", string>>;

export function toLocalDateTime(value: string | Date) {
  const date = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return "";
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function futureRounded(now: number, hours: number) {
  const date = new Date(now + hours * 60 * 60 * 1000);
  date.setMinutes(0, 0, 0);
  return toLocalDateTime(date);
}

export function emptyCommercialCampaignDraft(now = Date.now()): CommercialCampaignDraft {
  return {
    name: "",
    description: "",
    source: "MARKETING",
    reason: "",
    startsAt: futureRounded(now, 1),
    endsAt: futureRounded(now, 25),
    audienceMode: "PUBLIC",
    explicitAccountIds: [],
    segmentId: "",
    segmentActivationId: "",
  };
}

export function draftFromCommercialCampaign(campaign: CommercialCampaignDetail): CommercialCampaignDraft {
  return {
    name: campaign.summary.name,
    description: campaign.description ?? "",
    source: campaign.summary.source,
    reason: campaign.reason,
    startsAt: toLocalDateTime(campaign.summary.startsAt),
    endsAt: toLocalDateTime(campaign.summary.endsAt),
    audienceMode: campaign.audience.mode,
    explicitAccountIds: [...campaign.audience.explicitAccountIds],
    segmentId: campaign.audience.segmentId ?? "",
    segmentActivationId: campaign.audience.segmentActivationId ?? "",
  };
}

export function validateCommercialCampaignDraft(
  draft: CommercialCampaignDraft,
  segmentState?: CommercialCampaignSegmentChoiceState,
): CampaignDraftErrors {
  const errors: CampaignDraftErrors = {};
  if (!draft.name.trim()) errors.name = "Le nom est obligatoire.";
  if (!draft.reason.trim()) errors.reason = "Le motif est obligatoire.";
  const startsAt = Date.parse(draft.startsAt);
  const endsAt = Date.parse(draft.endsAt);
  if (!Number.isFinite(startsAt) || !Number.isFinite(endsAt) || endsAt <= startsAt) {
    errors.window = "La fin doit être postérieure au début.";
  }
  if (draft.audienceMode === "EXPLICIT_ACCOUNTS" && draft.explicitAccountIds.length === 0) {
    errors.audience = "Sélectionnez au moins un compte.";
  }
  if (draft.audienceMode === "SEGMENT") {
    if (!draft.segmentId || !draft.segmentActivationId) errors.audience = "Sélectionnez un segment actif.";
    else if (!segmentState)
      errors.audience = "L’activation exacte du segment doit être vérifiée avant l’enregistrement.";
    else if (segmentState !== "AVAILABLE") {
      errors.audience = campaignSegmentStateLabel[segmentState];
    }
  }
  return errors;
}

export function hasCampaignDraftErrors(errors: CampaignDraftErrors) {
  return Object.keys(errors).length > 0;
}

export function toCommercialCampaignWriteInput(draft: CommercialCampaignDraft): CommercialCampaignWriteInput {
  return {
    name: draft.name.trim(),
    description: draft.description.trim() || null,
    source: draft.source,
    reason: draft.reason.trim(),
    startsAt: new Date(draft.startsAt).toISOString(),
    endsAt: new Date(draft.endsAt).toISOString(),
    audience: {
      mode: draft.audienceMode,
      explicitAccountIds: draft.audienceMode === "EXPLICIT_ACCOUNTS" ? [...new Set(draft.explicitAccountIds)] : [],
      segmentId: draft.audienceMode === "SEGMENT" ? draft.segmentId : null,
      segmentActivationId: draft.audienceMode === "SEGMENT" ? draft.segmentActivationId : null,
    },
  };
}

export const campaignSegmentStateLabel: Record<CommercialCampaignSegmentChoiceState, string> = {
  AVAILABLE: "Disponible",
  SEGMENT_INACTIVE: "Ce segment n’est plus actif. Sélectionnez une audience disponible.",
  ACTIVATION_SUPERSEDED: "Cette activation a été remplacée. Sélectionnez la nouvelle activation explicitement.",
  ACTIVATION_UNAVAILABLE: "Cette activation n’est plus disponible.",
};

export function reviewedCampaignScheduleReady(
  campaign: CommercialCampaignDetail,
  preview: CommercialCampaignSchedulePreview | null | undefined,
  now = Date.now(),
) {
  return Boolean(
    preview &&
      preview.campaignId === campaign.summary.id &&
      preview.campaignVersion === campaign.summary.version &&
      preview.previewToken &&
      preview.schedulable &&
      preview.blockers.length === 0 &&
      Date.parse(preview.expiresAt) > now,
  );
}

export function campaignMutationMessage(error: unknown) {
  if (!(error instanceof ApiError)) return "L’opération a échoué.";
  if (error.code === "STALE_RESOURCE_VERSION") return "La campagne a changé. Rechargez-la avant de continuer.";
  if (error.code === "STALE_SCHEDULE_PREVIEW") return "La vérification a expiré ou ne correspond plus à la campagne.";
  if (error.code === "DRAFT_SUCCESSOR_EXISTS") return "Un brouillon successeur existe déjà.";
  if (error.status === 403) return "Vous n’avez pas l’autorisation nécessaire pour cette opération.";
  if (error.status === 404) return "La campagne demandée n’existe plus.";
  if (error.status === 409) return "L’état de la campagne ne permet plus cette opération. Rechargez la fiche.";
  if (error.status === 400) return "La demande est invalide. Vérifiez les champs et réessayez.";
  return "L’opération a échoué. Réessayez ou consultez les journaux opérationnels.";
}

const campaignHistoryLabels: Record<string, string> = {
  CREATE: "Brouillon créé",
  UPDATE: "Brouillon modifié",
  DUPLICATE: "Copie indépendante créée",
  REVISE: "Révision créée",
  SCHEDULE: "Campagne planifiée",
  PAUSE: "Campagne mise en pause",
  RESUME: "Campagne reprise",
  END: "Campagne terminée",
  ARCHIVE: "Campagne archivée",
  DELETE_DRAFT: "Brouillon supprimé",
  REASSIGN_OWNER: "Responsable réassigné",
  PROCESS_DUE_START: "Campagne démarrée automatiquement",
  PROCESS_DUE_END: "Campagne terminée automatiquement",
};

export function campaignHistoryAction(action: string) {
  const key = (action.split(".").at(-1) ?? action).replaceAll("-", "_").toUpperCase();
  return (
    campaignHistoryLabels[key] ??
    key
      .toLowerCase()
      .replaceAll("_", " ")
      .replace(/^./, (value) => value.toUpperCase())
  );
}

export function validCampaignId(value: string | undefined): value is string {
  return Boolean(value && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value));
}
