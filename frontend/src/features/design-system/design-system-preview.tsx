import {
  ArrowUpRightIcon,
  CheckCircleIcon,
  DotsThreeIcon,
  MagnifyingGlassIcon,
  PlusIcon,
  WarningIcon,
} from "@phosphor-icons/react";
import type { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { AppShell } from "@/components/patterns/app-shell";
import { StatusBadge, type StatusTone } from "@/components/patterns/status-badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

type OperatorStatus = "active" | "suspended" | "pending";

const operatorTone: Record<OperatorStatus, StatusTone> = {
  active: "success",
  suspended: "danger",
  pending: "warning",
};

function SectionHeading({ title }: { title: string }) {
  return <h2 className="text-lg font-semibold tracking-tight">{title}</h2>;
}

export function DesignSystemPreview() {
  const { t } = useTranslation();
  const operators: Array<{
    name: string;
    email: string;
    initials: string;
    role: string;
    status: OperatorStatus;
    activity: string;
  }> = [
    {
      name: "Salma Benali",
      email: "salma@hiveapp.ma",
      initials: "SB",
      role: "Super administrateur",
      status: "active",
      activity: t("preview.minutesAgo"),
    },
    {
      name: "Youssef Amrani",
      email: "youssef@hiveapp.ma",
      initials: "YA",
      role: "Opérations abonnements",
      status: "active",
      activity: t("preview.hoursAgo"),
    },
    {
      name: "Lina Haddad",
      email: "lina@hiveapp.ma",
      initials: "LH",
      role: "Support plateforme",
      status: "suspended",
      activity: t("preview.yesterday"),
    },
  ];

  const statusLabel: Record<OperatorStatus, string> = {
    active: t("preview.active"),
    suspended: t("preview.suspended"),
    pending: t("preview.pending"),
  };

  const announceDemo = () => {
    toast.info(t("preview.noticeTitle"), { description: t("preview.noticeDescription") });
  };

  const submitDemo = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    toast.success(t("preview.success"), { description: t("preview.formDescription") });
  };

  return (
    <AppShell>
      <div className="space-y-8">
        <section className="flex flex-col justify-between gap-5 sm:flex-row sm:items-center">
          <h1 className="text-2xl font-semibold tracking-[-0.035em] md:text-3xl">{t("preview.title")}</h1>
          <div className="flex flex-wrap items-center gap-2">
            <Button onClick={announceDemo}>
              <PlusIcon aria-hidden="true" weight="bold" />
              {t("preview.createOperator")}
            </Button>
          </div>
        </section>

        <section className="grid gap-5 2xl:grid-cols-[minmax(0,1.65fr)_minmax(21rem,0.85fr)]">
          <Card className="gap-0 overflow-hidden border bg-card py-0 shadow-panel">
            <CardHeader className="gap-4 border-b px-5 py-5 md:flex-row md:items-center md:justify-between md:px-6">
              <CardTitle className="text-base">{t("preview.operatorsTitle")}</CardTitle>
              <div className="relative w-full md:w-72">
                <MagnifyingGlassIcon
                  aria-hidden="true"
                  className="pointer-events-none absolute top-1/2 start-3 size-4 -translate-y-1/2 text-muted-foreground"
                />
                <Input aria-label={t("common.search")} className="ps-9" placeholder={t("preview.searchOperators")} />
              </div>
            </CardHeader>
            <CardContent className="p-0">
              <Table>
                <TableHeader className="bg-muted/45">
                  <TableRow className="hover:bg-transparent">
                    <TableHead className="h-11 px-5 md:px-6">{t("preview.name")}</TableHead>
                    <TableHead>{t("preview.role")}</TableHead>
                    <TableHead>{t("preview.status")}</TableHead>
                    <TableHead>{t("preview.lastActivity")}</TableHead>
                    <TableHead className="w-14 text-end">
                      <span className="sr-only">{t("preview.actions")}</span>
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {operators.map((operator) => (
                    <TableRow key={operator.email}>
                      <TableCell className="px-5 py-3 md:px-6">
                        <div className="flex items-center gap-3">
                          <Avatar className="size-9 border">
                            <AvatarFallback className="bg-secondary text-xs font-semibold text-primary">
                              {operator.initials}
                            </AvatarFallback>
                          </Avatar>
                          <div>
                            <p className="font-medium">{operator.name}</p>
                            <p className="mt-0.5 text-xs text-muted-foreground" dir="ltr">
                              {operator.email}
                            </p>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{operator.role}</TableCell>
                      <TableCell>
                        <StatusBadge tone={operatorTone[operator.status]}>{statusLabel[operator.status]}</StatusBadge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{operator.activity}</TableCell>
                      <TableCell className="pe-4 text-end">
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              aria-label={`${t("common.moreActions")} — ${operator.name}`}
                              size="icon-sm"
                              variant="ghost"
                            >
                              <DotsThreeIcon aria-hidden="true" weight="bold" />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>{t("common.moreActions")}</TooltipContent>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <Card className="gap-0 border bg-card py-0 shadow-panel">
            <CardHeader className="border-b px-5 py-5">
              <CardTitle className="text-base">{t("preview.planTitle")}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-5 p-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-lg font-semibold">{t("preview.proPlan")}</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    <span className="font-semibold text-foreground">299 MAD</span> / {t("preview.monthly")}
                  </p>
                </div>
                <StatusBadge tone="success">{t("preview.active")}</StatusBadge>
              </div>
              <Separator />
              <dl className="space-y-4 text-sm">
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-muted-foreground">{t("preview.includedFeatures")}</dt>
                  <dd className="font-semibold tabular-nums">12</dd>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-muted-foreground">{t("preview.currentSubscribers")}</dt>
                  <dd className="font-semibold tabular-nums">248</dd>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-muted-foreground">{t("preview.quotaPolicy")}</dt>
                  <dd>
                    <Badge variant="secondary">{t("preview.paidExpansion")}</Badge>
                  </dd>
                </div>
              </dl>
              <div className="rounded-lg border border-warning/20 bg-warning-subtle/55 p-4">
                <div className="flex items-start gap-3">
                  <WarningIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-warning" weight="fill" />
                  <div>
                    <p className="text-sm font-semibold">{t("preview.impactTitle")}</p>
                    <p className="mt-1 text-xs leading-5 text-muted-foreground">{t("preview.impactDescription")}</p>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <StatusBadge dot={false} tone="warning">
                        {t("preview.affectedAccounts")}
                      </StatusBadge>
                      <StatusBadge dot={false} tone="info">
                        {t("preview.renewalTiming")}
                      </StatusBadge>
                    </div>
                  </div>
                </div>
              </div>
              <Button className="w-full" onClick={announceDemo} variant="outline">
                {t("preview.inspectImpact")}
                <ArrowUpRightIcon aria-hidden="true" />
              </Button>
            </CardContent>
          </Card>
        </section>

        <section className="grid gap-8 border-t pt-8 xl:grid-cols-2 xl:gap-12">
          <div>
            <SectionHeading title={t("preview.componentTitle")} />
            <div className="mt-5 space-y-6">
              <div className="flex flex-wrap gap-2">
                <Button onClick={announceDemo}>{t("preview.primaryAction")}</Button>
                <Button onClick={announceDemo} variant="secondary">
                  {t("preview.secondaryAction")}
                </Button>
                <Button onClick={announceDemo} variant="outline">
                  {t("preview.neutralAction")}
                </Button>
                <Button onClick={announceDemo} variant="destructive">
                  {t("preview.destructiveAction")}
                </Button>
                <Button disabled>{t("preview.disabledAction")}</Button>
              </div>
              <Separator />
              <div>
                <SectionHeading title={t("preview.statusesTitle")} />
                <div className="mt-4 flex flex-wrap gap-2">
                  <StatusBadge tone="success">{t("preview.success")}</StatusBadge>
                  <StatusBadge tone="warning">{t("preview.warning")}</StatusBadge>
                  <StatusBadge tone="danger">{t("preview.danger")}</StatusBadge>
                  <StatusBadge tone="info">{t("preview.info")}</StatusBadge>
                </div>
              </div>
            </div>
          </div>

          <div>
            <SectionHeading title={t("preview.formTitle")} />
            <div className="mt-5">
              <form className="space-y-5" onSubmit={submitDemo}>
                <div className="space-y-2">
                  <Label htmlFor="role-name">{t("preview.roleName")}</Label>
                  <Input id="role-name" placeholder={t("preview.roleNamePlaceholder")} />
                  <p className="text-xs leading-5 text-muted-foreground">{t("preview.roleNameHelp")}</p>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="role-audience">{t("preview.audience")}</Label>
                  <Select>
                    <SelectTrigger className="h-10 w-full" id="role-audience">
                      <SelectValue placeholder={t("preview.selectAudience")} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="platform">{t("preview.platformAudience")}</SelectItem>
                      <SelectItem value="client">{t("preview.clientAudience")}</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="flex justify-end">
                  <Button type="submit">
                    <CheckCircleIcon aria-hidden="true" weight="bold" />
                    {t("preview.saveRole")}
                  </Button>
                </div>
              </form>
            </div>
          </div>
        </section>
      </div>
    </AppShell>
  );
}
