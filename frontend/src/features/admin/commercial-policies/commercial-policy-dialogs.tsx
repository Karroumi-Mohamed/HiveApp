import { WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { CommercialPolicyAction, CommercialPolicyDetail } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  adminCommercialKeys,
  commercialQueryEnabled,
  invalidateAdminCommercial,
} from "@/features/commercial/commercial-query";
import {
  executionBlocker,
  policyBlocker,
  policyMutationMessage,
  reviewedActivationReady,
} from "./commercial-policy-rules";

type ReasonAction = "DUPLICATE" | "REVISE" | "PAUSE" | "END" | "ARCHIVE" | "DELETE_DRAFT";

const reasonCopy: Record<ReasonAction, { title: string; description: string; confirm: string; destructive?: boolean }> =
  {
    DUPLICATE: {
      title: "Dupliquer cette politique ?",
      description: "Une nouvelle lignée indépendante sera créée en brouillon avec la même cible et les mêmes effets.",
      confirm: "Créer la copie",
    },
    REVISE: {
      title: "Créer une nouvelle révision ?",
      description: "Un successeur en brouillon copiera cette définition. La révision courante reste inchangée.",
      confirm: "Créer la révision",
    },
    PAUSE: {
      title: "Suspendre cette politique ?",
      description: "La définition restera courante et pourra être réactivée après une nouvelle vérification signée.",
      confirm: "Suspendre",
    },
    END: {
      title: "Terminer définitivement cette politique ?",
      description:
        "Cette révision ne pourra plus être reprise. Une nouvelle révision restera possible si la lignée le permet.",
      confirm: "Terminer",
      destructive: true,
    },
    ARCHIVE: {
      title: "Archiver cette politique ?",
      description: "L’archive est conservée en lecture seule et disparaît des listes ordinaires.",
      confirm: "Archiver",
      destructive: true,
    },
    DELETE_DRAFT: {
      title: "Supprimer ce brouillon ?",
      description:
        "Le brouillon sera supprimé définitivement. Une définition possédant une preuve d’activation ne peut pas l’être.",
      confirm: "Supprimer le brouillon",
      destructive: true,
    },
  };

function invalidatePolicy(queryClient: ReturnType<typeof useQueryClient>, policyId: string) {
  return invalidateAdminCommercial(
    queryClient,
    adminCommercialKeys.policies.all(),
    adminCommercialKeys.policies.detail(policyId),
  );
}

