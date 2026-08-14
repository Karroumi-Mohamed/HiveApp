import { EnvelopeSimpleIcon } from "@phosphor-icons/react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import { ApiError } from "@/api/http";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PermissionInventory } from "@/components/patterns/permission-inventory";
import { StatusBadge } from "@/components/patterns/status-badge";
import { Button } from "@/components/ui/button";
import { announceEmailDelivery } from "@/features/admin/operators/operator-email-delivery";

export function AdminMePage() {
  const { me, retry } = useAdminSession();
  const verification = useMutation({
    mutationFn: adminApi.sendMyEmailVerification,
    onSuccess: (access) => announceEmailDelivery(access.emailDelivery),
    onError: (reason) => {
      if (reason instanceof ApiError && reason.status === 409) retry();
      toast.error(reason instanceof ApiError ? reason.message : "Demande impossible");
    },
  });
  if (!me) return null;
  return (
    <div className="space-y-7">
      <PageHeader title="Mon accès" />

      {!me.emailVerified ? (
        <section className="max-w-3xl border-y border-warning/35 bg-warning-subtle/35 py-4">
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
            <div className="min-w-0">
              <h2 className="text-sm font-semibold">Récupération du mot de passe indisponible</h2>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">
                Vérifiez votre adresse email pour pouvoir utiliser « Mot de passe oublié ».
              </p>
            </div>
            <Button
              className="shrink-0"
              disabled={verification.isPending}
              onClick={() => verification.mutate()}
              size="sm"
              variant="outline"
            >
              <EnvelopeSimpleIcon aria-hidden="true" />
              {verification.isPending ? "Demande…" : "Envoyer la vérification"}
            </Button>
          </div>
        </section>
      ) : null}

      <section className="max-w-3xl divide-y rounded-xl border bg-card">
        <div className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="font-medium">{me.email}</p>
            <p className="mt-1 text-xs text-muted-foreground">Identifiant opérateur</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <StatusBadge tone={me.emailVerified ? "success" : "warning"}>
              {me.emailVerified ? "Email vérifié" : "Email non vérifié"}
            </StatusBadge>
            <StatusBadge tone={me.isActive ? "success" : "danger"}>{me.isActive ? "Actif" : "Inactif"}</StatusBadge>
          </div>
        </div>
        <div className="grid gap-5 p-5 sm:grid-cols-2">
          <div>
            <p className="text-xs text-muted-foreground">Niveau</p>
            <p className="mt-1 font-medium">{me.isSuperAdmin ? "SuperAdmin" : "Opérateur"}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Permissions effectives</p>
            <p className="mt-1 font-medium tabular-nums">{me.permissions.length}</p>
          </div>
        </div>
        <div className="p-5">
          <PermissionInventory permissions={me.permissions} />
        </div>
      </section>
    </div>
  );
}
