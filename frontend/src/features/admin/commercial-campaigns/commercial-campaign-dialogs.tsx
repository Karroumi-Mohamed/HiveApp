import { WarningCircleIcon } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { CommercialCampaignAction, CommercialCampaignDetail } from "@/api/contracts";
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
  invalidateCommercialCampaignTargeting,
} from "@/features/commercial/commercial-query";
import { campaignBlocker, campaignMutationMessage, reviewedCampaignScheduleReady } from "./commercial-campaign-rules";

type ReasonAction = "DUPLICATE" | "REVISE" | "PAUSE" | "RESUME" | "END" | "ARCHIVE" | "DELETE_DRAFT";

const reasonCopy: Record<ReasonAction, { title: string; description: string; confirm: string; destructive?: boolean }> =
  {
    DUPLICATE: {
      title: "Dupliquer cette campagne ?",
      description: "Une campagne indépendante sera créée en brouillon avec la même audience et le même calendrier.",
      confirm: "Créer la copie",
    },
    REVISE: {
      title: "Créer une nouvelle révision ?",
      description: "Un brouillon successeur copiera cette campagne. La révision courante reste inchangée.",
      confirm: "Créer la révision",
    },
    PAUSE: {
      title: "Mettre la campagne en pause ?",
      description: "Elle cessera d’être active jusqu’à sa reprise explicite.",
      confirm: "Mettre en pause",
    },
    RESUME: {
      title: "Reprendre la campagne ?",
      description: "Elle redeviendra active dans la fenêtre déjà planifiée.",
      confirm: "Reprendre",
    },
    END: {
      title: "Terminer la campagne ?",
      description: "La campagne prendra fin immédiatement et ne pourra pas être reprise.",
      confirm: "Terminer",
      destructive: true,
    },
    ARCHIVE: {
      title: "Archiver la campagne ?",
      description: "Elle restera consultable dans les archives et ne pourra pas être désarchivée.",
      confirm: "Archiver",
      destructive: true,
    },
    DELETE_DRAFT: {
      title: "Supprimer ce brouillon ?",
      description: "La suppression est définitive. Le nom exact et un motif sont obligatoires.",
      confirm: "Supprimer le brouillon",
      destructive: true,
    },
  };

async function invalidateCampaign(queryClient: ReturnType<typeof useQueryClient>, campaignId: string) {
  await invalidateCommercialCampaignTargeting(queryClient, adminCommercialKeys.campaigns.detail(campaignId));
}

