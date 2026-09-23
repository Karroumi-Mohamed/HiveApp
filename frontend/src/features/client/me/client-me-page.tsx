import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { clientApi } from "@/api/client-api";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PermissionInventory } from "@/components/patterns/permission-inventory";
import { StatusBadge } from "@/components/patterns/status-badge";
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

export function ClientMePage({
  embedded = false,
  accountSettings = false,
}: {
  embedded?: boolean;
  accountSettings?: boolean;
}) {
  const session = useClientSession();
  const navigate = useNavigate();
  const [confirmation, setConfirmation] = useState("");
  const deactivate = useMutation({
    mutationFn: clientApi.deactivateAccount,
    onSuccess: async () => {
      toast.success("Compte suspendu");
      await session.logout();
      navigate("/app/login");
    },
  });
  return (
    <div className="space-y-7">
      {!embedded && <PageHeader title="Mon accès" />}
      <section className="max-w-4xl divide-y rounded-xl border bg-card">
        <div className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="font-medium">{session.account?.name}</p>
            <p className="mt-1 text-xs text-muted-foreground">{session.account?.slug}</p>
          </div>
          <StatusBadge tone={session.permissions?.isOwner ? "success" : "neutral"}>
            {session.permissions?.isOwner ? "Propriétaire" : "Membre"}
          </StatusBadge>
        </div>
        <div className="grid gap-5 p-5 sm:grid-cols-2">
          <div>
            <p className="text-xs text-muted-foreground">Contexte</p>
            <p className="mt-1 font-medium">
              {session.companies.find((company) => company.id === session.selectedCompanyId)?.name ?? "Compte"}
            </p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Permissions effectives</p>
            <p className="mt-1 font-medium tabular-nums">{session.permissions?.permissions.length ?? 0}</p>
          </div>
        </div>
        <div className="p-5">
          <PermissionInventory permissions={session.permissions?.permissions ?? []} />
        </div>
      </section>
      {accountSettings &&
      !session.isB2B &&
      session.permissions?.isOwner &&
      session.can(clientPermissions.workspaceDelete) ? (
        <section className="max-w-4xl border-t pt-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-sm font-semibold">Suspendre le compte</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Bloque immédiatement l’accès de tous les membres sans effacer les données.
              </p>
            </div>
            <Dialog>
              <DialogTrigger asChild>
                <Button variant="destructive">Suspendre</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Suspendre {session.account?.name}</DialogTitle>
                  <DialogDescription>
                    Tous les membres seront déconnectés. Saisissez le code du compte pour confirmer.
                  </DialogDescription>
                </DialogHeader>
                <div className="space-y-4">
                  <Input
                    aria-label="Code du compte"
                    onChange={(event) => setConfirmation(event.target.value)}
                    value={confirmation}
                  />
                  <div className="flex justify-end">
                    <Button
                      disabled={confirmation !== session.account?.slug || deactivate.isPending}
                      onClick={() => deactivate.mutate()}
                      variant="destructive"
                    >
                      Confirmer la suspension
                    </Button>
                  </div>
                </div>
              </DialogContent>
            </Dialog>
          </div>
        </section>
      ) : null}
    </div>
  );
}
