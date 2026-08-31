import { XIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { adminApi } from "@/api/admin-api";
import type {
  AccountDirectoryEntry,
  CommercialCampaignSegmentChoice,
  CommercialCampaignSegmentChoiceState,
} from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { type CommercialCampaignDraft, campaignSegmentStateLabel } from "./commercial-campaign-rules";

function AccountPicker({
  draft,
  onChange,
}: {
  draft: CommercialCampaignDraft;
  onChange: (next: CommercialCampaignDraft) => void;
}) {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [page, setPage] = useState(0);
  const canChoose = session.can(adminPermissions.campaignsChooseAccounts);
  const canResolve = session.can(adminPermissions.campaignsResolveAccountChoices);
  const choices = useQuery({
    queryKey: adminCommercialKeys.campaigns.accountChoices({ search: debounced, page }),
    queryFn: () =>
      adminApi.commercialCampaignAccountChoices({ query: debounced || undefined, active: true, page, size: 10 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsChooseAccounts),
  });
  const selected = useQuery({
    queryKey: adminCommercialKeys.campaigns.accountChoices({ selected: draft.explicitAccountIds }),
    queryFn: async () => {
      const batches: string[][] = [];
      for (let index = 0; index < draft.explicitAccountIds.length; index += 100) {
        batches.push(draft.explicitAccountIds.slice(index, index + 100));
      }
      return (await Promise.all(batches.map((ids) => adminApi.resolveCommercialCampaignAccountChoices(ids)))).flat();
    },
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.campaignsResolveAccountChoices,
      draft.explicitAccountIds.length > 0,
    ),
  });
  const labels = useMemo(() => {
    const merged = new Map<string, AccountDirectoryEntry>();
    for (const account of choices.data?.content ?? []) merged.set(account.id, account);
    for (const account of selected.data ?? []) merged.set(account.id, account);
    return merged;
  }, [choices.data, selected.data]);
  const toggle = (account: AccountDirectoryEntry, checked: boolean) =>
    onChange({
      ...draft,
      explicitAccountIds: checked
        ? [...new Set([...draft.explicitAccountIds, account.id])]
        : draft.explicitAccountIds.filter((id) => id !== account.id),
    });

  if (!canChoose) {
    return (
      <div className="border-y py-4 text-sm text-muted-foreground">
        <p>La recherche des comptes est protégée séparément.</p>
        {draft.explicitAccountIds.length ? (
          <p className="mt-2 break-all text-xs">
            Sélection conservée{canResolve ? "" : " sans identité"} :{" "}
            {draft.explicitAccountIds.map((id) => labels.get(id)?.name ?? id).join(", ")}
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="campaign-account-search">Comptes actifs</Label>
        <Input
          id="campaign-account-search"
          maxLength={180}
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
          placeholder="Rechercher par nom ou identifiant…"
          value={search}
        />
      </div>
      {draft.explicitAccountIds.length ? (
        <ul aria-label="Comptes sélectionnés" className="flex flex-wrap gap-2">
          {draft.explicitAccountIds.map((id) => (
            <li
              className="inline-flex items-center gap-1 rounded-md border bg-muted/40 py-1 pe-1 ps-2 text-sm"
              key={id}
            >
              <span className="max-w-56 truncate">{labels.get(id)?.name ?? id}</span>
              <Button
                aria-label={`Retirer ${labels.get(id)?.name ?? id}`}
                onClick={() =>
                  onChange({
                    ...draft,
                    explicitAccountIds: draft.explicitAccountIds.filter((candidate) => candidate !== id),
                  })
                }
                size="icon-sm"
                type="button"
                variant="ghost"
              >
                <XIcon />
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
      {selected.isError ? (
        <p className="text-xs text-warning">
          Les libellés n’ont pas pu être relus ; les identifiants restent conservés.
        </p>
      ) : null}
      {choices.isLoading ? <LoadingState rows={3} /> : null}
      {choices.isError ? <ErrorState retry={() => void choices.refetch()} /> : null}
      {choices.data ? (
        <div className="overflow-hidden rounded-lg border">
          <ul className="divide-y">
            {choices.data.content.map((account) => {
              const id = `campaign-account-${account.id}`;
              const checked = draft.explicitAccountIds.includes(account.id);
              return (
                <li key={account.id}>
                  <Label
                    className="flex min-h-12 cursor-pointer items-center gap-3 px-3 py-2 font-normal hover:bg-muted/50"
                    htmlFor={id}
                  >
                    <Checkbox checked={checked} id={id} onCheckedChange={(value) => toggle(account, value === true)} />
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium">{account.name}</span>
                      <span className="block truncate text-xs text-muted-foreground">{account.slug}</span>
                    </span>
                  </Label>
                </li>
              );
            })}
          </ul>
          {!choices.data.content.length ? (
            <p className="px-3 py-5 text-sm text-muted-foreground">Aucun compte actif trouvé.</p>
          ) : null}
          <div className="flex items-center justify-between border-t px-3 py-2 text-xs text-muted-foreground">
            <span>{choices.data.totalElements} compte(s)</span>
            <div className="flex gap-1">
              <Button
                disabled={choices.data.first}
                onClick={() => setPage((value) => value - 1)}
                size="sm"
                type="button"
                variant="ghost"
              >
                Précédent
              </Button>
              <Button
                disabled={choices.data.last}
                onClick={() => setPage((value) => value + 1)}
                size="sm"
                type="button"
                variant="ghost"
              >
                Suivant
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function segmentLabel(choice: CommercialCampaignSegmentChoice) {
  const count =
    choice.immutableAccountCount == null ? "audience indisponible" : `${choice.immutableAccountCount} compte(s)`;
  const activation = choice.activationNumber == null ? "activation inconnue" : `A${choice.activationNumber}`;
  return `${choice.name} · ${choice.code} · R${choice.revisionNumber} · ${activation} · ${count}`;
}

function SegmentPicker({
  draft,
  onChange,
  onStateChange,
}: {
  draft: CommercialCampaignDraft;
  onChange: (next: CommercialCampaignDraft) => void;
  onStateChange?: (state: CommercialCampaignSegmentChoiceState | undefined) => void;
}) {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [page, setPage] = useState(0);
  const canChoose = session.can(adminPermissions.campaignsChooseSegments);
  const canResolve = session.can(adminPermissions.campaignsResolveSegmentChoices);
  const choices = useQuery({
    queryKey: adminCommercialKeys.campaigns.segmentChoices({ search: debounced, page }),
    queryFn: () => adminApi.commercialCampaignSegmentChoices({ query: debounced || undefined, page, size: 20 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsChooseSegments),
  });
  const exact = useQuery({
    queryKey: adminCommercialKeys.campaigns.exactSegmentChoice(draft.segmentId, draft.segmentActivationId),
    queryFn: () => adminApi.resolveCommercialCampaignSegmentChoice(draft.segmentId, draft.segmentActivationId),
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.campaignsResolveSegmentChoices,
      Boolean(draft.segmentId && draft.segmentActivationId),
    ),
  });
  const options = useMemo(() => {
    const byActivation = new Map<string, CommercialCampaignSegmentChoice>();
    if (exact.data) byActivation.set(exact.data.activationId, exact.data);
    for (const choice of choices.data?.content ?? []) byActivation.set(choice.activationId, choice);
    return [...byActivation.values()];
  }, [choices.data, exact.data]);
  const retainedChoice = options.find((choice) => choice.activationId === draft.segmentActivationId);
  useEffect(() => {
    onStateChange?.(exact.data?.state ?? retainedChoice?.state);
  }, [exact.data?.state, onStateChange, retainedChoice?.state]);

  if (!canChoose) {
    return (
      <div className="border-y py-4 text-sm text-muted-foreground">
        <p>Votre rôle ne permet pas de parcourir les segments exécutables.</p>
        {draft.segmentId ? (
          <p className="mt-2 break-all text-xs">
            Référence conservée :{" "}
            {exact.data ? segmentLabel(exact.data) : `${draft.segmentId} / ${draft.segmentActivationId}`}
          </p>
        ) : null}
        {!canResolve && draft.segmentId ? (
          <p className="mt-1 text-xs">L’identité de cette activation est masquée.</p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="space-y-2">
        <Label htmlFor="campaign-segment-search">Segment actif avec audience figée</Label>
        <Input
          id="campaign-segment-search"
          maxLength={180}
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
          placeholder="Rechercher par nom ou code…"
          value={search}
        />
      </div>
      {choices.isLoading ? <LoadingState rows={3} /> : null}
      {choices.isError ? <ErrorState retry={() => void choices.refetch()} /> : null}
      {exact.isError ? (
        <p className="text-xs text-warning">
          L’activation conservée n’a pas pu être relue. Elle n’a pas été remplacée.
        </p>
      ) : null}
      {draft.segmentId && !retainedChoice ? (
        <p className="break-all text-xs text-muted-foreground">
          Activation conservée non résolue : {draft.segmentId} / {draft.segmentActivationId}
        </p>
      ) : null}
      {exact.data && exact.data.state !== "AVAILABLE" ? (
        <p className="text-sm text-warning" role="alert">
          {campaignSegmentStateLabel[exact.data.state]}
        </p>
      ) : null}
      <Select
        onValueChange={(activationId) => {
          const choice = options.find((candidate) => candidate.activationId === activationId);
          if (choice?.state === "AVAILABLE") {
            onChange({ ...draft, segmentId: choice.id, segmentActivationId: choice.activationId });
            onStateChange?.("AVAILABLE");
          }
        }}
        value={draft.segmentActivationId || undefined}
      >
        <SelectTrigger aria-label="Segment ciblé" className="w-full">
          <SelectValue placeholder="Choisir un segment exécutable" />
        </SelectTrigger>
        <SelectContent>
          {options.map((choice) => (
            <SelectItem
              disabled={choice.state !== "AVAILABLE"}
              key={`${choice.id}:${choice.activationId}`}
              value={choice.activationId}
            >
              {segmentLabel(choice)}
              {choice.state === "AVAILABLE" ? "" : " · indisponible"}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {choices.data ? (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>{choices.data.totalElements} segment(s) disponible(s)</span>
          <div className="flex gap-1">
            <Button
              disabled={choices.data.first}
              onClick={() => setPage((value) => value - 1)}
              size="sm"
              type="button"
              variant="ghost"
            >
              Précédent
            </Button>
            <Button
              disabled={choices.data.last}
              onClick={() => setPage((value) => value + 1)}
              size="sm"
              type="button"
              variant="ghost"
            >
              Suivant
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function CommercialCampaignAudienceEditor({
  draft,
  onChange,
  onSegmentStateChange,
}: {
  draft: CommercialCampaignDraft;
  onChange: (next: CommercialCampaignDraft) => void;
  onSegmentStateChange?: (state: CommercialCampaignSegmentChoiceState | undefined) => void;
}) {
  if (draft.audienceMode === "PUBLIC") {
    return (
      <p className="border-y py-5 text-sm text-muted-foreground">
        Aucune liste de comptes n’est stockée. L’audience reste publique et dynamique pendant la fenêtre de la campagne.
      </p>
    );
  }
  if (draft.audienceMode === "EXPLICIT_ACCOUNTS") return <AccountPicker draft={draft} onChange={onChange} />;
  return <SegmentPicker draft={draft} onChange={onChange} onStateChange={onSegmentStateChange} />;
}
