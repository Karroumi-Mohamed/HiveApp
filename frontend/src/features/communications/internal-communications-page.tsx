import { PlusIcon } from "@phosphor-icons/react";
import { useState } from "react";
import { PageHeader } from "@/components/patterns/page-header";
import { PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { useCommunicationCopy } from "./communication-copy";
import { InternalNotificationComposer } from "./internal-notification-composer";
import { InternalNotificationHistory } from "./internal-notification-history";
import { useClientNotificationContext } from "./notification-context";
export function InternalCommunicationsPage() {
  const context = useClientNotificationContext(),
    c = useCommunicationCopy();
  const [compose, setCompose] = useState(false);
  if (!context.send && !context.sent) return <PermissionState />;
  return (
    <div className="space-y-6">
      <PageHeader
        title={c("title")}
        actions={
          context.send && (
            <Button onClick={() => setCompose(true)}>
              <PlusIcon />
              {c("notifyMembers")}
            </Button>
          )
        }
      />
      {context.sent && <InternalNotificationHistory identity={context.identity} />}
      {compose && context.send && <InternalNotificationComposer onClose={() => setCompose(false)} />}
    </div>
  );
}
