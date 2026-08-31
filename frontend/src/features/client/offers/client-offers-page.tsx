import {
  ArrowLeftIcon,
  ArrowRightIcon,
  CheckCircleIcon,
  KeyIcon,
  TagIcon,
  WarningCircleIcon,
} from "@phosphor-icons/react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { Link, Navigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { clientOfferApi } from "@/api/client-offer-api";
import { ApiError } from "@/api/http";
import type { OfferClient, OfferCodeResolution, OfferEligibilityPreview } from "@/api/offer-contracts";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { SectionTabs } from "@/components/patterns/section-tabs";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { newIdempotencyKey } from "@/features/admin/commercial-offers/offer-rules";
import { clientCommercialKeys } from "@/features/commercial/commercial-query";

const dateTime = (value: string | null | undefined) =>
  value ? new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";
const money = (value: string, currency: string) =>
  Number.isFinite(Number(value))
    ? new Intl.NumberFormat("fr-FR", { style: "currency", currency }).format(Number(value))
    : `${value} ${currency}`;

function clientContext(session: ReturnType<typeof useClientSession>) {
  return {
    companyId: session.selectedCompanyId ?? null,
    isB2B: window.localStorage.getItem("hiveapp-b2b-mode") === "true",
  };
}

function discountLabel(offer: OfferClient) {
  if (offer.discountType === "NONE") return "Conditions exclusives";
  if (offer.discountType === "FIXED") return `${offer.discountAmount} de réduction`;
  return `${offer.percentage}% de réduction, plafonnée à ${offer.percentageCap}`;
}

function OfferTerms({ offer }: { offer: OfferClient }) {
  return (
    <dl className="grid gap-x-8 gap-y-5 border-y py-5 sm:grid-cols-2">
      <div>
        <dt className="text-xs text-muted-foreground">Forfait</dt>
        <dd className="mt-1 font-medium">{offer.selection.plan.name}</dd>
        <dd className="text-xs text-muted-foreground">
          {money(offer.selection.plan.amount, offer.selection.plan.currencyCode)} · {offer.selection.plan.billingCycle}
        </dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Application</dt>
        <dd className="mt-1 font-medium">
          {offer.selection.timing === "IMMEDIATE" ? "Immédiate" : "Au renouvellement"}
        </dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Add-ons</dt>
        <dd className="mt-1 text-sm">
          {offer.selection.addOns.length
            ? offer.selection.addOns
                .map((item) => `${item.name}${item.pricingMode === "FREE" ? " (offert)" : ""}`)
                .join(", ")
            : "Aucun"}
        </dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Packs de capacité</dt>
        <dd className="mt-1 text-sm">
          {offer.selection.quotaPackages.length
            ? offer.selection.quotaPackages
                .map((item) => `${item.quantity ?? 1} × ${item.name}${item.pricingMode === "FREE" ? " (offert)" : ""}`)
                .join(", ")
            : "Aucun"}
        </dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Avantage</dt>
        <dd className="mt-1 font-medium">{discountLabel(offer)}</dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Expire</dt>
        <dd className="mt-1 font-medium">{dateTime(offer.endsAt)}</dd>
      </div>
    </dl>
  );
}

