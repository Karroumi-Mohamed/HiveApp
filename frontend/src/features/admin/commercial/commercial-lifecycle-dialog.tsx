import { ArchiveIcon, CheckCircleIcon, PauseIcon, PlayIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  AddOn,
  AddOnActivationPreview,
  CommercialLifecycleAction,
  Plan,
  PlanActivationPreview,
  QuotaPackage,
  QuotaPackageActivationPreview,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { formatExactMoney } from "@/lib/exact-decimal";

type Product = Plan | AddOn | QuotaPackage;
type Preview = PlanActivationPreview | AddOnActivationPreview | QuotaPackageActivationPreview;
type Kind = "plan" | "add-on" | "quota";

const actionCopy: Record<CommercialLifecycleAction, { label: string; title: string }> = {
  ACTIVATE: { label: "Activer", title: "Mettre cette version en vente ?" },
  DEACTIVATE: { label: "Suspendre", title: "Suspendre les nouvelles ventes ?" },
  ARCHIVE: { label: "Archiver", title: "Archiver définitivement cette version ?" },
};

const blockerCopy: Record<string, string> = {
  WRONG_LIFECYCLE_STATE: "Cette action n’est pas possible depuis le statut actuel.",
  ARCHIVED_TERMINAL: "Une version archivée ne peut pas être réactivée.",
  NO_INCLUDED_FEATURES: "Ajoutez au moins une fonctionnalité incluse.",
  NO_FEATURES: "Ajoutez au moins une fonctionnalité.",
  FEATURE_CONFIGURATION_INVALID: "La composition contient une configuration invalide.",
  INCOMPLETE_QUOTA_CONFIGURATION: "Toutes les limites requises doivent être définies.",
  NO_APPLICABLE_PRICE: "Ajoutez et activez un tarif applicable.",
  TARGET_PLAN_MISSING: "Un forfait ciblé n’existe plus.",
  TARGET_PLAN_INCOMPATIBLE: "Un forfait ciblé n’est pas compatible.",
  NO_COMPATIBLE_ACTIVE_PLAN: "Aucun forfait actif compatible n’est disponible.",
  DEPENDENT_ADD_ON_REQUIRES_MIGRATION: "Un add-on dépendant doit être migré.",
  TARGETED_QUOTA_PACKAGE_REQUIRES_MIGRATION: "Un pack de capacité ciblé doit être migré.",
};

function reviewedPrices(preview: Preview) {
  return preview.reviewedPrices ?? [];
}

function previewProductId(preview: Preview) {
  if ("planId" in preview) return preview.planId;
  if ("addOnId" in preview) return preview.addOnId;
  return preview.quotaPackageId;
}

function activationEvidenceIsCurrent(product: Product, preview: Preview | null, now: number) {
  if (!preview) return false;
  const expiresAt = Date.parse(preview.expiresAt);
  return Boolean(
    preview.previewToken &&
      previewProductId(preview) === product.id &&
      preview.expectedVersion === product.version &&
      Number.isFinite(expiresAt) &&
      expiresAt > now,
  );
}

export function CommercialLifecycleDialog({
  action,
  kind,
  product,
  onChanged,
  trigger,
}: {
  action: CommercialLifecycleAction;
  kind: Kind;
  product: Product;
  onChanged: () => void;
  trigger: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [evidenceClock, setEvidenceClock] = useState(() => Date.now());
  const copy = actionCopy[action];

  const previewMutation = useMutation<Preview, Error, void>({
    mutationFn: () => {
      if (kind === "plan") return adminApi.previewPlanActivation(product.id);
      if (kind === "add-on") return adminApi.previewAddOnActivation(product.id);
      return adminApi.previewQuotaPackageActivation(product.id);
    },
    onMutate: () => setPreview(null),
    onSuccess: (result) => {
      setEvidenceClock(Date.now());
      setPreview(result);
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Prévisualisation impossible"),
  });
  const refreshPreview = () => {
    setEvidenceClock(Date.now());
    previewMutation.mutate();
  };
  const evidenceCurrent = activationEvidenceIsCurrent(product, preview, evidenceClock);
  useEffect(() => {
    if (!open || action !== "ACTIVATE" || !preview?.expiresAt) return;
    const expiresAt = Date.parse(preview.expiresAt);
    if (!Number.isFinite(expiresAt)) return;
    const timer = window.setTimeout(() => setEvidenceClock(Date.now()), Math.max(0, expiresAt - Date.now() + 1));
    return () => window.clearTimeout(timer);
  }, [action, open, preview?.expiresAt]);
  const transition = useMutation<unknown, Error, void>({
    mutationFn: () => {
      if (action === "ACTIVATE" && !activationEvidenceIsCurrent(product, preview, Date.now())) {
        throw new Error("La vérification d’activation n’est plus actuelle.");
      }
      const input = {
        action,
        expectedVersion: action === "ACTIVATE" && preview ? preview.expectedVersion : product.version,
        reason: reason.trim(),
        activationPreviewToken: action === "ACTIVATE" ? preview?.previewToken : null,
      };
      if (kind === "plan") return adminApi.changePlanLifecycle(product.id, input);
      if (kind === "add-on") return adminApi.changeAddOnLifecycle(product.id, input);
      return adminApi.changeQuotaPackageLifecycle(product.id, input);
    },
    onSuccess: () => {
      setOpen(false);
      setPreview(null);
      setReason("");
      onChanged();
      toast.success("Cycle de vie mis à jour");
    },
    onError: (error) => {
      if (
        error instanceof ApiError &&
        ["STALE_ACTIVATION_PREVIEW", "STALE_IMPACT_PREVIEW", "STALE_RESOURCE_VERSION"].includes(error.code)
      ) {
        setPreview(null);
        setEvidenceClock(Date.now());
        toast.warning("La version a changé. Recalculez l’activation.");
        return;
      }
      toast.error(error instanceof Error ? error.message : "Action impossible");
    },
  });

  const blockers = action === "ACTIVATE" ? (preview?.blockers ?? []) : [];
  const canConfirm =
    reason.trim().length > 0 &&
    (action !== "ACTIVATE" ||
      (evidenceCurrent && !previewMutation.isPending && !previewMutation.isError && Boolean(preview?.activatable)));
  const stats = useMemo(() => {
    if (!preview) return [];
    if ("includedFeatureCount" in preview)
      return [
        ["Fonctionnalités incluses", preview.includedFeatureCount],
        ["Fonctionnalités en add-on", preview.optionalAddOnFeatureCount],
      ];
    if ("compatiblePlanCount" in preview)
      return [
        ["Forfaits compatibles", preview.compatiblePlanCount],
        ["Fonctionnalités", preview.featureCount],
      ];
    return [
      ["Tarifs vérifiés", reviewedPrices(preview).length],
      ["Versions remplacées", preview.packagesToDeactivate.length],
    ];
  }, [preview]);

  return (
    <Dialog
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) {
          setPreview(null);
          setReason("");
        } else if (action === "ACTIVATE") refreshPreview();
      }}
      open={open}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{copy.title}</DialogTitle>
          <DialogDescription>
            {action === "ACTIVATE"
              ? "La plateforme vérifie la composition, la compatibilité et les tarifs avant l’activation."
              : action === "DEACTIVATE"
                ? "Les abonnements existants restent conservés, mais cette version ne sera plus proposée."
                : "L’archivage est définitif. Les abonnements existants conservent leurs conditions."}
          </DialogDescription>
        </DialogHeader>
        {action === "ACTIVATE" ? (
          <div aria-live="polite" className="space-y-4 border-y py-4">
            {previewMutation.isPending ? (
              <p className="text-sm text-muted-foreground">Vérification en cours…</p>
            ) : previewMutation.isError ? (
              <Button onClick={refreshPreview} variant="outline">
                Réessayer la vérification
              </Button>
            ) : preview && !evidenceCurrent ? (
              <div className="space-y-3 text-sm" role="status">
                <p>La vérification a expiré ou ne correspond plus à cette version.</p>
                <Button onClick={refreshPreview} variant="outline">
                  Recalculer l’activation
                </Button>
              </div>
            ) : preview ? (
              <>
                <div className="flex items-start gap-3 text-sm">
                  {preview.activatable ? (
                    <CheckCircleIcon className="mt-0.5 size-5 text-success" />
                  ) : (
                    <WarningCircleIcon className="mt-0.5 size-5 text-destructive" />
                  )}
                  <div>
                    <p className="font-medium">{preview.activatable ? "Prête à être activée" : "Activation bloquée"}</p>
                    {"expiresAt" in preview ? (
                      <p className="text-muted-foreground">
                        Vérification valable jusqu’au{" "}
                        {new Intl.DateTimeFormat("fr-MA", { timeStyle: "short" }).format(new Date(preview.expiresAt))}.
                      </p>
                    ) : null}
                  </div>
                </div>
                {stats.length ? (
                  <dl className="grid gap-3 sm:grid-cols-2">
                    {stats.map(([label, value]) => (
                      <div className="flex justify-between gap-3 text-sm" key={label}>
                        <dt className="text-muted-foreground">{label}</dt>
                        <dd className="font-medium tabular-nums">{value}</dd>
                      </div>
                    ))}
                  </dl>
                ) : null}
                {reviewedPrices(preview).length ? (
                  <div className="space-y-1 text-sm">
                    <p className="font-medium">Tarifs vérifiés</p>
                    {reviewedPrices(preview).map((item) => (
                      <p className="text-muted-foreground" key={item.id}>
                        {formatExactMoney(item.amount, item.currencyCode)} ·{" "}
                        {item.billingCycle === "MONTHLY"
                          ? "mensuel"
                          : item.billingCycle === "YEARLY"
                            ? "annuel"
                            : "permanent"}
                      </p>
                    ))}
                  </div>
                ) : null}
                {blockers.length ? (
                  <ul className="space-y-2 text-sm text-destructive">
                    {blockers.map((blocker) => (
                      <li className="flex gap-2" key={blocker}>
                        <WarningCircleIcon className="mt-0.5 size-4 shrink-0" />
                        {blockerCopy[blocker] ?? "Une condition d’activation n’est pas satisfaite."}
                      </li>
                    ))}
                  </ul>
                ) : null}
              </>
            ) : (
              <Button onClick={refreshPreview} variant="outline">
                Recalculer l’activation
              </Button>
            )}
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor={`lifecycle-reason-${product.id}-${action}`}>Motif</Label>
          <Textarea
            id={`lifecycle-reason-${product.id}-${action}`}
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Décision et contexte opérationnel"
            value={reason}
          />
        </div>
        <div className="flex justify-end gap-2">
          <Button onClick={() => setOpen(false)} variant="ghost">
            Annuler
          </Button>
          <Button
            disabled={!canConfirm || transition.isPending}
            onClick={() => transition.mutate()}
            variant={action === "ARCHIVE" ? "destructive" : "default"}
          >
            {action === "ACTIVATE" ? <PlayIcon /> : action === "DEACTIVATE" ? <PauseIcon /> : <ArchiveIcon />}
            {transition.isPending ? "Application…" : copy.label}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
