import type { ReactNode } from "react";
import { useAdminSession, useClientSession } from "@/auth/session-provider";
import { LoadingState, PermissionState } from "@/components/patterns/remote-state";

export type PermissionRequirement = Readonly<{
  allOf?: readonly string[];
  anyOf?: readonly string[];
}>;

export function meetsPermissionRequirement(
  can: (permission: string) => boolean,
  { allOf = [], anyOf = [] }: PermissionRequirement,
) {
  return allOf.every(can) && (anyOf.length === 0 || anyOf.some(can));
}

export function ReadPermissionGate({
  allowed,
  children,
  description,
  loading = false,
}: {
  allowed: boolean;
  children: ReactNode;
  description?: string;
  loading?: boolean;
}) {
  if (loading) return <LoadingState />;
  if (!allowed) {
    return (
      <div aria-live="polite" role="status">
        <PermissionState description={description} />
      </div>
    );
  }
  return children;
}

export function AdminReadPermissionGate({
  children,
  description,
  ...requirement
}: PermissionRequirement & { children: ReactNode; description?: string }) {
  const session = useAdminSession();
  return (
    <ReadPermissionGate
      allowed={meetsPermissionRequirement(session.can, requirement)}
      description={description}
      loading={session.loading}
    >
      {children}
    </ReadPermissionGate>
  );
}

export function ClientReadPermissionGate({
  children,
  description,
  ...requirement
}: PermissionRequirement & { children: ReactNode; description?: string }) {
  const session = useClientSession();
  return (
    <ReadPermissionGate
      allowed={meetsPermissionRequirement(session.can, requirement)}
      description={description}
      loading={session.loading}
    >
      {children}
    </ReadPermissionGate>
  );
}
