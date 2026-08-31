import { XIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { adminApi } from "@/api/admin-api";
import type { AccountDirectoryEntry } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { adminCommercialKeys, commercialQueryEnabled } from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";

const MAX_ACCOUNTS = 500;

export function SubscriptionJobAccountPicker({
  selectedIds,
  onChange,
}: {
  selectedIds: string[];
  onChange: (ids: string[]) => void;
}) {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const deferredSearch = useDebouncedValue(search);
  const [page, setPage] = useState(0);
  const canChoose = session.can(adminPermissions.subscriptionsChooseAccounts);
  const canResolve = session.can(adminPermissions.subscriptionsResolveAccountChoices);
  const choices = useQuery({
    queryKey: adminCommercialKeys.subscriptions.jobAccountChoices({ search: deferredSearch, page }),
    queryFn: () =>
      adminApi.chooseSubscriptionAccounts({
        query: deferredSearch || undefined,
        active: true,
        page,
        size: 10,
        sort: "name",
        direction: "asc",
      }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.subscriptionsChooseAccounts),
  });
  const selected = useQuery({
    queryKey: adminCommercialKeys.subscriptions.jobAccountChoices({ selected: selectedIds }),
    queryFn: async () => {
      const batches: string[][] = [];
      for (let index = 0; index < selectedIds.length; index += 100) batches.push(selectedIds.slice(index, index + 100));
      return (await Promise.all(batches.map((ids) => adminApi.resolveSubscriptionAccounts(ids)))).flat();
    },
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.subscriptionsResolveAccountChoices,
      selectedIds.length > 0,
    ),
  });
  const labels = useMemo(() => {
    const byId = new Map<string, AccountDirectoryEntry>();
    for (const account of choices.data?.content ?? []) byId.set(account.id, account);
    for (const account of selected.data ?? []) byId.set(account.id, account);
    return byId;
  }, [choices.data, selected.data]);

  const toggle = (account: AccountDirectoryEntry, checked: boolean) => {
    if (checked && selectedIds.length >= MAX_ACCOUNTS) return;
    onChange(checked ? [...new Set([...selectedIds, account.id])] : selectedIds.filter((id) => id !== account.id));
  };

  if (!canChoose) {
    return (
      <div className="border-y py-4 text-sm text-muted-foreground">
        La recherche des comptes est protégée séparément.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="flex-1 space-y-2">
          <Label htmlFor="subscription-job-account-search">Comptes actifs</Label>
          <Input
            id="subscription-job-account-search"
            maxLength={180}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Rechercher par nom ou identifiant…"
            value={search}
          />
        </div>
        <p className="pb-2 text-xs tabular-nums text-muted-foreground">
          {selectedIds.length} / {MAX_ACCOUNTS}
        </p>
      </div>

      {selectedIds.length ? (
        <ul aria-label="Comptes sélectionnés" className="flex flex-wrap gap-2">
          {selectedIds.map((id) => (
            <li
              className="inline-flex items-center gap-1 rounded-md border bg-muted/40 py-1 pe-1 ps-2 text-sm"
              key={id}
            >
              <span className="max-w-56 truncate">{labels.get(id)?.name ?? id}</span>
              <Button
                aria-label={`Retirer ${labels.get(id)?.name ?? id}`}
                onClick={() => onChange(selectedIds.filter((candidate) => candidate !== id))}
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
          Les noms n’ont pas pu être relus ; les identifiants restent sélectionnés.
        </p>
      ) : null}
      {choices.isLoading ? <LoadingState rows={3} /> : null}
      {choices.isError ? <ErrorState retry={() => void choices.refetch()} /> : null}
      {choices.data ? (
        <div className="overflow-hidden rounded-lg border">
          <ul className="divide-y">
            {choices.data.content.map((account) => {
              const inputId = `subscription-job-account-${account.id}`;
              const checked = selectedIds.includes(account.id);
              return (
                <li key={account.id}>
                  <Label
                    className="flex min-h-12 cursor-pointer items-center gap-3 px-3 py-2 font-normal hover:bg-muted/50"
                    htmlFor={inputId}
                  >
                    <Checkbox
                      checked={checked}
                      disabled={!checked && selectedIds.length >= MAX_ACCOUNTS}
                      id={inputId}
                      onCheckedChange={(value) => toggle(account, value === true)}
                    />
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
      {!canResolve && selectedIds.length ? (
        <p className="text-xs text-muted-foreground">
          Les identités déjà sélectionnées restent masquées par votre rôle.
        </p>
      ) : null}
    </div>
  );
}
