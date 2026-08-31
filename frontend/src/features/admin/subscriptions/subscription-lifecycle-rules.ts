import type { SubscriptionLifecycleAction, SubscriptionLifecyclePreview, SubscriptionStatus } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";

export const subscriptionLifecyclePresentation: Record<
  SubscriptionLifecycleAction,
  {
    label: string;
    dialogTitle: string;
    description: string;
    permission: string;
    destructive: boolean;
  }
> = {
  CANCEL_AT_PERIOD_END: {
    label: "Résilier à l’échéance",
    dialogTitle: "Planifier la résiliation",
    description: "L’accès reste ouvert jusqu’à la fin de la période en cours.",
    permission: adminPermissions.subscriptionsCancelAtPeriodEnd,
    destructive: false,
  },
  KEEP_RENEWING: {
    label: "Conserver le renouvellement",
    dialogTitle: "Annuler la résiliation planifiée",
    description: "L’abonnement continuera à son prochain renouvellement.",
    permission: adminPermissions.subscriptionsKeepRenewing,
    destructive: false,
  },
  CANCEL_IMMEDIATELY: {
    label: "Résilier maintenant",
    dialogTitle: "Résilier immédiatement",
    description: "L’accès et les opérations de changement ouvertes seront arrêtés immédiatement.",
    permission: adminPermissions.subscriptionsCancelImmediately,
    destructive: true,
  },
  SUSPEND: {
    label: "Suspendre l’accès",
    dialogTitle: "Suspendre l’accès au compte",
    description: "Toutes les sessions client seront invalidées. Les données restent conservées.",
    permission: adminPermissions.subscriptionsSuspend,
    destructive: true,
  },
  RESTORE: {
    label: "Rétablir l’accès",
    dialogTitle: "Rétablir l’accès au compte",
    description: "Les membres devront se reconnecter; aucune ancienne session ne sera réactivée.",
    permission: adminPermissions.subscriptionsRestore,
    destructive: false,
  },
  EXTEND_GRACE: {
    label: "Prolonger le délai",
    dialogTitle: "Prolonger le délai de paiement",
    description: "L’accès sera réouvert uniquement jusqu’à la nouvelle échéance de recouvrement.",
    permission: adminPermissions.subscriptionsExtendGrace,
    destructive: false,
  },
};

export const subscriptionLifecycleActionOrder: SubscriptionLifecycleAction[] = [
  "RESTORE",
  "KEEP_RENEWING",
  "CANCEL_AT_PERIOD_END",
  "EXTEND_GRACE",
  "SUSPEND",
  "CANCEL_IMMEDIATELY",
];

export function lifecyclePreviewIsReady(
  preview: SubscriptionLifecyclePreview | null,
  action: SubscriptionLifecycleAction,
  graceEndsAt: string | null,
  now = new Date(),
) {
  if (
    !preview ||
    preview.action !== action ||
    !preview.previewToken.trim() ||
    preview.blockers.length > 0 ||
    new Date(preview.expiresAt).getTime() <= now.getTime()
  ) {
    return false;
  }
  return action !== "EXTEND_GRACE" || preview.nextGraceEndsAt === graceEndsAt;
}

export function lifecycleStatusTransition(before: SubscriptionStatus, after: SubscriptionStatus) {
  return before === after ? null : { before, after };
}
