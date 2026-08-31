import { ArrowSquareOutIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { adminApi } from "@/api/admin-api";
import type { PlatformBacklogs } from "@/api/contracts";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { ErrorState, PermissionState } from "@/components/patterns/remote-state";
import { StatusText } from "@/components/patterns/status-text";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  backlogLabel,
  backlogStatusLabel,
  componentStatePresentation,
  logAccessGuidance,
  observabilityComponentLabel,
  observabilityGuidance,
  operationDateTime,
} from "./operations-presentation";

function PermissionSection({ allowed, children }: { allowed: boolean; children: React.ReactNode }) {
  return allowed ? children : <PermissionState description="Cette section exige une autorisation distincte." />;
}

function BacklogCounts({ counts }: { counts: Record<string, number> }) {
  const nonEmpty = Object.entries(counts).filter(([, count]) => count > 0);
  if (!nonEmpty.length) return <span className="text-sm text-muted-foreground">Vide</span>;
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-1">
      {nonEmpty.map(([status, count]) => (
        <span className="text-xs" key={status}>
          <span className="text-muted-foreground">{backlogStatusLabel(status)} </span>
          <span className="font-medium tabular-nums">{count}</span>
        </span>
      ))}
    </div>
  );
}

function BacklogLink({ backlog }: { backlog: PlatformBacklogs["components"][number] }) {
  if (!backlog.destination) return null;
  const label = backlogLabel(backlog.key, backlog.label);
  return (
    <Button aria-label={`Ouvrir ${label}`} asChild size="icon-sm" variant="ghost">
      <Link to={backlog.destination}>
        <ArrowSquareOutIcon />
      </Link>
    </Button>
  );
}

