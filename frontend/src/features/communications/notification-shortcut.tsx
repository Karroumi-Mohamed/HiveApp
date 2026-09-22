import { BellIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { communicationApi } from "@/api/communication-api";
import { adminPermissions, clientPermissions } from "@/auth/permissions";
import { useAdminSession, useClientSession } from "@/auth/session-provider";
import { Button } from "@/components/ui/button";
import { useCommunicationCopy } from "./communication-copy";

function NotificationShortcut({
  platform,
  identity,
  companyId,
  allowed,
}: {
  platform: boolean;
  identity: string;
  companyId?: string | null;
  allowed: boolean;
}) {
  const c = useCommunicationCopy();
  const query = useQuery({
    queryKey: [platform ? "admin" : "client", "notifications", identity, "summary"],
    queryFn: () => communicationApi.summary(platform, companyId),
    enabled: allowed,
    staleTime: 15000,
    refetchInterval: 60000,
    refetchIntervalInBackground: false,
  });
  if (!allowed) return null;
  const count = query.data?.unread ?? 0;
  return (
    <Button asChild variant="ghost" size="sm" className="min-h-11 min-w-11">
      <Link
        to={platform ? "/admin/notifications" : "/app/communications"}
        aria-label={`${c("notifications")}${count ? ` · ${count} ${c("unread")}` : ""}`}
      >
        <BellIcon />
        {count > 0 && (
          <span className="min-w-4 text-xs font-semibold tabular-nums text-primary">{count > 99 ? "99+" : count}</span>
        )}
      </Link>
    </Button>
  );
}
export function ClientNotificationShortcut() {
  const s = useClientSession();
  return (
    <NotificationShortcut
      platform={false}
      companyId={s.selectedCompanyId}
      identity={[s.account?.id, s.permissions?.memberId, s.selectedCompanyId].join(":")}
      allowed={!s.isB2B && s.can(clientPermissions.communicationsRead)}
    />
  );
}
export function OperatorNotificationShortcut() {
  const s = useAdminSession();
  return (
    <NotificationShortcut platform identity={s.me?.id ?? ""} allowed={s.can(adminPermissions.notificationsRead)} />
  );
}
