import { ArrowClockwiseIcon, CheckIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  AddOn,
  CommercialAvailabilityHistoryEntry,
  ExtensionCompatibility,
  PageResponse,
  Plan,
  PlanAvailabilityPreview,
  PlanExtensionPolicy,
  ProductSalesVisibility,
  ProductVisibilityPreview,
  QuotaPackage,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { StatusText } from "@/components/patterns/status-text";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { adminCommercialKeys, invalidateAdminCommercial } from "@/features/commercial/commercial-query";
import { availabilityLabel, extensionPolicyLabel } from "./commercial-presentation";

type Product = Plan | AddOn | QuotaPackage;
type Kind = "plan" | "add-on" | "quota";

const availabilityBlockerLabel: Record<string, string> = {
  ARCHIVED_PRODUCT: "Une révision archivée ne peut plus être modifiée.",
  NO_CHANGE: "La configuration proposée est identique à la configuration actuelle.",
};

function previewStats(preview: PlanAvailabilityPreview | ProductVisibilityPreview) {
  if ("totalExtensions" in preview) {
    return [
      ["Extensions sélectionnables", `${preview.operatorSelectableBefore} → ${preview.operatorSelectableAfter}`],
      ["Visibles aux clients", `${preview.clientVisibleBefore} → ${preview.clientVisibleAfter}`],
      ["Extensions modifiées", String(preview.changedCount)],
      ["Abonnements concernés", String(preview.affectedSubscriptionCount)],
    ];
  }
  return [
    ["Forfaits compatibles", String(preview.compatiblePlanCount)],
    ["Visibles aux clients", `${preview.clientVisiblePlanCountBefore} → ${preview.clientVisiblePlanCountAfter}`],
  ];
}

export function CommercialAvailabilityPanel({ kind, product }: { kind: Kind; product: Product }) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const isPlan = kind === "plan";
  const previewPermission = isPlan
    ? adminPermissions.commercialPreviewPlanPolicy
    : kind === "add-on"
      ? adminPermissions.commercialPreviewAddOnVisibility
      : adminPermissions.commercialPreviewQuotaVisibility;
  const updatePermission = isPlan
    ? adminPermissions.commercialUpdatePlanPolicy
    : kind === "add-on"
      ? adminPermissions.commercialUpdateAddOnVisibility
      : adminPermissions.commercialUpdateQuotaVisibility;
  const [visibility, setVisibility] = useState<ProductSalesVisibility>(product.salesVisibility);
  const [extensionPolicy, setExtensionPolicy] = useState<PlanExtensionPolicy>(
    "extensionPolicy" in product ? product.extensionPolicy : "OPEN_COMPATIBLE",
  );
  const [reason, setReason] = useState("");
  const [preview, setPreview] = useState<PlanAvailabilityPreview | ProductVisibilityPreview | null>(null);
  const invalidate = () =>
    invalidateAdminCommercial(
      queryClient,
      kind === "plan"
        ? adminCommercialKeys.plans.all()
        : kind === "add-on"
          ? adminCommercialKeys.addOns.all()
          : adminCommercialKeys.quotaPackages.all(),
    );
  const previewMutation = useMutation({
    mutationFn: () =>
      isPlan
        ? adminApi.previewPlanAvailability(product.id, extensionPolicy, visibility)
        : kind === "add-on"
          ? adminApi.previewAddOnVisibility(product.id, visibility)
          : adminApi.previewQuotaPackageVisibility(product.id, visibility),
    onSuccess: setPreview,
    onError: (error) => toast.error(error instanceof Error ? error.message : "Prévisualisation impossible"),
  });
  const apply = useMutation<unknown, Error, void>({
    mutationFn: () => {
      if (!preview) throw new Error("Prévisualisez d’abord ce changement.");
      if (isPlan && "targetExtensionPolicy" in preview) {
        return adminApi.updatePlanAvailability(product.id, {
          expectedVersion: preview.expectedVersion,
          extensionPolicy: preview.targetExtensionPolicy,
          salesVisibility: preview.targetSalesVisibility,
          reason,
          previewToken: preview.previewToken,
        });
      }
      const productPreview = preview as ProductVisibilityPreview;
      const input = {
        expectedVersion: productPreview.expectedVersion,
        salesVisibility: productPreview.targetSalesVisibility,
        reason,
        previewToken: productPreview.previewToken,
      };
      return kind === "add-on"
        ? adminApi.updateAddOnVisibility(product.id, input)
        : adminApi.updateQuotaPackageVisibility(product.id, input);
    },
    onSuccess: () => {
      void invalidate();
      setPreview(null);
      setReason("");
      toast.success("Disponibilité mise à jour");
    },
    onError: (error) => {
      if (error instanceof ApiError && ["STALE_IMPACT_PREVIEW", "STALE_RESOURCE_VERSION"].includes(error.code)) {
        setPreview(null);
        void invalidate();
        toast.warning("Le catalogue a changé. Recalculez l’impact.");
        return;
      }
      toast.error(error instanceof Error ? error.message : "Mise à jour impossible");
    },
  });
  if (!session.can(previewPermission)) return <PermissionState />;
  const dirty =
    visibility !== product.salesVisibility ||
    (isPlan && "extensionPolicy" in product && extensionPolicy !== product.extensionPolicy);
  return (
    <section className="space-y-5 rounded-xl border bg-card p-5">
      <div>
        <h2 className="text-sm font-semibold">Disponibilité commerciale</h2>
        <p className="mt-1 text-xs text-muted-foreground">
          Prévisualisez l’effet avant de changer ce qui peut être vendu.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="commercial-availability-visibility">Visibilité</Label>
          <Select
            onValueChange={(value) => {
              setVisibility(value as ProductSalesVisibility);
              setPreview(null);
            }}
            value={visibility}
          >
            <SelectTrigger id="commercial-availability-visibility">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="PUBLIC">Catalogue public</SelectItem>
              <SelectItem value="DIRECT_ONLY">Attribution directe</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {isPlan ? (
          <div className="space-y-2">
            <Label htmlFor="commercial-availability-extension-policy">Extensions</Label>
            <Select
              onValueChange={(value) => {
                setExtensionPolicy(value as PlanExtensionPolicy);
                setPreview(null);
              }}
              value={extensionPolicy}
            >
              <SelectTrigger id="commercial-availability-extension-policy">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="CLOSED">Fermées</SelectItem>
                <SelectItem value="ALLOW_LIST">Liste autorisée</SelectItem>
                <SelectItem value="OPEN_COMPATIBLE">Toutes les extensions compatibles</SelectItem>
              </SelectContent>
            </Select>
          </div>
        ) : null}
      </div>
      <Button disabled={!dirty || previewMutation.isPending} onClick={() => previewMutation.mutate()} variant="outline">
        <ArrowClockwiseIcon />
        {previewMutation.isPending ? "Calcul…" : "Prévisualiser"}
      </Button>
      {preview ? (
        <div className="space-y-5 border-t pt-5" aria-live="polite">
          <dl className="grid gap-x-8 gap-y-3 sm:grid-cols-2">
            {previewStats(preview).map(([label, value]) => (
              <div className="flex justify-between gap-4 text-sm" key={label}>
                <dt className="text-muted-foreground">{label}</dt>
                <dd className="font-medium tabular-nums">{value}</dd>
              </div>
            ))}
          </dl>
          {preview.blockers.length ? (
            <ul className="space-y-1 text-sm text-warning">
              {preview.blockers.map((blocker) => (
                <li className="flex gap-2" key={blocker}>
                  <WarningCircleIcon className="mt-0.5 size-4 shrink-0" />
                  {availabilityBlockerLabel[blocker] ?? "Une condition de disponibilité n’est pas satisfaite."}
                </li>
              ))}
            </ul>
          ) : null}
          {session.can(updatePermission) ? (
            <div className="space-y-2">
              <Label htmlFor={`availability-reason-${product.id}`}>Motif</Label>
              <Input
                id={`availability-reason-${product.id}`}
                maxLength={500}
                onChange={(event) => setReason(event.target.value)}
                value={reason}
              />
              <div className="flex justify-end">
                <Button
                  disabled={!preview.applicable || !reason.trim() || apply.isPending}
                  onClick={() => apply.mutate()}
                >
                  <CheckIcon />
                  {apply.isPending ? "Application…" : "Appliquer"}
                </Button>
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

export function CommercialAvailabilityHistory({ productId }: { productId: string }) {
  const session = useAdminSession();
  const [page, setPage] = useState(0);
  const history = useQuery({
    queryKey: ["admin", "commercial", "availability-history", productId, page],
    queryFn: () => adminApi.commercialAvailabilityHistory(productId, page),
    enabled: session.can(adminPermissions.commercialReadHistory),
    placeholderData: keepPreviousData,
  });
  if (!session.can(adminPermissions.commercialReadHistory)) return <PermissionState />;
  if (history.isLoading) return <LoadingState />;
  if (history.isError) return <ErrorState retry={() => void history.refetch()} />;
  const data = history.data as PageResponse<CommercialAvailabilityHistoryEntry>;
  if (!data.content.length) return <EmptyState title="Aucun changement de disponibilité" />;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Changement</TableHead>
            <TableHead>Motif</TableHead>
            <TableHead>Opérateur</TableHead>
            <TableHead>Date</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.content.map((entry) => (
            <TableRow key={entry.id}>
              <TableCell>
                <span className="block font-medium">{entry.action}</span>
                <span className="mt-1 block text-xs text-muted-foreground">
                  {entry.previousSalesVisibility ? availabilityLabel[entry.previousSalesVisibility] : "—"} →{" "}
                  {entry.resultingSalesVisibility ? availabilityLabel[entry.resultingSalesVisibility] : "—"}
                  {entry.resultingExtensionPolicy ? ` · ${extensionPolicyLabel[entry.resultingExtensionPolicy]}` : ""}
                </span>
              </TableCell>
              <TableCell>{entry.reason ?? "—"}</TableCell>
              <TableCell>{entry.actorEmail ?? "Système"}</TableCell>
              <TableCell>
                <time dateTime={entry.occurredAt}>
                  {new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(
                    new Date(entry.occurredAt),
                  )}
                </time>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <PaginationBar
        page={data.page}
        totalElements={data.totalElements}
        totalPages={data.totalPages}
        onPageChange={setPage}
      />
    </section>
  );
}

const issueLabel: Record<string, string> = {
  PLAN_EXTENSIONS_CLOSED: "Extensions fermées sur ce forfait",
  NOT_ALLOW_LISTED: "Hors liste autorisée",
  PRODUCT_NOT_ACTIVE: "Révision non active",
  DIRECT_ONLY: "Attribution directe uniquement",
  PRICE_UNAVAILABLE: "Aucun tarif compatible",
  DEPENDENCY_UNAVAILABLE: "Dépendance indisponible",
  EXCLUDED_SELECTION: "Exclusion avec une sélection actuelle",
};

export function PlanCompatibilityPanel({ planId }: { planId: string }) {
  const session = useAdminSession();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [availability, setAvailability] = useState("all");
  const compatibility = useQuery({
    queryKey: ["admin", "commercial", "compatibility", planId, page, search, availability],
    queryFn: () =>
      adminApi.inspectPlanCompatibility(planId, {
        search: search || undefined,
        available: availability === "all" ? undefined : availability === "available",
        page,
      }),
    enabled: session.can(adminPermissions.commercialInspectCompatibility),
    placeholderData: keepPreviousData,
  });
  if (!session.can(adminPermissions.commercialInspectCompatibility)) return <PermissionState />;
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="grid gap-3 border-b p-4 sm:grid-cols-[1fr_190px]">
        <Input
          aria-label="Rechercher une extension"
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
          placeholder="Add-on ou pack…"
          value={search}
        />
        <Select
          onValueChange={(value) => {
            setAvailability(value);
            setPage(0);
          }}
          value={availability}
        >
          <SelectTrigger aria-label="Disponibilité">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Toutes</SelectItem>
            <SelectItem value="available">Sélectionnables</SelectItem>
            <SelectItem value="blocked">Bloquées</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {compatibility.isLoading ? (
        <div className="p-5">
          <LoadingState />
        </div>
      ) : compatibility.isError ? (
        <ErrorState retry={() => void compatibility.refetch()} />
      ) : compatibility.data?.content.length ? (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Extension</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Opérateur</TableHead>
                <TableHead>Client</TableHead>
                <TableHead>Diagnostic</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {compatibility.data.content.map((item: ExtensionCompatibility) => (
                <TableRow key={`${item.productType}-${item.productId}`}>
                  <TableCell>
                    <span className="font-medium">{item.name}</span>
                  </TableCell>
                  <TableCell>{item.productType === "ADD_ON" ? "Add-on" : "Pack"}</TableCell>
                  <TableCell>
                    <StatusText tone={item.operatorSelectable ? "success" : "warning"}>
                      {item.operatorSelectable ? "Sélectionnable" : "Bloqué"}
                    </StatusText>
                  </TableCell>
                  <TableCell>{item.clientCatalogVisible ? "Visible" : "Masqué"}</TableCell>
                  <TableCell className="max-w-sm text-xs text-muted-foreground">
                    {item.issues.length
                      ? item.issues.map((issue) => issueLabel[issue.reason] ?? "Condition non satisfaite").join(" · ")
                      : "Compatible"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <PaginationBar
            page={compatibility.data.page}
            totalElements={compatibility.data.totalElements}
            totalPages={compatibility.data.totalPages}
            onPageChange={setPage}
          />
        </>
      ) : (
        <EmptyState title="Aucune extension" description="Aucun produit ne correspond à ces filtres." />
      )}
    </section>
  );
}
