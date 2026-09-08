import { useQuery } from "@tanstack/react-query";
import { useId, useState } from "react";
import type { PageResponse } from "@/api/contracts";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useDebouncedValue } from "@/lib/use-debounced-value";

/** Server-paged single choice, shared by tariffs, Plans and Segments in this flow. */
export function RepricingChoiceList<T extends { id: string }>({
  title,
  cacheKey,
  load,
  label,
  selectedId,
  onSelect,
  allowed = true,
  eligible = () => true,
}: {
  title: string;
  cacheKey: readonly unknown[];
  load: (search: string, page: number) => Promise<PageResponse<T>>;
  label: (item: T) => string;
  selectedId?: string;
  onSelect: (item: T) => void;
  allowed?: boolean;
  eligible?: (item: T) => boolean;
}) {
  const id = useId();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const deferredSearch = useDebouncedValue(search);
  const query = useQuery({
    queryKey: [...cacheKey, deferredSearch, page],
    queryFn: () => load(deferredSearch, page),
    enabled: allowed,
  });
  if (!allowed)
    return (
      <p className="text-sm text-muted-foreground">La consultation de {title.toLowerCase()} n’est pas autorisée.</p>
    );
  return (
    <div className="space-y-3">
      <Label htmlFor={id}>{title}</Label>
      <Input
        id={id}
        placeholder="Rechercher…"
        value={search}
        onChange={(event) => {
          setSearch(event.target.value);
          setPage(0);
        }}
      />
      {query.isLoading ? (
        <LoadingState rows={3} />
      ) : query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : query.data ? (
        <div className="overflow-hidden rounded-lg border">
          <fieldset aria-label={title} className="divide-y">
            {query.data.content.map((item) => (
              <label
                key={item.id}
                className={`flex min-h-12 items-center gap-3 px-4 py-3 text-sm ${eligible(item) ? "cursor-pointer hover:bg-muted/50" : "opacity-50"}`}
              >
                <input
                  type="radio"
                  name={id}
                  checked={selectedId === item.id}
                  disabled={!eligible(item)}
                  onChange={() => onSelect(item)}
                  className="size-4 accent-primary"
                />
                <span>{label(item)}</span>
              </label>
            ))}
          </fieldset>
          {!query.data.content.length ? <EmptyState title="Aucun résultat" /> : null}
          <PaginationBar {...query.data} onPageChange={setPage} />
        </div>
      ) : null}
    </div>
  );
}
