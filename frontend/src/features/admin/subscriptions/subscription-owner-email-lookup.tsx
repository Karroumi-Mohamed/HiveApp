import { ArrowRightIcon, MagnifyingGlassIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";
import { Link } from "react-router";
import { adminApi } from "@/api/admin-api";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { StatusText } from "@/components/patterns/status-text";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { adminCommercialKeys } from "@/features/commercial/commercial-query";

const pageSize = 10;
const exactEmailPattern = /^[^\s@]+@[^\s@]+$/u;

type SubmittedLookup = Readonly<{ ownerEmail: string; page: number }>;

function normalizedOwnerEmail(value: string) {
  return value.trim().toLocaleLowerCase("en-US");
}

function OwnerEmailLookupDialog({ canOpenAccount }: { canOpenAccount: boolean }) {
  const [open, setOpen] = useState(false);
  const [ownerEmail, setOwnerEmail] = useState("");
  const [submitted, setSubmitted] = useState<SubmittedLookup | null>(null);
  const [attempted, setAttempted] = useState(false);
  const normalizedEmail = normalizedOwnerEmail(ownerEmail);
  const validEmail = exactEmailPattern.test(normalizedEmail);
  const lookup = submitted ?? { ownerEmail: "", page: 0 };
  const results = useQuery({
    queryKey: adminCommercialKeys.subscriptions.accountOwnerLookup(lookup),
    queryFn: () =>
      adminApi.subscriptionAccountsByOwnerEmail({
        ownerEmail: lookup.ownerEmail,
        page: lookup.page,
        size: pageSize,
        sort: "name",
        direction: "asc",
      }),
    enabled: submitted !== null,
    gcTime: 0,
    retry: false,
  });

  const closeOrOpen = (nextOpen: boolean) => {
    setOpen(nextOpen);
    if (!nextOpen) {
      setOwnerEmail("");
      setSubmitted(null);
      setAttempted(false);
    }
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAttempted(true);
    if (!validEmail) return;
    setSubmitted({ ownerEmail: normalizedEmail, page: 0 });
  };

  return (
    <Dialog onOpenChange={closeOrOpen} open={open}>
      <DialogTrigger asChild>
        <Button variant="outline">
          <MagnifyingGlassIcon aria-hidden="true" />
          Trouver par e-mail
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Trouver un compte</DialogTitle>
          <DialogDescription>Recherche exacte, séparée de la liste générale.</DialogDescription>
        </DialogHeader>
        <form className="space-y-2" onSubmit={submit}>
          <Label htmlFor="subscription-owner-email">E-mail exact du propriétaire</Label>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Input
              aria-describedby={attempted && !validEmail ? "subscription-owner-email-error" : undefined}
              aria-invalid={attempted && !validEmail}
              autoComplete="off"
              className="min-w-0 flex-1"
              id="subscription-owner-email"
              inputMode="email"
              onChange={(event) => {
                setOwnerEmail(event.target.value);
                setSubmitted(null);
                setAttempted(false);
              }}
              placeholder="proprietaire@entreprise.com"
              spellCheck={false}
              type="email"
              value={ownerEmail}
            />
            <Button disabled={!validEmail || results.isFetching} type="submit">
              {results.isFetching ? "Recherche…" : "Rechercher"}
            </Button>
          </div>
          {attempted && !validEmail ? (
            <p className="text-xs text-destructive" id="subscription-owner-email-error" role="alert">
              Saisissez une adresse e-mail complète.
            </p>
          ) : null}
        </form>

        <div aria-busy={results.isFetching} aria-live="polite" className="min-h-8">
          {submitted && results.isError ? (
            <div className="flex flex-col gap-3 rounded-lg border border-destructive/20 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm text-destructive">Recherche impossible.</p>
              <Button onClick={() => void results.refetch()} size="sm" variant="outline">
                Réessayer
              </Button>
            </div>
          ) : null}
          {submitted && results.data?.content.length === 0 ? (
            <p className="rounded-lg border px-4 py-5 text-sm text-muted-foreground">
              Aucun compte trouvé pour cette adresse.
            </p>
          ) : null}
          {results.data?.content.length ? (
            <div className="overflow-hidden rounded-lg border">
              <ul className="divide-y">
                {results.data.content.map(({ account }) => (
                  <li
                    className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between"
                    key={account.id}
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                        <p className="truncate font-medium">{account.name}</p>
                        <StatusText tone={account.active ? "success" : "danger"}>
                          {account.active ? "Actif" : "Inactif"}
                        </StatusText>
                      </div>
                      <p className="mt-1 truncate text-xs text-muted-foreground">
                        {account.slug}
                        {account.latestSubscription
                          ? ` · ${account.latestSubscription.planName}`
                          : " · Aucun abonnement"}
                      </p>
                    </div>
                    {canOpenAccount ? (
                      <Button asChild className="sm:shrink-0" size="sm" variant="outline">
                        <Link to={`/admin/subscriptions/${account.id}`}>
                          Ouvrir
                          <ArrowRightIcon aria-hidden="true" className="rtl:rotate-180" />
                        </Link>
                      </Button>
                    ) : (
                      <Button disabled size="sm" variant="outline">
                        Consultation non autorisée
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
              <PaginationBar
                onPageChange={(page) => {
                  if (submitted) setSubmitted({ ...submitted, page });
                }}
                page={results.data.page}
                totalElements={results.data.totalElements}
                totalPages={results.data.totalPages}
              />
            </div>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}

/**
 * Keeps the identity-bearing query out of the component tree unless its narrow permission is
 * present. The general Account search never receives or displays owner identity.
 */
export function SubscriptionOwnerEmailLookup() {
  const session = useAdminSession();
  if (!session.can(adminPermissions.subscriptionsLookupAccountOwnerEmail)) return null;
  return <OwnerEmailLookupDialog canOpenAccount={session.can(adminPermissions.subscriptionsRead)} />;
}