function AcceptanceReview({ offer, discoveryToken }: { offer: OfferClient; discoveryToken?: string | null }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const [previewState, setPreviewState] = useState<OfferEligibilityPreview | null>(null);
  const key = useRef(newIdempotencyKey());
  const preview = useMutation({
    mutationFn: () => clientOfferApi.preview(offer.id, discoveryToken),
    onSuccess: (data) => {
      setPreviewState(data);
      key.current = newIdempotencyKey();
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "L’offre ne peut pas être vérifiée."),
  });
  const accept = useMutation({
    mutationFn: () =>
      previewState
        ? clientOfferApi.accept(offer.id, previewState.previewToken, key.current)
        : Promise.reject(new Error("Preview required")),
    onSuccess: async (result) => {
      setPreviewState(null);
      await queryClient.invalidateQueries({
        queryKey: clientCommercialKeys.offers.history(clientContext(session), {}),
      });
      toast.success(
        result.nextAction === "RETRY_LATER" ? "Acceptation enregistrée; traitement en cours" : "Offre acceptée",
      );
    },
    onError: (error) => {
      if (error instanceof ApiError) key.current = newIdempotencyKey();
      toast.error(
        error instanceof ApiError ? error.message : "Résultat réseau incertain. Réessayez sans changer la demande.",
      );
    },
  });
  if (!session.can(clientPermissions.subscriptionOfferPreview)) return <PermissionState />;
  return (
    <section className="space-y-4">
      <Button disabled={preview.isPending} onClick={() => preview.mutate()} variant="outline">
        {preview.isPending ? "Vérification…" : previewState ? "Recalculer" : "Vérifier mon abonnement"}
      </Button>
      {previewState ? (
        <div className="space-y-4">
          <Alert>
            <CheckCircleIcon />
            <AlertTitle>Offre applicable</AlertTitle>
            <AlertDescription>
              Prix final: {money(previewState.finalPrice, previewState.currencyCode)}.{" "}
              {previewState.change.timing === "IMMEDIATE" ? "Application immédiate." : "Application au renouvellement."}
            </AlertDescription>
          </Alert>
          <dl className="grid gap-4 border-y py-4 sm:grid-cols-3">
            <div>
              <dt className="text-xs text-muted-foreground">Catalogue</dt>
              <dd className="mt-1 font-medium">{money(previewState.catalogueSubtotal, previewState.currencyCode)}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Après règles</dt>
              <dd className="mt-1 font-medium">{money(previewState.policyPrice, previewState.currencyCode)}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Avec l’offre</dt>
              <dd className="mt-1 font-semibold">{money(previewState.finalPrice, previewState.currencyCode)}</dd>
            </div>
          </dl>
          {previewState.change.checkoutRequired ? (
            <Alert variant="destructive">
              <WarningCircleIcon />
              <AlertTitle>Paiement non disponible</AlertTitle>
              <AlertDescription>
                Cette offre nécessite un paiement. Le paiement automatisé sera branché avec le contrôle de facturation;
                l’acceptation reste bloquée pour éviter une activation gratuite.
              </AlertDescription>
            </Alert>
          ) : session.can(clientPermissions.subscriptionOfferAccept) ? (
            <Button
              disabled={accept.isPending || Date.parse(previewState.expiresAt) <= Date.now()}
              onClick={() => accept.mutate()}
            >
              {accept.isPending ? "Acceptation…" : "Accepter l’offre"}
            </Button>
          ) : (
            <PermissionState />
          )}
        </div>
      ) : null}
    </section>
  );
}

function CataloguePanel() {
  const session = useClientSession();
  const [params, setParams] = useSearchParams();
  const page = params.get("page") && /^\d+$/.test(params.get("page") ?? "") ? Number(params.get("page")) : 0;
  const context = clientContext(session);
  const catalogue = useQuery({
    queryKey: clientCommercialKeys.offers.catalogue(context, { page }),
    queryFn: ({ signal }) => clientOfferApi.catalogue({ page, size: 12, sort: "endsAt", direction: "asc" }, { signal }),
    enabled: session.can(clientPermissions.subscriptionOfferCatalog),
    placeholderData: keepPreviousData,
  });
  if (!session.can(clientPermissions.subscriptionOfferCatalog)) return <PermissionState />;
  if (catalogue.isLoading) return <LoadingState />;
  if (catalogue.isError || !catalogue.data) return <ErrorState retry={() => void catalogue.refetch()} />;
  if (!catalogue.data.totalElements)
    return (
      <EmptyState
        description="Aucune offre publique n’est actuellement applicable à ce compte."
        title="Aucune offre disponible"
      />
    );
  return (
    <section className="border-y">
      <ul className="divide-y">
        {catalogue.data.content.map((offer) => (
          <li className="grid gap-4 py-5 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center" key={offer.id}>
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="font-semibold">{offer.name}</h2>
                <StatusBadge dot={false} tone="success">
                  {discountLabel(offer)}
                </StatusBadge>
              </div>
              {offer.description ? (
                <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{offer.description}</p>
              ) : null}
              <p className="mt-2 text-xs text-muted-foreground">
                {offer.selection.plan.name} · expire {dateTime(offer.endsAt)}
              </p>
            </div>
            {session.can(clientPermissions.subscriptionOfferDetail) ? (
              <Button asChild size="sm" variant="ghost">
                <Link to={`/app/offers/${offer.id}`}>
                  Voir l’offre
                  <ArrowRightIcon className="rtl:rotate-180" />
                </Link>
              </Button>
            ) : null}
          </li>
        ))}
      </ul>
      <PaginationBar
        onPageChange={(nextPage) => {
          const next = new URLSearchParams(params);
          if (nextPage) next.set("page", String(nextPage));
          else next.delete("page");
          setParams(next, { replace: true });
        }}
        page={catalogue.data.page}
        totalElements={catalogue.data.totalElements}
        totalPages={catalogue.data.totalPages}
      />
    </section>
  );
}

