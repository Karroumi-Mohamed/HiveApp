import { ApiError } from "@/api/http";

export function subscriptionChangeFailureMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return "Le changement n’a pas pu être vérifié. Réessayez.";
  if (error.status === 409) return `${error.message} Les tarifs ont été rechargés ; prévisualisez à nouveau.`;
  return error.message;
}
