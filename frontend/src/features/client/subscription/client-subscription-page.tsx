import { ArrowRightIcon, CheckIcon, MinusIcon, PlusIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { clientApi } from "@/api/client-api";
import type { ClientPlanCatalog, SubscriptionChangeInput, SubscriptionChangePreview } from "@/api/contracts";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { capacityUnitLabel } from "@/features/admin/commercial/commercial-presentation";
import {
  currentCatalogPrice,
  defaultCatalogPrice,
  initialCatalogPlanCode,
  matchingCatalogPrice,
  preserveRetainedSelection,
  pruneCommercialSelection,
  sameStringSet,
} from "@/features/commercial/catalog-price-rules";
import {
  clientCommercialKeys,
  commercialQueryEnabled,
  invalidateClientCommercial,
} from "@/features/commercial/commercial-query";
import { formatExactMoney } from "@/lib/exact-decimal";
import { subscriptionChangeFailureMessage } from "./subscription-change-rules";

const money = formatExactMoney;
const date = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium" }).format(new Date(value)) : "—";

function QuantityControl({
  value,
  maximum,
  onChange,
}: {
  value: number;
  maximum: number;
  onChange: (value: number) => void;
}) {
  return (
    <div className="inline-flex items-center rounded-lg border">
      <Button
        aria-label="Réduire"
        disabled={value <= 0}
        onClick={() => onChange(value - 1)}
        size="icon-sm"
        type="button"
        variant="ghost"
      >
        <MinusIcon />
      </Button>
      <span className="min-w-8 text-center text-sm font-semibold tabular-nums">{value}</span>
      <Button
        aria-label="Augmenter"
        disabled={value >= maximum}
        onClick={() => onChange(value + 1)}
        size="icon-sm"
        type="button"
        variant="ghost"
      >
        <PlusIcon />
      </Button>
    </div>
  );
}

function PreviewDialog({
  preview,
  open,
  onOpenChange,
  timing,
  onApply,
  applying,
  canApply,
  currentPlanName,
  targetPlanName,
  featureNames,
}: {
  preview: SubscriptionChangePreview | null;
  open: boolean;
  onOpenChange: (value: boolean) => void;
  timing: "IMMEDIATE" | "AT_RENEWAL";
  onApply: () => void;
  applying: boolean;
  canApply: boolean;
  currentPlanName: string;
  targetPlanName: string;
  featureNames: ReadonlyMap<string, string>;
}) {
  if (!preview) return null;
  return (
    <Dialog onOpenChange={onOpenChange} open={open}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Vérifier le changement</DialogTitle>
          <DialogDescription>
            {currentPlanName} → {targetPlanName} · {timing === "IMMEDIATE" ? "effet immédiat" : "au renouvellement"}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-6">
          <div className="grid gap-px overflow-hidden rounded-lg border bg-border sm:grid-cols-2">
            <div className="bg-card p-4">
              <p className="text-xs text-muted-foreground">Prix actuel</p>
              <p className="mt-1 text-xl font-semibold">{money(preview.currentPrice, preview.currencyCode)}</p>
            </div>
            <div className="bg-card p-4">
              <p className="text-xs text-muted-foreground">Nouveau prix</p>
              <p className="mt-1 text-xl font-semibold">{money(preview.previewPrice, preview.currencyCode)}</p>
            </div>
          </div>
          {preview.conflicts.length ? (
            <section className="rounded-lg border border-destructive/30 bg-destructive/5 p-4">
              <div className="flex items-center gap-2 text-sm font-semibold text-destructive">
                <WarningCircleIcon />
                Conflits à résoudre
              </div>
              <ul className="mt-3 list-disc space-y-1 ps-5 text-sm">
                {preview.conflicts.map((conflict) => (
                  <li key={`${conflict.code}-${conflict.featureCode}-${conflict.resource}`}>{conflict.message}</li>
                ))}
              </ul>
            </section>
          ) : null}
          <section>
            <h3 className="text-sm font-semibold">Capacités effectives</h3>
            <div className="mt-3 divide-y rounded-lg border">
              {preview.effectiveQuotaLimits.map((quota) => (
                <div
                  className="flex items-center justify-between gap-4 p-3"
                  key={`${quota.featureCode}-${quota.resource}`}
                >
                  <div>
                    <p className="text-sm font-medium">{capacityUnitLabel(quota.resource)}</p>
                    <p className="text-xs text-muted-foreground">
                      {featureNames.get(quota.featureCode) ?? "Fonctionnalité indisponible"}
                    </p>
                  </div>
                  <span className="text-sm font-semibold tabular-nums">{quota.effectiveLimit ?? "Illimité"}</span>
                </div>
              ))}
            </div>
          </section>
          {canApply ? (
            <div className="flex justify-end">
              <Button
                disabled={
                  Boolean(preview.conflicts.length) || applying || (timing === "IMMEDIATE" && !preview.immediateAllowed)
                }
                onClick={onApply}
              >
                {applying ? "Application…" : "Confirmer le changement"}
              </Button>
            </div>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function Configurator({ catalog }: { catalog: ClientPlanCatalog }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const current = catalog.currentSubscription;
  const [planCode, setPlanCode] = useState(() => initialCatalogPlanCode(catalog.plans, current?.planCode));
  const plan = catalog.plans.find((item) => item.code === planCode);
  const initialPrice = plan ? currentCatalogPrice(plan.prices, current) : null;
  const [planPriceId, setPlanPriceId] = useState(initialPrice?.priceEntryId ?? "");
  const selectedPlanPrice = plan
    ? (plan.prices.find((item) => item.priceEntryId === planPriceId) ?? defaultCatalogPrice(plan.prices))
    : null;
  const [addOns, setAddOns] = useState<string[]>(current?.addOnCodes ?? []);
  const [quantities, setQuantities] = useState<Record<string, number>>(
    Object.fromEntries((current?.quotaPackages ?? []).map((item) => [item.packageCode, item.quantity])),
  );
  const [timing, setTiming] = useState<"IMMEDIATE" | "AT_RENEWAL">("IMMEDIATE");
  const [preview, setPreview] = useState<SubscriptionChangePreview | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const retainedAddOns = useMemo(() => current?.retainedAddOns ?? [], [current?.retainedAddOns]);
  const retainedQuotaPackages = useMemo(() => current?.retainedQuotaPackages ?? [], [current?.retainedQuotaPackages]);
  const changeError = (error: unknown) => {
    setPreview(null);
    setPreviewOpen(false);
    void invalidateClientCommercial(queryClient);
    toast.error(subscriptionChangeFailureMessage(error));
  };
  useEffect(() => {
    setAddOns(current?.addOnCodes ?? []);
    setQuantities(Object.fromEntries((current?.quotaPackages ?? []).map((item) => [item.packageCode, item.quantity])));
  }, [current?.addOnCodes, current?.quotaPackages]);
  useEffect(() => {
    if (!plan) return;
    const nextPrice = defaultCatalogPrice(plan.prices, selectedPlanPrice);
    if (nextPrice && nextPrice.priceEntryId !== planPriceId) setPlanPriceId(nextPrice.priceEntryId);
  }, [plan, planPriceId, selectedPlanPrice]);

  const compatibleAddOns = useMemo(
    () => plan?.addOns.filter((item) => matchingCatalogPrice(item.prices, selectedPlanPrice)) ?? [],
    [plan, selectedPlanPrice],
  );
  const compatibleQuotaPackages = useMemo(
    () =>
      plan?.quotaPackages.filter(
        (item) =>
          matchingCatalogPrice(item.prices, selectedPlanPrice) &&
          (item.directlyAvailable || item.requiresAddOnCodes.some((code) => addOns.includes(code))),
      ) ?? [],
    [addOns, plan, selectedPlanPrice],
  );
  const featureNames = useMemo(
    () =>
      new Map(
        catalog.plans.flatMap((catalogPlan) =>
          catalogPlan.features.map((feature) => [feature.featureCode, feature.displayName] as const),
        ),
      ),
    [catalog.plans],
  );
  const currentPlanName =
    catalog.plans.find((catalogPlan) => catalogPlan.code === current?.planCode)?.name ?? "Forfait actuel";
  useEffect(() => {
    if (!plan) return;
    setAddOns((currentItems) => {
      const next = pruneCommercialSelection(currentItems, plan.addOns, selectedPlanPrice);
      const resolved = preserveRetainedSelection(
        next,
        currentItems,
        retainedAddOns.map((item) => item.code),
        plan.current,
      );
      return sameStringSet(currentItems, resolved) ? currentItems : resolved;
    });
    const compatiblePackageCodes = new Set(compatibleQuotaPackages.map((item) => item.code));
    if (plan.current)
      retainedQuotaPackages.forEach((item) => {
        compatiblePackageCodes.add(item.code);
      });
    setQuantities((currentItems) => {
      const next = Object.fromEntries(
        Object.entries(currentItems).filter(([code, quantity]) => compatiblePackageCodes.has(code) && quantity > 0),
      );
      const same =
        Object.keys(currentItems).length === Object.keys(next).length &&
        Object.entries(next).every(([code, quantity]) => currentItems[code] === quantity);
      return same ? currentItems : next;
    });
  }, [compatibleQuotaPackages, plan, retainedAddOns, retainedQuotaPackages, selectedPlanPrice]);

  const request = useMemo<SubscriptionChangeInput | null>(
    () =>
      selectedPlanPrice
        ? {
            targetPlanCode: planCode,
            addOnCodes: addOns,
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
    [planCode, addOns, quantities, timing, selectedPlanPrice],
  );
  const previewMutation = useMutation({
    mutationFn: () => {
      if (!request) throw new Error("Aucun tarif disponible");
      return clientApi.previewSubscriptionChange(request);
    },
    onSuccess: (data) => {
      setPreview(data);
      setPreviewOpen(true);
    },
    onError: changeError,
  });
  const apply = useMutation({
    mutationFn: () => {
      if (!request) throw new Error("Aucun tarif disponible");
      return clientApi.applySubscriptionChange(request);
    },
    onSuccess: () => {
      void invalidateClientCommercial(queryClient);
      setPreviewOpen(false);
      toast.success(timing === "IMMEDIATE" ? "Changement appliqué" : "Changement planifié");
    },
    onError: changeError,
  });
  if (!plan) return <EmptyState title="Aucun forfait disponible" />;
  return (
    <div className="grid gap-6 xl:grid-cols-[0.72fr_1.28fr]">
      <aside className="space-y-2">
        {catalog.plans.map((item) => {
          const startingPrice = defaultCatalogPrice(item.prices);
          return (
            <button
              className={`w-full rounded-xl border p-4 text-start transition-colors ${item.code === planCode ? "border-primary bg-primary/5" : "bg-card hover:border-foreground/20"}`}
              disabled={(!item.selectable && !item.current) || !item.prices.length}
              key={item.code}
              onClick={() => {
                const nextPrice = defaultCatalogPrice(item.prices);
                setPlanCode(item.code);
                setPlanPriceId(nextPrice?.priceEntryId ?? "");
                setAddOns(item.current ? (current?.addOnCodes ?? []) : []);
                setQuantities(
                  item.current
                    ? Object.fromEntries(
                        (current?.quotaPackages ?? []).map((entry) => [entry.packageCode, entry.quantity]),
                      )
                    : {},
                );
                setPreview(null);
              }}
              type="button"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-semibold">{item.name}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {!item.selectable && !item.current
                      ? "Non disponible pour un nouveau choix"
                      : item.prices.length
                        ? `${item.prices.length} option${item.prices.length > 1 ? "s" : ""} tarifaire${item.prices.length > 1 ? "s" : ""}`
                        : "Indisponible"}
                  </p>
                </div>
                {item.current ? <StatusBadge tone="success">Actuel</StatusBadge> : null}
              </div>
              {startingPrice ? (
                <p className="mt-4 text-lg font-semibold">
                  <span className="me-1 text-xs font-normal text-muted-foreground">À partir de</span>
                  {money(startingPrice.amount, startingPrice.currencyCode)}
                </p>
              ) : null}
            </button>
          );
        })}
      </aside>
      <div className="space-y-6">
        <section className="rounded-xl border bg-card p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold">{plan.name}</h2>
              {plan.description ? <p className="mt-2 text-sm text-muted-foreground">{plan.description}</p> : null}
            </div>
            {selectedPlanPrice ? (
              <div className="w-full space-y-2 sm:w-64">
                <Label>Facturation</Label>
                <Select onValueChange={setPlanPriceId} value={selectedPlanPrice.priceEntryId}>
                  <SelectTrigger aria-label="Tarif du forfait">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {plan.prices.map((item) => (
                      <SelectItem key={item.priceEntryId} value={item.priceEntryId}>
                        {item.billingCycle === "MONTHLY" ? "Mensuel" : "Annuel"} ·{" "}
                        {money(item.amount, item.currencyCode)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            ) : (
              <p className="text-sm text-destructive">Aucun tarif actif</p>
            )}
          </div>
          <div className="mt-6 columns-1 gap-x-8 sm:columns-2">
            {plan.features.map((feature) => (
              <div className="mb-4 flex break-inside-avoid gap-3" key={feature.featureCode}>
                <CheckIcon className="mt-0.5 size-4 shrink-0 text-success" />
                <div>
                  <p className="text-sm font-medium">{feature.displayName}</p>
                  {feature.description ? (
                    <p className="mt-1 text-xs text-muted-foreground">{feature.description}</p>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        </section>
        {compatibleAddOns.length ? (
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Add-ons disponibles</h2>
            <div className="mt-4 divide-y rounded-lg border">
              {compatibleAddOns.map((item) => {
                const itemPrice = matchingCatalogPrice(item.prices, selectedPlanPrice);
                return (
                  <div className="flex items-start gap-3 p-4" key={item.code}>
                    <Checkbox
                      checked={addOns.includes(item.code)}
                      id={`client-addon-${item.code}`}
                      onCheckedChange={(checked) =>
                        setAddOns((currentItems) =>
                          checked
                            ? [...new Set([...currentItems, item.code])]
                            : currentItems.filter((code) => code !== item.code),
                        )
                      }
                    />
                    <Label className="min-w-0 flex-1 font-normal" htmlFor={`client-addon-${item.code}`}>
                      <span className="block text-sm font-medium">{item.name}</span>
                      <span className="mt-1 block text-xs text-muted-foreground">
                        {itemPrice ? money(itemPrice.amount, itemPrice.currencyCode) : "Indisponible"}
                        {item.description ? ` · ${item.description}` : ""}
                      </span>
                    </Label>
                  </div>
                );
              })}
            </div>
          </section>
        ) : null}
        {plan.current && retainedAddOns.length ? (
          <section className="border-y py-5">
            <h2 className="text-sm font-semibold">Add-ons conservés</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Conservés aux conditions déjà achetées, mais indisponibles pour une nouvelle sélection.
            </p>
            <div className="mt-4 divide-y border-y">
              {retainedAddOns.map((item) => (
                <div className="flex items-start gap-3 py-4" key={item.code}>
                  <Checkbox
                    checked={addOns.includes(item.code)}
                    disabled={!item.removable}
                    id={`retained-addon-${item.code}`}
                    onCheckedChange={(checked) =>
                      setAddOns((items) =>
                        checked ? [...new Set([...items, item.code])] : items.filter((code) => code !== item.code),
                      )
                    }
                  />
                  <Label className="min-w-0 flex-1 font-normal" htmlFor={`retained-addon-${item.code}`}>
                    <span className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
                      <strong>{item.name}</strong>
                      <span className="text-muted-foreground">
                        {money(item.unitPrice, item.currencyCode)} ·{" "}
                        {item.billingCycle === "MONTHLY"
                          ? "mensuel"
                          : item.billingCycle === "YEARLY"
                            ? "annuel"
                            : "permanent"}
                      </span>
                    </span>
                    <span className="mt-1 block text-xs text-muted-foreground">
                      Révision détenue {item.definitionVersion}
                      {item.removable ? " · peut être retirée" : " · retrait indisponible"}
                    </span>
                  </Label>
                </div>
              ))}
            </div>
          </section>
        ) : null}
        {compatibleQuotaPackages.length ? (
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Capacités supplémentaires</h2>
            <div className="mt-4 divide-y rounded-lg border">
              {compatibleQuotaPackages.map((item) => {
                const itemPrice = matchingCatalogPrice(item.prices, selectedPlanPrice);
                return (
                  <div className="flex items-center justify-between gap-4 p-4" key={item.code}>
                    <div>
                      <p className="text-sm font-medium">{item.name}</p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        +{item.capacityPerUnit} {capacityUnitLabel(item.resource)} ·{" "}
                        {itemPrice ? money(itemPrice.amount, itemPrice.currencyCode) : "Indisponible"} par unité
                      </p>
                    </div>
                    <QuantityControl
                      maximum={item.maximumQuantity}
                      onChange={(value) => setQuantities((currentItems) => ({ ...currentItems, [item.code]: value }))}
                      value={quantities[item.code] ?? 0}
                    />
                  </div>
                );
              })}
            </div>
          </section>
        ) : null}
        {plan.current && retainedQuotaPackages.length ? (
          <section className="border-y py-5">
            <h2 className="text-sm font-semibold">Capacités conservées</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Non proposées à la vente, elles restent actives et facturées aux conditions détenues.
            </p>
            <div className="mt-4 divide-y border-y">
              {retainedQuotaPackages.map((item) => (
                <div className="flex items-center justify-between gap-4 py-4" key={item.code}>
                  <div>
                    <p className="text-sm font-medium">{item.name}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      +{item.capacityPerUnit} {capacityUnitLabel(item.resource)} ·{" "}
                      {money(item.unitPrice, item.currencyCode)} par unité · conservé
                    </p>
                  </div>
                  {item.quantityEditable || item.removable ? (
                    <QuantityControl
                      maximum={item.maximumSelectableQuantity ?? item.quantity}
                      onChange={(value) => setQuantities((items) => ({ ...items, [item.code]: value }))}
                      value={quantities[item.code] ?? 0}
                    />
                  ) : (
                    <span className="text-sm font-semibold tabular-nums">× {item.quantity}</span>
                  )}
                </div>
              ))}
            </div>
          </section>
        ) : null}
        <section className="flex flex-col gap-4 rounded-xl border bg-card p-5 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-2">
            <Label>Moment du changement</Label>
            <Select onValueChange={(value) => setTiming(value as "IMMEDIATE" | "AT_RENEWAL")} value={timing}>
              <SelectTrigger aria-label="Moment du changement" className="w-56">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="IMMEDIATE">Maintenant</SelectItem>
                <SelectItem value="AT_RENEWAL">Au renouvellement</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {session.can(clientPermissions.subscriptionPreview) ? (
            <Button disabled={!request || previewMutation.isPending} onClick={() => previewMutation.mutate()}>
              {previewMutation.isPending ? (
                "Calcul…"
              ) : (
                <>
                  <ArrowRightIcon className="rtl:rotate-180" />
                  Prévisualiser
                </>
              )}
            </Button>
          ) : null}
        </section>
      </div>
      <PreviewDialog
        applying={apply.isPending}
        canApply={session.can(clientPermissions.subscriptionApply)}
        currentPlanName={currentPlanName}
        featureNames={featureNames}
        onApply={() => apply.mutate()}
        onOpenChange={setPreviewOpen}
        open={previewOpen}
        preview={preview}
        targetPlanName={plan.name}
        timing={timing}
      />
    </div>
  );
}

function ChangeHistory() {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const commercialContext = { companyId: session.selectedCompanyId, isB2B: session.isB2B };
  const changes = useQuery({
    queryKey: clientCommercialKeys.changes(commercialContext),
    queryFn: clientApi.subscriptionChanges,
    enabled: commercialQueryEnabled(session.can, clientPermissions.subscriptionReadChanges),
  });
  const cancel = useMutation({
    mutationFn: clientApi.cancelSubscriptionChange,
    onSuccess: () => {
      void invalidateClientCommercial(queryClient);
      toast.success("Changement annulé");
    },
  });
  if (changes.isLoading) return <LoadingState />;
  if (changes.isError) {
    return <ErrorState retry={() => void changes.refetch()} title="Impossible de charger les changements" />;
  }
  if (!changes.data?.length) return <EmptyState title="Aucun changement" />;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Changement</TableHead>
            <TableHead>Timing</TableHead>
            <TableHead>Effet</TableHead>
            <TableHead>Statut</TableHead>
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {changes.data.map((operation) => (
            <TableRow key={operation.id}>
              <TableCell>
                <span className="text-sm">Changement de forfait</span>
                {operation.attentionReason ? (
                  <p className="mt-1 text-xs text-destructive">{operation.attentionReason}</p>
                ) : null}
              </TableCell>
              <TableCell>{operation.timing === "IMMEDIATE" ? "Immédiat" : "Renouvellement"}</TableCell>
              <TableCell>{date(operation.effectiveAt)}</TableCell>
              <TableCell>
                <StatusBadge
                  tone={
                    operation.status === "APPLIED"
                      ? "success"
                      : operation.status === "NEEDS_ATTENTION"
                        ? "danger"
                        : "warning"
                  }
                >
                  {operation.status}
                </StatusBadge>
              </TableCell>
              <TableCell>
                {["PENDING", "AWAITING_CONFIRMATION"].includes(operation.status) &&
                session.can(clientPermissions.subscriptionCancel) ? (
                  <Button onClick={() => cancel.mutate(operation.id)} size="sm" variant="ghost">
                    Annuler
                  </Button>
                ) : null}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  );
}

export function ClientSubscriptionPage() {
  const session = useClientSession();
  const canReadSubscription = session.can(clientPermissions.subscriptionRead);
  const canReadCatalog = session.can(clientPermissions.subscriptionCatalog);
  const canReadChanges = session.can(clientPermissions.subscriptionReadChanges);
  const availableTabs = [
    ...(canReadSubscription ? [{ label: "Abonnement actuel", value: "current" as const }] : []),
    ...(canReadCatalog ? [{ label: "Changer de forfait", value: "catalog" as const }] : []),
    ...(canReadChanges ? [{ label: "Changements", value: "changes" as const }] : []),
  ];
  const [requestedTab, setRequestedTab] = useState<"current" | "catalog" | "changes">("current");
  const tab = availableTabs.some((item) => item.value === requestedTab)
    ? requestedTab
    : (availableTabs[0]?.value ?? "current");
  const commercialContext = { companyId: session.selectedCompanyId, isB2B: session.isB2B };
  const [subscription, catalog] = useQueries({
    queries: [
      {
        queryKey: clientCommercialKeys.subscription(commercialContext),
        queryFn: clientApi.subscription,
        enabled: commercialQueryEnabled(session.can, clientPermissions.subscriptionRead, tab === "current"),
        retry: false,
      },
      {
        queryKey: clientCommercialKeys.catalog(commercialContext),
        queryFn: clientApi.planCatalog,
        enabled: commercialQueryEnabled(session.can, clientPermissions.subscriptionCatalog, tab === "catalog"),
      },
    ],
  });
  return (
    <div className="space-y-7">
      <PageHeader title="Abonnement" />
      <SectionTabs
        items={availableTabs}
        onValueChange={(value) => setRequestedTab(value as "current" | "catalog" | "changes")}
        value={tab}
      />
      {(tab === "current" && subscription.isLoading) || (tab === "catalog" && catalog.isLoading) ? (
        <LoadingState />
      ) : tab === "current" && subscription.isError ? (
        <ErrorState retry={() => void subscription.refetch()} title="Impossible de charger l’abonnement" />
      ) : tab === "catalog" && catalog.isError ? (
        <ErrorState retry={() => void catalog.refetch()} />
      ) : tab === "catalog" && canReadCatalog && catalog.data ? (
        <Configurator catalog={catalog.data} />
      ) : tab === "changes" && canReadChanges ? (
        <ChangeHistory />
      ) : tab === "current" && canReadSubscription && subscription.data ? (
        <div className="grid gap-6 lg:grid-cols-[1fr_0.8fr]">
          <section className="rounded-xl border bg-card p-5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold">{subscription.data.plan.name}</h2>
              </div>
              <StatusBadge tone={subscription.data.status === "ACTIVE" ? "success" : "warning"}>
                {subscription.data.status}
              </StatusBadge>
            </div>
            <p className="mt-6 text-2xl font-semibold">
              {money(subscription.data.currentPrice, subscription.data.currentPriceCurrencyCode)}
            </p>
          </section>
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">Période en cours</h2>
            <dl className="mt-5 space-y-4">
              <div className="flex justify-between gap-4 text-sm">
                <dt className="text-muted-foreground">Début</dt>
                <dd className="font-medium">{date(subscription.data.currentPeriodStart)}</dd>
              </div>
              <div className="flex justify-between gap-4 text-sm">
                <dt className="text-muted-foreground">Renouvellement</dt>
                <dd className="font-medium">{date(subscription.data.currentPeriodEnd)}</dd>
              </div>
              <div className="flex justify-between gap-4 text-sm">
                <dt className="text-muted-foreground">Résiliation planifiée</dt>
                <dd className="font-medium">{subscription.data.cancelAtPeriodEnd ? "Oui" : "Non"}</dd>
              </div>
            </dl>
          </section>
        </div>
      ) : tab === "current" && canReadSubscription ? (
        <EmptyState title="Aucun abonnement" />
      ) : null}
    </div>
  );
}