function CodePanel() {
  const session = useClientSession();
  const [code, setCode] = useState("");
  const [resolution, setResolution] = useState<OfferCodeResolution | null>(null);
  const resolve = useMutation({
    mutationFn: () => clientOfferApi.resolveCode(code.trim()),
    onSuccess: setResolution,
    onError: (error) => {
      setResolution(null);
      toast.error(error instanceof ApiError ? error.message : "Ce code n’a pas pu être vérifié.");
    },
  });
  if (!session.can(clientPermissions.subscriptionOfferCode)) return <PermissionState />;
  return (
    <section className="space-y-6">
      <div className="max-w-lg space-y-3">
        <Label htmlFor="offer-private-code">Code de l’offre</Label>
        <div className="flex gap-2">
          <Input
            autoComplete="off"
            id="offer-private-code"
            maxLength={64}
            onChange={(event) => {
              setCode(event.target.value);
              setResolution(null);
            }}
            value={code}
          />
          <Button disabled={!code.trim() || resolve.isPending} onClick={() => resolve.mutate()}>
            <KeyIcon />
            {resolve.isPending ? "Vérification…" : "Vérifier"}
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          Le code reste en mémoire uniquement le temps de cette vérification.
        </p>
      </div>
      {resolution ? (
        <div className="space-y-6">
          <div>
            <h2 className="text-lg font-semibold">{resolution.offer.name}</h2>
            {resolution.offer.description ? (
              <p className="mt-1 text-sm text-muted-foreground">{resolution.offer.description}</p>
            ) : null}
          </div>
          <OfferTerms offer={resolution.offer} />
          <AcceptanceReview discoveryToken={resolution.discoveryToken} offer={resolution.offer} />
        </div>
      ) : null}
    </section>
  );
}

function HistoryPanel() {
  const session = useClientSession();
  const [params, setParams] = useSearchParams();
  const page = params.get("page") && /^\d+$/.test(params.get("page") ?? "") ? Number(params.get("page")) : 0;
  const context = clientContext(session);
  const history = useQuery({
    queryKey: clientCommercialKeys.offers.history(context, { page }),
    queryFn: ({ signal }) =>
      clientOfferApi.history({ page, size: 20, sort: "reservedAt", direction: "desc" }, { signal }),
    enabled: session.can(clientPermissions.subscriptionOfferHistory),
    placeholderData: keepPreviousData,
  });
  if (!session.can(clientPermissions.subscriptionOfferHistory)) return <PermissionState />;
  if (history.isLoading) return <LoadingState />;
  if (history.isError || !history.data) return <ErrorState retry={() => void history.refetch()} />;
  if (!history.data.totalElements) return <EmptyState title="Aucune offre acceptée" />;
  return (
    <section className="border-y">
      <ul className="divide-y">
        {history.data.content.map((item) => (
          <li className="grid gap-3 py-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center" key={item.id}>
            <div>
              <p className="font-medium">{item.offerName}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                {dateTime(item.reservedAt)} · R{item.offerRevisionNumber}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <StatusBadge
                dot={false}
                tone={item.status === "APPLIED" ? "success" : item.status === "FAILED" ? "warning" : "neutral"}
              >
                {item.status === "RESERVED"
                  ? "En cours"
                  : item.status === "APPLIED"
                    ? "Appliquée"
                    : item.status === "CANCELLED"
                      ? "Annulée"
                      : "Échouée"}
              </StatusBadge>
              {session.can(clientPermissions.subscriptionOfferHistoryDetail) ? (
                <Button asChild size="icon-sm" title="Ouvrir">
                  <Link to={`/app/offers/redemptions/${item.id}`}>
                    <ArrowRightIcon className="rtl:rotate-180" />
                  </Link>
                </Button>
              ) : null}
            </div>
          </li>
        ))}
      </ul>
      <PaginationBar
        onPageChange={(nextPage) => {
          const next = new URLSearchParams(params);
          if (nextPage) next.set("page", String(nextPage));
          else next.delete("page");
          setParams(next, { replace: true });
        }}
        page={history.data.page}
        totalElements={history.data.totalElements}
        totalPages={history.data.totalPages}
      />
    </section>
  );
}

function OfferDetail({ offerId }: { offerId: string }) {
  const session = useClientSession();
  const context = clientContext(session);
  const detail = useQuery({
    queryKey: clientCommercialKeys.offers.detail(context, offerId),
    queryFn: ({ signal }) => clientOfferApi.detail(offerId, { signal }),
    enabled: session.can(clientPermissions.subscriptionOfferDetail),
  });
  if (!session.can(clientPermissions.subscriptionOfferDetail)) return <PermissionState />;
  if (detail.isLoading) return <LoadingState />;
  if (detail.isError || !detail.data)
    return <ErrorState retry={() => void detail.refetch()} title="Offre indisponible" />;
  return (
    <div className="space-y-7">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to="/app/offers">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Offres
        </Link>
      </Button>
      <PageHeader title={detail.data.name} />
      {detail.data.description ? (
        <p className="max-w-3xl text-sm text-muted-foreground">{detail.data.description}</p>
      ) : null}
      <OfferTerms offer={detail.data} />
      <AcceptanceReview offer={detail.data} />
    </div>
  );
}

