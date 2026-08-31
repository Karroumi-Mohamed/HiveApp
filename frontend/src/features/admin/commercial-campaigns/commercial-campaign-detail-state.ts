export function boundedCampaignPage(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 && parsed <= 10_000 ? parsed : 0;
}

export function boundedCampaignResponsePage(page: number, totalPages: number) {
  return Math.max(0, Math.min(page, Math.max(totalPages - 1, 0)));
}

export function withCampaignSearchParam(params: URLSearchParams, key: string, value: string | number | null) {
  const next = new URLSearchParams(params);
  if (value === null || value === "" || value === 0) next.delete(key);
  else next.set(key, String(value));
  return next;
}

export const campaignChangedField: Record<string, string> = {
  name: "Nom",
  description: "Description",
  startsAt: "Début",
  endsAt: "Fin",
  source: "Origine",
  audience: "Audience",
  audienceMode: "Type d’audience",
  explicitAccountIds: "Comptes sélectionnés",
  segmentId: "Segment",
  segmentActivationId: "Activation du segment",
};
