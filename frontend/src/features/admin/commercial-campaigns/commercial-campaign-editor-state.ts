import type { CampaignDraftErrors } from "./commercial-campaign-rules";

export type CampaignEditorStep = "definition" | "audience" | "calendar" | "review";

export const campaignEditorSteps = [
  { value: "definition", label: "Définition" },
  { value: "audience", label: "Audience" },
  { value: "calendar", label: "Calendrier" },
  { value: "review", label: "Révision" },
] as const;

export function adjacentCampaignEditorSteps(step: CampaignEditorStep) {
  const index = campaignEditorSteps.findIndex((candidate) => candidate.value === step);
  return {
    previous: index > 0 ? campaignEditorSteps[index - 1]?.value : undefined,
    next: index >= 0 && index < campaignEditorSteps.length - 1 ? campaignEditorSteps[index + 1]?.value : undefined,
  };
}

export function shouldBlockCampaignEditorNavigation(
  dirty: boolean,
  completed: boolean,
  currentPathname: string,
  nextPathname: string,
) {
  return dirty && !completed && currentPathname !== nextPathname;
}

export function campaignEditorErrorLocation(errors: CampaignDraftErrors): {
  step: CampaignEditorStep;
  fieldId: string;
} | null {
  if (errors.name) return { step: "definition", fieldId: "campaign-name" };
  if (errors.reason) return { step: "definition", fieldId: "campaign-reason" };
  if (errors.audience) return { step: "audience", fieldId: "campaign-audience-PUBLIC" };
  if (errors.window) return { step: "calendar", fieldId: "campaign-start" };
  return null;
}
