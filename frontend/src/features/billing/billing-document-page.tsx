import { ArrowLeftIcon, PrinterIcon, WarningIcon } from "@phosphor-icons/react";
import type { QueryKey } from "@tanstack/react-query";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import type { BillingDocumentParty, BillingInvoiceDocument } from "@/api/contracts";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { billingCycleLabel } from "@/features/admin/billing/billing-presentation";
import { formatExactMoney, subtractExactDecimals } from "@/lib/exact-decimal";

type Props = {
  backHref: string;
  enabled: boolean;
  load: () => Promise<BillingInvoiceDocument>;
  queryKey: QueryKey;
};

const date = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "long" }).format(new Date(value)) : "—";

function Party({ party, title }: { party: BillingDocumentParty; title: string }) {
  return (
    <section>
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</p>
      <p className="mt-2 font-semibold">{party.name}</p>
      {party.address ? <p className="mt-1 whitespace-pre-line text-sm">{party.address}</p> : null}
      {party.countryCode ? <p className="text-sm">{party.countryCode}</p> : null}
      {party.billingEmail ? <p className="mt-1 text-sm">{party.billingEmail}</p> : null}
      {party.taxId ? <p className="mt-1 text-sm text-muted-foreground">ID fiscal · {party.taxId}</p> : null}
    </section>
  );
}

const fiscalLabels: Record<string, string> = {
  SELLER_ADDRESS: "adresse de l’émetteur",
  SELLER_COUNTRY: "pays de l’émetteur",
  SELLER_TAX_ID: "identifiant fiscal de l’émetteur",
  CUSTOMER_ADDRESS: "adresse du client",
  CUSTOMER_COUNTRY: "pays du client",
  CUSTOMER_TAX_ID: "identifiant fiscal du client",
  TAX_CALCULATION_NOT_IMPLEMENTED: "calcul et ventilation des taxes",
  JURISDICTIONAL_NUMBERING_NOT_IMPLEMENTED: "numérotation fiscale conforme à la juridiction",
};

export function BillingDocumentPage({ backHref, enabled, load, queryKey }: Props) {
  const document = useQuery({ queryKey, queryFn: load, enabled, retry: false });
  if (document.isLoading) return <LoadingState rows={8} />;
  if (document.isError) return <ErrorState retry={() => void document.refetch()} title="Document indisponible" />;
  if (!document.data) return null;
  const data = document.data;
  const netAfterCredits = subtractExactDecimals(data.invoice.totalAmount, data.creditedAmount);
  return (
    <div className="billing-document mx-auto max-w-5xl space-y-6">
      <div className="billing-document-actions flex items-center justify-between gap-4">
        <Button asChild size="sm" variant="ghost">
          <Link to={backHref}>
            <ArrowLeftIcon className="rtl:rotate-180" />
            Retour
          </Link>
        </Button>
        <Button onClick={() => window.print()}>
          <PrinterIcon />
          Imprimer
        </Button>
      </div>
      {!data.fiscalReady ? (
        <div
          className="billing-document-warning border-s-2 border-warning bg-warning-subtle px-4 py-3 text-sm"
          role="status"
        >
          <div className="flex gap-2 font-semibold text-warning">
            <WarningIcon className="mt-0.5 shrink-0" />
            Document commercial — ce document n’est pas une facture fiscale conforme.
          </div>
          <p className="mt-1 text-muted-foreground">
            Éléments encore requis : {data.missingFiscalFields.map((item) => fiscalLabels[item] ?? item).join(", ")}.
          </p>
        </div>
      ) : null}
      <article className="rounded-xl border bg-card p-6 sm:p-10">
        <header className="flex flex-col gap-6 border-b pb-8 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Document commercial</p>
            <h1 className="mt-2 text-3xl font-semibold">{data.invoice.invoiceNumber}</h1>
          </div>
          <dl className="grid grid-cols-[auto_auto] gap-x-5 gap-y-1 text-sm sm:text-end">
            <dt className="text-muted-foreground">Émission</dt>
            <dd>{date(data.invoice.issuedAt)}</dd>
            <dt className="text-muted-foreground">Période</dt>
            <dd>
              {date(data.invoice.periodStart)} – {date(data.invoice.periodEnd)}
            </dd>
            <dt className="text-muted-foreground">Cycle</dt>
            <dd>{billingCycleLabel[data.invoice.billingCycle]}</dd>
            <dt className="text-muted-foreground">État</dt>
            <dd>{data.invoice.status}</dd>
          </dl>
        </header>
        <div className="grid gap-8 border-b py-8 sm:grid-cols-2">
          <Party party={data.seller} title="Émetteur" />
          <Party party={data.customer} title="Client" />
        </div>
        <div className="overflow-x-auto py-8">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-start text-muted-foreground">
                <th className="pb-3 text-start font-medium">Désignation</th>
                <th className="pb-3 text-end font-medium">Qté</th>
                <th className="pb-3 text-end font-medium">Prix unitaire</th>
                <th className="pb-3 text-end font-medium">Montant</th>
              </tr>
            </thead>
            <tbody>
              {data.lines.map((line) => (
                <tr className="border-b" key={line.id}>
                  <td className="py-4 font-medium">{line.sourceName}</td>
                  <td className="py-4 text-end tabular-nums">{line.quantity}</td>
                  <td className="py-4 text-end tabular-nums">{formatExactMoney(line.unitAmount, line.currencyCode)}</td>
                  <td className="py-4 text-end font-semibold tabular-nums">
                    {formatExactMoney(line.lineAmount, line.currencyCode)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <dl className="ms-auto grid max-w-sm grid-cols-[1fr_auto] gap-x-8 gap-y-2 border-t pt-6 text-sm">
          <dt className="text-muted-foreground">Total émis</dt>
          <dd className="text-end tabular-nums">
            {formatExactMoney(data.invoice.totalAmount, data.invoice.currencyCode)}
          </dd>
          <dt className="text-muted-foreground">Avoirs</dt>
          <dd className="text-end tabular-nums">
            − {formatExactMoney(data.creditedAmount, data.invoice.currencyCode)}
          </dd>
          <dt className="text-muted-foreground">Remboursements</dt>
          <dd className="text-end tabular-nums">
            − {formatExactMoney(data.refundedAmount, data.invoice.currencyCode)}
          </dd>
          <dt className="border-t pt-3 font-semibold">Montant après avoirs</dt>
          <dd className="border-t pt-3 text-end text-lg font-semibold tabular-nums">
            {formatExactMoney(netAfterCredits, data.invoice.currencyCode)}
          </dd>
        </dl>
      </article>
    </div>
  );
}
