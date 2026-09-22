import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { adminOfferApi } from "@/api/admin-offer-api";
import { adminPermissions } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { useCommunicationCopy } from "./communication-copy";

export function NotificationOfferPicker({ id, onChange }: { id: string | null; onChange: (id: string) => void }) {
  const c = useCommunicationCopy(),
    session = useAdminSession();
  const [search, setSearch] = useState(""),
    [page, setPage] = useState(0);
  const q = useDebouncedValue(search, 250),
    allowed = session.can(adminPermissions.offersRead);
  const selected = useQuery({
    queryKey: ["admin", "communications", "offer", id],
    queryFn: () => adminOfferApi.detail(id ?? ""),
    enabled: allowed && !!id,
  });
  const query = useQuery({
    queryKey: ["admin", "communications", "offer-choices", q, page],
    queryFn: () =>
      adminOfferApi.list({
        search: q,
        page,
        size: 10,
        status: "PUBLISHED",
        discovery: "CATALOG",
        acceptance: "CLIENT_OR_OPERATOR",
      }),
    enabled: allowed,
  });
  if (!allowed) return <PermissionState />;
  return (
    <fieldset className="space-y-3 rounded-lg border p-4">
      <legend className="px-1 text-sm font-medium">{c("offer")}</legend>
      <p className="text-sm text-muted-foreground">{c("offerRequired")}</p>
      {selected.data && <p className="font-medium text-primary">{selected.data.name}</p>}
      {selected.isError && <ErrorState retry={() => void selected.refetch()} />}
      <Label htmlFor="offer-search">{c("offerChoose")}</Label>
      <Input
        id="offer-search"
        value={search}
        onChange={(e) => {
          setSearch(e.target.value);
          setPage(0);
        }}
      />
      {query.isLoading ? (
        <LoadingState rows={2} />
      ) : query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <div className="divide-y">
          {query.data?.content.map((offer) => (
            <label key={offer.id} className="flex min-h-11 cursor-pointer items-center gap-3 py-2 text-sm">
              <input
                type="radio"
                name="notification-offer"
                checked={id === offer.id}
                onChange={() => onChange(offer.id)}
              />
              {offer.name}
            </label>
          ))}
          {query.data?.content.length === 0 && <p className="py-4 text-sm text-muted-foreground">{c("empty")}</p>}
        </div>
      )}
      {query.data && <PaginationBar {...query.data} page={page} onPageChange={setPage} />}
    </fieldset>
  );
}
