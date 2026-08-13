import { ArrowClockwiseIcon, FolderOpenIcon, LockKeyIcon, WarningCircleIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

export function LoadingState({ rows = 5 }: { rows?: number }) {
  const skeletonRows = Array.from({ length: rows }, (_, index) => `skeleton-row-${index + 1}`);

  return (
    <div aria-label="Chargement" className="space-y-3" role="status">
      {skeletonRows.map((row) => (
        <Skeleton className="h-12 w-full rounded-lg" key={row} />
      ))}
    </div>
  );
}

function StateSurface({
  icon,
  title,
  description,
  action,
}: {
  icon: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="grid min-h-56 place-items-center px-6 py-10 text-center">
      <div className="max-w-md">
        <div className="mx-auto mb-4 flex justify-center text-muted-foreground">{icon}</div>
        <h2 className="text-base font-semibold">{title}</h2>
        {description ? <p className="mt-1.5 text-sm text-muted-foreground">{description}</p> : null}
        {action ? <div className="mt-5 flex justify-center">{action}</div> : null}
      </div>
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <StateSurface
      action={action}
      description={description}
      icon={<FolderOpenIcon className="size-8" />}
      title={title}
    />
  );
}

export function ErrorState({
  title = "Impossible de charger les données",
  description,
  retry,
}: {
  title?: string;
  description?: string;
  retry?: () => void;
}) {
  return (
    <StateSurface
      action={
        retry ? (
          <Button onClick={retry} variant="outline">
            <ArrowClockwiseIcon />
            Réessayer
          </Button>
        ) : undefined
      }
      description={description}
      icon={<WarningCircleIcon className="size-8 text-destructive" />}
      title={title}
    />
  );
}

export function PermissionState({
  description = "Vous n’avez pas l’autorisation nécessaire pour consulter cette surface.",
}: {
  description?: string;
}) {
  return (
    <StateSurface description={description} icon={<LockKeyIcon className="size-8" />} title="Accès indisponible" />
  );
}
