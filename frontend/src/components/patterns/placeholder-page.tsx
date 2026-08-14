import type { Icon } from "@phosphor-icons/react";
import { CheckCircleIcon } from "@phosphor-icons/react";
import { PageHeader } from "@/components/patterns/page-header";
import { StatusBadge } from "@/components/patterns/status-badge";

/**
 * A navigable destination for a section that is planned but not built. Shipping the entry with
 * an honest placeholder keeps the navigation teaching the product's shape, instead of sections
 * appearing from nowhere the day they land.
 */
export function PlaceholderPage({
  title,
  description,
  icon: SectionIcon,
  planned,
}: {
  title: string;
  description: string;
  icon: Icon;
  planned: string[];
}) {
  return (
    <div className="space-y-7">
      <PageHeader description={description} title={title} />
      <section
        className={[
          "relative grid min-h-[26rem] place-items-center rounded-lg border text-muted-foreground/15",
          "[background-image:repeating-linear-gradient(45deg,transparent,transparent_6px,currentColor_6px,currentColor_7px)]",
        ].join(" ")}
      >
        <div className="max-w-md rounded-lg border bg-card px-8 py-10 text-center shadow-sm">
          <span className="mx-auto grid size-16 place-items-center rounded-full border bg-muted/40">
            <SectionIcon className="size-8 text-muted-foreground" />
          </span>
          <div className="mt-4 flex justify-center">
            <StatusBadge tone="info">En construction</StatusBadge>
          </div>
          <h2 className="mt-3 text-lg font-semibold text-foreground">Bientôt dans cet espace</h2>
          <ul className="mt-4 space-y-2 text-start text-sm text-foreground">
            {planned.map((item) => (
              <li className="flex items-start gap-2" key={item}>
                <CheckCircleIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                <span className="text-muted-foreground">{item}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </div>
  );
}
