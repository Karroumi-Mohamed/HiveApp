import {
  ArrowLeftIcon,
  ArrowRightIcon,
  CheckIcon,
  PlusIcon,
  TrashIcon,
  WarningCircleIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, Navigate, useBlocker, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type {
  AccountDirectoryEntry,
  CommercialChooserItem,
  CommercialPolicyDetail,
  CommercialPolicyEffectType,
  CommercialPolicyProductType,
  CommercialPolicySegmentChoice,
  PageResponse,
  RegistryFeature,
} from "@/api/contracts";
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
import { statusText } from "@/features/admin/plans/plan-presentation";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { CommercialPolicyExplicitApplicationNotice } from "./commercial-policy-application-notice";
import { validPolicyIdentity } from "./commercial-policy-detail-state";
import {
  type CommercialPolicyDraft,
  type CommercialPolicyDraftEffect,
  draftFromPolicy,
  effectLabel,
  emptyCommercialPolicyDraft,
  isPolicyQuotaFeatureChoice,
  isPolicyVersionConflict,
  newDraftEffect,
  policyMutationMessage,
  policySource,
  policyTarget,
  toCommercialPolicyWriteInput,
  validateCommercialPolicyDraft,
} from "./commercial-policy-rules";

type EditorStep = "target" | "terms" | "effects" | "review";
const steps: Array<{ value: EditorStep; label: string }> = [
  { value: "target", label: "Cible" },
  { value: "terms", label: "Cadre" },
  { value: "effects", label: "Effets" },
  { value: "review", label: "Révision" },
];

const effectTypes = Object.keys(effectLabel) as CommercialPolicyEffectType[];
const productTypeLabel: Record<CommercialPolicyProductType, string> = {
  PLAN: "Forfait",
  ADD_ON: "Add-on",
  QUOTA_PACKAGE: "Pack de capacité",
};

function draftEffectSummary(effect: CommercialPolicyDraftEffect) {
  if (effect.type === "FIXED_SUBSCRIPTION_PRICE") {
    return `${effect.amount || "—"} ${effect.currencyCode} · ${effect.billingCycle === "YEARLY" ? "annuel" : "mensuel"}`;
  }
  if (effect.type === "FIXED_DISCOUNT") return `−${effect.amount || "—"} ${effect.currencyCode}`;
  if (effect.type === "PERCENTAGE_DISCOUNT") {
    return `−${effect.percentage || "—"}% · plafond ${effect.maximumAmount || "—"} ${effect.maximumCurrencyCode}`;
  }
  if (effect.type === "ADDITIVE_QUOTA_BONUS") {
    return `${effect.featureCode || "Fonctionnalité manquante"} · +${effect.quantityDelta || "—"} ${effect.quotaResource}`;
  }
  if (effect.type === "BLOCK_FEATURE") return effect.featureCode || "Fonctionnalité manquante";
  return `${effect.productType ? productTypeLabel[effect.productType] : "Type de produit manquant"} · ${effect.productRevisionId ? "révision sélectionnée" : "révision manquante"}`;
}

function reviewDate(value: string) {
  if (!value) return "sans fin";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(parsed);
}

function FieldError({ children }: { children?: ReactNode }) {
  return children ? (
    <p className="text-xs text-destructive" role="alert">
      {children}
    </p>
  ) : null;
}

function AccountTargetEditor({
  draft,
  onChange,
}: {
  draft: CommercialPolicyDraft;
  onChange: (next: CommercialPolicyDraft) => void;
}) {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [page, setPage] = useState(0);
  const canChoose = session.can(adminPermissions.commercialPoliciesChooseAccounts);
  const canResolve = session.can(adminPermissions.commercialPoliciesResolveAccountChoices);
  const selectedIds = draft.targetKind === "ACCOUNT" ? (draft.accountId ? [draft.accountId] : []) : draft.accountIds;
  const choices = useQuery({
    queryKey: adminCommercialKeys.policies.accountChoices({ search: debounced, page }),
    queryFn: () =>
      adminApi.commercialPolicyAccountChoices({ query: debounced || undefined, active: true, page, size: 10 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesChooseAccounts),
  });
  const selected = useQuery({
    queryKey: [...adminCommercialKeys.policies.accountChoices({ selected: true }), selectedIds],
    queryFn: async () => {
      const batches: string[][] = [];
      for (let index = 0; index < selectedIds.length; index += 100) batches.push(selectedIds.slice(index, index + 100));
      return (await Promise.all(batches.map((ids) => adminApi.resolveCommercialPolicyAccountChoices(ids)))).flat();
    },
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.commercialPoliciesResolveAccountChoices,
      selectedIds.length > 0,
    ),
  });
  const selectedById = useMemo(
    () => new Map((selected.data ?? []).map((account) => [account.id, account])),
    [selected.data],
  );

  if (!canChoose) {
    return (
      <div className="space-y-2 rounded-lg border border-warning/30 bg-warning/5 p-4 text-sm text-muted-foreground">
        <p>Votre rôle permet de définir la politique, mais pas de parcourir les comptes cibles.</p>
        {selectedIds.length ? (
          <p className="break-all text-xs">
            Sélection conservée{canResolve ? "" : " sans lecture d’identité"} :{" "}
            {selectedIds
              .slice(0, 3)
              .map((id) => selectedById.get(id)?.name ?? id)
              .join(", ")}
            {selectedIds.length > 3 ? ` et ${selectedIds.length - 3} autre(s)` : ""}
          </p>
        ) : null}
        {selected.isError ? (
          <p className="text-xs text-warning">Les libellés conservés n’ont pas pu être relus.</p>
        ) : null}
      </div>
    );
  }
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="policy-account-search">Rechercher un compte actif</Label>
        <Input
          id="policy-account-search"
          maxLength={180}
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
          placeholder="Nom ou identifiant du compte…"
          value={search}
        />
      </div>
      {selectedIds.length ? (
        <ul className="flex flex-wrap gap-2" aria-label="Comptes sélectionnés">
          {selectedIds.map((id) => (
            <li key={id}>
              <Button
                onClick={() =>
                  onChange({
                    ...draft,
                    accountId: draft.targetKind === "ACCOUNT" ? "" : draft.accountId,
                    accountIds: draft.accountIds.filter((candidate) => candidate !== id),
                  })
                }
                size="sm"
                title="Retirer de la sélection"
                type="button"
                variant="outline"
              >
                {selectedById.get(id)?.name ?? id}
                <span aria-hidden>×</span>
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
      {selected.isError ? (
        <p className="text-xs text-warning" role="status">
          Les noms sélectionnés n’ont pas pu être relus ; leurs identifiants restent conservés.
        </p>
      ) : null}
      {choices.isLoading ? <LoadingState rows={3} /> : null}
      {choices.isError ? <ErrorState retry={() => void choices.refetch()} /> : null}
      {choices.data ? (
        <div className="overflow-hidden rounded-lg border">
          <ul className="divide-y">
            {choices.data.content.map((account: AccountDirectoryEntry) => {
              const checked = selectedIds.includes(account.id);
              const checkboxId = `commercial-policy-account-${account.id}`;
              return (
                <li key={account.id}>
                  <Label
                    className="flex min-h-12 cursor-pointer items-center gap-3 px-3 py-2 font-normal hover:bg-muted/50"
                    htmlFor={checkboxId}
                  >
                    <Checkbox
                      checked={checked}
                      id={checkboxId}
                      onCheckedChange={(next) => {
                        if (draft.targetKind === "ACCOUNT") {
                          onChange({ ...draft, accountId: next === true ? account.id : "" });
                          return;
                        }
                        onChange({
                          ...draft,
                          accountIds:
                            next === true
                              ? [...new Set([...draft.accountIds, account.id])]
                              : draft.accountIds.filter((id) => id !== account.id),
                        });
                      }}
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
          <div className="flex items-center justify-between border-t px-3 py-2 text-xs text-muted-foreground">
            <span>{choices.data.totalElements} compte(s)</span>
            <div className="flex gap-1">
              <Button disabled={page === 0} onClick={() => setPage((value) => value - 1)} size="sm" variant="ghost">
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

function PlanTargetEditor({
  draft,
  onChange,
}: {
  draft: CommercialPolicyDraft;
  onChange: (next: CommercialPolicyDraft) => void;
}) {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [page, setPage] = useState(0);
  const plans = useQuery({
    queryKey: [...adminCommercialKeys.plans.all(), "policy-target", debounced, page],
    queryFn: () => adminApi.planChoices({ search: debounced || undefined, page, size: 20 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansChoose),
  });
  const selectedPlan = useQuery({
    queryKey: [...adminCommercialKeys.plans.all(), "policy-target-selected", draft.planRevisionId],
    queryFn: () => adminApi.selectedPlanChoices([draft.planRevisionId]),
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansResolveChoices, Boolean(draft.planRevisionId)),
  });
  const planOptions = useMemo(() => {
    const byId = new Map((selectedPlan.data ?? []).map((plan) => [plan.id, plan]));
    for (const plan of plans.data?.content ?? []) byId.set(plan.id, plan);
    return [...byId.values()];
  }, [plans.data, selectedPlan.data]);
  if (!session.can(adminPermissions.plansChoose)) {
    const retained = planOptions.find((plan) => plan.id === draft.planRevisionId);
    return (
      <div className="space-y-1 text-sm text-muted-foreground">
        <p>Votre rôle ne permet pas de choisir une révision de forfait.</p>
        {draft.planRevisionId ? (
          <p className="break-all text-xs">
            Révision conservée :{" "}
            {retained ? `${retained.name} · ${retained.code} · R${retained.revisionNumber}` : draft.planRevisionId}
          </p>
        ) : null}
        {selectedPlan.isError ? (
          <p className="text-xs text-warning">Le libellé conservé n’a pas pu être relu.</p>
        ) : null}
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <Input
        aria-label="Rechercher un forfait"
        maxLength={180}
        onChange={(event) => {
          setSearch(event.target.value);
          setPage(0);
        }}
        placeholder="Rechercher un forfait…"
        value={search}
      />
      {plans.isLoading ? <LoadingState rows={3} /> : null}
      {plans.isError ? <ErrorState retry={() => void plans.refetch()} /> : null}
      {selectedPlan.isError ? (
        <p className="text-xs text-warning" role="status">
          La révision sélectionnée reste conservée, mais son libellé n’a pas pu être relu.
        </p>
      ) : null}
      <Select
        onValueChange={(value) => onChange({ ...draft, planRevisionId: value })}
        value={draft.planRevisionId || undefined}
      >
        <SelectTrigger className="w-full" aria-label="Révision de forfait ciblée">
          <SelectValue placeholder="Choisir une révision" />
        </SelectTrigger>
        <SelectContent>
          {planOptions.map((plan) => (
            <SelectItem key={plan.id} value={plan.id}>
              {plan.name} · {plan.code} · R{plan.revisionNumber} · {statusText[plan.status]}
            </SelectItem>
          ))}
          {draft.planRevisionId && !planOptions.some((plan) => plan.id === draft.planRevisionId) ? (
            <SelectItem value={draft.planRevisionId}>Révision conservée · identité non résolue</SelectItem>
          ) : null}
        </SelectContent>
      </Select>
      {plans.data ? (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>{plans.data.totalElements} révision(s)</span>
          <div className="flex gap-1">
            <Button disabled={plans.data.first} onClick={() => setPage((value) => value - 1)} size="sm" variant="ghost">
              Précédent
            </Button>
            <Button disabled={plans.data.last} onClick={() => setPage((value) => value + 1)} size="sm" variant="ghost">
              Suivant
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function SegmentTargetEditor({
  draft,
  onChange,
}: {
  draft: CommercialPolicyDraft;
  onChange: (next: CommercialPolicyDraft) => void;
}) {
  const session = useAdminSession();
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [page, setPage] = useState(0);
  const canChoose = session.can(adminPermissions.commercialPoliciesChooseSegments);
  const canResolve = session.can(adminPermissions.commercialPoliciesResolveSegmentChoices);
  const choices = useQuery({
    queryKey: adminCommercialKeys.policies.segmentChoices({ search: debounced, page }),
    queryFn: () => adminApi.commercialPolicySegmentChoices({ query: debounced || undefined, page, size: 20 }),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesChooseSegments),
  });
  const selected = useQuery({
    queryKey: adminCommercialKeys.policies.segmentChoices({ selected: draft.segmentReference }),
    queryFn: () => adminApi.resolveCommercialPolicySegmentChoices([draft.segmentReference]),
    enabled: commercialQueryEnabled(
      session.can,
      adminPermissions.commercialPoliciesResolveSegmentChoices,
      Boolean(draft.segmentReference),
    ),
  });
  const options = useMemo(() => {
    const byCode = new Map<string, CommercialPolicySegmentChoice>();
    for (const segment of selected.data ?? []) byCode.set(segment.code, segment);
    for (const segment of choices.data?.content ?? []) byCode.set(segment.code, segment);
    return [...byCode.values()];
  }, [choices.data, selected.data]);
  const label = (segment: CommercialPolicySegmentChoice) =>
    `${segment.name} · ${segment.code} · R${segment.revisionNumber} · ${segment.immutableAccountCount} compte(s)`;

  if (!canChoose) {
    const retained = options.find((segment) => segment.code === draft.segmentReference);
    return (
      <div className="space-y-1 border-y py-4 text-sm text-muted-foreground">
        <p>Votre rôle ne permet pas de parcourir les Segments exécutables.</p>
        {draft.segmentReference ? (
          <p className="text-xs">
            Cible conservée : {retained ? label(retained) : draft.segmentReference}
            {!canResolve ? " (identité masquée)" : ""}
          </p>
        ) : null}
        {selected.isError ? <p className="text-xs text-warning">Le libellé conservé n’a pas pu être relu.</p> : null}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="space-y-2">
        <Label htmlFor="policy-segment-search">Segment actif avec audience figée</Label>
        <Input
          id="policy-segment-search"
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
      {selected.isError ? (
        <p className="text-xs text-warning" role="status">
          La référence reste conservée, mais son Segment n’a pas pu être relu.
        </p>
      ) : null}
      <Select
        onValueChange={(value) => onChange({ ...draft, segmentReference: value })}
        value={draft.segmentReference || undefined}
      >
        <SelectTrigger className="w-full" aria-label="Segment ciblé">
          <SelectValue placeholder="Choisir un Segment exécutable" />
        </SelectTrigger>
        <SelectContent>
          {options.map((segment) => (
            <SelectItem key={segment.id} value={segment.code}>
              {label(segment)}
            </SelectItem>
          ))}
          {draft.segmentReference && !options.some((segment) => segment.code === draft.segmentReference) ? (
            <SelectItem value={draft.segmentReference}>Référence conservée · non résolue</SelectItem>
          ) : null}
        </SelectContent>
      </Select>
      {choices.data ? (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>{choices.data.totalElements} Segment(s) exécutable(s)</span>
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
      ) : null}
    </div>
  );
}

function TargetStep({
  draft,
  onChange,
  error,
}: {
  draft: CommercialPolicyDraft;
  onChange: (next: CommercialPolicyDraft) => void;
  error?: string;
}) {
  return (
    <section className="space-y-6">
      <div className="max-w-xl space-y-2">
        <Label htmlFor="policy-target-kind">Qui recevra ces conditions ?</Label>
        <Select
          onValueChange={(value) =>
            onChange({
              ...draft,
              targetKind: value as CommercialPolicyDraft["targetKind"],
              accountId: "",
              accountIds: [],
              planRevisionId: "",
              segmentReference: "",
            })
          }
          value={draft.targetKind}
        >
          <SelectTrigger className="w-full" id="policy-target-kind">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(policyTarget).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <FieldError>{error}</FieldError>
      </div>
      {draft.targetKind === "ACCOUNT" || draft.targetKind === "ACCOUNT_SET" ? (
        <AccountTargetEditor draft={draft} onChange={onChange} />
      ) : draft.targetKind === "PLAN_REVISION_SUBSCRIBERS" ? (
        <PlanTargetEditor draft={draft} onChange={onChange} />
      ) : (
        <SegmentTargetEditor draft={draft} onChange={onChange} />
      )}
    </section>
  );
}

function TermsStep({
  draft,
  onChange,
  errors,
}: {
  draft: CommercialPolicyDraft;
  onChange: (next: CommercialPolicyDraft) => void;
  errors: ReturnType<typeof validateCommercialPolicyDraft>;
}) {
  return (
    <section className="space-y-7">
      <div className="grid gap-5 lg:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="policy-name">Nom de la politique</Label>
          <Input
            id="policy-name"
            maxLength={180}
            onChange={(event) => onChange({ ...draft, name: event.target.value })}
            value={draft.name}
          />
          <FieldError>{errors.name}</FieldError>
        </div>
        <div className="space-y-2">
          <Label htmlFor="policy-source">Origine de la décision</Label>
          <Select
            onValueChange={(value) => onChange({ ...draft, source: value as CommercialPolicyDraft["source"] })}
            value={draft.source}
          >
            <SelectTrigger className="w-full" id="policy-source">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(policySource).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="policy-description">Description interne</Label>
        <Textarea
          id="policy-description"
          maxLength={1000}
          onChange={(event) => onChange({ ...draft, description: event.target.value })}
          rows={3}
          value={draft.description}
        />
      </div>
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        <div className="space-y-2">
          <Label htmlFor="policy-from">Début d’effet</Label>
          <Input
            id="policy-from"
            onChange={(event) => onChange({ ...draft, effectiveFrom: event.target.value })}
            type="datetime-local"
            value={draft.effectiveFrom}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="policy-until">Fin d’effet (facultative)</Label>
          <Input
            id="policy-until"
            onChange={(event) => onChange({ ...draft, effectiveUntil: event.target.value })}
            type="datetime-local"
            value={draft.effectiveUntil}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="policy-priority">Priorité (0–1000)</Label>
          <Input
            id="policy-priority"
            min={0}
            max={1000}
            onChange={(event) => onChange({ ...draft, priority: event.target.value })}
            type="number"
            value={draft.priority}
          />
          <FieldError>{errors.priority}</FieldError>
        </div>
      </div>
      <FieldError>{errors.window}</FieldError>
      <div className="space-y-2">
        <Label htmlFor="policy-reason">Motif métier obligatoire</Label>
        <Textarea
          id="policy-reason"
          maxLength={500}
          onChange={(event) => onChange({ ...draft, reason: event.target.value })}
          rows={3}
          value={draft.reason}
        />
        <FieldError>{errors.reason}</FieldError>
      </div>
      <div className="grid gap-5 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="policy-approval">Référence d’approbation</Label>
          <Input
            id="policy-approval"
            maxLength={180}
            onChange={(event) => onChange({ ...draft, approvalReference: event.target.value })}
            value={draft.approvalReference}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="policy-contract">Référence contractuelle</Label>
          <Input
            id="policy-contract"
            maxLength={180}
            onChange={(event) => onChange({ ...draft, contractReference: event.target.value })}
            value={draft.contractReference}
          />
        </div>
      </div>
    </section>
  );
}

function ProductRevisionPicker({
  effect,
  onChange,
}: {
  effect: CommercialPolicyDraftEffect;
  onChange: (next: CommercialPolicyDraftEffect) => void;
}) {
  const productTypeId = `policy-effect-${effect.key}-product-type`;
  const productRevisionId = `policy-effect-${effect.key}-product-revision`;
  const session = useAdminSession();
  const type =
    effect.type === "GRANT_ADD_ON"
      ? "ADD_ON"
      : effect.type === "GRANT_QUOTA_PACKAGE"
        ? "QUOTA_PACKAGE"
        : effect.productType;
  const permission =
    type === "PLAN"
      ? adminPermissions.plansChoose
      : type === "ADD_ON"
        ? adminPermissions.addOnsChoose
        : adminPermissions.quotaPackagesChoose;
  const resolvePermission =
    type === "PLAN"
      ? adminPermissions.plansResolveChoices
      : type === "ADD_ON"
        ? adminPermissions.addOnsResolveChoices
        : adminPermissions.quotaPackagesResolveChoices;
  const enabled = Boolean(type) && session.can(permission);
  const [search, setSearch] = useState("");
  const debounced = useDebouncedValue(search);
  const [page, setPage] = useState(0);
  const choices = useQuery<PageResponse<CommercialChooserItem>>({
    queryKey: [...adminCommercialKeys.policies.all(), "product-choices", type, debounced, page],
    queryFn: async () => {
      if (type === "PLAN") return adminApi.planChoices({ search: debounced || undefined, page, size: 20 });
      if (type === "ADD_ON") return adminApi.addOnChoices({ search: debounced || undefined, page, size: 20 });
      return adminApi.quotaPackageChoices({ search: debounced || undefined, page, size: 20 });
    },
    enabled,
  });
  const selectedChoice = useQuery<CommercialChooserItem[]>({
    queryKey: [...adminCommercialKeys.policies.all(), "product-choice-selected", type, effect.productRevisionId],
    queryFn: async () => {
      if (type === "PLAN") return adminApi.selectedPlanChoices([effect.productRevisionId]);
      if (type === "ADD_ON") return adminApi.selectedAddOnChoices([effect.productRevisionId]);
      return adminApi.selectedQuotaPackageChoices([effect.productRevisionId]);
    },
    enabled: enabled && Boolean(effect.productRevisionId) && session.can(resolvePermission),
  });
  const productOptions = useMemo(() => {
    const byId = new Map((selectedChoice.data ?? []).map((choice) => [choice.id, choice]));
    for (const choice of choices.data?.content ?? []) byId.set(choice.id, choice);
    return [...byId.values()];
  }, [choices.data, selectedChoice.data]);
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {effect.type === "ALLOW_PRODUCT_SELECTION" || effect.type === "BLOCK_PRODUCT_SELECTION" ? (
        <div className="space-y-2">
          <Label htmlFor={productTypeId}>Type de produit</Label>
          <Select
            onValueChange={(value) =>
              onChange({ ...effect, productType: value as CommercialPolicyProductType, productRevisionId: "" })
            }
            value={effect.productType || undefined}
          >
            <SelectTrigger className="w-full" id={productTypeId}>
              <SelectValue placeholder="Choisir" />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(productTypeLabel).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      ) : null}
      <div className="space-y-2">
        <Label htmlFor={productRevisionId}>Révision du produit</Label>
        {!type ? (
          <p className="text-sm text-muted-foreground">Choisissez d’abord le type.</p>
        ) : !session.can(permission) ? (
          <div className="space-y-1 text-sm text-muted-foreground">
            <p>Votre rôle ne permet pas de parcourir ces produits.</p>
            {effect.productRevisionId ? (
              <p className="break-all text-xs">Révision conservée : {effect.productRevisionId}</p>
            ) : null}
          </div>
        ) : choices.isError ? (
          <div className="space-y-2">
            {effect.productRevisionId ? (
              <p className="break-all text-xs text-muted-foreground">Révision conservée : {effect.productRevisionId}</p>
            ) : null}
            <Button onClick={() => void choices.refetch()} size="sm" variant="outline">
              Réessayer
            </Button>
          </div>
        ) : (
          <div className="space-y-2">
            <Input
              aria-label="Rechercher une révision de produit"
              maxLength={180}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder="Rechercher une révision…"
              value={search}
            />
            <Select
              onValueChange={(value) => onChange({ ...effect, productRevisionId: value })}
              value={effect.productRevisionId || undefined}
            >
              <SelectTrigger className="w-full" id={productRevisionId}>
                <SelectValue placeholder={choices.isLoading ? "Chargement…" : "Choisir une révision"} />
              </SelectTrigger>
              <SelectContent>
                {productOptions.map((choice) => (
                  <SelectItem key={choice.id} value={choice.id}>
                    {choice.name} · {choice.code} · R{choice.revisionNumber} · {statusText[choice.status]}
                  </SelectItem>
                ))}
                {effect.productRevisionId &&
                !productOptions.some((choice) => choice.id === effect.productRevisionId) ? (
                  <SelectItem value={effect.productRevisionId}>Révision conservée · identité non résolue</SelectItem>
                ) : null}
              </SelectContent>
            </Select>
            {choices.data ? (
              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span>{choices.data.totalElements} révision(s)</span>
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
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}

function FeaturePicker({
  effect,
  onChange,
  quota,
}: {
  effect: CommercialPolicyDraftEffect;
  onChange: (next: CommercialPolicyDraftEffect) => void;
  quota: boolean;
}) {
  const featureId = `policy-effect-${effect.key}-feature`;
  const quotaResourceId = `policy-effect-${effect.key}-quota-resource`;
  const session = useAdminSession();
  const features = useQuery({
    queryKey: adminCommercialKeys.registry.featureCatalog("PLAN_ASSIGNABLE"),
    queryFn: () => adminApi.featureCatalog("PLAN_ASSIGNABLE"),
    enabled: commercialQueryEnabled(session.can, adminPermissions.registryFeatureCatalog),
  });
  const flat = useMemo(() => features.data?.flatMap((module) => module.features) ?? [], [features.data]);
  const choices = quota ? flat.filter(isPolicyQuotaFeatureChoice) : flat;
  const selected = flat.find((feature) => feature.code === effect.featureCode);
  if (!session.can(adminPermissions.registryFeatureCatalog)) {
    return (
      <div className="space-y-1 text-sm text-muted-foreground">
        <p>Votre rôle ne permet pas de parcourir le catalogue de fonctionnalités.</p>
        {effect.featureCode ? <p className="text-xs">Valeur conservée : {effect.featureCode}</p> : null}
      </div>
    );
  }
  if (features.isError) {
    return (
      <div className="space-y-2">
        <p className="text-sm text-destructive" role="alert">
          Le catalogue de fonctionnalités n’a pas pu être chargé.
        </p>
        {effect.featureCode ? (
          <p className="text-xs text-muted-foreground">Valeur conservée : {effect.featureCode}</p>
        ) : null}
        <Button onClick={() => void features.refetch()} size="sm" type="button" variant="outline">
          Réessayer
        </Button>
      </div>
    );
  }
  const retainedFeature = effect.featureCode && !choices.some((feature) => feature.code === effect.featureCode);
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="space-y-2">
        <Label htmlFor={featureId}>Fonctionnalité</Label>
        <Select
          onValueChange={(value) => onChange({ ...effect, featureCode: value, quotaResource: "" })}
          value={effect.featureCode || undefined}
        >
          <SelectTrigger className="w-full" id={featureId}>
            <SelectValue placeholder={features.isLoading ? "Chargement…" : "Choisir"} />
          </SelectTrigger>
          <SelectContent>
            {choices.map((feature: RegistryFeature) => (
              <SelectItem key={feature.code} value={feature.code}>
                {feature.displayName} · {feature.code}
              </SelectItem>
            ))}
            {retainedFeature ? (
              <SelectItem value={effect.featureCode}>Fonctionnalité conservée · {effect.featureCode}</SelectItem>
            ) : null}
          </SelectContent>
        </Select>
      </div>
      {quota ? (
        <div className="space-y-2">
          <Label htmlFor={quotaResourceId}>Ressource de capacité</Label>
          <Select
            disabled={!selected?.quotaSchema.length}
            onValueChange={(value) => onChange({ ...effect, quotaResource: value })}
            value={effect.quotaResource || undefined}
          >
            <SelectTrigger className="w-full" id={quotaResourceId}>
              <SelectValue placeholder="Choisir une ressource déclarée" />
            </SelectTrigger>
            <SelectContent>
              {selected?.quotaSchema.map((slot) => (
                <SelectItem key={slot.resource} value={slot.resource}>
                  {slot.resource} · {slot.unit}
                </SelectItem>
              ))}
              {effect.quotaResource && !selected?.quotaSchema.some((slot) => slot.resource === effect.quotaResource) ? (
                <SelectItem value={effect.quotaResource}>Ressource conservée · {effect.quotaResource}</SelectItem>
              ) : null}
            </SelectContent>
          </Select>
        </div>
      ) : null}
    </div>
  );
}

function MoneyFields({
  effect,
  onChange,
  percentage = false,
}: {
  effect: CommercialPolicyDraftEffect;
  onChange: (next: CommercialPolicyDraftEffect) => void;
  percentage?: boolean;
}) {
  const prefix = `policy-effect-${effect.key}`;
  const amountId = `${prefix}-${percentage ? "maximum-amount" : "amount"}`;
  const currencyId = `${prefix}-${percentage ? "maximum-currency" : "currency"}`;
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      {percentage ? (
        <div className="space-y-2">
          <Label htmlFor={`${prefix}-percentage`}>Pourcentage</Label>
          <Input
            id={`${prefix}-percentage`}
            min="0"
            max="100"
            onChange={(event) => onChange({ ...effect, percentage: event.target.value })}
            step="0.0001"
            type="number"
            value={effect.percentage}
          />
        </div>
      ) : null}
      <div className="space-y-2">
        <Label htmlFor={amountId}>{percentage ? "Plafond" : "Montant"}</Label>
        <Input
          id={amountId}
          min="0"
          onChange={(event) => onChange({ ...effect, [percentage ? "maximumAmount" : "amount"]: event.target.value })}
          step="0.0001"
          type="number"
          value={percentage ? effect.maximumAmount : effect.amount}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor={currencyId}>Devise</Label>
        <Input
          id={currencyId}
          maxLength={3}
          onChange={(event) =>
            onChange({
              ...effect,
              [percentage ? "maximumCurrencyCode" : "currencyCode"]: event.target.value.toUpperCase(),
            })
          }
          value={percentage ? effect.maximumCurrencyCode : effect.currencyCode}
        />
      </div>
      {effect.type === "FIXED_SUBSCRIPTION_PRICE" ? (
        <div className="space-y-2">
          <Label htmlFor={`${prefix}-cycle`}>Cycle</Label>
          <Select
            onValueChange={(value) => onChange({ ...effect, billingCycle: value as "MONTHLY" | "YEARLY" })}
            value={effect.billingCycle || undefined}
          >
            <SelectTrigger className="w-full" id={`${prefix}-cycle`}>
              <SelectValue placeholder="Choisir" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="MONTHLY">Mensuel</SelectItem>
              <SelectItem value="YEARLY">Annuel</SelectItem>
            </SelectContent>
          </Select>
        </div>
      ) : null}
    </div>
  );
}

function EffectFields({
  effect,
  onChange,
}: {
  effect: CommercialPolicyDraftEffect;
  onChange: (next: CommercialPolicyDraftEffect) => void;
}) {
  if (effect.type === "FIXED_SUBSCRIPTION_PRICE" || effect.type === "FIXED_DISCOUNT")
    return <MoneyFields effect={effect} onChange={onChange} />;
  if (effect.type === "PERCENTAGE_DISCOUNT") return <MoneyFields effect={effect} onChange={onChange} percentage />;
  if (
    ["ALLOW_PRODUCT_SELECTION", "BLOCK_PRODUCT_SELECTION", "GRANT_ADD_ON", "GRANT_QUOTA_PACKAGE"].includes(effect.type)
  )
    return <ProductRevisionPicker effect={effect} onChange={onChange} />;
  if (effect.type === "BLOCK_FEATURE") return <FeaturePicker effect={effect} onChange={onChange} quota={false} />;
  return (
    <div className="space-y-3">
      <FeaturePicker effect={effect} onChange={onChange} quota />
      <div className="max-w-xs space-y-2">
        <Label htmlFor={`policy-effect-${effect.key}-quantity`}>Capacité ajoutée</Label>
        <Input
          id={`policy-effect-${effect.key}-quantity`}
          min={1}
          onChange={(event) => onChange({ ...effect, quantityDelta: event.target.value })}
          step={1}
          type="number"
          value={effect.quantityDelta}
        />
      </div>
    </div>
  );
}

function EffectsStep({
  draft,
  onChange,
  errors,
}: {
  draft: CommercialPolicyDraft;
  onChange: (next: CommercialPolicyDraft) => void;
  errors: ReturnType<typeof validateCommercialPolicyDraft>;
}) {
  const updateEffect = (key: string, next: CommercialPolicyDraftEffect) =>
    onChange({ ...draft, effects: draft.effects.map((effect) => (effect.key === key ? next : effect)) });
  return (
    <section className="space-y-5">
      <CommercialPolicyExplicitApplicationNotice />
      <div className="divide-y rounded-xl border bg-card">
        {draft.effects.map((effect, index) => (
          <fieldset className="space-y-4 p-4 sm:p-5" key={effect.key}>
            <legend className="sr-only">Effet {index + 1}</legend>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1 space-y-2">
                <Label htmlFor={`policy-effect-${effect.key}-type`}>Effet {index + 1}</Label>
                <Select
                  onValueChange={(value) =>
                    updateEffect(effect.key, {
                      ...newDraftEffect(value as CommercialPolicyEffectType),
                      key: effect.key,
                    })
                  }
                  value={effect.type}
                >
                  <SelectTrigger className="w-full" id={`policy-effect-${effect.key}-type`}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {effectTypes.map((type) => (
                      <SelectItem key={type} value={type}>
                        {effectLabel[type]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <Button
                aria-label={`Supprimer l’effet ${index + 1}`}
                disabled={draft.effects.length === 1}
                onClick={() =>
                  onChange({ ...draft, effects: draft.effects.filter((candidate) => candidate.key !== effect.key) })
                }
                size="icon"
                type="button"
                variant="ghost"
              >
                <TrashIcon />
              </Button>
            </div>
            <EffectFields effect={effect} onChange={(next) => updateEffect(effect.key, next)} />
            <FieldError>{errors.effectErrors?.[effect.key]}</FieldError>
          </fieldset>
        ))}
      </div>
      <FieldError>{errors.effects}</FieldError>
      <Button
        disabled={draft.effects.length >= 50}
        onClick={() => onChange({ ...draft, effects: [...draft.effects, newDraftEffect()] })}
        type="button"
        variant="outline"
      >
        <PlusIcon />
        Ajouter un effet ({draft.effects.length}/50)
      </Button>
    </section>
  );
}

function ReviewStep({
  draft,
  errors,
}: {
  draft: CommercialPolicyDraft;
  errors: ReturnType<typeof validateCommercialPolicyDraft>;
}) {
  const errorCount =
    Object.keys(errors).filter((key) => key !== "effectErrors").length + Object.keys(errors.effectErrors ?? {}).length;
  return (
    <section className="space-y-6">
      {errorCount ? (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>{errorCount} point(s) à corriger</AlertTitle>
          <AlertDescription>
            Revenez aux étapes précédentes. Aucun brouillon ne sera créé tant que ces points restent ouverts.
          </AlertDescription>
        </Alert>
      ) : (
        <Alert>
          <CheckIcon />
          <AlertTitle>Prête à enregistrer</AlertTitle>
          <AlertDescription>
            La politique sera créée en brouillon. Une activation séparée exigera une audience et une preuve actuelles.
          </AlertDescription>
        </Alert>
      )}
      <dl className="grid gap-x-8 gap-y-5 border-y py-5 sm:grid-cols-2">
        <div>
          <dt className="text-xs text-muted-foreground">Politique</dt>
          <dd className="mt-1 font-medium">{draft.name || "—"}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Cible</dt>
          <dd className="mt-1 font-medium">{policyTarget[draft.targetKind]}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Source et priorité</dt>
          <dd className="mt-1 font-medium">
            {policySource[draft.source]} · {draft.priority || "0"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Période</dt>
          <dd className="mt-1 font-medium">
            {reviewDate(draft.effectiveFrom)} → {reviewDate(draft.effectiveUntil)}
          </dd>
        </div>
      </dl>
      <div>
        <h2 className="text-sm font-semibold">Effets configurés</h2>
        <ol className="mt-3 divide-y rounded-lg border">
          {draft.effects.map((effect, index) => (
            <li
              className="grid gap-1 px-4 py-3 text-sm sm:grid-cols-[32px_minmax(180px,0.8fr)_1.2fr] sm:gap-3"
              key={effect.key}
            >
              <span className="tabular-nums text-muted-foreground">{index + 1}</span>
              <span className="font-medium">{effectLabel[effect.type]}</span>
              <span className="text-muted-foreground">{draftEffectSummary(effect)}</span>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

export function PolicyEditor({ existing }: { existing?: CommercialPolicyDetail }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [step, setStep] = useState<EditorStep>("target");
  const [draft, setDraft] = useState<CommercialPolicyDraft>(() =>
    existing ? draftFromPolicy(existing) : emptyCommercialPolicyDraft(),
  );
  const [submitted, setSubmitted] = useState(false);
  const [versionConflict, setVersionConflict] = useState(false);
  const initialDraft = useRef(JSON.stringify(draft));
  const expectedVersion = useRef(existing?.summary.version);
  const completed = useRef(false);
  const editorFocus = useRef<HTMLElement>(null);
  const errors = useMemo(() => validateCommercialPolicyDraft(draft), [draft]);
  const hasErrors = Object.keys(errors).length > 0;
  const mutation = useMutation({
    mutationFn: () => {
      const input = toCommercialPolicyWriteInput(draft);
      return existing
        ? adminApi.updateCommercialPolicy(existing.summary.id, { ...input, version: expectedVersion.current ?? 0 })
        : adminApi.createCommercialPolicy(input);
    },
    onSuccess: async (policy) => {
      await invalidateAdminCommercial(queryClient, adminCommercialKeys.policies.all());
      completed.current = true;
      toast.success(existing ? "Brouillon mis à jour" : "Politique créée en brouillon");
      navigate(`/admin/commercial-policies/${policy.summary.id}`);
    },
    onError: async (error) => {
      if (existing) {
        if (isPolicyVersionConflict(error)) setVersionConflict(true);
        await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.policies.detail(existing.summary.id) });
      }
      toast.error(policyMutationMessage(error));
    },
  });
  const reload = useMutation({
    mutationFn: () => {
      if (!existing) throw new Error("Aucune politique à recharger.");
      return adminApi.commercialPolicy(existing.summary.id);
    },
    onSuccess: (fresh) => {
      const nextDraft = draftFromPolicy(fresh);
      queryClient.setQueryData(adminCommercialKeys.policies.detail(fresh.summary.id), fresh);
      setDraft(nextDraft);
      initialDraft.current = JSON.stringify(nextDraft);
      expectedVersion.current = fresh.summary.version;
      setVersionConflict(false);
      setSubmitted(false);
      setStep("target");
      window.requestAnimationFrame(() => editorFocus.current?.focus());
      toast.success("Nouvelle version chargée");
    },
    onError: (error) => toast.error(policyMutationMessage(error)),
  });
  const dirty = !completed.current && JSON.stringify(draft) !== initialDraft.current;
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;
  const blocker = useBlocker(useCallback(() => dirtyRef.current && !completed.current, []));
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm("Quitter sans enregistrer cette politique ? Les changements saisis seront perdus.")) {
      blocker.proceed();
    } else {
      blocker.reset();
    }
  }, [blocker]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const index = steps.findIndex((candidate) => candidate.value === step);
  const previousStep = steps.at(index - 1)?.value;
  const nextStep = steps.at(index + 1)?.value;
  const submit = () => {
    setSubmitted(true);
    if (hasErrors) return;
    mutation.mutate();
  };
  return (
    <section aria-label="Éditeur de politique commerciale" className="space-y-6" ref={editorFocus} tabIndex={-1}>
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to={existing ? `/admin/commercial-policies/${existing.summary.id}` : "/admin/commercial-policies"}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          {existing ? "Politique" : "Politiques commerciales"}
        </Link>
      </Button>
      <PageHeader title={existing ? "Modifier le brouillon" : "Nouvelle politique commerciale"} />
      <SectionTabs
        ariaLabel="Étapes de définition"
        items={steps}
        onValueChange={(value) => setStep(value as EditorStep)}
        value={step}
      />
      <div className="min-h-[420px] py-2">
        {step === "target" ? (
          <TargetStep draft={draft} error={submitted ? errors.target : undefined} onChange={setDraft} />
        ) : step === "terms" ? (
          <TermsStep draft={draft} errors={submitted ? errors : {}} onChange={setDraft} />
        ) : step === "effects" ? (
          <EffectsStep draft={draft} errors={submitted ? errors : {}} onChange={setDraft} />
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
        <Button
          disabled={!previousStep}
          onClick={() => previousStep && setStep(previousStep)}
          type="button"
          variant="ghost"
        >
          <ArrowLeftIcon className="rtl:rotate-180" />
          Précédent
        </Button>
        {step === "review" ? (
          <Button disabled={mutation.isPending || versionConflict} onClick={submit}>
            {mutation.isPending ? "Enregistrement…" : existing ? "Enregistrer le brouillon" : "Créer le brouillon"}
          </Button>
        ) : (
          <Button disabled={!nextStep} onClick={() => nextStep && setStep(nextStep)} type="button">
            Continuer
            <ArrowRightIcon className="rtl:rotate-180" />
          </Button>
        )}
      </footer>
    </section>
  );
}

export function AdminCommercialPolicyCreatePage() {
  const session = useAdminSession();
  if (!session.can(adminPermissions.commercialPoliciesCreate)) return <PermissionState />;
  return <PolicyEditor />;
}

export function AdminCommercialPolicyEditPage() {
  const session = useAdminSession();
  const { policyId } = useParams();
  const id = validPolicyIdentity(policyId ?? "");
  const policy = useQuery({
    queryKey: adminCommercialKeys.policies.detail(id),
    queryFn: () => adminApi.commercialPolicy(id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesRead, Boolean(id)),
  });
  if (!session.can(adminPermissions.commercialPoliciesUpdateDraft)) return <PermissionState />;
  if (!id) {
    return (
      <ErrorState
        description="L’identifiant présent dans l’adresse n’est pas un identifiant de politique valide."
        title="Adresse de politique invalide"
      />
    );
  }
  if (policy.isLoading) return <LoadingState />;
  if (policy.isError || !policy.data) return <ErrorState retry={() => void policy.refetch()} />;
  if (policy.data.summary.status !== "DRAFT" || !policy.data.summary.availableActions.includes("EDIT_DRAFT")) {
    return <Navigate replace to={`/admin/commercial-policies/${id}`} />;
  }
  return <PolicyEditor existing={policy.data} />;
}