function RedemptionDetail({ redemptionId }: { redemptionId: string }) {
  const session = useClientSession();
  const context = clientContext(session);
  const detail = useQuery({
    queryKey: clientCommercialKeys.offers.redemption(context, redemptionId),
    queryFn: ({ signal }) => clientOfferApi.redemption(redemptionId, { signal }),
    enabled: session.can(clientPermissions.subscriptionOfferHistoryDetail),
    refetchInterval: (query) => (query.state.data?.status === "RESERVED" ? 5000 : false),
  });
  if (!session.can(clientPermissions.subscriptionOfferHistoryDetail)) return <PermissionState />;
  if (detail.isLoading) return <LoadingState />;
  if (detail.isError || !detail.data)
    return <ErrorState retry={() => void detail.refetch()} title="Acceptation introuvable" />;
  const item = detail.data;
  return (
    <div className="space-y-7">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to="/app/offers?tab=history">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Historique
        </Link>
      </Button>
      <PageHeader title={item.offerName} />
      <dl className="grid gap-x-8 gap-y-5 border-y py-5 sm:grid-cols-2">
        <div>
          <dt className="text-xs text-muted-foreground">Statut</dt>
          <dd className="mt-1 font-medium">
            {item.status === "RESERVED"
              ? "Traitement en cours"
              : item.status === "APPLIED"
                ? "Appliquée"
                : item.status === "CANCELLED"
                  ? "Annulée"
                  : "Échouée"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Réservée</dt>
          <dd className="mt-1 font-medium">{dateTime(item.reservedAt)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Prix accepté</dt>
          <dd className="mt-1 font-medium">{money(item.acceptedTerms.finalPrice, item.acceptedTerms.currencyCode)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Forfait</dt>
          <dd className="mt-1 font-medium">
            {item.selection.plan.name} · R{item.selection.plan.revisionNumber}
          </dd>
        </div>
      </dl>
      {item.operation ? (
        <Alert>
          <AlertTitle>Modification d’abonnement</AlertTitle>
          <AlertDescription>
            {item.operation.sourcePlanCode} → {item.operation.targetPlanCode} · {item.operation.status}
          </AlertDescription>
        </Alert>
      ) : item.status === "RESERVED" ? (
        <Alert>
          <AlertTitle>Traitement en cours</AlertTitle>
          <AlertDescription>Cette page se met à jour pendant la réservation.</AlertDescription>
        </Alert>
      ) : null}
    </div>
  );
}

export function ClientOffersPage() {
  const session = useClientSession();
  const { offerId, redemptionId } = useParams();
  const [params, setParams] = useSearchParams();
  if (redemptionId) return <RedemptionDetail redemptionId={redemptionId} />;
  if (offerId) return <OfferDetail offerId={offerId} />;
  const requested = params.get("tab");
  const available = [
    session.can(clientPermissions.subscriptionOfferCatalog) ? "catalogue" : null,
    session.can(clientPermissions.subscriptionOfferCode) ? "code" : null,
    session.can(clientPermissions.subscriptionOfferHistory) ? "history" : null,
  ].filter((item): item is string => Boolean(item));
  const tab = requested && available.includes(requested) ? requested : available[0];
  if (!tab) return <PermissionState />;
  if (requested && requested !== tab) return <Navigate replace to={`/app/offers?tab=${tab}`} />;
  const items = [
    { value: "catalogue", label: "Catalogue", icon: TagIcon },
    { value: "code", label: "Code privé", icon: KeyIcon },
    { value: "history", label: "Historique", icon: CheckCircleIcon },
  ].filter((item) => available.includes(item.value));
  return (
    <div className="space-y-7">
      <PageHeader title="Offres" />
      <SectionTabs
        ariaLabel="Sections des offres"
        items={items.map(({ value, label }) => ({ value, label }))}
        onValueChange={(value) => {
          const next = new URLSearchParams();
          if (value !== "catalogue") next.set("tab", value);
          setParams(next, { replace: true });
        }}
        value={tab}
      />
      {tab === "catalogue" ? <CataloguePanel /> : tab === "code" ? <CodePanel /> : <HistoryPanel />}
    </div>
  );
}
