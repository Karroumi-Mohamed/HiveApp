import { ArchiveIcon, CheckCircleIcon, GitBranchIcon, PauseCircleIcon, PencilSimpleIcon } from "@phosphor-icons/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { toast } from "sonner";
import { adminApi } from "@/api/admin-api";
import type { Plan, PlanOperationalItem, PlanStatus } from "@/api/contracts";
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
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { adminCommercialKeys, invalidateAdminCommercial } from "@/features/commercial/commercial-query";
import { planTone } from "./plan-presentation";

export function PlanStatusTag({ status }: { status: PlanStatus }) {
  const { t } = useTranslation();
  const Icon = { ACTIVE: CheckCircleIcon, INACTIVE: PauseCircleIcon, DRAFT: PencilSimpleIcon, ARCHIVED: ArchiveIcon }[
    status
  ];
  return (
    <StatusBadge dot={false} tone={planTone[status]} className="text-xs tracking-normal">
      <Icon aria-hidden="true" className="size-3.5" />
      {t(`planVersions.${status}`)}
    </StatusBadge>
  );
}

export function PlanVersionTag({ id, number, clickable = true }: { id: string; number: number; clickable?: boolean }) {
  const { t } = useTranslation();
  const label = t("planVersions.version", { number });
  return clickable ? (
    <Button asChild size="sm" variant="outline" className="h-8 text-xs tracking-normal">
      <Link to={`/admin/plans/${id}/versions`} aria-label={t("planVersions.viewVersions")}>
        <GitBranchIcon aria-hidden="true" />
        {label}
      </Link>
    </Button>
  ) : (
    <span className="inline-flex items-center gap-1.5 text-xs font-normal tracking-normal text-muted-foreground">
      <GitBranchIcon aria-hidden="true" className="size-4" />
      {label}
    </span>
  );
}

export function SelectPlanPublicVersion({
  plan,
  catalogRevision,
}: {
  plan: PlanOperationalItem;
  catalogRevision: number;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: () =>
      adminApi.selectPlanPublicVersion(plan.id, {
        expectedVersion: plan.version,
        expectedCatalogRevision: catalogRevision,
        reason: reason.trim(),
      }),
    onSuccess: async () => {
      await invalidateAdminCommercial(client, adminCommercialKeys.plans.all());
      setOpen(false);
      toast.success(t("planVersions.saved"));
    },
  });
  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        setOpen(value);
        if (value) {
          setReason("");
          mutation.reset();
        }
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          {t("planVersions.choosePublic")}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("planVersions.publicTitle")}</DialogTitle>
          <DialogDescription>{t("planVersions.publicDescription")}</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          <p className="font-medium">
            {plan.name} · {t("planVersions.version", { number: plan.revisionNumber })}
          </p>
          <div className="space-y-2">
            <Label htmlFor={`public-reason-${plan.id}`}>{t("planVersions.reason")}</Label>
            <Textarea
              id={`public-reason-${plan.id}`}
              required
              maxLength={1000}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </div>
          {mutation.isError && (
            <p role="alert" className="text-sm text-destructive">
              {mutation.error.message}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              {t("planVersions.cancel")}
            </Button>
            <Button disabled={mutation.isPending || !reason.trim()}>{t("planVersions.confirm")}</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function PlanMetadataDialog({ plan }: { plan: Plan }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(plan.name);
  const [description, setDescription] = useState(plan.description ?? "");
  const [reason, setReason] = useState("");
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: () =>
      adminApi.updatePlanMetadata(plan.id, {
        expectedVersion: plan.version,
        name: name.trim(),
        description,
        reason: reason.trim(),
      }),
    onSuccess: async () => {
      await invalidateAdminCommercial(client, adminCommercialKeys.plans.all());
      setOpen(false);
      toast.success(t("planVersions.metadataSaved"));
    },
  });
  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        setOpen(value);
        if (value) {
          setName(plan.name);
          setDescription(plan.description ?? "");
          setReason("");
          mutation.reset();
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm">
          <PencilSimpleIcon />
          {t("planVersions.metadata")}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("planVersions.metadata")}</DialogTitle>
          <DialogDescription>{t("planVersions.metadataHint")}</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="version-name">{t("planVersions.name")}</Label>
            <Input
              id="version-name"
              required
              maxLength={255}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="version-description">{t("planVersions.description")}</Label>
            <Textarea
              id="version-description"
              maxLength={255}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="version-reason">{t("planVersions.reason")}</Label>
            <Textarea
              id="version-reason"
              required
              maxLength={1000}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </div>
          {mutation.isError && (
            <p role="alert" className="text-sm text-destructive">
              {mutation.error.message}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              {t("planVersions.cancel")}
            </Button>
            <Button disabled={mutation.isPending || !name.trim() || !reason.trim()}>{t("planVersions.save")}</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
