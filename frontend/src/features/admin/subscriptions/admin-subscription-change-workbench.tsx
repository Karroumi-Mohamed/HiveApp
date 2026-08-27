import { ArrowRightIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { ClientPlanCatalog, SubscriptionChangeInput, SubscriptionChangePreview } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { capacityUnitLabel } from "@/features/admin/commercial/commercial-presentation";
import {
  subscriptionChangeFailureMessage,
  subscriptionChangePreviewIsCurrent,
  subscriptionChangeSelectionKey,
  subscriptionChangeSelectionMatchesCurrent,
} from "@/features/client/subscription/subscription-change-rules";
import {
  catalogAddOnSelectionState,
  currentCatalogPrice,
  defaultCatalogPrice,
  initialCatalogPlanCode,
  matchingCatalogPrice,
  preserveRetainedSelection,
  pruneCommercialSelection,
  sameStringSet,
  updateCatalogAddOnSelection,
} from "@/features/commercial/catalog-price-rules";
import { adminCommercialKeys, invalidateAdminSubscriptionEntitlement } from "@/features/commercial/commercial-query";
import { subscriptionChangeRecordedMessage } from "@/features/commercial/subscription-presentation";
import {
  RetainedSubscriptionQuantityControl,
  SubscriptionQuantityControl,
} from "@/features/commercial/subscription-quantity-control";
import { formatExactMoney } from "@/lib/exact-decimal";
import {
  adminChangeNeedsFreshReview,
  normalizedOperatorReason,
  operatorReasonError,
} from "./subscription-operation-rules";

type CatalogPlan = ClientPlanCatalog["plans"][number];

const cycleLabel = (cycle: "MONTHLY" | "YEARLY") => (cycle === "MONTHLY" ? "Mensuel" : "Annuel");

function PreviewDialog({
  open,
  onOpenChange,
  preview,
  selection,
  previewSelectionKey,
  previewClock,
  previewing,
  applying,
  reason,
  onReasonChange,
  reasonTouched,
  onReasonTouched,
  canApply,
  currentPlanName,
  targetPlanName,
  featureNames,
  onRefresh,
  onApply,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  preview: SubscriptionChangePreview | null;
  selection: SubscriptionChangeInput | null;
  previewSelectionKey: string | null;
  previewClock: number;
  previewing: boolean;
  applying: boolean;
  reason: string;
  onReasonChange: (value: string) => void;
  reasonTouched: boolean;
  onReasonTouched: () => void;
  canApply: boolean;
  currentPlanName: string;
  targetPlanName: string;
  featureNames: ReadonlyMap<string, string>;
  onRefresh: () => void;
  onApply: () => void;
}) {
  const previewReady = subscriptionChangePreviewIsCurrent(preview, selection, previewSelectionKey, previewClock);
  const reasonProblem = operatorReasonError(reason);
  return (
    <Dialog onOpenChange={onOpenChange} open={open}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Vérifier le changement</DialogTitle>
          <DialogDescription>
            {currentPlanName} → {targetPlanName}
          </DialogDescription>
        </DialogHeader>
        {!preview ? (
          <div className="py-8">
            {previewing ? (
              <LoadingState rows={3} />
            ) : (
              <div className="space-y-3 text-sm">
                <p className="text-muted-foreground">La prévisualisation doit être recalculée.</p>
                <Button onClick={onRefresh} size="sm" variant="outline">
                  Recalculer
                </Button>
              </div>
            )}
          </div>
        ) : (
          <div className="space-y-6">
            <dl className="grid gap-4 border-y py-4 sm:grid-cols-3">
              <div>
                <dt className="text-xs text-muted-foreground">Prix actuel</dt>
                <dd className="mt-1 font-semibold">{formatExactMoney(preview.currentPrice, preview.currencyCode)}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Nouveau prix</dt>
                <dd className="mt-1 font-semibold">{formatExactMoney(preview.previewPrice, preview.currencyCode)}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Application</dt>
                <dd className="mt-1 font-semibold">
                  {selection?.timing === "AT_RENEWAL" ? "Au renouvellement" : "Maintenant"}
                </dd>
              </div>
            </dl>

            {preview.conflicts.length ? (
              <section className="border-s-2 border-destructive ps-4">
                <h3 className="flex items-center gap-2 text-sm font-semibold text-destructive">
                  <WarningCircleIcon aria-hidden="true" />
                  Conflits à résoudre
                </h3>
                <ul className="mt-2 list-disc space-y-1 ps-5 text-sm">
                  {preview.conflicts.map((conflict) => (
                    <li key={`${conflict.code}-${conflict.featureCode}-${conflict.resource}`}>{conflict.message}</li>
                  ))}
                </ul>
              </section>
            ) : null}

            <section>
              <h3 className="text-sm font-semibold">Capacités après changement</h3>
              <div className="mt-2 divide-y border-y">
                {preview.effectiveQuotaLimits.length ? (
                  preview.effectiveQuotaLimits.map((quota) => (
                    <div
                      className="flex items-center justify-between gap-4 py-3 text-sm"
                      key={`${quota.featureCode}-${quota.resource}`}
                    >
                      <span>
                        <span className="block font-medium">{capacityUnitLabel(quota.resource)}</span>
                        <span className="text-xs text-muted-foreground">
                          {featureNames.get(quota.featureCode) ?? quota.featureCode}
                        </span>
                      </span>
                      <strong className="tabular-nums">{quota.effectiveLimit ?? "Illimité"}</strong>
                    </div>
                  ))
                ) : (
                  <p className="py-3 text-sm text-muted-foreground">Aucune capacité mesurée.</p>
                )}
              </div>
            </section>

            {!previewReady ? (
              <div className="flex flex-col gap-3 border-s-2 border-warning ps-4 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-sm text-warning" role="alert">
                  La sélection ou les données ont changé. Recalculez avant de confirmer.
                </p>
                <Button disabled={previewing} onClick={onRefresh} size="sm" variant="outline">
                  {previewing ? "Calcul…" : "Recalculer"}
                </Button>
              </div>
            ) : null}

            {canApply ? (
              <div className="space-y-2">
                <Label htmlFor="subscription-change-reason">Justification de l’opération</Label>
                <Textarea
                  aria-describedby={reasonTouched && reasonProblem ? "subscription-change-reason-error" : undefined}
                  aria-invalid={reasonTouched && Boolean(reasonProblem)}
                  id="subscription-change-reason"
                  maxLength={2000}
                  onBlur={onReasonTouched}
                  onChange={(event) => onReasonChange(event.target.value)}
                  placeholder="Contrat, demande client ou décision commerciale…"
                  value={reason}
                />
                {reasonTouched && reasonProblem ? (
                  <p className="text-xs text-destructive" id="subscription-change-reason-error" role="alert">
                    {reasonProblem}
                  </p>
                ) : null}
              </div>
            ) : null}

            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button disabled={previewing} onClick={onRefresh} variant="outline">
                {previewing ? "Calcul…" : "Recalculer"}
              </Button>
              {canApply ? (
                <Button
                  disabled={
                    applying ||
                    !previewReady ||
                    Boolean(preview.conflicts.length) ||
                    (selection?.timing === "IMMEDIATE" && !preview.immediateAllowed)
                  }
                  onClick={onApply}
                >
                  {applying ? "Application…" : "Appliquer le changement"}
                </Button>
              ) : null}
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

export function AdminSubscriptionChangeWorkbench({
  accountId,
  catalog,
}: {
  accountId: string;
  catalog: ClientPlanCatalog;
}) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const current = catalog.currentSubscription;
  const [planCode, setPlanCode] = useState(() => initialCatalogPlanCode(catalog.plans, current?.planCode));
  const plan = catalog.plans.find((item) => item.code === planCode) ?? null;
  const currentPrice = plan ? currentCatalogPrice(plan.prices, current) : null;
  const [planPriceId, setPlanPriceId] = useState(currentPrice?.priceEntryId ?? "");
  const selectedPlanPrice = plan
    ? (plan.prices.find((item) => item.priceEntryId === planPriceId) ?? defaultCatalogPrice(plan.prices))
    : null;
  const [addOnCodes, setAddOnCodes] = useState<string[]>(current?.addOnCodes ?? []);
  const [quantities, setQuantities] = useState<Record<string, number>>(
    Object.fromEntries((current?.quotaPackages ?? []).map((item) => [item.packageCode, item.quantity])),
  );
  const [timing, setTiming] = useState<"IMMEDIATE" | "AT_RENEWAL">("IMMEDIATE");
  const [preview, setPreview] = useState<SubscriptionChangePreview | null>(null);
  const [previewSelectionKey, setPreviewSelectionKey] = useState<string | null>(null);
  const [previewClock, setPreviewClock] = useState(() => Date.now());
  const [previewOpen, setPreviewOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [reasonTouched, setReasonTouched] = useState(false);
  const retainedAddOns = useMemo(() => current?.retainedAddOns ?? [], [current?.retainedAddOns]);
  const retainedQuotaPackages = useMemo(() => current?.retainedQuotaPackages ?? [], [current?.retainedQuotaPackages]);

  useEffect(() => {
    if (!plan) return;
    const next = currentCatalogPrice(plan.prices, plan.current ? current : null) ?? defaultCatalogPrice(plan.prices);
    if (next && next.priceEntryId !== planPriceId) setPlanPriceId(next.priceEntryId);
  }, [current, plan, planPriceId]);

  const compatibleAddOns = useMemo(
    () => plan?.addOns.filter((item) => matchingCatalogPrice(item.prices, selectedPlanPrice)) ?? [],
    [plan, selectedPlanPrice],
  );
  const compatibleQuotaPackages = useMemo(
    () =>
      plan?.quotaPackages.filter(
        (item) =>
          matchingCatalogPrice(item.prices, selectedPlanPrice) &&
          (item.directlyAvailable || item.requiresAddOnCodes.some((code) => addOnCodes.includes(code))),
      ) ?? [],
    [addOnCodes, plan, selectedPlanPrice],
  );
  const hiddenRetainedAddOns = useMemo(() => {
    const visible = new Set(compatibleAddOns.map((item) => item.code));
    return plan?.current ? retainedAddOns.filter((item) => !visible.has(item.code)) : [];
  }, [compatibleAddOns, plan?.current, retainedAddOns]);
  const hiddenRetainedQuotaPackages = useMemo(() => {
    const visible = new Set(compatibleQuotaPackages.map((item) => item.code));
    return plan?.current ? retainedQuotaPackages.filter((item) => !visible.has(item.code)) : [];
  }, [compatibleQuotaPackages, plan?.current, retainedQuotaPackages]);
  const featureNames = useMemo(
    () =>
      new Map(
        catalog.plans.flatMap((item) =>
          item.features.map((feature) => [feature.featureCode, feature.displayName] as const),
        ),
      ),
    [catalog.plans],
  );

  useEffect(() => {
    if (!plan) return;
    setAddOnCodes((selected) => {
      const selectable = pruneCommercialSelection(selected, plan.addOns, selectedPlanPrice);
      const resolved = preserveRetainedSelection(
        selectable,
        selected,
        retainedAddOns.map((item) => item.code),
        plan.current,
      );
      return sameStringSet(selected, resolved) ? selected : resolved;
    });
    const availablePackages = new Set(compatibleQuotaPackages.map((item) => item.code));
    if (plan.current)
      retainedQuotaPackages.forEach((item) => {
        availablePackages.add(item.code);
      });
    setQuantities((selected) => {
      const next = Object.fromEntries(
        Object.entries(selected).filter(([code, quantity]) => availablePackages.has(code) && quantity > 0),
      );
      return Object.keys(selected).length === Object.keys(next).length &&
        Object.entries(next).every(([code, quantity]) => selected[code] === quantity)
        ? selected
        : next;
    });
  }, [compatibleQuotaPackages, plan, retainedAddOns, retainedQuotaPackages, selectedPlanPrice]);

  const selection = useMemo<SubscriptionChangeInput | null>(
    () =>
      selectedPlanPrice && plan
        ? {
            targetPlanCode: plan.code,
            addOnCodes,
            quotaPackages: Object.entries(quantities)
              .filter(([, quantity]) => quantity > 0)
              .map(([packageCode, quantity]) => ({ packageCode, quantity })),
            timing,
            planPriceSelection: {
              priceEntryId: selectedPlanPrice.priceEntryId,
              currencyCode: selectedPlanPrice.currencyCode,
              billingCycle: selectedPlanPrice.billingCycle,
            },
          }
        : null,
    [addOnCodes, plan, quantities, selectedPlanPrice, timing],
  );
  const selectionIsNoOp = subscriptionChangeSelectionMatchesCurrent(selection, current);

  useEffect(() => {
    if (!preview) return;
    setPreviewClock(Date.now());
    const expiresAt = Date.parse(preview.expiresAt);
    if (!Number.isFinite(expiresAt)) return;
    const delay = expiresAt - Date.now();
    if (delay <= 0) return;
    const timer = window.setTimeout(() => setPreviewClock(Date.now()), delay + 25);
    return () => window.clearTimeout(timer);
  }, [preview]);

  const previewMutation = useMutation({
    mutationFn: (input: SubscriptionChangeInput) => adminApi.previewSubscriptionChange(accountId, input),
    onSuccess: (data, input) => {
      setPreview(data);
      setPreviewSelectionKey(subscriptionChangeSelectionKey(input));
      setPreviewOpen(true);
    },
    onError: (error) => {
      setPreview(null);
      setPreviewSelectionKey(null);
      toast.error(subscriptionChangeFailureMessage(error));
    },
  });
  const refreshPreview = () => {
    if (!selection) return;
    setPreview(null);
    setPreviewSelectionKey(null);
    setPreviewOpen(true);
    previewMutation.mutate(selection);
  };
  const applyMutation = useMutation({
    mutationFn: () => {
      if (!selection || !preview || !subscriptionChangePreviewIsCurrent(preview, selection, previewSelectionKey)) {
        throw new Error("A reviewed selection is required");
      }
      const reasonProblem = operatorReasonError(reason);
      if (reasonProblem) throw new Error(reasonProblem);
      return adminApi.applySubscriptionChange(accountId, {
        selection,
        previewToken: preview.previewToken,
        reason: normalizedOperatorReason(reason),
      });
    },
    onSuccess: (result) => {
      void invalidateAdminSubscriptionEntitlement(queryClient);
      setPreviewOpen(false);
      setPreview(null);
      setPreviewSelectionKey(null);
      setReason("");
      setReasonTouched(false);
      toast.success(subscriptionChangeRecordedMessage(result.operation));
    },
    onError: (error) => {
      setPreview(null);
      setPreviewSelectionKey(null);
      if (adminChangeNeedsFreshReview(error) && selection) {
        void queryClient.invalidateQueries({ queryKey: adminCommercialKeys.subscriptions.detail(accountId) });
        void queryClient.invalidateQueries({ queryKey: adminCommercialKeys.subscriptions.changeCatalog(accountId) });
        previewMutation.mutate(selection);
      }
      toast.error(subscriptionChangeFailureMessage(error));
    },
  });

  const changePlan = (code: string) => {
    const next = catalog.plans.find((item) => item.code === code) ?? null;
    const nextPrice = next ? currentCatalogPrice(next.prices, next.current ? current : null) : null;
    setPlanCode(code);
    setPlanPriceId((nextPrice ?? (next ? defaultCatalogPrice(next.prices) : null))?.priceEntryId ?? "");
    setAddOnCodes(next?.current ? (current?.addOnCodes ?? []) : []);
    setQuantities(
      next?.current
        ? Object.fromEntries((current?.quotaPackages ?? []).map((item) => [item.packageCode, item.quantity]))
        : {},
    );
    setPreview(null);
    setPreviewSelectionKey(null);
  };

  const toggleAddOn = (item: CatalogPlan["addOns"][number], checked: boolean) => {
    setAddOnCodes((selected) => updateCatalogAddOnSelection(selected, item, compatibleAddOns, checked));
    setPreview(null);
  };

  if (!catalog.plans.length) return null;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="border-b p-4">
        <h2 className="text-sm font-semibold">Préparer un changement</h2>
      </div>
      <div className="grid gap-4 border-b p-4 lg:grid-cols-3">
        <div className="space-y-2">
          <Label htmlFor="operator-target-plan">Forfait cible</Label>
          <Select onValueChange={changePlan} value={plan?.code ?? ""}>
            <SelectTrigger id="operator-target-plan">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {catalog.plans.map((item) => (
                <SelectItem
                  disabled={(!item.selectable && !item.current) || !item.prices.length}
                  key={item.code}
                  value={item.code}
                >
                  {item.name}
                  {item.current ? " · actuel" : ""}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="operator-plan-price">Tarif</Label>
          <Select
            disabled={!selectedPlanPrice}
            onValueChange={setPlanPriceId}
            value={selectedPlanPrice?.priceEntryId ?? ""}
          >
            <SelectTrigger id="operator-plan-price">
              <SelectValue placeholder="Aucun tarif" />
            </SelectTrigger>
            <SelectContent>
              {(plan?.prices ?? []).map((item) => (
                <SelectItem key={item.priceEntryId} value={item.priceEntryId}>
                  {cycleLabel(item.billingCycle)} · {formatExactMoney(item.amount, item.currencyCode)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="operator-change-timing">Application</Label>
          <Select onValueChange={(value) => setTiming(value as typeof timing)} value={timing}>
            <SelectTrigger id="operator-change-timing">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="IMMEDIATE">Maintenant</SelectItem>
              <SelectItem value="AT_RENEWAL">Au renouvellement</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="grid lg:grid-cols-2 lg:divide-x lg:divide-x-reverse rtl:lg:divide-x-reverse">
        <section className="p-4">
          <h3 className="text-sm font-semibold">Add-ons</h3>
          <div className="mt-3 divide-y border-y">
            {compatibleAddOns.length ? (
              compatibleAddOns.map((item) => {
                const price = matchingCatalogPrice(item.prices, selectedPlanPrice);
                const { selected, excludedBy, missingDependency, requiredBy } = catalogAddOnSelectionState(
                  item,
                  compatibleAddOns,
                  addOnCodes,
                );
                return (
                  <div className="flex items-start gap-3 py-3" key={item.code}>
                    <Checkbox
                      checked={selected}
                      disabled={selected ? Boolean(requiredBy) : Boolean(missingDependency || excludedBy)}
                      id={`operator-addon-${item.code}`}
                      onCheckedChange={(checked) => toggleAddOn(item, Boolean(checked))}
                    />
                    <Label className="min-w-0 flex-1 font-normal" htmlFor={`operator-addon-${item.code}`}>
                      <span className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
                        <strong>{item.name}</strong>
                        <span className="text-muted-foreground">
                          {price ? formatExactMoney(price.amount, price.currencyCode) : "Indisponible"}
                        </span>
                      </span>
                      {missingDependency || excludedBy || requiredBy ? (
                        <span className="mt-1 block text-xs text-muted-foreground">
                          {missingDependency
                            ? `Dépendance indisponible : ${missingDependency}`
                            : excludedBy
                              ? `Incompatible avec ${excludedBy.name}`
                              : `Requis par ${requiredBy?.name}`}
                        </span>
                      ) : null}
                    </Label>
                  </div>
                );
              })
            ) : hiddenRetainedAddOns.length === 0 ? (
              <p className="py-3 text-sm text-muted-foreground">Aucun add-on compatible.</p>
            ) : null}
            {hiddenRetainedAddOns.map((item) => (
              <div className="flex items-start gap-3 py-3" key={`retained-${item.code}`}>
                <Checkbox
                  checked={addOnCodes.includes(item.code)}
                  disabled={!item.removable}
                  id={`operator-retained-addon-${item.code}`}
                  onCheckedChange={(checked) => {
                    setAddOnCodes((selected) =>
                      checked ? [...new Set([...selected, item.code])] : selected.filter((code) => code !== item.code),
                    );
                    setPreview(null);
                  }}
                />
                <Label className="min-w-0 flex-1 font-normal" htmlFor={`operator-retained-addon-${item.code}`}>
                  <span className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
                    <strong>{item.name}</strong>
                    <span className="text-muted-foreground">
                      {formatExactMoney(item.unitPrice, item.currencyCode)} · conservé
                    </span>
                  </span>
                  <span className="mt-1 block text-xs text-muted-foreground">
                    Indisponible à la vente{item.removable ? " · retrait possible" : " · retrait bloqué"}
                  </span>
                </Label>
              </div>
            ))}
          </div>
        </section>

        <section className="border-t p-4 lg:border-t-0">
          <h3 className="text-sm font-semibold">Packs de capacité</h3>
          <div className="mt-3 divide-y border-y">
            {compatibleQuotaPackages.length ? (
              compatibleQuotaPackages.map((item) => {
                const price = matchingCatalogPrice(item.prices, selectedPlanPrice);
                return (
                  <div className="flex items-center justify-between gap-4 py-3" key={item.code}>
                    <div className="min-w-0">
                      <p className="text-sm font-medium">{item.name}</p>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        +{item.capacityPerUnit} {capacityUnitLabel(item.resource)} ·{" "}
                        {price ? formatExactMoney(price.amount, price.currencyCode) : "Indisponible"}
                      </p>
                    </div>
                    <SubscriptionQuantityControl
                      label={item.name}
                      maximum={item.maximumQuantity}
                      onChange={(quantity) => {
                        setQuantities((selected) => ({ ...selected, [item.code]: quantity }));
                        setPreview(null);
                      }}
                      value={quantities[item.code] ?? 0}
                    />
                  </div>
                );
              })
            ) : hiddenRetainedQuotaPackages.length === 0 ? (
              <p className="py-3 text-sm text-muted-foreground">Aucun pack compatible.</p>
            ) : null}
            {hiddenRetainedQuotaPackages.map((item) => (
              <div className="flex items-center justify-between gap-4 py-3" key={`retained-${item.code}`}>
                <div className="min-w-0">
                  <p className="text-sm font-medium">{item.name}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    +{item.capacityPerUnit} {capacityUnitLabel(item.resource)} ·{" "}
                    {formatExactMoney(item.unitPrice, item.currencyCode)} · conservé
                  </p>
                </div>
                <RetainedSubscriptionQuantityControl
                  label={item.name}
                  maximum={item.maximumSelectableQuantity ?? item.quantity}
                  onChange={(quantity) => {
                    setQuantities((selected) => ({ ...selected, [item.code]: quantity }));
                    setPreview(null);
                  }}
                  quantityEditable={item.quantityEditable}
                  removable={item.removable}
                  retainedQuantity={item.quantity}
                  value={quantities[item.code] ?? item.quantity}
                />
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="flex flex-col gap-2 border-t p-4 sm:flex-row sm:items-center sm:justify-end">
        {selectionIsNoOp ? <p className="text-xs text-muted-foreground">Aucun changement sélectionné.</p> : null}
        <Button
          disabled={
            !selection ||
            selectionIsNoOp ||
            previewMutation.isPending ||
            !session.can(adminPermissions.subscriptionsPreviewChange)
          }
          onClick={refreshPreview}
          title={
            session.can(adminPermissions.subscriptionsPreviewChange)
              ? undefined
              : "Votre rôle n’autorise pas la prévisualisation."
          }
        >
          {previewMutation.isPending ? (
            "Calcul…"
          ) : (
            <>
              Prévisualiser
              <ArrowRightIcon className="rtl:rotate-180" />
            </>
          )}
        </Button>
      </div>

      <PreviewDialog
        applying={applyMutation.isPending}
        canApply={session.can(adminPermissions.subscriptionsApplyChange)}
        currentPlanName={
          catalog.plans.find((item) => item.code === current?.planCode)?.name ?? current?.planCode ?? "Forfait actuel"
        }
        featureNames={featureNames}
        onApply={() => {
          setReasonTouched(true);
          if (!operatorReasonError(reason)) applyMutation.mutate();
        }}
        onOpenChange={(open) => {
          setPreviewOpen(open);
          if (!open) {
            setPreview(null);
            setPreviewSelectionKey(null);
            setReason("");
            setReasonTouched(false);
          }
        }}
        onReasonChange={setReason}
        onReasonTouched={() => setReasonTouched(true)}
        onRefresh={refreshPreview}
        open={previewOpen}
        preview={preview}
        previewClock={previewClock}
        previewing={previewMutation.isPending}
        previewSelectionKey={previewSelectionKey}
        reason={reason}
        reasonTouched={reasonTouched}
        selection={selection}
        targetPlanName={plan?.name ?? planCode}
      />
    </section>
  );
}
