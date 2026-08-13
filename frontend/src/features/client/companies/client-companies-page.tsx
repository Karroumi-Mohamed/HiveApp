import { ArrowLeftIcon, PlusIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { clientApi } from "@/api/client-api";
import type { Company } from "@/api/contracts";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";

function CompanyForm({ company, onDone }: { company?: Company; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(company?.name ?? "");
  const [legalName, setLegalName] = useState(company?.legalName ?? "");
  const [taxId, setTaxId] = useState(company?.taxId ?? "");
  const [industry, setIndustry] = useState(company?.industry ?? "");
  const [country, setCountry] = useState(company?.country ?? "MA");
  const [address, setAddress] = useState(company?.address ?? "");
  const [logoUrl, setLogoUrl] = useState(company?.logoUrl ?? "");
  const save = useMutation({
    mutationFn: () => {
      const input = {
        name,
        legalName: legalName || null,
        taxId: taxId || null,
        industry: industry || null,
        country,
        address: address || null,
        logoUrl: logoUrl || null,
      };
      return company ? clientApi.updateCompany(company.id, input) : clientApi.createCompany(input);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "companies"] });
      toast.success(company ? "Entreprise mise à jour" : "Entreprise créée");
      onDone();
    },
  });
  return (
    <form
      className="space-y-4"
      onSubmit={(event: FormEvent) => {
        event.preventDefault();
        save.mutate();
      }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="company-name">Nom</Label>
          <Input id="company-name" onChange={(event) => setName(event.target.value)} required value={name} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="company-legal-name">Raison sociale</Label>
          <Input id="company-legal-name" onChange={(event) => setLegalName(event.target.value)} value={legalName} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="company-tax">Identifiant fiscal</Label>
          <Input id="company-tax" onChange={(event) => setTaxId(event.target.value)} value={taxId} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="company-industry">Secteur</Label>
          <Input id="company-industry" onChange={(event) => setIndustry(event.target.value)} value={industry} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="company-country">Pays (ISO)</Label>
          <Input
            id="company-country"
            maxLength={2}
            onChange={(event) => setCountry(event.target.value.toUpperCase())}
            required
            value={country}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="company-logo">URL du logo</Label>
          <Input id="company-logo" onChange={(event) => setLogoUrl(event.target.value)} type="url" value={logoUrl} />
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="company-address">Adresse</Label>
        <Textarea id="company-address" onChange={(event) => setAddress(event.target.value)} value={address} />
      </div>
      <div className="flex justify-end">
        <Button disabled={save.isPending} type="submit">
          {save.isPending ? "Enregistrement…" : "Enregistrer"}
        </Button>
      </div>
    </form>
  );
}

function CompanyDialog({ company }: { company?: Company }) {
  const session = useClientSession();
  const [open, setOpen] = useState(false);
  const allowed = session.can(company ? clientPermissions.companiesUpdate : clientPermissions.companiesCreate);
  if (!allowed) return null;
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button variant={company ? "outline" : "default"}>
          {company ? (
            "Modifier"
          ) : (
            <>
              <PlusIcon />
              Créer une entreprise
            </>
          )}
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{company ? "Modifier l’entreprise" : "Nouvelle entreprise"}</DialogTitle>
        </DialogHeader>
        <CompanyForm company={company} onDone={() => setOpen(false)} />
      </DialogContent>
    </Dialog>
  );
}

function CompanyDetail({ id }: { id: string }) {
  const session = useClientSession();
  const queryClient = useQueryClient();
  const company = useQuery({ queryKey: ["client", "companies", id], queryFn: () => clientApi.company(id) });
  const transition = useMutation({
    mutationFn: async () => {
      if (company.data?.isActive) await clientApi.deactivateCompany(id);
      else await clientApi.reactivateCompany(id);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["client", "companies"] });
      toast.success(company.data?.isActive ? "Entreprise désactivée" : "Entreprise réactivée");
    },
  });
  if (company.isLoading) return <LoadingState />;
  if (!company.data || company.isError) return <ErrorState retry={() => void company.refetch()} />;
  return (
    <div className="space-y-7">
      <Button asChild size="sm" variant="ghost">
        <Link to="/app/companies">
          <ArrowLeftIcon className="rtl:rotate-180" />
          Entreprises
        </Link>
      </Button>
      <PageHeader
        actions={
          <>
            <CompanyDialog company={company.data} />
            {session.can(
              company.data.isActive ? clientPermissions.companiesDelete : clientPermissions.companiesReactivate,
            ) ? (
              <Button onClick={() => transition.mutate()} variant={company.data.isActive ? "destructive" : "outline"}>
                {company.data.isActive ? "Désactiver" : "Réactiver"}
              </Button>
            ) : null}
          </>
        }
        title={company.data.name}
      />
      <section className="rounded-xl border bg-card">
        <dl className="grid gap-px overflow-hidden sm:grid-cols-2 lg:grid-cols-3">
          {[
            ["Raison sociale", company.data.legalName],
            ["Identifiant fiscal", company.data.taxId],
            ["Secteur", company.data.industry],
            ["Pays", company.data.country],
            ["Adresse", company.data.address],
            ["Statut", company.data.isActive ? "Active" : "Inactive"],
          ].map(([label, value]) => (
            <div className="bg-card p-5" key={label}>
              <dt className="text-xs text-muted-foreground">{label}</dt>
              <dd className="mt-1 font-medium">{value || "—"}</dd>
            </div>
          ))}
        </dl>
      </section>
      {company.data.warnings.length ? (
        <section className="rounded-xl border border-warning/30 bg-warning/5 p-5">
          <h2 className="text-sm font-semibold">Points d’attention</h2>
          <ul className="mt-3 list-disc space-y-1 ps-5 text-sm">
            {company.data.warnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

export function ClientCompaniesPage() {
  const session = useClientSession();
  const { companyId } = useParams();
  const companies = useQuery({ queryKey: ["client", "companies"], queryFn: clientApi.companies });
  if (companyId) return <CompanyDetail id={companyId} />;
  return (
    <div className="space-y-7">
      <PageHeader actions={<CompanyDialog />} title="Entreprises" />
      <section className="overflow-hidden rounded-xl border bg-card">
        {companies.isLoading ? (
          <div className="p-5">
            <LoadingState />
          </div>
        ) : companies.isError ? (
          <ErrorState retry={() => void companies.refetch()} />
        ) : !companies.data?.length ? (
          <EmptyState title="Aucune entreprise" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Entreprise</TableHead>
                <TableHead>Secteur</TableHead>
                <TableHead>Pays</TableHead>
                <TableHead>Statut</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {companies.data.map((company) => (
                <TableRow key={company.id}>
                  <TableCell>
                    {session.can(clientPermissions.companyDetailRead) ? (
                      <Link className="font-medium hover:underline" to={`/app/companies/${company.id}`}>
                        {company.name}
                      </Link>
                    ) : (
                      <span className="font-medium">{company.name}</span>
                    )}
                    {company.legalName ? <p className="text-xs text-muted-foreground">{company.legalName}</p> : null}
                  </TableCell>
                  <TableCell>{company.industry ?? "—"}</TableCell>
                  <TableCell>{company.country ?? "—"}</TableCell>
                  <TableCell>
                    <StatusBadge tone={company.isActive ? "success" : "danger"}>
                      {company.isActive ? "Active" : "Inactive"}
                    </StatusBadge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>
    </div>
  );
}
