import type { Icon } from "@phosphor-icons/react";
import { PageHeader } from "@/components/patterns/page-header";

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
    <div>
      <PageHeader title={title} />
      <section className="mt-10 max-w-3xl border-t pt-6" aria-label="Fonctionnalités prévues">
        <div className="flex items-start gap-3">
          <SectionIcon aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-muted-foreground" />
          <div>
            <h2 className="text-sm font-semibold">Section en préparation</h2>
            <p className="mt-1 text-sm leading-6 text-muted-foreground">{description}</p>
          </div>
        </div>
        <ul className="mt-6 divide-y border-y text-sm">
          {planned.map((item) => (
            <li className="py-3 text-muted-foreground" key={item}>
              {item}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
