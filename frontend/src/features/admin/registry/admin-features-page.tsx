import { ArrowLeftIcon, MagnifyingGlassIcon, WarningIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { RegistryFeature } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { invalidateCommercialCatalogs } from "@/features/commercial/commercial-query";

type Control = "public-visibility" | "new-sales" | "new-grants" | "emergency-runtime";

const controlLabels: Record<string, string> = {
  PUBLIC_VISIBILITY: "Catalogue public",
  NEW_SALES: "Nouvelles ventes",
  NEW_GRANTS: "Nouvelles attributions",
  EMERGENCY_RUNTIME: "Accès runtime",
};

const dateTime = (value: string) =>
  new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));

function ControlDialog({
  feature,
  control,
  enabled,
  label,
  danger = false,
}: {
  feature: RegistryFeature;
  control: Control;
  enabled: boolean;
  label: string;
  danger?: boolean;
}) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [impact, setImpact] = useState(false);
  const [communication, setCommunication] = useState(false);
  const mutation = useMutation({
    mutationFn: () =>
      control === "emergency-runtime"
        ? adminApi.updateEmergencyRuntime(feature.id, !enabled, reason, impact, communication)
        : adminApi.updateFeatureControl(feature.id, control, !enabled, reason),
    onSuccess: () => {
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin", "registry"] }),
        invalidateCommercialCatalogs(queryClient),
      ]);
      toast.success("Contrôle opérationnel mis à jour");
      setOpen(false);
    },
  });
  const required =
    control === "public-visibility"
      ? adminPermissions.registryPublicVisibility
      : control === "new-sales"
        ? adminPermissions.registryNewSales
        : control === "new-grants"
          ? adminPermissions.registryNewGrants
          : adminPermissions.registryRuntime;
  if (!session.can(required))
    return (
      <div className="flex w-full items-center justify-between gap-4 rounded-lg border p-3 text-start">
        <span>
          <span className="block text-sm font-medium">{label}</span>
          <span className="block text-xs text-muted-foreground">{enabled ? "Autorisé" : "Bloqué"}</span>
        </span>
        <Switch checked={enabled} disabled />
      </div>
    );
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <button
          aria-label={`Modifier ${label}`}
          className="flex w-full items-center justify-between gap-4 rounded-lg border p-3 text-start hover:bg-muted/50"
          type="button"
        >
          <span>
            <span className="block text-sm font-medium">{label}</span>
            <span className="block text-xs text-muted-foreground">{enabled ? "Autorisé" : "Bloqué"}</span>
          </span>
          <Switch checked={enabled} className="pointer-events-none" />
        </button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {enabled ? "Bloquer" : "Autoriser"} — {label}
          </DialogTitle>
          <DialogDescription>
            {danger
              ? "Ce contrôle modifie immédiatement l’accès au runtime pour les abonnés existants."
              : "Cette modification est enregistrée avec son auteur et sa justification."}
          </DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor={`reason-${control}`}>Justification</Label>
            <Textarea
              id={`reason-${control}`}
              onChange={(event) => setReason(event.target.value)}
              required
              value={reason}
            />
          </div>
          {danger ? (
            <div className="space-y-3">
              <div className="flex items-start gap-3 rounded-lg border p-3">
                <Checkbox
                  id="impact-confirmed"
                  checked={impact}
                  onCheckedChange={(value) => setImpact(value === true)}
                />
                <Label className="font-normal" htmlFor="impact-confirmed">
                  J’ai examiné les comptes et fonctionnalités affectés.
                </Label>
              </div>
              <div className="flex items-start gap-3 rounded-lg border p-3">
                <Checkbox
                  id="communication-confirmed"
                  checked={communication}
                  onCheckedChange={(value) => setCommunication(value === true)}
                />
                <Label className="font-normal" htmlFor="communication-confirmed">
                  La communication opérationnelle a été préparée.
                </Label>
              </div>
            </div>
          ) : null}
          <div className="flex justify-end">
            <Button
              disabled={!reason.trim() || mutation.isPending || (danger && (!impact || !communication))}
              type="submit"
              variant={danger && enabled ? "destructive" : "default"}
            >
              Confirmer
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function FeatureDetail({ feature }: { feature: RegistryFeature }) {
  const session = useAdminSession();
  const history = useQuery({
    queryKey: ["admin", "registry", feature.id, "history"],
    queryFn: () => adminApi.featureHistory(feature.id),
    enabled: session.can(adminPermissions.registryControlHistory),
  });
  return (
    <div className="space-y-7">
      <Button asChild size="sm" variant="ghost">
        <Link to="/admin/features">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Fonctionnalités
        </Link>
      </Button>
      <PageHeader
        description={
          <span className="flex flex-wrap items-center gap-2">
            <code>{feature.code}</code>
            <StatusBadge tone={feature.runtimeEnabled ? "success" : "danger"}>
              {feature.runtimeEnabled ? "Runtime actif" : "Runtime bloqué"}
            </StatusBadge>
          </span>
        }
        title={feature.displayName}
      />
      <div className="grid gap-5 xl:grid-cols-[1fr_0.85fr]">
        <section className="rounded-xl border bg-card p-5">
          <h2 className="text-sm font-semibold">Contrôles opérationnels</h2>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <ControlDialog
              control="public-visibility"
              enabled={feature.publicVisible}
              feature={feature}
              label="Catalogue public"
            />
            <ControlDialog
              control="new-sales"
              enabled={feature.newSalesEnabled}
              feature={feature}
              label="Nouvelles ventes"
            />
            <ControlDialog
              control="new-grants"
              enabled={feature.newGrantsEnabled}
              feature={feature}
              label="Nouvelles attributions"
            />
            <ControlDialog
              control="emergency-runtime"
              danger
              enabled={feature.runtimeEnabled}
              feature={feature}
              label="Accès runtime"
            />
          </div>
        </section>
        <section className="rounded-xl border bg-card p-5">
          <h2 className="text-sm font-semibold">Propriétés</h2>
          <dl className="mt-5 space-y-3 text-sm">
            <Pair label="Surface" value={feature.surface} />
            <Pair label="Cycle" value={feature.status} />
            <Pair label="Assignable aux forfaits" value={feature.planAssignable ? "Oui" : "Non"} />
            <Pair label="Rôles clients" value={feature.clientRoleGrantable ? "Oui" : "Non"} />
            <Pair label="Délégation B2B" value={feature.b2bDelegatable ? "Oui" : "Non"} />
            <Pair label="Permissions" value={feature.permissions.length} />
          </dl>
        </section>
      </div>
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="border-b p-4">
          <h2 className="text-sm font-semibold">Permissions déclarées</h2>
        </div>
        {feature.permissions.length ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Permission</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Ressource</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {feature.permissions.map((permission) => (
                <TableRow key={permission.id}>
                  <TableCell>
                    <p className="font-medium">{permission.name}</p>
                    <code className="text-xs text-muted-foreground">{permission.code}</code>
                  </TableCell>
                  <TableCell>{permission.action}</TableCell>
                  <TableCell>{permission.resource}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <EmptyState title="Aucune permission" />
        )}
      </section>
      <section className="rounded-xl border bg-card p-5">
        <h2 className="text-sm font-semibold">Historique des contrôles</h2>
        {!session.can(adminPermissions.registryControlHistory) ? (
          <p className="mt-3 text-sm text-muted-foreground">Historique non disponible pour cet accès.</p>
        ) : history.isLoading ? (
          <div className="mt-4">
            <LoadingState rows={3} />
          </div>
        ) : history.isError ? (
          <ErrorState retry={() => void history.refetch()} />
        ) : history.data?.length ? (
          <div className="mt-4 divide-y border-y">
            {history.data.map((change) => (
              <article className="grid gap-2 py-4 sm:grid-cols-[11rem_1fr_auto] sm:items-start" key={change.id}>
                <div>
                  <p className="text-sm font-medium">{controlLabels[change.control] ?? change.control}</p>
                  <time className="text-xs text-muted-foreground" dateTime={change.createdAt}>
                    {dateTime(change.createdAt)}
                  </time>
                </div>
                <div>
                  <p className="text-sm">{change.reason}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Par <code>{change.actorUserId}</code>
                  </p>
                </div>
                <StatusBadge tone={change.newValue ? "success" : "danger"}>
                  {change.previousValue === change.newValue ? "Confirmé" : change.newValue ? "Autorisé" : "Bloqué"}
                </StatusBadge>
              </article>
            ))}
          </div>
        ) : (
          <p className="mt-3 text-sm text-muted-foreground">Aucune modification enregistrée.</p>
        )}
      </section>
    </div>
  );
}

export function AdminFeaturesPage() {
  const { featureId } = useParams();
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const [module, setModule] = useState("all");
  const inventory = useQuery({
    queryKey: ["admin", "registry", "inventory"],
    queryFn: adminApi.registryInventory,
    enabled: session.can(adminPermissions.registryRead),
  });
  const sync = useQuery({
    queryKey: ["admin", "registry", "sync"],
    queryFn: adminApi.latestRegistrySync,
    enabled: session.can(adminPermissions.registrySync),
    retry: false,
  });
  const features = useMemo(() => adminApi.flattenFeatures(inventory.data ?? []), [inventory.data]);
  const selected = features.find((feature) => feature.id === featureId);
  const filtered = features.filter(
    (feature) =>
      (module === "all" || feature.moduleCode === module) &&
      `${feature.code} ${feature.displayName}`.toLowerCase().includes(search.toLowerCase()),
  );
  if (featureId && inventory.isLoading) return <LoadingState />;
  if (featureId && inventory.isError) return <ErrorState retry={() => void inventory.refetch()} />;
  if (featureId && selected) return <FeatureDetail feature={selected} />;
  if (featureId && !selected) return <ErrorState title="Fonctionnalité introuvable" />;
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          sync.data ? (
            <StatusBadge tone={sync.data.status === "SUCCEEDED" ? "success" : "danger"}>
              {sync.data.status === "SUCCEEDED"
                ? `${sync.data.discoveredFeatures} synchronisées`
                : "Synchronisation en échec"}
            </StatusBadge>
          ) : sync.isError ? (
            <Button onClick={() => void sync.refetch()} size="sm" variant="outline">
              Synchronisation indisponible · Réessayer
            </Button>
          ) : undefined
        }
        title="Fonctionnalités plateforme"
      />
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="flex flex-col gap-3 border-b p-4 sm:flex-row">
          <div className="relative flex-1 sm:max-w-sm">
            <MagnifyingGlassIcon
              aria-hidden="true"
              className="absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            />
            <Input
              aria-label="Rechercher des fonctionnalités"
              className="ps-9"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Code ou nom…"
              value={search}
            />
          </div>
          <Select onValueChange={setModule} value={module}>
            <SelectTrigger aria-label="Module" className="w-full sm:w-48">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tous les modules</SelectItem>
              {(inventory.data ?? []).map((item) => (
                <SelectItem key={item.code} value={item.code}>
                  {item.code}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {inventory.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : inventory.isError ? (
          <ErrorState retry={() => void inventory.refetch()} />
        ) : !filtered.length ? (
          <EmptyState title="Aucune fonctionnalité" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Fonctionnalité</TableHead>
                <TableHead>Surface</TableHead>
                <TableHead>Ventes</TableHead>
                <TableHead>Attributions</TableHead>
                <TableHead>Runtime</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((feature) => (
                <TableRow key={feature.id}>
                  <TableCell>
                    <Link className="block" to={`/admin/features/${feature.id}`}>
                      <span className="font-medium">{feature.displayName}</span>
                      <code className="mt-1 block text-xs text-muted-foreground">{feature.code}</code>
                    </Link>
                  </TableCell>
                  <TableCell>{feature.surface}</TableCell>
                  <TableCell>
                    <StatusBadge tone={feature.newSalesEnabled ? "success" : "neutral"}>
                      {feature.newSalesEnabled ? "Ouvertes" : "Bloquées"}
                    </StatusBadge>
                  </TableCell>
                  <TableCell>
                    <StatusBadge tone={feature.newGrantsEnabled ? "success" : "neutral"}>
                      {feature.newGrantsEnabled ? "Ouvertes" : "Bloquées"}
                    </StatusBadge>
                  </TableCell>
                  <TableCell>
                    {feature.runtimeEnabled ? (
                      <StatusBadge tone="success">Actif</StatusBadge>
                    ) : (
                      <StatusBadge tone="danger">
                        <WarningIcon />
                        Bloqué
                      </StatusBadge>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>
    </div>
  );
}

function Pair({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}
