import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router";
import { type CommunicationPreference, communicationApi } from "@/api/communication-api";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { useCommunicationCopy } from "./communication-copy";
import { useClientNotificationContext, useOperatorNotificationContext } from "./notification-context";
import { NotificationInbox } from "./notification-inbox";
export function MarketingPreferences() {
  const c = useCommunicationCopy(),
    session = useClientSession(),
    cache = useQueryClient();
  const key = ["client", "communications", session.account?.id, "preferences"];
  const query = useQuery({ queryKey: key, queryFn: communicationApi.preferences });
  const [next, setNext] = useState<CommunicationPreference | null>(null);
  const canChange = session.permissions?.isOwner && session.can(clientPermissions.communicationsPreferences);
  const save = useMutation({
    mutationFn: () => {
      const value = next ?? query.data;
      if (!value) throw new Error(c("error"));
      return communicationApi.updatePreferences(value);
    },
    onSuccess: () => {
      setNext(null);
      void cache.invalidateQueries({ queryKey: ["client", "communications"] });
      void cache.invalidateQueries({ queryKey: ["client", "notifications"] });
    },
  });
  if (query.isError) return <ErrorState retry={() => void query.refetch()} />;
  if (query.isLoading) return <LoadingState rows={2} />;
  const value = next ?? query.data;
  return (
    <details className="rounded-xl border bg-card p-4">
      <summary className="cursor-pointer font-medium">{c("prefs")}</summary>
      <p className="my-3 text-sm text-muted-foreground">{c("prefHint")}</p>
      {value && (
        <div className="space-y-3">
          {(["marketingInApp", "marketingEmail"] as const).map((field) => (
            <label htmlFor={field} className="flex min-h-11 items-center gap-3 text-sm" key={field}>
              <Checkbox
                id={field}
                checked={value[field]}
                disabled={!canChange || save.isPending}
                onCheckedChange={(checked) => setNext({ ...value, [field]: checked === true })}
              />
              {c(field)}
            </label>
          ))}
          {canChange && (
            <Button variant="outline" onClick={() => save.mutate()} disabled={!next || save.isPending}>
              {c("savePreferences")}
            </Button>
          )}
        </div>
      )}
      <p className="mt-3 text-xs text-muted-foreground">{c("ownerOnly")}</p>
      {save.isError && (
        <p role="alert" className="text-destructive">
          {c("error")}
        </p>
      )}
      {save.isSuccess && !next && (
        <p role="status" className="text-sm text-success">
          {c("saved")}
        </p>
      )}
    </details>
  );
}

export function ClientCommunicationsPage() {
  const context = useClientNotificationContext();
  const [params] = useSearchParams();
  // A source deep link carries its company scope; the server revalidates access.
  const companyId = params.get("item") && params.get("company") ? params.get("company") : context.companyId;
  return (
    <NotificationInbox
      key={context.identity}
      context={{ ...context, companyId, identity: `${context.identity}:${companyId}` }}
    />
  );
}
export function OperatorNotificationsPage() {
  const context = useOperatorNotificationContext();
  return <NotificationInbox key={context.identity} context={context} />;
}
