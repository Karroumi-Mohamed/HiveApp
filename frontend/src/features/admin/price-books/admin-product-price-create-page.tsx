import { ArrowLeftIcon, CheckCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useBlocker, useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { AddOn, Plan, ProductPriceOwnerType, QuotaPackage } from "@/api/contracts";
import { ApiError } from "@/api/http";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";
import {
  instantFromLocalValue,
  localDateTimeValue,
  type ProductPriceDraftErrors,
  type ProductPriceDraftFields,
  productPriceCycle,
  productPriceOwner,
  validateProductPriceDraft,
} from "./product-price-rules";
import { ProductPriceTermsForm } from "./product-price-terms-form";

type ProductOption = {
  id: string;
  name: string;
  code: string;
  revision: string;
  status: string;
  currencyCode: string;
  billingCycle: "MONTHLY" | "YEARLY" | "FOREVER";
};

const STEPS = ["Produit", "Conditions", "Vérification"] as const;

const ownerStatus: Record<string, string> = {
  DRAFT: "Brouillon",
  ACTIVE: "Actif",
  INACTIVE: "Inactif",
  ARCHIVED: "Archivé",
};

function planOption(plan: Plan): ProductOption {
  return {
    id: plan.id,
    name: plan.name,
    code: plan.code,
    revision: `R${plan.revisionNumber}`,
    status: plan.status,
    currencyCode: plan.currencyCode,
    billingCycle: plan.billingCycle,
  };
}

function addOnOption(addOn: AddOn): ProductOption {
  return {
    id: addOn.id,
    name: addOn.name,
    code: addOn.code,
    revision: `R${addOn.revisionNumber}`,
    status: addOn.status,
    currencyCode: addOn.currencyCode,
    billingCycle: addOn.billingCycle,
  };
}

function quotaOption(item: QuotaPackage): ProductOption {
  return {
    id: item.id,
    name: item.name,
    code: item.code,
    revision: `V${item.definitionVersion}`,
    status: item.status,
    currencyCode: item.currencyCode,
    billingCycle: item.billingCycle,
  };
}

function money(amount: string, currency: string) {
  const value = Number(amount);
  if (!Number.isFinite(value) || !/^[A-Z]{3}$/.test(currency)) return `${amount || "—"} ${currency}`;
  return new Intl.NumberFormat("fr-MA", { style: "currency", currency }).format(value);
}

export function AdminProductPriceCreatePage() {
  const session = useAdminSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const initialType = params.get("ownerType") as ProductPriceOwnerType | null;
  const initialOwnerId = params.get("ownerId");
  const [step, setStep] = useState(0);
  const [ownerType, setOwnerType] = useState<ProductPriceOwnerType>(
    initialType && ["PLAN", "ADD_ON", "QUOTA_PACKAGE"].includes(initialType) ? initialType : "PLAN",
  );
  const [ownerId, setOwnerId] = useState(initialOwnerId ?? "");
  const [fields, setFields] = useState<ProductPriceDraftFields>({
    amount: "",
    currencyCode: "MAD",
    billingCycle: "MONTHLY",
    effectiveFrom: localDateTimeValue(new Date().toISOString()),
    effectiveUntil: "",
  });
  const [errors, setErrors] = useState<ProductPriceDraftErrors>({});
  const completed = useRef(false);

  const plans = useQuery({
    queryKey: adminCommercialKeys.plans.list(),
    queryFn: adminApi.plans,
    enabled: commercialQueryEnabled(session.can, adminPermissions.plansList, ownerType === "PLAN"),
  });
  const addOns = useQuery({
    queryKey: adminCommercialKeys.addOns.list(),
    queryFn: adminApi.addOns,
    enabled: commercialQueryEnabled(session.can, adminPermissions.addOnsList, ownerType === "ADD_ON"),
  });
  const quotaPackages = useQuery({
    queryKey: adminCommercialKeys.quotaPackages.list(),
    queryFn: adminApi.quotaPackages,
    enabled: commercialQueryEnabled(session.can, adminPermissions.quotaPackagesList, ownerType === "QUOTA_PACKAGE"),
  });

  const canReadOwner =
    ownerType === "PLAN"
      ? session.can(adminPermissions.plansList)
      : ownerType === "ADD_ON"
        ? session.can(adminPermissions.addOnsList)
        : session.can(adminPermissions.quotaPackagesList);
  const ownerQuery = ownerType === "PLAN" ? plans : ownerType === "ADD_ON" ? addOns : quotaPackages;
  const options = useMemo(
    () =>
      ownerType === "PLAN"
        ? (plans.data ?? []).map(planOption)
        : ownerType === "ADD_ON"
          ? (addOns.data ?? []).map(addOnOption)
          : (quotaPackages.data ?? []).map(quotaOption),
    [addOns.data, ownerType, plans.data, quotaPackages.data],
  );
  const owner = options.find((option) => option.id === ownerId);
  const prefilledOwner = useRef<string | null>(null);

  useEffect(() => {
    if (!initialOwnerId || ownerId !== initialOwnerId || owner || ownerQuery.isLoading) return;
    setOwnerId("");
  }, [initialOwnerId, owner, ownerId, ownerQuery.isLoading]);
  useEffect(() => {
    if (!owner || prefilledOwner.current === owner.id) return;
    prefilledOwner.current = owner.id;
    setFields((current) => ({
      ...current,
      currencyCode: owner.currencyCode,
      billingCycle: owner.billingCycle === "YEARLY" ? "YEARLY" : "MONTHLY",
    }));
  }, [owner]);

  const create = useMutation({
    mutationFn: () =>
      adminApi.createProductPrice(ownerType, ownerId, {
        amount: Number(fields.amount),
        currencyCode: fields.currencyCode.trim().toUpperCase(),
        billingCycle: fields.billingCycle,
        effectiveFrom: instantFromLocalValue(fields.effectiveFrom),
        effectiveUntil: fields.effectiveUntil ? instantFromLocalValue(fields.effectiveUntil) : null,
      }),
    onSuccess: (created) => {
      completed.current = true;
      void invalidateAdminCommercial(queryClient, adminCommercialKeys.priceBooks.all());
      toast.success("Tarif créé en brouillon");
      navigate(`/admin/price-books/${created.id}`);
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "Création impossible"),
  });

  const dirty = !completed.current && (ownerId !== "" || fields.amount !== "");
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;
  const blocker = useBlocker(useCallback(() => dirtyRef.current && !completed.current, []));
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm("Quitter sans créer le tarif ? Les informations saisies seront perdues.")) blocker.proceed();
    else blocker.reset();
  }, [blocker]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  const continueFromTerms = () => {
    const nextErrors = validateProductPriceDraft(fields);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length === 0) setStep(2);
  };

  if (!session.can(adminPermissions.priceBooksCreate)) return <PermissionState />;

  return (
    <div className="space-y-7">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to="/admin/price-books">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Grille tarifaire
        </Link>
      </Button>
      <PageHeader title="Créer un tarif" />

      <nav aria-label="Étapes de création">
        <ol className="grid gap-px overflow-hidden rounded-lg border bg-border sm:grid-cols-3">
          {STEPS.map((label, index) => (
            <li
              aria-current={step === index ? "step" : undefined}
              className={`flex items-center gap-2 bg-card px-4 py-3 text-sm ${step === index ? "font-semibold text-primary" : "text-muted-foreground"}`}
              key={label}
            >
              {index < step ? (
                <CheckCircleIcon className="size-4 text-success" weight="fill" />
              ) : (
                <span>{index + 1}</span>
              )}
              {label}
            </li>
          ))}
        </ol>
      </nav>

      <section className="rounded-xl border bg-card p-5 sm:p-6">
        {step === 0 ? (
          <div className="mx-auto max-w-2xl space-y-6">
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="price-owner-type">
                Type de produit
              </label>
              <Select
                onValueChange={(value: ProductPriceOwnerType) => {
                  setOwnerType(value);
                  setOwnerId("");
                  prefilledOwner.current = null;
                }}
                value={ownerType}
              >
                <SelectTrigger id="price-owner-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem disabled={!session.can(adminPermissions.plansList)} value="PLAN">
                    Forfait
                  </SelectItem>
                  <SelectItem disabled={!session.can(adminPermissions.addOnsList)} value="ADD_ON">
                    Add-on
                  </SelectItem>
                  <SelectItem disabled={!session.can(adminPermissions.quotaPackagesList)} value="QUOTA_PACKAGE">
                    Pack de capacité
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
            {!canReadOwner ? (
              <PermissionState description="La création nécessite aussi l’accès à la liste du type de produit choisi." />
            ) : ownerQuery.isLoading ? (
              <LoadingState rows={3} />
            ) : ownerQuery.isError ? (
              <ErrorState retry={() => void ownerQuery.refetch()} />
            ) : !options.length ? (
              <EmptyState title={`Aucune révision de ${productPriceOwner[ownerType].toLocaleLowerCase("fr")}`} />
            ) : (
              <div className="space-y-2">
                <label className="text-sm font-medium" htmlFor="price-owner-id">
                  Révision exacte
                </label>
                <Select
                  onValueChange={(value) => {
                    prefilledOwner.current = null;
                    setOwnerId(value);
                  }}
                  value={ownerId}
                >
                  <SelectTrigger id="price-owner-id">
                    <SelectValue placeholder="Choisir un produit" />
                  </SelectTrigger>
                  <SelectContent>
                    {options.map((option) => (
                      <SelectItem key={option.id} value={option.id}>
                        {option.name} · {option.revision} · {ownerStatus[option.status] ?? option.status}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  Le tarif restera lié à cette révision, même lorsqu’une révision plus récente du produit sera créée.
                </p>
              </div>
            )}
          </div>
        ) : step === 1 ? (
          <div className="mx-auto max-w-3xl">
            <ProductPriceTermsForm errors={errors} fields={fields} onChange={setFields} />
          </div>
        ) : (
          <div className="mx-auto max-w-3xl space-y-6">
            <dl className="grid gap-px overflow-hidden rounded-lg border bg-border sm:grid-cols-2">
              <div className="bg-card p-4">
                <dt className="text-xs text-muted-foreground">Produit</dt>
                <dd className="mt-1 font-medium">{owner?.name}</dd>
                <dd className="mt-0.5 text-xs text-muted-foreground">
                  {productPriceOwner[ownerType]} · {owner?.revision}
                </dd>
              </div>
              <div className="bg-card p-4">
                <dt className="text-xs text-muted-foreground">Tarif</dt>
                <dd className="mt-1 text-xl font-semibold tabular-nums">{money(fields.amount, fields.currencyCode)}</dd>
                <dd className="mt-0.5 text-xs text-muted-foreground">{productPriceCycle[fields.billingCycle]}</dd>
              </div>
              <div className="bg-card p-4">
                <dt className="text-xs text-muted-foreground">Début</dt>
                <dd className="mt-1 text-sm font-medium">{new Date(fields.effectiveFrom).toLocaleString("fr-MA")}</dd>
              </div>
              <div className="bg-card p-4">
                <dt className="text-xs text-muted-foreground">Fin</dt>
                <dd className="mt-1 text-sm font-medium">
                  {fields.effectiveUntil ? new Date(fields.effectiveUntil).toLocaleString("fr-MA") : "Sans date de fin"}
                </dd>
              </div>
            </dl>
            <p className="text-sm text-muted-foreground">
              Le tarif sera créé en brouillon. Sa mise en vente demandera ensuite une prévisualisation serveur.
            </p>
          </div>
        )}
      </section>

      <div className="flex justify-between gap-3">
        <Button disabled={step === 0} onClick={() => setStep((current) => Math.max(0, current - 1))} variant="outline">
          Précédent
        </Button>
        {step === 0 ? (
          <Button disabled={!owner} onClick={() => setStep(1)}>
            Continuer
          </Button>
        ) : step === 1 ? (
          <Button onClick={continueFromTerms}>Vérifier</Button>
        ) : (
          <Button disabled={create.isPending} onClick={() => create.mutate()}>
            {create.isPending ? "Création…" : "Créer le brouillon"}
          </Button>
        )}
      </div>
    </div>
  );
}
