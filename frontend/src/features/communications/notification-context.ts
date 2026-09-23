import { adminPermissions, clientPermissions } from "@/auth/permissions";
import { useAdminSession, useClientSession } from "@/auth/session-provider";
import type { NotificationContext } from "./notification-inbox";

export function useClientNotificationContext(): NotificationContext {
  const s = useClientSession();
  return {
    platform: false,
    companyId: s.selectedCompanyId,
    identity: [s.account?.id, s.permissions?.memberId, s.selectedCompanyId].join(":"),
    allowed: !s.isB2B && s.can(clientPermissions.communicationsRead),
    read: s.can(clientPermissions.communicationsMarkRead),
    acknowledge: s.can(clientPermissions.communicationsAcknowledge),
    archive: s.can(clientPermissions.communicationsArchive),
    preferences: s.can(clientPermissions.notificationsPreferences),
    send: !s.isB2B && s.can(clientPermissions.notificationsSend) && s.can(clientPermissions.notificationsChoose),
    sent: !s.isB2B && s.can(clientPermissions.notificationsSent),
    delivery: false,
  };
}
export function useOperatorNotificationContext(): NotificationContext {
  const s = useAdminSession();
  return {
    platform: true,
    identity: s.me?.id ?? "",
    allowed: s.can(adminPermissions.notificationsRead),
    read: s.can(adminPermissions.notificationsMarkRead),
    acknowledge: s.can(adminPermissions.notificationsAcknowledge),
    archive: s.can(adminPermissions.notificationsArchive),
    preferences: s.can(adminPermissions.notificationsPreferences),
    send: false,
    delivery: s.can(adminPermissions.notificationsDelivery),
  };
}
