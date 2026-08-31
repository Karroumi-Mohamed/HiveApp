import { ArrowLeftIcon, ArrowRightIcon, PlusIcon, WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, Navigate, useBlocker, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  AccountDirectoryEntry,
  BillingCycle,
  CommercialChooserItem,
  CommercialSegmentProductType,
} from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import {
  billingCycleLabel,
  type CommercialSegmentDraft,
  draftFromCommercialSegment,
  emptyCommercialSegmentDraft,
  hasDraftErrors,
  segmentCurrentSubscriptionStatuses,
  segmentKind,
  segmentMutationMessage,
  segmentProductType,
  segmentSource,
  subscriptionStatusLabel,
  toCommercialSegmentWriteInput,
  validateCommercialSegmentDraft,
  validSegmentId,
} from "./commercial-segment-rules";

type EditorStep = "identity" | "audience" | "review";
const steps: Array<{ value: EditorStep; label: string }> = [
  { value: "identity", label: "Définition" },
  { value: "audience", label: "Audience" },
  { value: "review", label: "Révision" },
];

function FieldError({ children }: { children?: ReactNode }) {
  return children ? (
    <p className="text-xs text-destructive" role="alert">
      {children}
    </p>
  ) : null;
}

function SelectionToken({
  children,
  onRemove,
  removeLabel,
}: {
  children: ReactNode;
  onRemove: () => void;
  removeLabel: string;
}) {
  return (
    <span className="inline-flex max-w-full items-center gap-1.5 rounded-md border bg-muted/30 px-2.5 py-1 text-sm">
      <span className="truncate">{children}</span>
      <button
        aria-label={removeLabel}
        className="-my-2 -me-2 inline-flex size-9 items-center justify-center rounded-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        onClick={onRemove}
        type="button"
      >
        ×
      </button>
    </span>
  );
}