export function CommercialPolicyReasonDialog({
  policy,
  action,
  trigger,
}: {
  policy: CommercialPolicyDetail;
  action: ReasonAction;
  trigger: ReactNode;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [name, setName] = useState(`${policy.summary.name} — copie`);
  const copy = reasonCopy[action];
  const mutation = useMutation({
    mutationFn: async () => {
      const input = { version: policy.summary.version, reason: reason.trim() };
      if (action === "DUPLICATE")
        return adminApi.duplicateCommercialPolicy(policy.summary.id, { ...input, name: name.trim() });
      if (action === "REVISE") return adminApi.reviseCommercialPolicy(policy.summary.id, input);
      if (action === "PAUSE") return adminApi.pauseCommercialPolicy(policy.summary.id, input);
      if (action === "END") return adminApi.endCommercialPolicy(policy.summary.id, input);
      if (action === "ARCHIVE") return adminApi.archiveCommercialPolicy(policy.summary.id, input);
      await adminApi.deleteCommercialPolicy(policy.summary.id, input);
      return null;
    },
    onSuccess: async (result) => {
      await invalidatePolicy(queryClient, policy.summary.id);
      setOpen(false);
      setReason("");
      if (action === "DELETE_DRAFT") {
        toast.success("Brouillon supprimé");
        navigate("/admin/commercial-policies");
      } else if (action === "DUPLICATE" || action === "REVISE") {
        toast.success(action === "DUPLICATE" ? "Copie créée en brouillon" : "Révision créée en brouillon");
        if (result) navigate(`/admin/commercial-policies/${result.summary.id}`);
      } else {
        toast.success("Cycle de vie mis à jour");
      }
    },
    onError: async (error) => {
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.policies.detail(policy.summary.id) });
      toast.error(policyMutationMessage(error));
    },
  });
  const valid = reason.trim() && (action !== "DUPLICATE" || name.trim());
  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{copy.title}</DialogTitle>
          <DialogDescription>{copy.description}</DialogDescription>
        </DialogHeader>
        {action === "DUPLICATE" ? (
          <div className="space-y-2">
            <Label htmlFor="policy-copy-name">Nom de la copie</Label>
            <Input
              id="policy-copy-name"
              maxLength={180}
              onChange={(event) => setName(event.target.value)}
              value={name}
            />
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor={`policy-reason-${action}`}>Motif obligatoire</Label>
          <Textarea
            id={`policy-reason-${action}`}
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
            rows={4}
            value={reason}
          />
          <p className="text-end text-xs text-muted-foreground tabular-nums">{reason.trim().length}/500</p>
        </div>
        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="outline">
            Annuler
          </Button>
          <Button
            disabled={!valid || mutation.isPending}
            onClick={() => mutation.mutate()}
            variant={copy.destructive ? "destructive" : "default"}
          >
            {mutation.isPending ? "Traitement…" : copy.confirm}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function CommercialPolicyActivationDialog({
  policy,
  trigger,
}: {
  policy: CommercialPolicyDetail;
  trigger: ReactNode;
}) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [clock, setClock] = useState(() => Date.now());
  const action: CommercialPolicyAction = policy.summary.status === "PAUSED" ? "RESUME" : "ACTIVATE";
  const permission =
    action === "RESUME" ? adminPermissions.commercialPoliciesResume : adminPermissions.commercialPoliciesActivate;
  const previewKey = adminCommercialKeys.policies.activationPreview(policy.summary.id, policy.summary.version);
  const preview = useQuery({
    queryKey: previewKey,
    queryFn: () => adminApi.previewCommercialPolicyActivation(policy.summary.id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.commercialPoliciesPreviewActivation, open),
    staleTime: 0,
  });
  const expiresAt = preview.data?.expiresAt;
  useEffect(() => {
    if (!open || !expiresAt) return;
    const expires = Date.parse(expiresAt);
    if (!Number.isFinite(expires)) return;
    const timer = window.setTimeout(() => setClock(Date.now()), Math.max(0, expires - Date.now() + 1));
    return () => window.clearTimeout(timer);
  }, [expiresAt, open]);
  const changeOpen = (next: boolean) => {
    setOpen(next);
    if (next) setClock(Date.now());
    else {
      setReason("");
      queryClient.removeQueries({ queryKey: previewKey });
    }
  };
  const mutation = useMutation({
    mutationFn: () => {
      if (!reviewedActivationReady(policy, preview.data, Date.now()))
        throw new Error("La preuve d’activation n’est plus actuelle.");
      const input = {
        version: preview.data?.expectedVersion ?? policy.summary.version,
        reason: reason.trim(),
        activationPreviewToken: preview.data?.previewToken ?? "",
      };
      return action === "RESUME"
        ? adminApi.resumeCommercialPolicy(policy.summary.id, input)
        : adminApi.activateCommercialPolicy(policy.summary.id, input);
    },
    onSuccess: async () => {
      changeOpen(false);
      await invalidatePolicy(queryClient, policy.summary.id);
      toast.success("Définition activée avec preuve — exécution abonnements toujours déconnectée");
    },
    onError: async (error) => {
      queryClient.removeQueries({ queryKey: previewKey });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: adminCommercialKeys.policies.detail(policy.summary.id) }),
        preview.refetch(),
      ]);
      setClock(Date.now());
      toast.error(policyMutationMessage(error));
    },
  });
  const ready = reviewedActivationReady(policy, preview.data, clock);
  return (
    <Dialog onOpenChange={changeOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{action === "RESUME" ? "Vérifier et reprendre" : "Vérifier et activer"}</DialogTitle>
          <DialogDescription>
            L’audience et l’état du catalogue sont signés pour cette opération. La preuve expire et ne peut pas être
            rejouée sur une autre révision.
          </DialogDescription>
        </DialogHeader>
        {preview.isLoading || preview.isFetching ? (
          <div className="py-4 text-sm text-muted-foreground">Vérification de l’audience et des effets…</div>
        ) : preview.isError ? (
          <Alert variant="destructive">
            <WarningCircleIcon />
            <AlertTitle>Vérification indisponible</AlertTitle>
            <AlertDescription>
              <Button onClick={() => void preview.refetch()} size="sm" variant="outline">
                Réessayer
              </Button>
            </AlertDescription>
          </Alert>
        ) : preview.data ? (
          <div className="space-y-4">
            <div className="grid gap-4 border-y py-4 sm:grid-cols-2">
              <div>
                <p className="text-xs text-muted-foreground">Comptes dans la preuve</p>
                <p className="mt-1 text-lg font-semibold tabular-nums">{preview.data.affectedAccountCount}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Catalogue</p>
                <p className="mt-1 text-sm font-medium">
                  R{preview.data.catalogRevision} · {preview.data.registryVersion}
                </p>
              </div>
            </div>
            {!preview.data.activatable ? (
              <Alert variant="destructive">
                <WarningCircleIcon />
                <AlertTitle>Activation bloquée</AlertTitle>
                <AlertDescription>
                  <ul className="list-disc ps-4">
                    {preview.data.blockers.length ? (
                      preview.data.blockers.map((blocker) => <li key={blocker}>{policyBlocker[blocker]}</li>)
                    ) : (
                      <li>Le backend refuse cette activation sans fournir de blocage détaillé.</li>
                    )}
                  </ul>
                </AlertDescription>
              </Alert>
            ) : (
              <Alert>
                <AlertTitle>Preuve actuelle</AlertTitle>
                <AlertDescription>L’audience exacte est dans la limite autorisée.</AlertDescription>
              </Alert>
            )}
            {preview.data.sampleAccounts.length ? (
              <div className="space-y-2">
                <p className="text-xs font-medium text-muted-foreground">Échantillon de l’audience signée</p>
                <ul className="divide-y rounded-lg border text-sm">
                  {preview.data.sampleAccounts.map((account) => (
                    <li className="flex justify-between gap-3 px-3 py-2" key={account.id}>
                      <span className="truncate">{account.name ?? account.id}</span>
                      <span className="truncate text-xs text-muted-foreground">{account.slug ?? account.id}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
            {preview.data.policyRevisionToEnd ? (
              <Alert>
                <AlertTitle>Révision courante remplacée</AlertTitle>
                <AlertDescription>
                  L’activation terminera atomiquement la révision courante de cette lignée avant de rendre celle-ci
                  active.
                </AlertDescription>
              </Alert>
            ) : null}
            {!preview.data.executionSupported ? (
              <Alert className="border-warning/30 bg-warning/5">
                <WarningCircleIcon />
                <AlertTitle>Cette activation ne modifie aucun abonnement</AlertTitle>
                <AlertDescription>
                  <ul className="list-disc ps-4">
                    {preview.data.executionBlockers.map((blocker) => (
                      <li key={blocker}>{executionBlocker[blocker]}</li>
                    ))}
                  </ul>
                </AlertDescription>
              </Alert>
            ) : null}
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor="policy-activation-reason">Motif de l’activation</Label>
          <Textarea
            id="policy-activation-reason"
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            value={reason}
          />
        </div>
        <DialogFooter>
          <Button onClick={() => changeOpen(false)} variant="outline">
            Annuler
          </Button>
          <Button
            disabled={!session.can(permission) || !reason.trim() || !ready || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending
              ? "Activation…"
              : action === "RESUME"
                ? "Reprendre la définition"
                : "Activer la définition"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
