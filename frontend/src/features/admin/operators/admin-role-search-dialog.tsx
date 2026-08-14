import { CheckIcon, MagnifyingGlassIcon } from "@phosphor-icons/react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { useDeferredValue, useMemo, useState } from "react";
import { adminApi } from "@/api/admin-api";
import { ApiError } from "@/api/http";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";

/** Paginated role chooser shared by actions that select one role from the full catalogue. */
export function AdminRoleSearchDialog({
  children,
  description,
  onConfirm,
  pending,
  title,
}: {
  children: React.ReactNode;
  description: string;
  onConfirm: (roleId: string) => Promise<unknown>;
  pending: boolean;
  title: string;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const deferredSearch = useDeferredValue(search.trim());
  const roles = useInfiniteQuery({
    queryKey: ["admin", "roles", "single-picker", deferredSearch],
    queryFn: ({ pageParam }) =>
      adminApi.roles({
        active: true,
        search: deferredSearch || undefined,
        page: pageParam,
        size: 50,
        sort: "name",
        direction: "asc",
      }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.page + 1),
    enabled: open,
  });
  const options = useMemo(
    () =>
      (roles.data?.pages.flatMap((page) => page.content) ?? []).filter((role) =>
        role.availableActions.includes("ASSIGN_TO_OPERATOR"),
      ),
    [roles.data?.pages],
  );

  const close = () => {
    setOpen(false);
    setSearch("");
    setSelectedId(null);
    setError(null);
  };

  return (
    <Dialog
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) {
          setSearch("");
          setSelectedId(null);
          setError(null);
        }
      }}
      open={open}
    >
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <div className="relative">
          <MagnifyingGlassIcon className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <Input
            aria-label="Rechercher un rôle"
            autoComplete="off"
            className="ps-9"
            name="bulk-role-search"
            onChange={(event) => {
              setSearch(event.target.value);
              setSelectedId(null);
            }}
            placeholder="Rechercher un rôle…"
            spellCheck={false}
            value={search}
          />
        </div>
        <div className="max-h-[min(55vh,26rem)] overflow-y-auto border-y">
          {roles.isLoading ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Chargement…</p>
          ) : roles.isError && options.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-8 text-sm text-muted-foreground">
              <p>Impossible de charger les rôles.</p>
              <Button onClick={() => void roles.refetch()} size="sm" variant="outline">
                Réessayer
              </Button>
            </div>
          ) : options.length ? (
            options.map((role) => (
              <button
                aria-pressed={selectedId === role.id}
                className="flex min-h-14 w-full items-start gap-3 border-b py-3 text-start last:border-b-0 hover:bg-muted/45"
                key={role.id}
                onClick={() => setSelectedId(role.id)}
                type="button"
              >
                <span className="flex size-5 shrink-0 items-center justify-center">
                  {selectedId === role.id ? <CheckIcon aria-hidden="true" weight="bold" /> : null}
                </span>
                <span className="min-w-0">
                  <span className="block text-sm font-medium">{role.name}</span>
                  {role.description ? (
                    <span className="mt-0.5 block text-xs leading-5 text-muted-foreground">{role.description}</span>
                  ) : null}
                </span>
              </button>
            ))
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">Aucun rôle correspondant.</p>
          )}
          {roles.hasNextPage ? (
            <div className="border-t py-3 text-center">
              <Button
                disabled={roles.isFetchingNextPage}
                onClick={() => void roles.fetchNextPage()}
                size="sm"
                variant="ghost"
              >
                {roles.isFetchingNextPage ? "Chargement…" : "Charger plus de rôles"}
              </Button>
            </div>
          ) : null}
        </div>
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
        <DialogFooter>
          <Button onClick={close} type="button" variant="outline">
            Annuler
          </Button>
          <Button
            disabled={!selectedId || pending}
            onClick={async () => {
              if (!selectedId) return;
              setError(null);
              try {
                await onConfirm(selectedId);
                close();
              } catch (reason) {
                setError(reason instanceof ApiError ? reason.message : "Attribution impossible");
              }
            }}
            type="button"
          >
            {pending ? "Attribution…" : "Attribuer le rôle"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
