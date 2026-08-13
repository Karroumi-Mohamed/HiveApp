import {
  ArchiveIcon,
  ArrowDownIcon,
  ArrowUpIcon,
  CaretRightIcon,
  FloppyDiskIcon,
  PlusIcon,
  TrashIcon,
  UserPlusIcon,
} from "@phosphor-icons/react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useMemo, useState } from "react";
import { toast } from "sonner";
import { clientApi } from "@/api/client-api";
import type { OrganizationGroup } from "@/api/contracts";
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
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Textarea } from "@/components/ui/textarea";

function GroupForm({
  companyId,
  groups,
  group,
  initialParentId,
  onDone,
}: {
  companyId: string;
  groups: OrganizationGroup[];
  group?: OrganizationGroup;
  initialParentId?: string;
  onDone: () => void;
}) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const [name, setName] = useState(group?.name ?? "");
  const [description, setDescription] = useState(group?.description ?? "");
  const [parentId, setParentId] = useState(group?.parentId ?? initialParentId ?? "root");
  const [positions, setPositions] = useState(group?.positionSuggestions.join(", ") ?? "");
  const save = useMutation({
    mutationFn: async () => {
      const input = {
        name,
        description: description || null,
        positionSuggestions: positions
          .split(",")
          .map((value) => value.trim())
          .filter(Boolean),
      };
      if (!group)
        return clientApi.createGroup({ companyId, parentId: parentId === "root" ? null : parentId, ...input });
      const updated = await clientApi.updateGroup(group.id, input);
      const nextParent = parentId === "root" ? null : parentId;
      return nextParent !== group.parentId ? clientApi.moveGroup(group.id, nextParent) : updated;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "organization", companyId] });
      toast.success(group ? "Groupe mis à jour" : "Groupe créé");
      onDone();
    },
  });
  return (
    <form
      className="space-y-4"
      onSubmit={(event: FormEvent) => {
        event.preventDefault();
        save.mutate();
      }}
    >
      <div className="space-y-2">
        <Label htmlFor="group-name">Nom</Label>
        <Input id="group-name" onChange={(event) => setName(event.target.value)} required value={name} />
      </div>
      <div className="space-y-2">
        <Label>Groupe parent</Label>
        <Select
          disabled={Boolean(group) && !session.can(clientPermissions.organizationMove)}
          onValueChange={setParentId}
          value={parentId}
        >
          <SelectTrigger aria-label="Groupe parent">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="root">Aucun parent</SelectItem>
            {groups
              .filter((item) => item.id !== group?.id)
              .map((item) => (
                <SelectItem key={item.id} value={item.id}>
                  {item.name}
                </SelectItem>
              ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-2">
        <Label htmlFor="group-description">Description</Label>
        <Textarea id="group-description" onChange={(event) => setDescription(event.target.value)} value={description} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="group-positions">Postes suggérés</Label>
        <Input
          id="group-positions"
          onChange={(event) => setPositions(event.target.value)}
          placeholder="Responsable, Analyste, Assistant"
          value={positions}
        />
      </div>
      <div className="flex justify-end">
        <Button disabled={save.isPending} type="submit">
          <FloppyDiskIcon />
          Enregistrer
        </Button>
      </div>
    </form>
  );
}

function GroupDialog({
  companyId,
  groups,
  group,
  parentId,
}: {
  companyId: string;
  groups: OrganizationGroup[];
  group?: OrganizationGroup;
  parentId?: string;
}) {
  const session = useClientSession();
  const [open, setOpen] = useState(false);
  if (!session.can(group ? clientPermissions.organizationUpdate : clientPermissions.organizationCreate)) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        {group ? (
          <Button size="sm" variant="outline">
            Modifier
          </Button>
        ) : (
          <Button>
            <PlusIcon />
            Nouveau groupe
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{group ? "Modifier le groupe" : "Nouveau groupe"}</DialogTitle>
          <DialogDescription>La structure reflète l’organisation. Elle ne confère aucune permission.</DialogDescription>
        </DialogHeader>
        <GroupForm
          companyId={companyId}
          group={group}
          groups={groups}
          initialParentId={parentId}
          onDone={() => setOpen(false)}
        />
      </DialogContent>
    </Dialog>
  );
}

function MembershipPanel({
  companyId,
  group,
  open,
  onOpenChange,
}: {
  companyId: string;
  group: OrganizationGroup | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const [memberId, setMemberId] = useState("");
  const [position, setPosition] = useState("");
  const [members, placements] = useQueries({
    queries: [
      { queryKey: ["client", "members"], queryFn: clientApi.members, enabled: open },
      {
        queryKey: ["client", "organization", "members", group?.id],
        queryFn: () => clientApi.groupMembers(group?.id ?? ""),
        enabled: open && Boolean(group),
      },
    ],
  });
  const add = useMutation({
    mutationFn: () => clientApi.putGroupMember(group?.id ?? "", memberId, position || null),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "organization", "members", group?.id] });
      void queryClient.invalidateQueries({ queryKey: ["client", "organization", companyId] });
      setMemberId("");
      setPosition("");
      toast.success("Placement ajouté");
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => clientApi.removeGroupMember(group?.id ?? "", id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "organization"] });
      toast.success("Placement retiré");
    },
  });
  return (
    <Sheet onOpenChange={onOpenChange} open={open}>
      <SheetContent className="overflow-y-auto sm:max-w-xl">
        <SheetHeader>
          <SheetTitle>{group?.name}</SheetTitle>
          <SheetDescription>Membres directement placés dans ce groupe.</SheetDescription>
        </SheetHeader>
        <div className="mt-6 space-y-6">
          {session.can(clientPermissions.organizationPutMember) ? (
            <form
              className="grid gap-3 rounded-lg border p-4 sm:grid-cols-[1fr_1fr_auto]"
              onSubmit={(event) => {
                event.preventDefault();
                add.mutate();
              }}
            >
              <Select onValueChange={setMemberId} value={memberId}>
                <SelectTrigger aria-label="Membre">
                  <SelectValue placeholder="Membre" />
                </SelectTrigger>
                <SelectContent>
                  {members.data
                    ?.filter(
                      (member) =>
                        member.isActive && !placements.data?.some((placement) => placement.memberId === member.id),
                    )
                    .map((member) => (
                      <SelectItem key={member.id} value={member.id}>
                        {member.displayName}
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
              <Input
                aria-label="Poste"
                onChange={(event) => setPosition(event.target.value)}
                placeholder="Poste"
                value={position}
              />
              <Button disabled={!memberId || add.isPending} size="icon" title="Ajouter" type="submit">
                <UserPlusIcon />
              </Button>
            </form>
          ) : null}
          {placements.isLoading ? (
            <LoadingState rows={3} />
          ) : !placements.data?.length ? (
            <EmptyState title="Aucun membre placé" />
          ) : (
            <div className="divide-y rounded-lg border">
              {placements.data.map((placement) => (
                <div className="flex items-center justify-between gap-4 p-4" key={placement.id}>
                  <div>
                    <p className="text-sm font-medium">{placement.memberDisplayName}</p>
                    <p className="text-xs text-muted-foreground">{placement.positionTitle || "Aucun poste"}</p>
                  </div>
                  {session.can(clientPermissions.organizationRemoveMember) ? (
                    <Button
                      aria-label="Retirer"
                      onClick={() => remove.mutate(placement.memberId)}
                      size="icon-sm"
                      variant="ghost"
                    >
                      <TrashIcon />
                    </Button>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

function GroupTree({ companyId, groups }: { companyId: string; groups: OrganizationGroup[] }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<OrganizationGroup | null>(null);
  const children = useMemo(() => {
    const map = new Map<string | null, OrganizationGroup[]>();
    for (const group of groups) {
      const siblings = map.get(group.parentId) ?? [];
      siblings.push(group);
      map.set(group.parentId, siblings);
    }
    for (const siblings of map.values()) siblings.sort((a, b) => a.displayOrder - b.displayOrder);
    return map;
  }, [groups]);
  const lifecycle = useMutation({
    mutationFn: ({ group, action }: { group: OrganizationGroup; action: "archive" | "restore" | "delete" }) =>
      action === "archive"
        ? clientApi.archiveGroup(group.id)
        : action === "restore"
          ? clientApi.restoreGroup(group.id)
          : clientApi.deleteGroup(group.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "organization", companyId] });
      toast.success("Structure mise à jour");
    },
  });
  const reorder = useMutation({
    mutationFn: ({ group, direction }: { group: OrganizationGroup; direction: -1 | 1 }) => {
      const siblings = [...(children.get(group.parentId) ?? [])];
      const index = siblings.findIndex((item) => item.id === group.id);
      const target = index + direction;
      if (target < 0 || target >= siblings.length) return Promise.resolve();
      const current = siblings[index];
      const destination = siblings[target];
      if (!current || !destination) return Promise.resolve();
      siblings[index] = destination;
      siblings[target] = current;
      return clientApi.reorderGroups(
        companyId,
        group.parentId,
        siblings.map((item) => item.id),
      );
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["client", "organization", companyId] }),
  });
  const render = (parentId: string | null, level = 0): React.ReactNode =>
    (children.get(parentId) ?? []).map((group, index, siblings) => (
      <div key={group.id}>
        <div
          className="group flex min-h-14 items-center gap-2 border-b px-3 last:border-b-0"
          style={{ paddingInlineStart: `${12 + level * 24}px` }}
        >
          <CaretRightIcon
            className={`size-4 shrink-0 text-muted-foreground rtl:rotate-180 ${children.has(group.id) ? "" : "opacity-20"}`}
          />
          <button
            className="min-w-0 flex-1 text-start"
            onClick={() => session.can(clientPermissions.organizationListMemberships) && setSelected(group)}
            type="button"
          >
            <span className="block truncate text-sm font-medium">{group.name}</span>
            <span className="block text-xs text-muted-foreground">
              {group.directMemberCount} membre{group.directMemberCount === 1 ? "" : "s"}
            </span>
          </button>
          <StatusBadge tone={group.status === "ACTIVE" ? "success" : "neutral"}>
            {group.status === "ACTIVE" ? "Actif" : "Archivé"}
          </StatusBadge>
          <div className="hidden gap-1 group-hover:flex group-focus-within:flex">
            {session.can(clientPermissions.organizationReorder) ? (
              <>
                <Button
                  aria-label="Monter"
                  disabled={index === 0}
                  onClick={() => reorder.mutate({ group, direction: -1 })}
                  size="icon-sm"
                  variant="ghost"
                >
                  <ArrowUpIcon />
                </Button>
                <Button
                  aria-label="Descendre"
                  disabled={index === siblings.length - 1}
                  onClick={() => reorder.mutate({ group, direction: 1 })}
                  size="icon-sm"
                  variant="ghost"
                >
                  <ArrowDownIcon />
                </Button>
              </>
            ) : null}
            <GroupDialog companyId={companyId} group={group} groups={groups} />
            {session.can(
              group.status === "ACTIVE" ? clientPermissions.organizationArchive : clientPermissions.organizationRestore,
            ) ? (
              <Button
                aria-label={group.status === "ACTIVE" ? "Archiver" : "Restaurer"}
                onClick={() => lifecycle.mutate({ group, action: group.status === "ACTIVE" ? "archive" : "restore" })}
                size="icon-sm"
                variant="ghost"
              >
                <ArchiveIcon />
              </Button>
            ) : null}
            {session.can(clientPermissions.organizationDelete) ? (
              <Button
                aria-label="Supprimer"
                onClick={() => lifecycle.mutate({ group, action: "delete" })}
                size="icon-sm"
                variant="ghost"
              >
                <TrashIcon />
              </Button>
            ) : null}
          </div>
        </div>
        {render(group.id, level + 1)}
      </div>
    ));
  return (
    <>
      <div className="overflow-hidden rounded-xl border bg-card">{render(null)}</div>
      <MembershipPanel
        companyId={companyId}
        group={selected}
        onOpenChange={(open) => !open && setSelected(null)}
        open={Boolean(selected)}
      />
    </>
  );
}

function Templates({ companyId, groups }: { companyId: string; groups: OrganizationGroup[] }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const templates = useQuery({
    queryKey: ["client", "organization", "templates", companyId],
    queryFn: () => clientApi.templates(companyId),
  });
  const [source, setSource] = useState("");
  const [name, setName] = useState("");
  const create = useMutation({
    mutationFn: () => clientApi.createTemplate({ companyId, sourceGroupId: source, scope: "COMPANY", name }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "organization", "templates", companyId] });
      setName("");
      setSource("");
      toast.success("Modèle créé");
    },
  });
  const instantiate = useMutation({
    mutationFn: async (id: string) => {
      const preview = await clientApi.previewTemplate(id, companyId);
      if (!preview.canInstantiate) throw new Error(preview.conflicts.join(" · "));
      return clientApi.instantiateTemplate(id, companyId);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "organization", companyId] });
      toast.success("Structure créée depuis le modèle");
    },
    onError: (error) => toast.error(error.message),
  });
  const remove = useMutation({
    mutationFn: clientApi.deleteTemplate,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "organization", "templates", companyId] });
      toast.success("Modèle supprimé");
    },
  });
  return (
    <div
      className={
        session.can(clientPermissions.organizationCreateTemplate)
          ? "grid gap-6 lg:grid-cols-[0.75fr_1.25fr]"
          : "grid gap-6"
      }
    >
      {session.can(clientPermissions.organizationCreateTemplate) ? (
        <form
          className="space-y-4 rounded-xl border bg-card p-5"
          onSubmit={(event) => {
            event.preventDefault();
            create.mutate();
          }}
        >
          <h2 className="text-sm font-semibold">Créer un modèle</h2>
          <div className="space-y-2">
            <Label>Structure source</Label>
            <Select onValueChange={setSource} value={source}>
              <SelectTrigger aria-label="Structure source">
                <SelectValue placeholder="Groupe racine" />
              </SelectTrigger>
              <SelectContent>
                {groups.map((group) => (
                  <SelectItem key={group.id} value={group.id}>
                    {group.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="template-name">Nom</Label>
            <Input id="template-name" onChange={(event) => setName(event.target.value)} value={name} />
          </div>
          <Button disabled={!source || !name || create.isPending} type="submit">
            <FloppyDiskIcon />
            Enregistrer
          </Button>
        </form>
      ) : null}
      <section className="overflow-hidden rounded-xl border bg-card">
        {templates.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : !templates.data?.length ? (
          <EmptyState title="Aucun modèle" />
        ) : (
          <div className="divide-y">
            {templates.data.map((template) => (
              <div className="flex items-center justify-between gap-4 p-4" key={template.id}>
                <div>
                  <p className="text-sm font-medium">{template.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {template.nodeCount} groupes · {template.scope}
                  </p>
                </div>
                <div className="flex items-center gap-1">
                  {session.can(clientPermissions.organizationInstantiateTemplate) &&
                  session.can(clientPermissions.organizationPreviewTemplate) ? (
                    <Button onClick={() => instantiate.mutate(template.id)} size="sm" variant="outline">
                      Utiliser
                    </Button>
                  ) : null}
                  {session.can(clientPermissions.organizationDeleteTemplate) ? (
                    <Button
                      aria-label="Supprimer le modèle"
                      onClick={() => remove.mutate(template.id)}
                      size="icon-sm"
                      variant="ghost"
                    >
                      <TrashIcon />
                    </Button>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

export function ClientOrganizationPage() {
  const session = useClientSession();
  const [tab, setTab] = useState("structure");
  const companyId = session.selectedCompanyId ?? session.companies.find((company) => company.isActive)?.id ?? null;
  const groups = useQuery({
    queryKey: ["client", "organization", companyId],
    queryFn: () => clientApi.groups(companyId ?? ""),
    enabled: Boolean(companyId),
  });
  if (!companyId)
    return (
      <div className="space-y-7">
        <PageHeader title="Structure" />
        <EmptyState title="Créez ou sélectionnez une entreprise" />
      </div>
    );
  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          tab === "structure" && groups.data ? <GroupDialog companyId={companyId} groups={groups.data} /> : undefined
        }
        title="Structure"
      />
      <SectionTabs
        items={[
          { label: "Groupes", value: "structure" },
          ...(session.can(clientPermissions.organizationListTemplates)
            ? [{ label: "Modèles", value: "templates" }]
            : []),
        ]}
        onValueChange={setTab}
        value={tab}
      />
      {groups.isLoading ? (
        <LoadingState />
      ) : groups.isError ? (
        <ErrorState retry={() => void groups.refetch()} />
      ) : tab === "templates" ? (
        <Templates companyId={companyId} groups={groups.data ?? []} />
      ) : !groups.data?.length ? (
        <EmptyState action={<GroupDialog companyId={companyId} groups={[]} />} title="Aucun groupe" />
      ) : (
        <GroupTree companyId={companyId} groups={groups.data} />
      )}
    </div>
  );
}
