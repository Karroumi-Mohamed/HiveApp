import { toast } from "sonner";
import type { EmailDeliverySummary } from "@/api/contracts";

export type EmailDeliveryFeedback = {
  title: string;
  description: string;
  tone: "success" | "warning" | "error" | "info";
};

/** The UI reports the tracked transport result, never merely that a token was created. */
export function emailDeliveryFeedback(delivery: EmailDeliverySummary | null): EmailDeliveryFeedback {
  switch (delivery?.status) {
    case "SENT":
      return {
        title: "Email envoyé",
        description: "Le serveur de messagerie a accepté l’envoi.",
        tone: "success",
      };
    case "SUPPRESSED":
      return {
        title: "Email non envoyé",
        description: "La messagerie est désactivée dans cet environnement.",
        tone: "warning",
      };
    case "FAILED":
      return {
        title: "Échec de l’envoi",
        description: "Vérifiez la configuration email puis réessayez.",
        tone: "error",
      };
    default:
      return {
        title: "Envoi en attente",
        description: "La demande est enregistrée, mais la livraison n’est pas encore confirmée.",
        tone: "info",
      };
  }
}

/** Announce the transport result without claiming that merely creating a token sent an email. */
export function announceEmailDelivery(delivery: EmailDeliverySummary | null) {
  const feedback = emailDeliveryFeedback(delivery);
  toast[feedback.tone](feedback.title, { description: feedback.description });
}