export function CommercialCampaignReasonDialog({
  campaign,
  action,
  trigger,
}: {
  campaign: CommercialCampaignDetail;
  action: ReasonAction;
  trigger: ReactNode;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [name, setName] = useState(`${campaign.summary.name} — copie`);
  const [confirmation, setConfirmation] = useState("");
  const expectedVersion = useRef(campaign.summary.version);
  const copy = reasonCopy[action];
  const changeOpen = (next: boolean) => {
    if (next) expectedVersion.current = campaign.summary.version;
    setOpen(next);
    if (!next) {
      setReason("");
      setName(`${campaign.summary.name} — copie`);
      setConfirmation("");
    }
  };
  const mutation = useMutation({
    mutationFn: async () => {
      const input = { version: expectedVersion.current, reason: reason.trim() };
      if (action === "DUPLICATE")
        return adminApi.duplicateCommercialCampaign(campaign.summary.id, { ...input, name: name.trim() });
      if (action === "REVISE") return adminApi.reviseCommercialCampaign(campaign.summary.id, input);
      if (action === "PAUSE") return adminApi.pauseCommercialCampaign(campaign.summary.id, input);
      if (action === "RESUME") return adminApi.resumeCommercialCampaign(campaign.summary.id, input);
      if (action === "END") return adminApi.endCommercialCampaign(campaign.summary.id, input);
      if (action === "ARCHIVE") return adminApi.archiveCommercialCampaign(campaign.summary.id, input);
      await adminApi.deleteCommercialCampaign(campaign.summary.id, input);
      return null;
    },
    onSuccess: async (result) => {
      await invalidateCampaign(queryClient, campaign.summary.id);
      changeOpen(false);
      if (action === "DELETE_DRAFT") {
        toast.success("Brouillon supprimé");
        navigate("/admin/campaigns");
      } else if (action === "DUPLICATE" || action === "REVISE") {
        toast.success(action === "DUPLICATE" ? "Copie créée" : "Révision créée");
        if (result) navigate(`/admin/campaigns/${result.summary.id}`);
      } else toast.success("Cycle de vie mis à jour");
    },
    onError: async (error) => {
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.campaigns.detail(campaign.summary.id) });
      toast.error(campaignMutationMessage(error));
    },
  });
  const valid =
    Boolean(reason.trim()) &&
    (action !== "DUPLICATE" || Boolean(name.trim())) &&
    (action !== "DELETE_DRAFT" || confirmation === campaign.summary.name);
  return (
    <Dialog onOpenChange={changeOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{copy.title}</DialogTitle>
          <DialogDescription>{copy.description}</DialogDescription>
        </DialogHeader>
        {action === "DUPLICATE" ? (
          <div className="space-y-2">
            <Label htmlFor="campaign-copy-name">Nom de la copie</Label>
            <Input
              id="campaign-copy-name"
              maxLength={180}
              onChange={(event) => setName(event.target.value)}
              value={name}
            />
          </div>
        ) : null}
        {action === "DELETE_DRAFT" ? (
          <div className="space-y-2">
            <Label htmlFor="campaign-delete-name">Saisissez le nom exact</Label>
            <button
              className="block max-w-full select-all truncate font-mono text-sm text-foreground underline-offset-4 hover:underline"
              onClick={() => setConfirmation(campaign.summary.name)}
              type="button"
            >
              {campaign.summary.name}
            </button>
            <Input
              id="campaign-delete-name"
              onChange={(event) => setConfirmation(event.target.value)}
              value={confirmation}
            />
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor={`campaign-reason-${action}`}>Motif obligatoire</Label>
          <Textarea
            id={`campaign-reason-${action}`}
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
            rows={4}
            value={reason}
          />
        </div>
        <DialogFooter>
          <Button onClick={() => changeOpen(false)} variant="outline">
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

export function CommercialCampaignScheduleDialog({
  campaign,
  trigger,
}: {
  campaign: CommercialCampaignDetail;
  trigger: ReactNode;
}) {
  const session = useAdminSession();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [clock, setClock] = useState(() => Date.now());
  const previewKey = adminCommercialKeys.campaigns.schedulePreview(campaign.summary.id, campaign.summary.version);
  const preview = useQuery({
    queryKey: previewKey,
    queryFn: () => adminApi.previewCommercialCampaignSchedule(campaign.summary.id),
    enabled: commercialQueryEnabled(session.can, adminPermissions.campaignsPreviewSchedule, open),
    gcTime: 0,
    staleTime: 0,
  });
  useEffect(
    () => () =>
      queryClient.removeQueries({
        queryKey: adminCommercialKeys.campaigns.schedulePreview(campaign.summary.id, campaign.summary.version),
      }),
    [campaign.summary.id, campaign.summary.version, queryClient],
  );
  useEffect(() => {
    if (!open || !preview.data?.expiresAt) return;
    const expires = Date.parse(preview.data.expiresAt);
    if (!Number.isFinite(expires)) return;
    const timer = window.setTimeout(() => setClock(Date.now()), Math.max(0, expires - Date.now() + 1));
    return () => window.clearTimeout(timer);
  }, [open, preview.data?.expiresAt]);
  const changeOpen = (next: boolean) => {
    setOpen(next);
    if (next) setClock(Date.now());
    else {
      setReason("");
      queryClient.removeQueries({ queryKey: previewKey });
    }
  };
  const refresh = async () => {
    queryClient.removeQueries({ queryKey: previewKey });
    await preview.refetch();
    setClock(Date.now());
  };
  const mutation = useMutation({
    mutationFn: () => {
      if (!reviewedCampaignScheduleReady(campaign, preview.data, Date.now())) throw new Error("Preuve expirée");
      return adminApi.scheduleCommercialCampaign(campaign.summary.id, {
        version: preview.data?.campaignVersion ?? campaign.summary.version,
        reason: reason.trim(),
        previewToken: preview.data?.previewToken ?? "",
      });
    },
    onSuccess: async () => {
      changeOpen(false);
      await invalidateCampaign(queryClient, campaign.summary.id);
      toast.success("Campagne planifiée avec l’audience vérifiée");
    },
    onError: async (error) => {
      queryClient.removeQueries({ queryKey: previewKey });
      await queryClient.invalidateQueries({ queryKey: adminCommercialKeys.campaigns.detail(campaign.summary.id) });
      if (open) await preview.refetch();
      setClock(Date.now());
      toast.error(campaignMutationMessage(error));
    },
  });
  const ready = reviewedCampaignScheduleReady(campaign, preview.data, clock);
  const expired = Boolean(preview.data && Date.parse(preview.data.expiresAt) <= clock);
  return (
    <Dialog onOpenChange={changeOpen} open={open}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Vérifier et planifier</DialogTitle>
          <DialogDescription>
            L’audience est figée et signée pour cette version uniquement. La preuve expire rapidement.
          </DialogDescription>
        </DialogHeader>
        {preview.isLoading || preview.isFetching ? (
          <p className="py-4 text-sm text-muted-foreground">Calcul de l’audience…</p>
        ) : preview.isError ? (
          <Alert variant="destructive">
            <WarningCircleIcon />
            <AlertTitle>Vérification indisponible</AlertTitle>
            <AlertDescription>
              <Button onClick={() => void refresh()} size="sm" variant="outline">
                Réessayer
              </Button>
            </AlertDescription>
          </Alert>
        ) : preview.data ? (
          <div className="space-y-4">
            <dl className="grid gap-4 border-y py-4 sm:grid-cols-2">
              <div>
                <dt className="text-xs text-muted-foreground">Audience figée</dt>
                <dd className="mt-1 text-lg font-semibold tabular-nums">
                  {preview.data.publicAudience
                    ? "Publique et dynamique"
                    : `${preview.data.targetedAccountCount ?? 0} compte(s)`}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Évaluée</dt>
                <dd className="mt-1 text-sm font-medium">
                  {new Date(preview.data.evaluatedAt).toLocaleString("fr-FR")} · registre {preview.data.registryVersion}
                </dd>
              </div>
            </dl>
            {!preview.data.schedulable || preview.data.blockers.length ? (
              <Alert variant="destructive">
                <WarningCircleIcon />
                <AlertTitle>Planification bloquée</AlertTitle>
                <AlertDescription>
                  <ul className="list-disc ps-4">
                    {preview.data.blockers.map((blocker) => (
                      <li key={blocker}>{campaignBlocker[blocker]}</li>
                    ))}
                  </ul>
                </AlertDescription>
              </Alert>
            ) : expired ? (
              <Alert>
                <WarningCircleIcon />
                <AlertTitle>Preuve expirée</AlertTitle>
                <AlertDescription>
                  <Button onClick={() => void refresh()} size="sm" variant="outline">
                    Vérifier de nouveau
                  </Button>
                </AlertDescription>
              </Alert>
            ) : (
              <Alert>
                <AlertTitle>Audience prête</AlertTitle>
                <AlertDescription>Cette audience exacte sera conservée avec la campagne.</AlertDescription>
              </Alert>
            )}
            {preview.data.sample.length ? (
              <div>
                <p className="text-xs font-medium text-muted-foreground">Échantillon opaque</p>
                <ul className="mt-2 divide-y border-y font-mono text-xs">
                  {preview.data.sample.map((account) => (
                    <li className="py-2" key={account.accountId}>
                      {account.accountId}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor="campaign-schedule-reason">Motif de planification</Label>
          <Textarea
            id="campaign-schedule-reason"
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
            disabled={
              !session.can(adminPermissions.campaignsSchedule) || !reason.trim() || !ready || mutation.isPending
            }
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? "Planification…" : "Planifier"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function campaignActionVisible(campaign: CommercialCampaignDetail, action: CommercialCampaignAction) {
  return campaign.summary.availableActions.includes(action) || Boolean(campaign.summary.blockedActions[action]);
}