function AccountPicker({
  draft,
  onChange,
}: {
  draft: CommercialSegmentDraft;
  onChange: (next: CommercialSegmentDraft) => void;
}) {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [page, setPage] = useState(0);
  const canChoose = session.can(adminPermissions.segmentsChooseAccounts);
  const canResolve = session.can(adminPermissions.segmentsResolveAccountChoices);
  const choices = useQuery({
    queryKey: adminCommercialKeys.segments.accountChoices({ search: debounced, page }),
    queryFn: () =>
      adminApi.commercialSegmentAccountChoices({ query: debounced || undefined, active: true, page, size: 10 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.segmentsChooseAccounts),
  });
  const selected = useQuery({
    queryKey: adminCommercialKeys.segments.accountChoices({ selected: draft.explicitAccountIds }),
    queryFn: async () => {
      const batches: string[][] = [];
      for (let index = 0; index < draft.explicitAccountIds.length; index += 100) {
        batches.push(draft.explicitAccountIds.slice(index, index + 100));
      }
      return (await Promise.all(batches.map((ids) => adminApi.resolveCommercialSegmentAccountChoices(ids)))).flat();
    },
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.segmentsResolveAccountChoices,
      draft.explicitAccountIds.length > 0,
    ),
  });
  const labels = useMemo(() => new Map((selected.data ?? []).map((account) => [account.id, account])), [selected.data]);
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
        <p>La recherche des comptes est protégée par une permission distincte.</p>
        {draft.explicitAccountIds.length ? (
          <p className="mt-2 break-all text-xs">
            Sélection conservée{canResolve ? "" : " sans lecture d’identité"} :{" "}
            {draft.explicitAccountIds.map((id) => labels.get(id)?.name ?? id).join(", ")}
          </p>
        ) : null}
        {selected.isError ? (
          <p className="mt-2 text-xs text-warning">Les libellés conservés n’ont pas pu être relus.</p>
        ) : null}
      </div>
    );
  }
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="segment-account-search">Comptes actifs</Label>
        <Input
          id="segment-account-search"
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
            <li key={id}>
              <SelectionToken
                removeLabel={`Retirer ${labels.get(id)?.name ?? id}`}
                onRemove={() =>
                  onChange({
                    ...draft,
                    explicitAccountIds: draft.explicitAccountIds.filter((candidate) => candidate !== id),
                  })
                }
              >
                {labels.get(id)?.name ?? id}
              </SelectionToken>
            </li>
          ))}
        </ul>
      ) : null}
      {selected.isError ? (
        <p className="text-xs text-warning">Les noms n’ont pas pu être relus ; les identifiants restent conservés.</p>
      ) : null}
      {choices.isLoading ? <LoadingState rows={3} /> : null}
      {choices.isError ? <ErrorState retry={() => void choices.refetch()} /> : null}
      {choices.data ? (
        <div className="overflow-hidden rounded-lg border">
          <ul className="divide-y">
            {choices.data.content.map((account) => {
              const id = `segment-account-${account.id}`;
              const checked = draft.explicitAccountIds.includes(account.id);
              return (
                <li key={account.id}>
                  <Label
                    className="flex min-h-12 cursor-pointer items-center gap-3 px-3 py-2 font-normal hover:bg-muted/50"
                    htmlFor={id}
                  >
                    <Checkbox checked={checked} id={id} onCheckedChange={(next) => toggle(account, next === true)} />
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium">{account.name}</span>
                      <span className="block truncate text-xs text-muted-foreground">{account.slug}</span>
                    </span>
                  </Label>
                </li>
              );
            })}
          </ul>
          <div className="flex items-center justify-between border-t px-3 py-2 text-xs text-muted-foreground">
            <span>{choices.data.totalElements} compte(s)</span>
            <div className="flex gap-1">
              <Button
                disabled={choices.data.first}
                onClick={() => setPage((value) => value - 1)}
                size="sm"
                variant="ghost"
              >
                Précédent
              </Button>
              <Button
                disabled={choices.data.last}
                onClick={() => setPage((value) => value + 1)}
                size="sm"
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

function ToggleSet<T extends string>({
  label,
  options,
  selected,
  onChange,
}: {
  label: string;
  options: Array<{ value: T; label: string }>;
  selected: T[];
  onChange: (next: T[]) => void;
}) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">{label}</legend>
      <div className="flex flex-wrap gap-x-5 gap-y-2">
        {options.map((option) => {
          const id = `segment-${label}-${option.value}`.replace(/\s+/g, "-");
          return (
            <Label className="flex cursor-pointer items-center gap-2 font-normal" htmlFor={id} key={option.value}>
              <Checkbox
                checked={selected.includes(option.value)}
                id={id}
                onCheckedChange={(checked) =>
                  onChange(
                    checked === true ? [...selected, option.value] : selected.filter((value) => value !== option.value),
                  )
                }
              />
              {option.label}
            </Label>
          );
        })}
      </div>
    </fieldset>
  );
}

function CurrencyEditor({
  draft,
  onChange,
}: {
  draft: CommercialSegmentDraft;
  onChange: (next: CommercialSegmentDraft) => void;
}) {
  const [value, setValue] = useState("");
  const add = () => {
    const code = value.trim().toUpperCase();
    if (!/^[A-Z]{3}$/.test(code)) return;
    onChange({ ...draft, currencyCodes: [...new Set([...draft.currencyCodes, code])] });
    setValue("");
  };
  return (
    <div className="space-y-2">
      <Label htmlFor="segment-currency">Devises courantes</Label>
      <div className="flex gap-2">
        <Input
          id="segment-currency"
          maxLength={3}
          onChange={(event) => setValue(event.target.value.toUpperCase())}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              add();
            }
          }}
          placeholder="MAD"
          value={value}
        />
        <Button disabled={!/^[A-Z]{3}$/.test(value)} onClick={add} type="button" variant="outline">
          <PlusIcon />
          Ajouter
        </Button>
      </div>
      {draft.currencyCodes.length ? (
        <div className="flex flex-wrap gap-2">
          {draft.currencyCodes.map((code) => (
            <SelectionToken
              key={code}
              removeLabel={`Retirer la devise ${code}`}
              onRemove={() =>
                onChange({ ...draft, currencyCodes: draft.currencyCodes.filter((candidate) => candidate !== code) })
              }
            >
              {code}
            </SelectionToken>
          ))}
        </div>
      ) : null}
    </div>
  );
}

