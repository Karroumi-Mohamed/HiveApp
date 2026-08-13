import type { ReactNode } from "react";

export function PageHeader({
  title,
  actions,
  description,
}: {
  title: string;
  actions?: ReactNode;
  description?: ReactNode;
}) {
  return (
    <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-[-0.035em] md:text-[1.75rem]">{title}</h1>
        {description ? <div className="mt-1.5 max-w-3xl text-sm text-muted-foreground">{description}</div> : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  );
}