export function AdminObservabilityPage() {
  const session = useAdminSession();
  const canHealth = session.can(adminPermissions.observabilityReadHealth);
  const canBacklogs = session.can(adminPermissions.observabilityReadBacklogs);
  const canLogs = session.can(adminPermissions.observabilityReadLogAccess);
  const health = useQuery({
    queryKey: ["admin", "observability", "health"],
    queryFn: adminApi.observabilityHealth,
    enabled: canHealth,
  });
  const backlogs = useQuery({
    queryKey: ["admin", "observability", "backlogs"],
    queryFn: adminApi.observabilityBacklogs,
    enabled: canBacklogs,
  });
  const logs = useQuery({
    queryKey: ["admin", "observability", "logs"],
    queryFn: adminApi.observabilityLogAccess,
    enabled: canLogs,
  });
  return (
    <div className="space-y-8">
      <PageHeader title="Santé & journaux" />
      <section className="space-y-3">
        <div>
          <h2 className="text-base font-semibold">Composants</h2>
          {health.data ? (
            <p className="text-xs text-muted-foreground">Vérifié {operationDateTime(health.data.generatedAt)}</p>
          ) : null}
        </div>
        <PermissionSection allowed={canHealth}>
          {health.isError ? (
            <ErrorState retry={() => health.refetch()} />
          ) : health.data ? (
            <>
              <div className="hidden overflow-hidden rounded-xl border md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Composant</TableHead>
                      <TableHead>État</TableHead>
                      <TableHead>Indication</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {health.data.components.map((component) => {
                      const view = componentStatePresentation(component.state);
                      return (
                        <TableRow key={component.key}>
                          <TableCell className="font-medium">
                            {observabilityComponentLabel(component.key, component.label)}
                          </TableCell>
                          <TableCell>
                            <StatusText tone={view.tone}>{view.label}</StatusText>
                          </TableCell>
                          <TableCell className="text-muted-foreground">
                            {observabilityGuidance(component.key, component.state, component.guidance)}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
              <div className="divide-y overflow-hidden rounded-xl border md:hidden">
                {health.data.components.map((component) => {
                  const view = componentStatePresentation(component.state);
                  return (
                    <article className="space-y-2 p-4" key={component.key}>
                      <div className="flex items-center justify-between gap-3">
                        <h3 className="font-medium">{observabilityComponentLabel(component.key, component.label)}</h3>
                        <StatusText tone={view.tone}>{view.label}</StatusText>
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {observabilityGuidance(component.key, component.state, component.guidance)}
                      </p>
                    </article>
                  );
                })}
              </div>
            </>
          ) : null}
        </PermissionSection>
      </section>
      <section className="space-y-3">
        <div>
          <h2 className="text-base font-semibold">Files opérationnelles</h2>
          {backlogs.data ? (
            <p className="text-xs text-muted-foreground">Vérifié {operationDateTime(backlogs.data.generatedAt)}</p>
          ) : null}
        </div>
        <PermissionSection allowed={canBacklogs}>
          {backlogs.isError ? (
            <ErrorState retry={() => backlogs.refetch()} />
          ) : backlogs.data ? (
            <>
              <div className="hidden overflow-hidden rounded-xl border md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>File</TableHead>
                      <TableHead>Volumes</TableHead>
                      <TableHead>Plus ancien signal</TableHead>
                      <TableHead>État</TableHead>
                      <TableHead className="w-16">
                        <span className="sr-only">Action</span>
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {backlogs.data.components.map((backlog) => (
                      <TableRow key={backlog.key}>
                        <TableCell className="font-medium">{backlogLabel(backlog.key, backlog.label)}</TableCell>
                        <TableCell>
                          <BacklogCounts counts={backlog.counts} />
                        </TableCell>
                        <TableCell>{operationDateTime(backlog.oldestAttentionAt)}</TableCell>
                        <TableCell>
                          <StatusText tone={backlog.attentionRequired ? "warning" : "success"}>
                            {backlog.attentionRequired ? "À examiner" : "Sain"}
                          </StatusText>
                        </TableCell>
                        <TableCell>
                          <BacklogLink backlog={backlog} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="divide-y overflow-hidden rounded-xl border md:hidden">
                {backlogs.data.components.map((backlog) => (
                  <article className="space-y-3 p-4" key={backlog.key}>
                    <div className="flex items-center gap-3">
                      <h3 className="min-w-0 flex-1 font-medium">{backlogLabel(backlog.key, backlog.label)}</h3>
                      <StatusText tone={backlog.attentionRequired ? "warning" : "success"}>
                        {backlog.attentionRequired ? "À examiner" : "Sain"}
                      </StatusText>
                      <BacklogLink backlog={backlog} />
                    </div>
                    <BacklogCounts counts={backlog.counts} />
                    {backlog.oldestAttentionAt ? (
                      <p className="text-xs text-muted-foreground">
                        Plus ancien signal : {operationDateTime(backlog.oldestAttentionAt)}
                      </p>
                    ) : null}
                  </article>
                ))}
              </div>
            </>
          ) : null}
        </PermissionSection>
      </section>
      <section className="space-y-3">
        <h2 className="text-base font-semibold">Journaux de production</h2>
        <PermissionSection allowed={canLogs}>
          {logs.isError ? (
            <ErrorState retry={() => logs.refetch()} />
          ) : logs.data ? (
            <div className="flex flex-col justify-between gap-4 border-y py-4 sm:flex-row sm:items-center">
              <div>
                <StatusText tone={logs.data.configured ? "success" : "neutral"}>
                  {logs.data.configured ? `${logs.data.provider} configuré` : "Aucun fournisseur configuré"}
                </StatusText>
                <p className="mt-1 max-w-3xl text-sm text-muted-foreground">
                  {logAccessGuidance(logs.data.configured)}
                </p>
              </div>
              {logs.data.configured && logs.data.destination ? (
                <Button asChild variant="outline">
                  <a href={logs.data.destination} rel="noreferrer" target="_blank">
                    <ArrowSquareOutIcon />
                    Ouvrir les journaux
                  </a>
                </Button>
              ) : null}
            </div>
          ) : null}
        </PermissionSection>
      </section>
    </div>
  );
}