type ProductChoice = Pick<CommercialChooserItem, "id" | "code" | "name" | "revisionNumber" | "choiceState">;

function ProductPicker({
  draft,
  onChange,
}: {
  draft: CommercialSegmentDraft;
  onChange: (next: CommercialSegmentDraft) => void;
}) {
  const session = useAdminSession();
  const [type, setType] = useState<CommercialSegmentProductType>("ADD_ON");
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const permission =
    type === "PLAN"
      ? adminPermissions.plansChoose
      : type === "ADD_ON"
        ? adminPermissions.addOnsChoose
        : adminPermissions.quotaPackagesChoose;
  const choices = useQuery({
    queryKey: ["admin", "commercial", "segments", "product-choices", type, debounced],
    queryFn: async (): Promise<ProductChoice[]> => {
      if (type === "PLAN") return (await adminApi.planChoices({ search: debounced || undefined, size: 20 })).content;
      if (type === "ADD_ON") return (await adminApi.addOnChoices({ search: debounced || undefined, size: 20 })).content;
      return (await adminApi.quotaPackageChoices({ search: debounced || undefined, size: 20 })).content;
    },
    enabled: commercialQueryEnabled(session.can, permission),
  });
  const add = (choice: ProductChoice) => {
    if (draft.productHoldings.some((holding) => holding.type === type && holding.code === choice.code)) return;
    onChange({ ...draft, productHoldings: [...draft.productHoldings, { type, code: choice.code }] });
  };
  return (
    <div className="space-y-3">
      <div className="grid gap-2 sm:grid-cols-[180px_1fr]">
        <Select onValueChange={(value) => setType(value as CommercialSegmentProductType)} value={type}>
          <SelectTrigger aria-label="Type de produit">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(segmentProductType).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input
          aria-label="Rechercher un produit"
          maxLength={180}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Rechercher un produit actif…"
          value={search}
        />
      </div>
      {!session.can(permission) ? (
        <p className="text-xs text-muted-foreground">Votre rôle ne permet pas de parcourir ce catalogue.</p>
      ) : null}
      {choices.isError ? <ErrorState retry={() => void choices.refetch()} /> : null}
      {choices.data?.length ? (
        <ul className="max-h-48 divide-y overflow-y-auto rounded-lg border">
          {choices.data.map((choice) => (
            <li className="flex items-center gap-3 px-3 py-2" key={choice.id}>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{choice.name}</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {choice.code} · R{choice.revisionNumber}
                </span>
              </span>
              <Button
                disabled={draft.productHoldings.some(
                  (holding) => holding.type === type && holding.code === choice.code,
                )}
                onClick={() => add(choice)}
                size="sm"
                type="button"
                variant="ghost"
              >
                Ajouter
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
      {draft.productHoldings.length ? (
        <div className="flex flex-wrap gap-2">
          {draft.productHoldings.map((holding) => {
            const key = `${holding.type}:${holding.code}`;
            return (
              <SelectionToken
                key={key}
                removeLabel={`Retirer ${segmentProductType[holding.type]} ${holding.code}`}
                onRemove={() =>
                  onChange({
                    ...draft,
                    productHoldings: draft.productHoldings.filter(
                      (candidate) => `${candidate.type}:${candidate.code}` !== key,
                    ),
                  })
                }
              >
                {segmentProductType[holding.type]} · {holding.code}
              </SelectionToken>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}

function PlanRevisionPicker({
  draft,
  onChange,
}: {
  draft: CommercialSegmentDraft;
  onChange: (next: CommercialSegmentDraft) => void;
}) {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const choices = useQuery({
    queryKey: ["admin", "commercial", "segments", "plan-revision-choices", debounced],
    queryFn: () => adminApi.planChoices({ search: debounced || undefined, size: 20 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansChoose),
  });
  const selected = useQuery({
    queryKey: ["admin", "commercial", "segments", "selected-plan-revisions", draft.currentPlanRevisionIds],
    queryFn: () => adminApi.selectedPlanChoices(draft.currentPlanRevisionIds),
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.plansResolveChoices,
      draft.currentPlanRevisionIds.length > 0,
    ),
  });
  const labels = new Map((selected.data ?? []).map((plan) => [plan.id, plan]));
  return (
    <div className="space-y-3">
      <Label htmlFor="segment-plan-revision">Révisions de forfait courantes</Label>
      {session.can(adminPermissions.plansChoose) ? (
        <Input
          id="segment-plan-revision"
          maxLength={180}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Rechercher une révision…"
          value={search}
        />
      ) : (
        <p className="text-xs text-muted-foreground">Le catalogue des forfaits est protégé séparément.</p>
      )}
      {choices.isError ? <ErrorState retry={() => void choices.refetch()} /> : null}
      {choices.data?.content.length ? (
        <ul className="max-h-48 divide-y overflow-y-auto rounded-lg border">
          {choices.data.content.map((plan) => (
            <li className="flex items-center gap-3 px-3 py-2" key={plan.id}>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{plan.name}</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {plan.code} · R{plan.revisionNumber}
                </span>
              </span>
              <Button
                disabled={draft.currentPlanRevisionIds.includes(plan.id)}
                onClick={() =>
                  onChange({ ...draft, currentPlanRevisionIds: [...draft.currentPlanRevisionIds, plan.id] })
                }
                size="sm"
                type="button"
                variant="ghost"
              >
                Ajouter
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
      {draft.currentPlanRevisionIds.length ? (
        <div className="flex flex-wrap gap-2">
          {draft.currentPlanRevisionIds.map((id) => (
            <SelectionToken
              key={id}
              removeLabel={`Retirer le forfait ${labels.get(id)?.name ?? id}`}
              onRemove={() =>
                onChange({
                  ...draft,
                  currentPlanRevisionIds: draft.currentPlanRevisionIds.filter((candidate) => candidate !== id),
                })
              }
            >
              {labels.get(id)?.name ?? id}
            </SelectionToken>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function IdentityStep({
  draft,
  onChange,
  errors,
}: {
  draft: CommercialSegmentDraft;
  onChange: (next: CommercialSegmentDraft) => void;
  errors: ReturnType<typeof validateCommercialSegmentDraft>;
}) {
  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="grid gap-5 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="segment-name">Nom</Label>
          <Input
            id="segment-name"
            maxLength={180}
            onChange={(event) => onChange({ ...draft, name: event.target.value })}
            value={draft.name}
          />
          <FieldError>{errors.name}</FieldError>
        </div>
        <div className="space-y-2">
          <Label htmlFor="segment-source">Origine</Label>
          <Select
            onValueChange={(value) => onChange({ ...draft, source: value as typeof draft.source })}
            value={draft.source}
          >
            <SelectTrigger id="segment-source">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(segmentSource).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="segment-description">Description</Label>
        <Textarea
          id="segment-description"
          maxLength={1000}
          onChange={(event) => onChange({ ...draft, description: event.target.value })}
          rows={3}
          value={draft.description}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="segment-reason">Motif métier</Label>
        <Textarea
          id="segment-reason"
          maxLength={500}
          onChange={(event) => onChange({ ...draft, reason: event.target.value })}
          rows={3}
          value={draft.reason}
        />
        <FieldError>{errors.reason}</FieldError>
      </div>
      <fieldset className="space-y-3 border-t pt-5">
        <legend className="text-sm font-medium">Méthode de définition</legend>
        <div className="grid gap-3 sm:grid-cols-2">
          {Object.entries(segmentKind).map(([value, label]) => {
            const checked = draft.kind === value;
            return (
              <Label
                className={`cursor-pointer rounded-lg border p-4 font-normal ${checked ? "border-primary bg-primary/5" : "hover:bg-muted/30"}`}
                htmlFor={`segment-kind-${value}`}
                key={value}
              >
                <span className="flex items-start gap-3">
                  <input
                    checked={checked}
                    className="mt-1"
                    id={`segment-kind-${value}`}
                    name="segment-kind"
                    onChange={() => onChange({ ...draft, kind: value as typeof draft.kind })}
                    type="radio"
                  />
                  <span>
                    <span className="block font-medium">{label}</span>
                    <span className="mt-1 block text-xs text-muted-foreground">
                      {value === "EXPLICIT_ACCOUNTS"
                        ? "Liste revue de comptes précis."
                        : "Règles fermées évaluées depuis l’état courant."}
                    </span>
                  </span>
                </span>
              </Label>
            );
          })}
        </div>
      </fieldset>
    </div>
  );
}

function AudienceStep({
  draft,
  onChange,
  errors,
}: {
  draft: CommercialSegmentDraft;
  onChange: (next: CommercialSegmentDraft) => void;
  errors: ReturnType<typeof validateCommercialSegmentDraft>;
}) {
  if (draft.kind === "EXPLICIT_ACCOUNTS")
    return (
      <div className="mx-auto max-w-4xl space-y-4">
        <AccountPicker draft={draft} onChange={onChange} />
        <FieldError>{errors.audience}</FieldError>
      </div>
    );
  return (
    <div className="mx-auto max-w-5xl space-y-7">
      <Alert>
        <WarningCircleIcon />
        <AlertTitle>Logique déterministe</AlertTitle>
        <AlertDescription>
          Les valeurs d’un même bloc sont reliées par « ou ». Les blocs renseignés sont reliés par « et ».
        </AlertDescription>
      </Alert>
      <PlanRevisionPicker draft={draft} onChange={onChange} />
      <div className="grid gap-6 border-y py-6 lg:grid-cols-2">
        <div className="space-y-2">
          <ToggleSet
            label="Statuts d’abonnement"
            onChange={(subscriptionStatuses) => onChange({ ...draft, subscriptionStatuses })}
            options={segmentCurrentSubscriptionStatuses.map((value) => ({
              value,
              label: subscriptionStatusLabel[value],
            }))}
            selected={draft.subscriptionStatuses}
          />
          <FieldError>{errors.statuses}</FieldError>
        </div>
        <ToggleSet
          label="Cycles de facturation"
          onChange={(billingCycles) => onChange({ ...draft, billingCycles })}
          options={(Object.entries(billingCycleLabel) as Array<[BillingCycle, string]>).map(([value, label]) => ({
            value,
            label,
          }))}
          selected={draft.billingCycles}
        />
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <CurrencyEditor draft={draft} onChange={onChange} />
        <div className="space-y-2">
          <Label>Création du compte</Label>
          <div className="grid gap-2 sm:grid-cols-2">
            <div>
              <Label className="text-xs text-muted-foreground" htmlFor="segment-created-from">
                Depuis
              </Label>
              <Input
                id="segment-created-from"
                onChange={(event) => onChange({ ...draft, accountCreatedFrom: event.target.value })}
                type="datetime-local"
                value={draft.accountCreatedFrom}
              />
            </div>
            <div>
              <Label className="text-xs text-muted-foreground" htmlFor="segment-created-until">
                Avant
              </Label>
              <Input
                id="segment-created-until"
                onChange={(event) => onChange({ ...draft, accountCreatedUntil: event.target.value })}
                type="datetime-local"
                value={draft.accountCreatedUntil}
              />
            </div>
          </div>
          <FieldError>{errors.createdWindow}</FieldError>
        </div>
      </div>
      <div className="space-y-2 border-t pt-6">
        <h2 className="text-sm font-medium">Produits actuellement détenus</h2>
        <ProductPicker draft={draft} onChange={onChange} />
        <FieldError>{errors.products}</FieldError>
      </div>
      <FieldError>{errors.currencies}</FieldError>
      <FieldError>{errors.audience}</FieldError>
    </div>
  );
}

function ReviewStep({
  draft,
  errors,
}: {
  draft: CommercialSegmentDraft;
  errors: ReturnType<typeof validateCommercialSegmentDraft>;
}) {
  const criteriaCount =
    draft.currentPlanRevisionIds.length +
    draft.subscriptionStatuses.length +
    draft.currencyCodes.length +
    draft.billingCycles.length +
    draft.productHoldings.length +
    (draft.accountCreatedFrom ? 1 : 0) +
    (draft.accountCreatedUntil ? 1 : 0);
  return (
    <div className="mx-auto max-w-4xl space-y-6">
      {hasDraftErrors(errors) ? (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>Définition incomplète</AlertTitle>
          <AlertDescription>Revenez aux étapes précédentes et corrigez les champs signalés.</AlertDescription>
        </Alert>
      ) : null}
      <dl className="grid gap-x-8 gap-y-5 border-y py-6 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-xs text-muted-foreground">Nom</dt>
          <dd className="mt-1 font-medium">{draft.name || "—"}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Origine</dt>
          <dd className="mt-1 font-medium">{segmentSource[draft.source]}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Définition</dt>
          <dd className="mt-1 font-medium">{segmentKind[draft.kind]}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Audience configurée</dt>
          <dd className="mt-1 font-medium tabular-nums">
            {draft.kind === "EXPLICIT_ACCOUNTS"
              ? `${draft.explicitAccountIds.length} compte(s)`
              : `${criteriaCount} critère(s)`}
          </dd>
        </div>
      </dl>
      <div>
        <h2 className="text-sm font-semibold">Motif</h2>
        <p className="mt-2 text-sm text-muted-foreground">{draft.reason || "—"}</p>
      </div>
      {draft.kind === "TYPED_CRITERIA" ? (
        <p className="text-sm text-muted-foreground">
          Cette création enregistre seulement le brouillon. Le comptage, la vérification signée et l’activation restent
          des opérations distinctes.
        </p>
      ) : null}
    </div>
  );
}

function SegmentEditor({ existing }: { existing?: import("@/api/contracts").CommercialSegmentDetail }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const editorFocus = useRef<HTMLElement>(null);
  const initial = useRef(existing ? draftFromCommercialSegment(existing) : emptyCommercialSegmentDraft());
  const initialSerialized = useRef(JSON.stringify(initial.current));
  const expectedVersion = useRef(existing?.summary.version ?? 0);
  const completed = useRef(false);
  const [draft, setDraft] = useState(initial.current);
  const [step, setStep] = useState<EditorStep>("identity");
  const [submitted, setSubmitted] = useState(false);
  const [versionConflict, setVersionConflict] = useState(false);
  const errors = validateCommercialSegmentDraft(draft);
  const mutation = useMutation({
    mutationFn: () =>
      existing
        ? adminApi.updateCommercialSegment(existing.summary.id, {
            ...toCommercialSegmentWriteInput(draft),
            version: expectedVersion.current,
          })
        : adminApi.createCommercialSegment(toCommercialSegmentWriteInput(draft)),
    onSuccess: async (segment) => {
      completed.current = true;
      await invalidateAdminCommercial(queryClient, adminCommercialKeys.segments.all());
      toast.success(existing ? "Brouillon enregistré" : "Segment créé");
      navigate(`/admin/segments/${segment.summary.id}`, { replace: true });
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.code === "STALE_RESOURCE_VERSION") setVersionConflict(true);
      if (existing) {
        await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.segments.detail(existing.summary.id) });
      }
      toast.error(segmentMutationMessage(error));
    },
  });
  const reload = useMutation({
    mutationFn: () => {
      if (!existing) throw new Error("Aucun segment à recharger.");
      return adminApi.commercialSegment(existing.summary.id);
    },
    onSuccess: (fresh) => {
      const nextDraft = draftFromCommercialSegment(fresh);
      queryClient.setQueryData(adminCommercialKeys.segments.detail(fresh.summary.id), fresh);
      setDraft(nextDraft);
      initialSerialized.current = JSON.stringify(nextDraft);
      expectedVersion.current = fresh.summary.version;
      setVersionConflict(false);
      setSubmitted(false);
      setStep("identity");
      window.requestAnimationFrame(() => editorFocus.current?.focus());
      toast.success("Nouvelle version chargée");
    },
    onError: (error) => toast.error(segmentMutationMessage(error)),
  });
  const dirty = !completed.current && JSON.stringify(draft) !== initialSerialized.current;
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;
  const blocker = useBlocker(useCallback(() => dirtyRef.current && !completed.current, []));
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm("Quitter sans enregistrer ce segment ? Les changements saisis seront perdus."))
      blocker.proceed();
    else blocker.reset();
  }, [blocker]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const index = steps.findIndex((candidate) => candidate.value === step);
  const previous = steps.at(index - 1)?.value;
  const next = steps.at(index + 1)?.value;
  const submit = () => {
    setSubmitted(true);
    if (!hasDraftErrors(errors)) mutation.mutate();
  };
  return (
    <section aria-label="Éditeur de segment" className="space-y-6" ref={editorFocus} tabIndex={-1}>
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to={existing ? `/admin/segments/${existing.summary.id}` : "/admin/segments"}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          {existing ? "Segment" : "Segments"}
        </Link>
      </Button>
      <PageHeader title={existing ? "Modifier le brouillon" : "Nouveau segment de comptes"} />
      <SectionTabs
        ariaLabel="Étapes de définition"
        items={steps}
        onValueChange={(value) => setStep(value as EditorStep)}
        value={step}
      />
      <div className="min-h-[420px] py-2">
        {step === "identity" ? (
          <IdentityStep draft={draft} errors={submitted ? errors : {}} onChange={setDraft} />
        ) : step === "audience" ? (
          <AudienceStep draft={draft} errors={submitted ? errors : {}} onChange={setDraft} />
        ) : (
          <ReviewStep draft={draft} errors={errors} />
        )}
      </div>
      {versionConflict ? (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>Le brouillon a changé ailleurs</AlertTitle>
          <AlertDescription>
            Vos saisies ne seront pas appliquées sur la nouvelle version sans relecture.
            <Button
              className="mt-3"
              disabled={reload.isPending}
              onClick={() => reload.mutate()}
              size="sm"
              type="button"
              variant="outline"
            >
              {reload.isPending ? "Rechargement…" : "Recharger et abandonner mes modifications"}
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}
      <footer className="flex flex-col-reverse justify-between gap-3 border-t pt-5 sm:flex-row">
        <Button disabled={!previous} onClick={() => previous && setStep(previous)} type="button" variant="ghost">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Précédent
        </Button>
        {step === "review" ? (
          <Button disabled={mutation.isPending || versionConflict} onClick={submit}>
            {mutation.isPending ? "Enregistrement…" : existing ? "Enregistrer le brouillon" : "Créer le brouillon"}
          </Button>
        ) : (
          <Button disabled={!next} onClick={() => next && setStep(next)} type="button">
            Continuer
            <ArrowRightIcon className="rtl:rotate-180" />
          </Button>
        )}
      </footer>
    </section>
  );
}

export function AdminCommercialSegmentCreatePage() {
  const session = useAdminSession();
  return session.can(adminPermissions.segmentsCreate) ? <SegmentEditor /> : <PermissionState />;
}

export function AdminCommercialSegmentEditPage() {
  const session = useAdminSession();
  const { segmentId } = useParams();
  const id = validSegmentId(segmentId);
  const segment = useQuery({
    queryKey: adminCommercialKeys.segments.detail(id),
    queryFn: () => adminApi.commercialSegment(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.segmentsRead, Boolean(id)),
  });
  if (!session.can(adminPermissions.segmentsUpdateDraft)) return <PermissionState />;
  if (!id)
    return (
      <ErrorState
        description="L’identifiant présent dans l’adresse est invalide."
        title="Adresse de segment invalide"
      />
    );
  if (!session.can(adminPermissions.segmentsRead)) return <Navigate replace to={`/admin/segments/${id}`} />;
  if (segment.isLoading) return <LoadingState />;
  if (segment.isError || !segment.data)
    return <ErrorState retry={() => void segment.refetch()} title="Segment introuvable" />;
  if (!segment.data.summary.availableActions.includes("EDIT_DRAFT"))
    return <Navigate replace to={`/admin/segments/${id}`} />;
  return <SegmentEditor existing={segment.data} />;
}
