import { BellIcon, GearIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router";
import { communicationApi } from "@/api/communication-api";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { useCommunicationCopy } from "./communication-copy";
import { useClientNotificationContext, useOperatorNotificationContext } from "./notification-context";
import { type NotificationContext, NotificationInbox } from "./notification-inbox";

function NotificationShortcut({ context }: { context: NotificationContext }) {
  const c = useCommunicationCopy();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => {
    void location.pathname;
    void context.identity;
    setOpen(false);
  }, [location.pathname, context.identity]);
  const query = useQuery({
    queryKey: [context.platform ? "admin" : "client", "notifications", context.identity, "summary"],
    queryFn: () => communicationApi.summary(context.platform, context.companyId),
    enabled: context.allowed,
    staleTime: 15000,
    refetchInterval: 60000,
    refetchIntervalInBackground: false,
  });
  if (!context.allowed) return null;
  const count = query.data?.unread ?? 0;
  const root = context.platform ? "/admin" : "/app";
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className="min-h-11 min-w-11"
          aria-label={`${c("notifications")}${count ? ` · ${count} ${c("unread")}` : ""}`}
        >
          <BellIcon />
          {count > 0 && (
            <span className="min-w-4 text-xs font-semibold tabular-nums text-primary">
              {count > 99 ? "99+" : count}
            </span>
          )}
        </Button>
      </SheetTrigger>
      <SheetContent className="w-full gap-0 sm:max-w-md" closeLabel={c("dismiss")}>
        <SheetHeader className="shrink-0 border-b pe-12">
          <SheetTitle>{c("notifications")}</SheetTitle>
          <SheetDescription className="sr-only">{c("inbox")}</SheetDescription>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto p-4">
          {open && <NotificationInbox key={context.identity} context={context} panel />}
        </div>
        <footer className="flex shrink-0 items-center justify-between gap-3 border-t p-4">
          <Button asChild variant="ghost">
            <Link onClick={() => setOpen(false)} to={context.platform ? "/admin/notifications" : "/app/communications"}>
              {c("viewAll")}
            </Link>
          </Button>
          <Button asChild variant="ghost" size="icon">
            <Link
              aria-label={c("personalSettings")}
              onClick={() => setOpen(false)}
              to={`${root}/settings?section=notifications`}
            >
              <GearIcon />
            </Link>
          </Button>
        </footer>
      </SheetContent>
    </Sheet>
  );
}
export function ClientNotificationShortcut() {
  return <NotificationShortcut context={useClientNotificationContext()} />;
}
export function OperatorNotificationShortcut() {
  return <NotificationShortcut context={useOperatorNotificationContext()} />;
}
