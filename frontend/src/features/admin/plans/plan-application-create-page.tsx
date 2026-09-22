import { ArrowLeftIcon, CheckCircleIcon, UsersIcon } from "@phosphor-icons/react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useBlocker, useNavigate, useParams, useSearchParams } from "react-router";
import { adminApi } from "@/api/admin-api";
import { ApiError } from "@/api/http";
import {
  type ContentNoticePolicy,
  type ContentRequest,
  type ContentTiming,
  planApplicationApi,
} from "@/api/plan-application-api";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { SubscriptionJobAccountPicker } from "@/features/admin/subscription-jobs/subscription-job-account-picker";
import { validContentRequest } from "./plan-application-rules";
import { PlanSubscriberTable } from "./plan-subscriber-table";

export function PlanApplicationCreatePage() {
  const { planId = "" } = useParams();
  const [params] = useSearchParams();
  const { t } = useTranslation();
  const id = useId();
  const session = useAdminSession();
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [versionPage, setVersionPage] = useState(0);
  const [localDate, setLocalDate] = useState("");
  const [target, setTarget] = useState(planId);
  const [request, setRequest] = useState<ContentRequest>({
    sourcePlanId: planId,
    scope: "FAMILY",
    audience: params.get("account") ? "SELECTED" : "ALL",
    accountIds: params.get("account") ? [params.get("account") as string] : [],
    excludedAccountIds: [],
    statuses: ["ACTIVE"],
    search: null,
    currency: null,
    billingCycle: null,
    application: { timing: "NOW", notBefore: null, reason: "" },
    notificationPolicy: "IN_APP",
  });
  const [dirty, setDirty] = useState(false);
  const completed = useRef(false);
  const update = (next: Partial<ContentRequest>) => {
    setDirty(true);
    setRequest((value) => ({ ...value, ...next }));
  };
  const blocker = useBlocker(() => dirty && !completed.current);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (dirty && !completed.current) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const versions = useQuery({
    queryKey: ["admin", "plans", planId, "application-versions", versionPage],
    queryFn: () => adminApi.planVersions(planId, { page: versionPage, size: 20 }),
    enabled: session.can(adminPermissions.plansListVersions),
  });
  const targetDetail = useQuery({
    queryKey: ["admin", "plans", target, "application-target"],
    queryFn: () => adminApi.plan(target),
    enabled: !!target && session.can(adminPermissions.plansReadDetail),
  });
  const sourceDetail = useQuery({
    queryKey: ["admin", "plans", request.sourcePlanId, "application-source"],
    queryFn: () => adminApi.plan(request.sourcePlanId),
    enabled: request.scope === "VERSION" && session.can(adminPermissions.plansReadDetail),
  });
  const create = useMutation({
    mutationFn: () => planApplicationApi.create(target, request),
    onSuccess: (detail) => {
      completed.current = true;
      navigate(`/admin/plans/${target}/applications/${detail.summary.id}`);
    },
  });
  const allowed = [
    adminPermissions.plansCreateApplication,
    adminPermissions.plansPreviewApplication,
    adminPermissions.plansReadApplication,
    adminPermissions.plansListVersions,
    adminPermissions.plansReadDetail,
  ].every(session.can);
  if (!allowed) return <PermissionState />;
  const versionOptions = versions.data?.versions.content ?? [];
  const audienceValid = request.audience !== "SELECTED" || request.accountIds.length > 0;
  const targetValid = targetDetail.data?.status === "ACTIVE";
  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <Button asChild variant="ghost" size="sm">
        <Link to={`/admin/plans/${planId}/subscribers`}>
          <ArrowLeftIcon className="rtl:rotate-180" />
          {t("planApplication.back")}
        </Link>
      </Button>
      <PageHeader title={t("planApplication.title")} description={t("planApplication.preserved")} />
      <ol className="grid grid-cols-3 gap-2 border-b pb-4 text-sm">
        {["audience", "timing", "review"].map((key, index) => (
          <li
            key={key}
            aria-current={step === index ? "step" : undefined}
            className={index === step ? "font-semibold text-primary" : "text-muted-foreground"}
          >
            {index + 1}. {t(`planApplication.${key}`)}
          </li>
        ))}
      </ol>
      {create.isError && (
        <ErrorState
          title={t("planApplication.error")}
          description={create.error instanceof ApiError && create.error.status < 500 ? create.error.message : undefined}
          retry={() => create.reset()}
        />
      )}
      <section className="space-y-6 rounded-xl border bg-card p-5 sm:p-7">
        {step === 0 ? (
          <>
            <div className="grid gap-5 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor={`${id}-target`}>{t("planApplication.target")}</Label>
                <Select
                  value={target}
                  onValueChange={(value) => {
                    setTarget(value);
                    setDirty(true);
                  }}
                >
                  <SelectTrigger id={`${id}-target`}>
                    <SelectValue placeholder={t("planApplication.target")} />
                  </SelectTrigger>
                  <SelectContent>
                    {!versionOptions.some((v) => v.id === target) && targetDetail.data && (
                      <SelectItem value={target}>
                        {targetDetail.data.name} · V{targetDetail.data.revisionNumber}
                      </SelectItem>
                    )}
                    {versionOptions.map((v) => (
                      <SelectItem key={v.id} value={v.id} disabled={v.status !== "ACTIVE"}>
                        {v.name} · V{v.revisionNumber} · {t(`planVersions.${v.status}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor={`${id}-scope`}>{t("planApplication.source")}</Label>
                <Select
                  value={request.scope === "FAMILY" ? "FAMILY" : request.sourcePlanId}
                  onValueChange={(value) =>
                    update(value === "FAMILY" ? { scope: "FAMILY" } : { scope: "VERSION", sourcePlanId: value })
                  }
                >
                  <SelectTrigger id={`${id}-scope`}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="FAMILY">{t("planApplication.FAMILY")}</SelectItem>
                    {!versionOptions.some((v) => v.id === request.sourcePlanId) && sourceDetail.data && (
                      <SelectItem value={request.sourcePlanId}>
                        V{sourceDetail.data.revisionNumber} · {sourceDetail.data.name}
                      </SelectItem>
                    )}
                    {versionOptions.map((v) => (
                      <SelectItem key={v.id} value={v.id}>
                        V{v.revisionNumber} · {v.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            {versions.isLoading || targetDetail.isLoading ? <LoadingState rows={2} /> : null}
            {versions.isError && <ErrorState retry={() => void versions.refetch()} />}
            {targetDetail.isError && <ErrorState retry={() => void targetDetail.refetch()} />}
            {versions.data && versions.data.versions.totalPages > 1 && (
              <PaginationBar {...versions.data.versions} onPageChange={setVersionPage} />
            )}
            <p className="text-sm text-muted-foreground">{t("planApplication.limitsHint")}</p>
            <fieldset className="space-y-3">
              <legend className="mb-3 font-medium">{t("planApplication.audience")}</legend>
              <div className="flex flex-wrap gap-2">
                {(["ALL", "FILTERED", "SELECTED"] as const).map((value) => (
                  <Button
                    key={value}
                    type="button"
                    variant={request.audience === value ? "secondary" : "outline"}
                    aria-pressed={request.audience === value}
                    onClick={() =>
                      update({ audience: value, search: null, currency: null, billingCycle: null, accountIds: [] })
                    }
                  >
                    {t(`planApplication.${value}`)}
                  </Button>
                ))}
              </div>
              {request.audience === "SELECTED" && session.can(adminPermissions.plansListFamilySubscribers) ? (
                <PlanSubscriberTable
                  planId={request.sourcePlanId}
                  selection={{
                    view: request.scope === "FAMILY" ? "ALL" : "CURRENT_VERSION",
                    ids: request.accountIds,
                    statuses: request.statuses,
                    onChange: (accountIds) => update({ accountIds }),
                  }}
                />
              ) : (
                request.audience === "SELECTED" && (
                  <SubscriptionJobAccountPicker
                    selectedIds={request.accountIds}
                    onChange={(accountIds) => update({ accountIds })}
                  />
                )
              )}
              {request.audience === "FILTERED" && (
                <div className="grid gap-4 sm:grid-cols-3">
                  <div className="space-y-2">
                    <Label htmlFor={`${id}-search`}>{t("planApplication.search")}</Label>
                    <Input
                      id={`${id}-search`}
                      maxLength={200}
                      value={request.search ?? ""}
                      onChange={(e) => update({ search: e.target.value || null })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor={`${id}-currency`}>{t("planApplication.currency")}</Label>
                    <Input
                      id={`${id}-currency`}
                      maxLength={3}
                      placeholder="MAD"
                      value={request.currency ?? ""}
                      onChange={(e) => update({ currency: e.target.value.toUpperCase() || null })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor={`${id}-cycle`}>{t("planApplication.cycle")}</Label>
                    <Select
                      value={request.billingCycle ?? "ALL"}
                      onValueChange={(v) =>
                        update({ billingCycle: v === "ALL" ? null : (v as ContentRequest["billingCycle"]) })
                      }
                    >
                      <SelectTrigger id={`${id}-cycle`}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {["ALL", "MONTHLY", "YEARLY"].map((v) => (
                          <SelectItem key={v} value={v}>
                            {t(`planApplication.${v === "ALL" ? "any" : v}`)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              )}
              <Label className="flex min-h-11 items-center gap-3">
                <Checkbox
                  checked={request.statuses.includes("TRIALING")}
                  onCheckedChange={(value) => update({ statuses: value ? ["ACTIVE", "TRIALING"] : ["ACTIVE"] })}
                />
                {t("planApplication.includeTrials")}
              </Label>
            </fieldset>
            <details className="rounded-lg border p-4">
              <summary className="cursor-pointer font-medium">
                {t("planApplication.excluded")} ({request.excludedAccountIds.length})
              </summary>
              <p className="my-3 text-sm text-muted-foreground">{t("planApplication.excludeHint")}</p>
              {session.can(adminPermissions.plansListFamilySubscribers) ? (
                <PlanSubscriberTable
                  planId={request.sourcePlanId}
                  selection={{
                    view: request.scope === "FAMILY" ? "ALL" : "CURRENT_VERSION",
                    ids: request.excludedAccountIds,
                    statuses: request.statuses,
                    onChange: (excludedAccountIds) => update({ excludedAccountIds }),
                  }}
                />
              ) : (
                <SubscriptionJobAccountPicker
                  selectedIds={request.excludedAccountIds}
                  onChange={(excludedAccountIds) => update({ excludedAccountIds })}
                />
              )}
            </details>
          </>
        ) : (
          <>
            <div className="space-y-2">
              <Label htmlFor={`${id}-timing`}>{t("planApplication.timing")}</Label>
              <Select
                value={request.application.timing}
                onValueChange={(timing) => {
                  setLocalDate("");
                  update({ application: { ...request.application, timing: timing as ContentTiming, notBefore: null } });
                }}
              >
                <SelectTrigger id={`${id}-timing`}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {["NOW", "AT_RENEWAL", "AT_DATE"].map((value) => (
                    <SelectItem
                      key={value}
                      value={value}
                      disabled={value === "AT_RENEWAL" && request.statuses.includes("TRIALING")}
                    >
                      {t(`planApplication.${value}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {request.statuses.includes("TRIALING") && (
                <p className="text-sm text-muted-foreground">{t("planApplication.trialTiming")}</p>
              )}
            </div>
            {request.application.timing === "AT_DATE" && (
              <div className="space-y-2">
                <Label htmlFor={`${id}-date`}>{t("planApplication.date")}</Label>
                <Input
                  id={`${id}-date`}
                  type="datetime-local"
                  value={localDate}
                  onChange={(event) => {
                    setLocalDate(event.target.value);
                    const date = new Date(event.target.value);
                    update({
                      application: {
                        ...request.application,
                        notBefore: Number.isNaN(date.getTime()) ? null : date.toISOString(),
                      },
                    });
                  }}
                />
                <p className="text-xs text-muted-foreground">{t("planApplication.dateHint")}</p>
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor={`${id}-notice`}>{t("planApplication.notifications")}</Label>
              <Select
                value={request.notificationPolicy}
                onValueChange={(notificationPolicy) =>
                  update({ notificationPolicy: notificationPolicy as ContentNoticePolicy })
                }
              >
                <SelectTrigger id={`${id}-notice`}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {["IN_APP", "EMAIL", "EMAIL_REQUIRED"].map((value) => (
                    <SelectItem key={value} value={value}>
                      {t(`planApplication.${value}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">{t("planApplication.noticeHint")}</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor={`${id}-reason`}>{t("planApplication.reason")}</Label>
              <Textarea
                id={`${id}-reason`}
                maxLength={2000}
                value={request.application.reason}
                onChange={(e) => update({ application: { ...request.application, reason: e.target.value } })}
              />
              <p className="text-xs text-muted-foreground">{t("planApplication.reasonHint")}</p>
            </div>
            <div className="flex items-center gap-3 rounded-lg bg-muted p-4 text-sm">
              <UsersIcon className="size-5 shrink-0" />
              <span>
                {t(`planApplication.${request.scope}`)} ·{" "}
                {request.audience === "SELECTED"
                  ? t("planApplication.selected", { count: request.accountIds.length })
                  : t(`planApplication.${request.audience}`)}
              </span>
            </div>
          </>
        )}
        <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-5">
          <p className="max-w-md text-xs text-muted-foreground">{t("planApplication.frozen")}</p>
          <div className="flex gap-2">
            {step > 0 && (
              <Button variant="ghost" onClick={() => setStep(0)}>
                {t("planApplication.back")}
              </Button>
            )}
            {step === 0 ? (
              <Button disabled={!audienceValid || !targetValid || versions.isError} onClick={() => setStep(1)}>
                {t("planApplication.next")}
              </Button>
            ) : (
              <Button
                disabled={!validContentRequest(request) || !targetValid || create.isPending}
                onClick={() => create.mutate()}
              >
                <CheckCircleIcon />
                {t("planApplication.prepare")}
              </Button>
            )}
          </div>
        </div>
      </section>
      <Dialog
        open={blocker.state === "blocked"}
        onOpenChange={(open) => {
          if (!open && blocker.state === "blocked") blocker.reset();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("planApplication.discard")}</DialogTitle>
            <DialogDescription>{t("planApplication.discardHint")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => blocker.state === "blocked" && blocker.reset()}>
              {t("planApplication.back")}
            </Button>
            <Button onClick={() => blocker.state === "blocked" && blocker.proceed()}>
              {t("planApplication.close")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
