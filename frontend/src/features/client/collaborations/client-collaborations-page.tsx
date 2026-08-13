import { ArrowLeftIcon, CopyIcon, KeyIcon, PlusIcon } from "@phosphor-icons/react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { clientApi } from "@/api/client-api";
import type { Collaboration } from "@/api/contracts";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";

const date = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";

function InitiateDialog() {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [shareCode, setShareCode] = useState("");
  const [purpose, setPurpose] = useState("");
  const resolution = useQuery({
    queryKey: ["client", "collaboration", "resolve", shareCode],
    queryFn: () => clientApi.resolveShareCode(shareCode),
    enabled: shareCode.trim().length >= 6,
    retry: false,
  });
  const initiate = useMutation({
    mutationFn: () => clientApi.initiateCollaboration({ shareCode, purpose, requestedPermissionCodes: [] }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "collaborations"] });
      setOpen(false);
      toast.success("Demande envoyée");
    },
  });
  if (
    !session.can(clientPermissions.collaborationsRequest) ||
    !session.can(clientPermissions.collaborationsResolveShareCode)
  )
    return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button>
          <PlusIcon />
          Nouvelle demande
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Demander une collaboration</DialogTitle>
          <DialogDescription>
            Le code identifie une entreprise. Son propriétaire doit accepter explicitement la demande.
          </DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            initiate.mutate();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="share-code">Code de partage</Label>
            <Input
              id="share-code"
              onChange={(event) => setShareCode(event.target.value.trim())}
              required
              value={shareCode}
            />
          </div>
          {resolution.data ? (
            <div className="rounded-lg border bg-muted/40 p-3 text-sm">
              <p className="font-medium">{resolution.data.companyName}</p>
              <p className="text-xs text-muted-foreground">
                {resolution.data.providerAccountName} · {resolution.data.companyCountry}
              </p>
            </div>
          ) : null}
          <div className="space-y-2">
            <Label htmlFor="collaboration-purpose">Objet de la mission</Label>
            <Textarea
              id="collaboration-purpose"
              onChange={(event) => setPurpose(event.target.value)}
              required
              value={purpose}
            />
          </div>
          <div className="flex justify-end">
            <Button disabled={!resolution.data || !purpose.trim() || initiate.isPending} type="submit">
              Envoyer la demande
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function LifecycleDialog({
  collaboration,
  action,
}: {
  collaboration: Collaboration;
  action: "accept" | "reject" | "cancel-request" | "suspend" | "resume" | "revoke";
}) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [scheduleAction, setScheduleAction] = useState("none");
  const [scheduledAt, setScheduledAt] = useState("");
  const mutation = useMutation({
    mutationFn: () => {
      const input = {
        expectedVersion: collaboration.version,
        reason: reason || null,
        suspensionScheduledAt: action === "suspend" && scheduledAt ? new Date(scheduledAt).toISOString() : null,
        suspensionScheduleAction: action === "suspend" && scheduleAction !== "none" ? scheduleAction : null,
      };
      return action === "revoke"
        ? clientApi.revokeCollaboration(collaboration.id, input)
        : clientApi.transitionCollaboration(collaboration.id, action, input);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "collaborations"] });
      setOpen(false);
      toast.success("Collaboration mise à jour");
    },
  });
  const destructive = ["reject", "cancel-request", "suspend", "revoke"].includes(action);
  const label = {
    accept: "Accepter",
    reject: "Refuser",
    "cancel-request": "Annuler la demande",
    suspend: "Suspendre",
    resume: "Reprendre",
    revoke: "Révoquer",
  }[action];
  const requiredPermission = {
    accept: clientPermissions.collaborationsAccept,
    reject: clientPermissions.collaborationsReject,
    "cancel-request": clientPermissions.collaborationsCancelRequest,
    suspend: clientPermissions.collaborationsSuspend,
    resume: clientPermissions.collaborationsResume,
    revoke: clientPermissions.collaborationsRevoke,
  }[action];
  if (!session.can(requiredPermission)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button variant={destructive ? "destructive" : "outline"}>{label}</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{label}</DialogTitle>
          <DialogDescription>Cette transition est enregistrée avec son auteur et sa date.</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor={`reason-${action}`}>Motif</Label>
            <Textarea id={`reason-${action}`} onChange={(event) => setReason(event.target.value)} value={reason} />
          </div>
          {action === "suspend" ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>Suivi planifié</Label>
                <Select onValueChange={setScheduleAction} value={scheduleAction}>
                  <SelectTrigger aria-label="Suivi planifié">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">Aucun</SelectItem>
                    <SelectItem value="REVIEW">Révision</SelectItem>
                    <SelectItem value="AUTOMATIC_RESUME">Reprise automatique</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              {scheduleAction !== "none" ? (
                <div className="space-y-2">
                  <Label htmlFor="suspension-date">Date</Label>
                  <Input
                    id="suspension-date"
                    onChange={(event) => setScheduledAt(event.target.value)}
                    type="datetime-local"
                    value={scheduledAt}
                  />
                </div>
              ) : null}
            </div>
          ) : null}
          <div className="flex justify-end">
            <Button
              disabled={mutation.isPending || (action === "suspend" && scheduleAction !== "none" && !scheduledAt)}
              type="submit"
              variant={destructive ? "destructive" : "default"}
            >
              Confirmer
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function PermissionEditor({ collaboration }: { collaboration: Collaboration }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const [grants, catalog] = useQueries({
    queries: [
      {
        queryKey: ["client", "collaborations", collaboration.id, "grants"],
        queryFn: () => clientApi.collaborationPermissions(collaboration.id),
        enabled: session.can(clientPermissions.collaborationsReadPermissions),
      },
      {
        queryKey: ["client", "collaborations", collaboration.id, "catalog"],
        queryFn: () => clientApi.collaborationCatalog(collaboration.id),
        enabled: session.can(clientPermissions.collaborationsPermissionCatalog),
      },
    ],
  });
  const grant = useMutation({
    mutationFn: (code: string) =>
      clientApi.grantCollaborationPermission(collaboration.id, code, catalog.data?.registryVersion ?? ""),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "collaborations", collaboration.id] });
      toast.success("Permission accordée");
    },
  });
  const revoke = useMutation({
    mutationFn: (code: string) => clientApi.revokeCollaborationPermission(collaboration.id, code),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["client", "collaborations", collaboration.id] }),
  });
  const choices =
    catalog.data?.availableChoices.flatMap((module) => module.features.flatMap((feature) => feature.permissions)) ?? [];
  if (grants.isLoading || catalog.isLoading) return <LoadingState />;
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="border-b p-4">
          <h2 className="text-sm font-semibold">Accès configurés</h2>
        </div>
        {!grants.data?.length ? (
          <EmptyState title="Aucun accès" />
        ) : (
          <div className="divide-y">
            {grants.data.map((item) => (
              <div className="flex items-center justify-between gap-4 p-4" key={item.permissionCode}>
                <div>
                  <code className="text-xs">{item.permissionCode}</code>
                  <p className="mt-1 text-xs text-muted-foreground">{item.description}</p>
                </div>
                {session.can(clientPermissions.collaborationsRevokeGrant) ? (
                  <Button onClick={() => revoke.mutate(item.permissionCode)} size="sm" variant="ghost">
                    Retirer
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </section>
      {session.can(clientPermissions.collaborationsGrant) &&
      session.can(clientPermissions.collaborationsPermissionCatalog) ? (
        <section className="overflow-hidden rounded-xl border bg-card">
          <div className="border-b p-4">
            <h2 className="text-sm font-semibold">Catalogue délégable</h2>
          </div>
          <div className="max-h-[32rem] divide-y overflow-y-auto">
            {choices
              .filter(
                (item) =>
                  !grants.data?.some((grantItem) => grantItem.permissionCode === item.code && grantItem.configured),
              )
              .map((item) => (
                <button
                  className="flex w-full items-start justify-between gap-4 p-4 text-start hover:bg-muted/50"
                  key={item.code}
                  onClick={() => grant.mutate(item.code)}
                  type="button"
                >
                  <span>
                    <span className="block text-sm font-medium">{item.name}</span>
                    <code className="mt-1 block text-xs text-muted-foreground">{item.code}</code>
                  </span>
                  <PlusIcon className="mt-1 size-4" />
                </button>
              ))}
          </div>
        </section>
      ) : null}
    </div>
  );
}

function CollaborationDetail({ id }: { id: string }) {
  const session = useClientSession();
  const [tab, setTab] = useState("summary");
  const collaboration = useQuery({
    queryKey: ["client", "collaborations", id],
    queryFn: () => clientApi.collaboration(id),
  });
  if (collaboration.isLoading) return <LoadingState />;
  if (!collaboration.data) return <ErrorState retry={() => void collaboration.refetch()} />;
  const data = collaboration.data;
  const actions = data.allowedNextActions;
  return (
    <div className="space-y-7">
      <Button asChild size="sm" variant="ghost">
        <Link to="/app/collaborations">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Collaborations
        </Link>
      </Button>
      <PageHeader
        actions={
          <>
            {actions.includes("ACCEPT") ? <LifecycleDialog action="accept" collaboration={data} /> : null}
            {actions.includes("REJECT") ? <LifecycleDialog action="reject" collaboration={data} /> : null}
            {actions.includes("CANCEL_REQUEST") ? (
              <LifecycleDialog action="cancel-request" collaboration={data} />
            ) : null}
            {actions.includes("SUSPEND") ? <LifecycleDialog action="suspend" collaboration={data} /> : null}
            {actions.includes("RESUME") ? <LifecycleDialog action="resume" collaboration={data} /> : null}
            {actions.includes("REVOKE") ? <LifecycleDialog action="revoke" collaboration={data} /> : null}
          </>
        }
        title={data.companyName}
      />
      <SectionTabs
        items={[
          { label: "Résumé", value: "summary" },
          ...(session.can(clientPermissions.collaborationsReadPermissions)
            ? [
                {
                  label: `Accès (${data.grants.filter((grant) => grant.configured).length})`,
                  value: "permissions",
                },
              ]
            : []),
          { label: "Historique", value: "history" },
        ]}
        onValueChange={setTab}
        value={tab}
      />
      {tab === "permissions" ? (
        <PermissionEditor collaboration={data} />
      ) : tab === "history" ? (
        <section className="max-w-3xl divide-y rounded-xl border bg-card">
          {[
            ["Demandée", data.requestedAt],
            ["Acceptée", data.acceptedAt],
            ["Suspendue", data.suspendedAt],
            ["Reprise", data.resumedAt],
            ["Annulée", data.cancelledAt],
            ["Refusée", data.rejectedAt],
            ["Révoquée", data.revokedAt],
          ]
            .filter(([, value]) => value)
            .map(([label, value]) => (
              <div className="flex items-center justify-between gap-4 p-4" key={label}>
                <span className="text-sm font-medium">{label}</span>
                <time className="text-xs text-muted-foreground">{date(value ?? null)}</time>
              </div>
            ))}
        </section>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[1fr_0.8fr]">
          <section className="rounded-xl border bg-card p-5">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-sm font-semibold">
                  {data.clientAccountName} → {data.providerAccountName}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">{data.companyCountry}</p>
              </div>
              <StatusBadge
                tone={
                  data.status === "ACTIVE"
                    ? "success"
                    : data.status === "PENDING" || data.status === "SUSPENDED"
                      ? "warning"
                      : "danger"
                }
              >
                {data.status}
              </StatusBadge>
            </div>
            <p className="mt-5 text-sm leading-6">{data.purpose}</p>
          </section>
          <section className="rounded-xl border bg-card p-5">
            <h2 className="text-sm font-semibold">État d’accès</h2>
            <dl className="mt-4 space-y-3">
              <div className="flex justify-between gap-4 text-sm">
                <dt className="text-muted-foreground">Accès actifs</dt>
                <dd className="font-semibold tabular-nums">
                  {data.grants.filter((grant) => grant.currentlyActive).length}
                </dd>
              </div>
              <div className="flex justify-between gap-4 text-sm">
                <dt className="text-muted-foreground">Blocages</dt>
                <dd className="font-semibold tabular-nums">{data.accessBlockers.length}</dd>
              </div>
            </dl>
            {data.accessBlockers.length ? (
              <ul className="mt-4 list-disc space-y-1 ps-5 text-xs text-destructive">
                {data.accessBlockers.map((blocker) => (
                  <li key={blocker}>{blocker}</li>
                ))}
              </ul>
            ) : null}
          </section>
        </div>
      )}
    </div>
  );
}

function ShareCodes() {
  const session = useClientSession();
  const companyId = session.selectedCompanyId ?? session.companies[0]?.id ?? null;
  const queryClient = useQueryClient();
  const code = useQuery({
    queryKey: ["client", "collaborations", "share-code", companyId],
    queryFn: () => clientApi.shareCode(companyId ?? ""),
    enabled: Boolean(companyId),
  });
  const regenerate = useMutation({
    mutationFn: () => clientApi.regenerateShareCode(companyId ?? ""),
    onSuccess: (data) => {
      queryClient.setQueryData(["client", "collaborations", "share-code", companyId], data);
      toast.success("Nouveau code généré");
    },
  });
  const toggle = useMutation({
    mutationFn: (enabled: boolean) => clientApi.setShareCodeEnabled(companyId ?? "", enabled),
    onSuccess: (data) => queryClient.setQueryData(["client", "collaborations", "share-code", companyId], data),
  });
  if (!companyId) return <EmptyState title="Sélectionnez une entreprise" />;
  if (code.isLoading) return <LoadingState />;
  return (
    <section className="max-w-3xl rounded-xl border bg-card p-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-sm font-semibold">Code de partage</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Il identifie l’entreprise mais ne donne aucun accès sans acceptation.
          </p>
        </div>
        <StatusBadge tone={code.data?.enabled ? "success" : "neutral"}>
          {code.data?.enabled ? "Activé" : "Désactivé"}
        </StatusBadge>
      </div>
      {code.data?.shareCode ? (
        <div className="mt-5 flex items-center gap-2 rounded-lg border bg-muted/40 p-3">
          <code className="min-w-0 flex-1 break-all">{code.data.shareCode}</code>
          <Button
            aria-label="Copier"
            onClick={() => void navigator.clipboard.writeText(code.data?.shareCode ?? "")}
            size="icon-sm"
            variant="outline"
          >
            <CopyIcon />
          </Button>
        </div>
      ) : null}
      <div className="mt-5 flex flex-wrap gap-2">
        {session.can(clientPermissions.collaborationsRegenerateShareCode) ? (
          <Button onClick={() => regenerate.mutate()} variant="outline">
            <KeyIcon />
            {code.data?.shareCode ? "Régénérer" : "Générer"}
          </Button>
        ) : null}
        {code.data && session.can(clientPermissions.collaborationsManageShareCode) ? (
          <Button onClick={() => toggle.mutate(!code.data.enabled)} variant="outline">
            {code.data.enabled ? "Désactiver" : "Activer"}
          </Button>
        ) : null}
      </div>
      {code.data ? (
        <dl className="mt-6 grid gap-4 border-t pt-5 sm:grid-cols-2">
          <div>
            <dt className="text-xs text-muted-foreground">Résolutions</dt>
            <dd className="mt-1 font-semibold tabular-nums">{code.data.resolutionCount}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Demandes</dt>
            <dd className="mt-1 font-semibold tabular-nums">{code.data.requestCount}</dd>
          </div>
        </dl>
      ) : null}
    </section>
  );
}

function CollaborationList({ items, perspective }: { items: Collaboration[]; perspective: "outgoing" | "incoming" }) {
  if (!items.length) return <EmptyState title="Aucune collaboration" />;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Entreprise</TableHead>
          <TableHead>{perspective === "outgoing" ? "Prestataire" : "Client"}</TableHead>
          <TableHead>Objet</TableHead>
          <TableHead>Demandée</TableHead>
          <TableHead>Statut</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.id}>
            <TableCell>
              <Link className="font-medium hover:underline" to={`/app/collaborations/${item.id}`}>
                {item.companyName}
              </Link>
            </TableCell>
            <TableCell>{perspective === "outgoing" ? item.providerAccountName : item.clientAccountName}</TableCell>
            <TableCell className="max-w-xs truncate">{item.purpose}</TableCell>
            <TableCell>{date(item.requestedAt)}</TableCell>
            <TableCell>
              <StatusBadge
                tone={
                  item.status === "ACTIVE"
                    ? "success"
                    : item.status === "PENDING" || item.status === "SUSPENDED"
                      ? "warning"
                      : "danger"
                }
              >
                {item.status}
              </StatusBadge>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function ClientCollaborationsPage() {
  const session = useClientSession();
  const { collaborationId } = useParams();
  const [tab, setTab] = useState(() =>
    session.can(clientPermissions.collaborationsRead)
      ? "outgoing"
      : session.can(clientPermissions.incomingCollaborationsRead)
        ? "incoming"
        : "share-code",
  );
  const [outgoing, incoming] = useQueries({
    queries: [
      {
        queryKey: ["client", "collaborations", "outgoing"],
        queryFn: clientApi.collaborations,
        enabled: session.can(clientPermissions.collaborationsRead),
      },
      {
        queryKey: ["client", "collaborations", "incoming"],
        queryFn: clientApi.incomingCollaborations,
        enabled: session.can(clientPermissions.incomingCollaborationsRead),
      },
    ],
  });
  if (collaborationId) return <CollaborationDetail id={collaborationId} />;
  return (
    <div className="space-y-7">
      <PageHeader actions={tab === "outgoing" ? <InitiateDialog /> : undefined} title="Collaborations" />
      <SectionTabs
        items={[
          ...(session.can(clientPermissions.collaborationsRead) ? [{ label: "Demandées", value: "outgoing" }] : []),
          ...(session.can(clientPermissions.incomingCollaborationsRead)
            ? [{ label: "Reçues", value: "incoming" }]
            : []),
          ...(session.can(clientPermissions.collaborationsReadShareCode)
            ? [{ label: "Code de partage", value: "share-code" }]
            : []),
        ]}
        onValueChange={setTab}
        value={tab}
      />
      {tab === "share-code" ? (
        <ShareCodes />
      ) : outgoing.isLoading || incoming.isLoading ? (
        <LoadingState />
      ) : (
        <section className="overflow-hidden rounded-xl border bg-card">
          {tab === "outgoing" ? (
            <CollaborationList items={outgoing.data ?? []} perspective="outgoing" />
          ) : (
            <CollaborationList items={incoming.data ?? []} perspective="incoming" />
          )}
        </section>
      )}
    </div>
  );
}
