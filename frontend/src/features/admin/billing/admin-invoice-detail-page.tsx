import {
  ArrowClockwiseIcon,
  ArrowLeftIcon,
  BankIcon,
  FileTextIcon,
  HandCoinsIcon,
  ReceiptIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { BillingInvoiceDetail, BillingPayment, BillingRefundPreview } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { compareExactDecimals, formatExactMoney, isCommercialAmount } from "@/lib/exact-decimal";
import {
  billingCycleLabel,
  billingErrorMessage,
  billingLineTypeLabel,
  invoiceStatusPresentation,
  paymentStatusPresentation,
  refundStatusPresentation,
} from "./billing-presentation";
import { adminBillingKeys } from "./billing-query";

const dateTime = (value: string | null) =>
  value ? new Intl.DateTimeFormat("fr-MA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";

function MutationDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  pending,
  destructive = false,
  children,
  onConfirm,
  confirmDisabled,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel: string;
  pending: boolean;
  destructive?: boolean;
  children: React.ReactNode;
  onConfirm: () => void;
  confirmDisabled?: boolean;
}) {
  return (
    <Dialog onOpenChange={onOpenChange} open={open}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {children}
        <DialogFooter>
          <Button disabled={pending} onClick={() => onOpenChange(false)} variant="outline">
            Retour
          </Button>
          <Button
            disabled={pending || confirmDisabled}
            onClick={onConfirm}
            variant={destructive ? "destructive" : "default"}
          >
            {pending ? "Enregistrement…" : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ManualSettlementDialog({
  invoiceId,
  open,
  onOpenChange,
}: {
  invoiceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [reference, setReference] = useState("");
  const [reason, setReason] = useState("");
  const mutation = useMutation({
    mutationFn: () =>
      adminApi.settleBillingInvoiceManually(invoiceId, { reference: reference.trim(), reason: reason.trim() }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminBillingKeys.all });
      toast.success("Règlement manuel enregistré");
      setReference("");
      setReason("");
      onOpenChange(false);
    },
    onError: (error) => toast.error(billingErrorMessage(error)),
  });
  return (
    <MutationDialog
      confirmDisabled={!reference.trim() || !reason.trim()}
      confirmLabel="Enregistrer le règlement"
      description="Cette preuve externe solde la facture. Elle reste auditée avec votre justification."
      onConfirm={() => mutation.mutate()}
      onOpenChange={onOpenChange}
      open={open}
      pending={mutation.isPending}
      title="Règlement manuel"
    >
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="settlement-reference">Référence externe</Label>
          <Input
            id="settlement-reference"
            maxLength={255}
            onChange={(event) => setReference(event.target.value)}
            value={reference}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="settlement-reason">Justification</Label>
          <Textarea
            id="settlement-reason"
            maxLength={2000}
            onChange={(event) => setReason(event.target.value)}
            value={reason}
          />
        </div>
      </div>
    </MutationDialog>
  );
}

function CreditDialog({
  invoice,
  open,
  onOpenChange,
}: {
  invoice: BillingInvoiceDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [reference, setReference] = useState("");
  const validAmount = isCommercialAmount(amount) && compareExactDecimals(amount, "0") > 0;
  const mutation = useMutation({
    mutationFn: () =>
      adminApi.issueBillingCredit(invoice.invoice.id, {
        amount,
        currencyCode: invoice.invoice.currencyCode,
        reason: reason.trim(),
        source: "OPERATOR",
        externalReference: reference.trim() || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminBillingKeys.all });
      toast.success("Avoir émis");
      setAmount("");
      setReason("");
      setReference("");
      onOpenChange(false);
    },
    onError: (error) => toast.error(billingErrorMessage(error)),
  });
  return (
    <MutationDialog
      confirmDisabled={!validAmount || !reason.trim()}
      confirmLabel="Émettre l’avoir"
      description={`L’avoir réduit le solde de ${invoice.invoice.invoiceNumber}. Il ne déclenche pas un remboursement.`}
      onConfirm={() => mutation.mutate()}
      onOpenChange={onOpenChange}
      open={open}
      pending={mutation.isPending}
      title="Émettre un avoir"
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="credit-amount">Montant ({invoice.invoice.currencyCode})</Label>
          <Input
            id="credit-amount"
            inputMode="decimal"
            onChange={(event) => setAmount(event.target.value)}
            value={amount}
          />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="credit-reference">Référence externe (facultatif)</Label>
          <Input
            id="credit-reference"
            maxLength={255}
            onChange={(event) => setReference(event.target.value)}
            value={reference}
          />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="credit-reason">Justification</Label>
          <Textarea
            id="credit-reason"
            maxLength={2000}
            onChange={(event) => setReason(event.target.value)}
            value={reason}
          />
        </div>
      </div>
    </MutationDialog>
  );
}

function RefundDialog({
  payment,
  open,
  onOpenChange,
}: {
  payment: BillingPayment;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<"provider" | "manual">("provider");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [reference, setReference] = useState("");
  const preview = useQuery({
    queryKey: adminBillingKeys.refundPreview(payment.id),
    queryFn: () => adminApi.previewBillingRefund(payment.id),
    enabled: open && session.can(adminPermissions.billingPreviewRefund),
    retry: false,
  });
  const data: BillingRefundPreview | undefined = preview.data;
  const allowed = mode === "provider" ? data?.providerRefundAllowed : data?.manualRefundAllowed;
  const canExecute =
    mode === "provider"
      ? session.can(adminPermissions.billingCreateRefund)
      : session.can(adminPermissions.billingRecordManualRefund);
  const validAmount =
    isCommercialAmount(amount) &&
    compareExactDecimals(amount, "0") > 0 &&
    (!data || compareExactDecimals(amount, data.remainingRefundableAmount) <= 0);
  const mutation = useMutation({
    mutationFn: () => {
      const key = crypto.randomUUID();
      return mode === "provider"
        ? adminApi.requestBillingRefund(payment.id, key, {
            amount,
            currencyCode: payment.currencyCode,
            reason: reason.trim(),
          })
        : adminApi.recordManualBillingRefund(payment.id, key, {
            amount,
            currencyCode: payment.currencyCode,
            reason: reason.trim(),
            externalReference: reference.trim(),
          });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminBillingKeys.all });
      toast.success(mode === "provider" ? "Remboursement envoyé" : "Remboursement manuel enregistré");
      setAmount("");
      setReason("");
      setReference("");
      onOpenChange(false);
    },
    onError: (error) => toast.error(billingErrorMessage(error)),
  });
  const blocker = mode === "provider" ? data?.providerBlocker : data?.manualBlocker;
  return (
    <MutationDialog
      confirmDisabled={
        !data || !allowed || !canExecute || !validAmount || !reason.trim() || (mode === "manual" && !reference.trim())
      }
      confirmLabel={mode === "provider" ? "Envoyer le remboursement" : "Enregistrer le remboursement"}
      description="Le montant disponible est calculé par le backend et réserve aussi les remboursements en attente."
      destructive
      onConfirm={() => mutation.mutate()}
      onOpenChange={onOpenChange}
      open={open}
      pending={mutation.isPending}
      title="Rembourser le paiement"
    >
      <div className="space-y-4">
        {preview.isLoading ? (
          <LoadingState rows={2} />
        ) : preview.isError ? (
          <ErrorState retry={() => void preview.refetch()} />
        ) : data ? (
          <dl className="grid grid-cols-2 gap-4 border-y py-4 text-sm">
            <div>
              <dt className="text-muted-foreground">Paiement</dt>
              <dd className="mt-1 font-semibold">{formatExactMoney(data.paymentAmount, data.currencyCode)}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Encore remboursable</dt>
              <dd className="mt-1 font-semibold">
                {formatExactMoney(data.remainingRefundableAmount, data.currencyCode)}
              </dd>
            </div>
          </dl>
        ) : null}
        <div className="grid grid-cols-2 gap-2">
          <Button
            onClick={() => setMode("provider")}
            type="button"
            variant={mode === "provider" ? "secondary" : "ghost"}
          >
            Fournisseur
          </Button>
          <Button onClick={() => setMode("manual")} type="button" variant={mode === "manual" ? "secondary" : "ghost"}>
            Manuel
          </Button>
        </div>
        {blocker ? <p className="border-s-2 border-warning ps-3 text-sm text-warning">{blocker}</p> : null}
        <div className="space-y-2">
          <Label htmlFor="refund-amount">Montant ({payment.currencyCode})</Label>
          <Input
            id="refund-amount"
            inputMode="decimal"
            onChange={(event) => setAmount(event.target.value)}
            value={amount}
          />
        </div>
        {mode === "manual" ? (
          <div className="space-y-2">
            <Label htmlFor="refund-reference">Référence externe</Label>
            <Input
              id="refund-reference"
              maxLength={255}
              onChange={(event) => setReference(event.target.value)}
              value={reference}
            />
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor="refund-reason">Justification</Label>
          <Textarea
            id="refund-reason"
            maxLength={2000}
            onChange={(event) => setReason(event.target.value)}
            value={reason}
          />
        </div>
      </div>
    </MutationDialog>
  );
}

function RetryChargeDialog({
  invoiceId,
  open,
  onOpenChange,
}: {
  invoiceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [reason, setReason] = useState("");
  const [reference, setReference] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const preview = useQuery({
    queryKey: adminBillingKeys.retryPreview(invoiceId),
    queryFn: () => adminApi.previewBillingChargeRetry(invoiceId),
    enabled: open && session.can(adminPermissions.billingPreviewChargeRetry),
    retry: false,
  });
  const mutation = useMutation({
    mutationFn: () =>
      adminApi.retryBillingCharge(invoiceId, crypto.randomUUID(), {
        reason: reason.trim(),
        recoveryReference: reference.trim(),
        providerConfirmedNotCaptured: confirmed,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminBillingKeys.all });
      toast.success("Nouvelle tentative créée");
      setReason("");
      setReference("");
      setConfirmed(false);
      onOpenChange(false);
    },
    onError: (error) => toast.error(billingErrorMessage(error)),
  });
  const data = preview.data;
  return (
    <MutationDialog
      confirmDisabled={
        !data?.retryAllowed ||
        !session.can(adminPermissions.billingRetryCharge) ||
        !reason.trim() ||
        !reference.trim() ||
        (data.providerConfirmationRequired && !confirmed)
      }
      confirmLabel="Créer la nouvelle tentative"
      description="Une nouvelle tentative et une nouvelle commande fournisseur seront créées. L’échec précédent reste inchangé."
      onConfirm={() => mutation.mutate()}
      onOpenChange={onOpenChange}
      open={open}
      pending={mutation.isPending}
      title="Relancer l’encaissement"
    >
      <div className="space-y-4">
        {preview.isLoading ? (
          <LoadingState rows={2} />
        ) : preview.isError ? (
          <ErrorState retry={() => void preview.refetch()} />
        ) : data?.blocker ? (
          <p className="border-s-2 border-destructive ps-3 text-sm text-destructive">{data.blocker}</p>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor="retry-reference">Référence de récupération</Label>
          <Input
            id="retry-reference"
            maxLength={255}
            onChange={(event) => setReference(event.target.value)}
            value={reference}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="retry-reason">Justification</Label>
          <Textarea
            id="retry-reason"
            maxLength={2000}
            onChange={(event) => setReason(event.target.value)}
            value={reason}
          />
        </div>
        {data?.providerConfirmationRequired ? (
          <label className="flex items-start gap-3 border-y py-4 text-sm" htmlFor="provider-not-captured">
            <Checkbox
              checked={confirmed}
              id="provider-not-captured"
              onCheckedChange={(value) => setConfirmed(value === true)}
            />
            <span>Le fournisseur confirme que la tentative précédente n’a pas été encaissée.</span>
          </label>
        ) : null}
      </div>
    </MutationDialog>
  );
}

function InvoiceLines({ detail }: { detail: BillingInvoiceDetail }) {
  return (
    <section className="overflow-hidden rounded-xl border bg-card">
      <div className="border-b px-4 py-3">
        <h2 className="font-semibold">Lignes de facture</h2>
      </div>
      <div className="divide-y">
        {detail.lines.map((line) => (
          <div className="grid gap-2 px-4 py-4 sm:grid-cols-[1fr_auto_auto] sm:items-center sm:gap-8" key={line.id}>
            <div>
              <p className="font-medium">{line.sourceName}</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {billingLineTypeLabel[line.type]} · version {line.sourceVersion}
              </p>
            </div>
            <p className="text-sm text-muted-foreground">
              {line.quantity} × {formatExactMoney(line.unitAmount, line.currencyCode)}
            </p>
            <p className="font-semibold tabular-nums">{formatExactMoney(line.lineAmount, line.currencyCode)}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

export function AdminInvoiceDetailPage() {
  const { invoiceId = "" } = useParams();
  const session = useAdminSession();
  const [dialog, setDialog] = useState<"settlement" | "credit" | "retry" | null>(null);
  const [refundPayment, setRefundPayment] = useState<BillingPayment | null>(null);
  const canReadInvoice = session.can(adminPermissions.billingReadInvoice);
  const canReadAccount = session.can(adminPermissions.billingReadAccountIdentity);
  const canReadPayments = session.can(adminPermissions.billingReadPayments);
  const detail = useQuery({
    queryKey: adminBillingKeys.invoice(invoiceId),
    queryFn: () => adminApi.billingInvoice(invoiceId),
    enabled: Boolean(invoiceId) && canReadInvoice,
    retry: false,
  });
  const account = useQuery({
    queryKey: adminBillingKeys.account(invoiceId),
    queryFn: () => adminApi.billingInvoiceAccountIdentity(invoiceId),
    enabled: Boolean(invoiceId) && canReadAccount,
    retry: false,
  });
  const payments = useQuery({
    queryKey: adminBillingKeys.payments(invoiceId),
    queryFn: () => adminApi.billingInvoicePayments(invoiceId),
    enabled: Boolean(invoiceId) && canReadPayments,
    retry: false,
  });
  const invoice = detail.data?.invoice;
  const accountIdentity = account.data ?? invoice?.account ?? null;
  const status = invoice ? invoiceStatusPresentation[invoice.status] : null;
  const paymentRows = payments.data ?? detail.data?.payments ?? [];
  const loading =
    (canReadInvoice && detail.isLoading) ||
    (canReadAccount && account.isLoading) ||
    (canReadPayments && payments.isLoading);
  const error =
    (canReadInvoice && detail.isError) || (canReadAccount && account.isError) || (canReadPayments && payments.isError);
  return (
    <div className="space-y-7">
      <Button asChild size="sm" variant="ghost">
        <Link to="/admin/billing">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Facturation
        </Link>
      </Button>
      <PageHeader
        actions={
          invoice ||
          session.can(adminPermissions.billingReadInvoiceDocument) ||
          session.can(adminPermissions.billingManualSettlement) ||
          (session.can(adminPermissions.billingPreviewChargeRetry) &&
            session.can(adminPermissions.billingRetryCharge)) ? (
            <>
              {session.can(adminPermissions.billingReadInvoiceDocument) ? (
                <Button asChild variant="ghost">
                  <Link to={`/admin/billing/invoices/${invoiceId}/document`}>
                    <FileTextIcon />
                    Document
                  </Link>
                </Button>
              ) : null}
              {session.can(adminPermissions.billingManualSettlement) && (!invoice || invoice.status === "OPEN") ? (
                <Button onClick={() => setDialog("settlement")} variant="outline">
                  <BankIcon />
                  Règlement manuel
                </Button>
              ) : null}
              {invoice && session.can(adminPermissions.billingIssueCredit) ? (
                <Button onClick={() => setDialog("credit")} variant="outline">
                  <HandCoinsIcon />
                  Émettre un avoir
                </Button>
              ) : null}
              {session.can(adminPermissions.billingPreviewChargeRetry) &&
              session.can(adminPermissions.billingRetryCharge) ? (
                <Button onClick={() => setDialog("retry")}>
                  <ArrowClockwiseIcon />
                  Relancer l’encaissement
                </Button>
              ) : null}
            </>
          ) : undefined
        }
        title={invoice?.invoiceNumber ?? "Facture"}
      />
      {loading ? (
        <LoadingState rows={6} />
      ) : error ? (
        <ErrorState
          retry={() => {
            void detail.refetch();
            void account.refetch();
            void payments.refetch();
          }}
        />
      ) : !invoice && !accountIdentity && !paymentRows.length ? (
        <PermissionState />
      ) : (
        <>
          {invoice ? (
            <section className="overflow-hidden rounded-xl border bg-card">
              <div className="flex flex-col gap-3 border-b p-4 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <p className="text-2xl font-semibold tabular-nums">
                    {formatExactMoney(invoice.totalAmount, invoice.currencyCode)}
                  </p>
                  <p className="mt-1 text-sm text-muted-foreground">{accountIdentity?.name ?? "Compte masqué"}</p>
                </div>
                {status ? (
                  <StatusBadge dot={false} tone={status.tone}>
                    {status.label}
                  </StatusBadge>
                ) : null}
              </div>
              <dl className="grid divide-y sm:grid-cols-4 sm:divide-x sm:divide-y-0">
                <div className="p-4">
                  <dt className="text-xs text-muted-foreground">Cycle</dt>
                  <dd className="mt-1 font-medium">{billingCycleLabel[invoice.billingCycle]}</dd>
                </div>
                <div className="p-4">
                  <dt className="text-xs text-muted-foreground">Période</dt>
                  <dd className="mt-1 text-sm">
                    {dateTime(invoice.periodStart)} – {dateTime(invoice.periodEnd)}
                  </dd>
                </div>
                <div className="p-4">
                  <dt className="text-xs text-muted-foreground">Émise le</dt>
                  <dd className="mt-1 text-sm">{dateTime(invoice.issuedAt)}</dd>
                </div>
                <div className="p-4">
                  <dt className="text-xs text-muted-foreground">Réglée le</dt>
                  <dd className="mt-1 text-sm">{dateTime(invoice.settledAt)}</dd>
                </div>
              </dl>
            </section>
          ) : null}
          {!invoice && accountIdentity ? (
            <section className="rounded-xl border bg-card p-4">
              <p className="text-xs text-muted-foreground">Compte</p>
              <p className="mt-1 font-semibold">{accountIdentity.name}</p>
            </section>
          ) : null}
          {detail.data ? <InvoiceLines detail={detail.data} /> : null}
          {canReadPayments ? (
            <section className="overflow-hidden rounded-xl border bg-card">
              <div className="border-b px-4 py-3">
                <h2 className="font-semibold">Paiements</h2>
              </div>
              {paymentRows.length ? (
                <div className="divide-y">
                  {paymentRows.map((payment) => {
                    const presentation = paymentStatusPresentation[payment.status];
                    return (
                      <div
                        className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between"
                        key={payment.id}
                      >
                        <div className="min-w-0">
                          <div className="flex items-center gap-3">
                            <p className="font-medium">{formatExactMoney(payment.amount, payment.currencyCode)}</p>
                            <StatusBadge dot={false} tone={presentation.tone}>
                              {presentation.label}
                            </StatusBadge>
                          </div>
                          <p className="mt-1 text-xs text-muted-foreground">
                            {payment.kind === "PROVIDER" ? "Fournisseur" : "Manuel"} · {dateTime(payment.createdAt)}
                          </p>
                          {payment.externalReference ? (
                            <p className="mt-1 truncate font-mono text-xs text-muted-foreground">
                              {payment.externalReference}
                            </p>
                          ) : null}
                          {payment.failureReason ? (
                            <p className="mt-1 text-sm text-destructive">{payment.failureReason}</p>
                          ) : null}
                        </div>
                        {session.can(adminPermissions.billingPreviewRefund) ? (
                          <Button
                            disabled={payment.status !== "SUCCEEDED"}
                            onClick={() => setRefundPayment(payment)}
                            size="sm"
                            variant="ghost"
                          >
                            <ReceiptIcon />
                            Rembourser
                          </Button>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <p className="p-4 text-sm text-muted-foreground">Aucune tentative de paiement.</p>
              )}
            </section>
          ) : null}
          {detail.data && (detail.data.credits.length || detail.data.refunds.length) ? (
            <section className="overflow-hidden rounded-xl border bg-card">
              <div className="border-b px-4 py-3">
                <h2 className="font-semibold">Ajustements</h2>
              </div>
              <div className="divide-y">
                {detail.data.credits.map((credit) => (
                  <div className="flex justify-between gap-4 p-4" key={credit.id}>
                    <div>
                      <p className="font-medium">Avoir · {credit.reason}</p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {dateTime(credit.issuedAt)} · {credit.source}
                      </p>
                    </div>
                    <p className="font-semibold tabular-nums">
                      − {formatExactMoney(credit.amount, credit.currencyCode)}
                    </p>
                  </div>
                ))}
                {detail.data.refunds.map((refund) => {
                  const presentation = refundStatusPresentation[refund.status];
                  return (
                    <div className="flex justify-between gap-4 p-4" key={refund.id}>
                      <div>
                        <div className="flex items-center gap-3">
                          <p className="font-medium">Remboursement · {refund.reason}</p>
                          <StatusBadge dot={false} tone={presentation.tone}>
                            {presentation.label}
                          </StatusBadge>
                        </div>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {refund.kind === "PROVIDER" ? "Fournisseur" : "Manuel"} · {dateTime(refund.createdAt)}
                        </p>
                      </div>
                      <p className="font-semibold tabular-nums">
                        − {formatExactMoney(refund.amount, refund.currencyCode)}
                      </p>
                    </div>
                  );
                })}
              </div>
            </section>
          ) : null}
        </>
      )}
      {session.can(adminPermissions.billingManualSettlement) ? (
        <ManualSettlementDialog
          invoiceId={invoiceId}
          onOpenChange={(open) => !open && setDialog(null)}
          open={dialog === "settlement"}
        />
      ) : null}
      {detail.data ? (
        <CreditDialog
          invoice={detail.data}
          onOpenChange={(open) => !open && setDialog(null)}
          open={dialog === "credit"}
        />
      ) : null}
      {session.can(adminPermissions.billingPreviewChargeRetry) ? (
        <RetryChargeDialog
          invoiceId={invoiceId}
          onOpenChange={(open) => !open && setDialog(null)}
          open={dialog === "retry"}
        />
      ) : null}
      {refundPayment ? (
        <RefundDialog onOpenChange={(open) => !open && setRefundPayment(null)} open payment={refundPayment} />
      ) : null}
    </div>
  );
}
