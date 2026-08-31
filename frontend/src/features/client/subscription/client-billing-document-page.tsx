import { useParams } from "react-router";
import { clientApi } from "@/api/client-api";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { clientBillingKeys } from "@/features/admin/billing/billing-query";
import { BillingDocumentPage } from "@/features/billing/billing-document-page";

export function ClientBillingDocumentPage() {
  const { invoiceId = "" } = useParams();
  const session = useClientSession();
  const context = { companyId: session.selectedCompanyId, isB2B: session.isB2B };
  return (
    <BillingDocumentPage
      backHref={`/app/subscription?tab=invoices&invoice=${invoiceId}`}
      enabled={Boolean(invoiceId) && session.can(clientPermissions.subscriptionReadInvoiceDocument)}
      load={() => clientApi.subscriptionInvoiceDocument(invoiceId)}
      queryKey={clientBillingKeys.document(context, invoiceId)}
    />
  );
}
