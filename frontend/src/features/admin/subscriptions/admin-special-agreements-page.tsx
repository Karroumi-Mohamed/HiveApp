import { ArrowLeftIcon } from "@phosphor-icons/react";
import { Link } from "react-router";
import { PageHeader } from "@/components/patterns/page-header";
import { Button } from "@/components/ui/button";
import { SpecialAgreementList } from "./special-agreement-list";

export function AdminSpecialAgreementsPage() {
  return (
    <div className="space-y-7">
      <Button asChild className="-ms-2" size="sm" variant="ghost">
        <Link to="/admin/subscriptions">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Abonnements
        </Link>
      </Button>
      <PageHeader title="Accords spéciaux" />
      <SpecialAgreementList />
    </div>
  );
}
