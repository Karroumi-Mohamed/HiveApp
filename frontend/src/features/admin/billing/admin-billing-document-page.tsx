import { useParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { BillingDocumentPage } from "@/features/billing/billing-document-page";
import { adminBillingKeys } from "./billing-query";

export function AdminBillingDocumentPage() {
  const { invoiceId = "" } = useParams();
  const session = useAdminSession();
  return (
    <BillingDocumentPage
      backHref={`/admin/billing/invoices/${invoiceId}`}
      enabled={Boolean(invoiceId) && session.can(adminPermissions.billingReadInvoiceDocument)}
      load={() => adminApi.billingInvoiceDocument(invoiceId)}
      queryKey={adminBillingKeys.document(invoiceId)}
    />
  );
}
