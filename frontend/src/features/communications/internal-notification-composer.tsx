import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { communicationApi } from "@/api/communication-api";
import { useClientSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { ErrorState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { useCommunicationCopy } from "./communication-copy";

const columns = createDataColumns<{ id: string; name: string }>();
export function InternalNotificationComposer({ onClose }: { onClose: () => void }) {
  const c = useCommunicationCopy(),
    cache = useQueryClient(),
    session = useClientSession();
  const [commandId] = useState(() => crypto.randomUUID());
  const [title, setTitle] = useState(""),
    [body, setBody] = useState(""),
    [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Record<string, string>>({}),
    [page, setPage] = useState(0),
    [review, setReview] = useState(false),
    [discard, setDiscard] = useState(false);
  const debounced = useDebouncedValue(search, 250);
  const query = useQuery({
    queryKey: ["client", "notification-members", session.account?.id, debounced, page],
    queryFn: () => communicationApi.members(debounced, page),
    enabled: !review,
  });
  useEffect(() => {
    if (!title && !body && !Object.keys(selected).length) return;
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [title, body, selected]);
  const send = useMutation({
    mutationFn: () =>
      communicationApi.sendInternal({
        commandId,
        messageTitle: title.trim(),
        messageBody: body.trim(),
        memberIds: Object.keys(selected),
      }),
    onSuccess: () => void cache.invalidateQueries({ queryKey: ["client", "notifications"] }),
  });
  const close = () => {
    if (send.isPending) return;
    if (!send.isSuccess && (title || body || Object.keys(selected).length)) setDiscard(true);
    else onClose();
  };
  const choices = columns.columns([
    columns.display({
      id: "selected",
      header: c("select"),
      cell: ({ row }) => (
        <Checkbox
          aria-label={`${c("select")} ${row.original.name}`}
          checked={Object.hasOwn(selected, row.original.id)}
          disabled={Object.keys(selected).length >= 100 && !Object.hasOwn(selected, row.original.id)}
          onCheckedChange={(v) =>
            setSelected((previous) => {
              const next = { ...previous };
              if (v === true) next[row.original.id] = row.original.name;
              else delete next[row.original.id];
              return next;
            })
          }
        />
      ),
    }),
    columns.accessor("name", { header: c("member") }),
  ]);
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) close();
      }}
    >
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl" closeLabel={c("dismiss")}>
        <DialogHeader>
          <DialogTitle>
            {c(discard ? "discard" : send.isSuccess ? "sentNotice" : review ? "reviewSend" : "notifyMembers")}
          </DialogTitle>
          <DialogDescription>{c(discard ? "unsaved" : "internalHint")}</DialogDescription>
        </DialogHeader>
        {discard ? (
          <DialogFooter>
            <Button variant="outline" onClick={() => setDiscard(false)}>
              {c("back")}
            </Button>
            <Button onClick={onClose}>{c("leave")}</Button>
          </DialogFooter>
        ) : send.isSuccess ? (
          <DialogFooter>
            <Button onClick={onClose}>{c("dismiss")}</Button>
          </DialogFooter>
        ) : (
          <>
            {review ? (
              <div className="space-y-4">
                <h3 dir="auto" className="font-semibold">
                  {title}
                </h3>
                <p dir="auto" className="max-w-prose whitespace-pre-wrap break-words">
                  {body}
                </p>
                <div className="border-t pt-4">
                  <p className="text-sm font-medium">
                    {Object.keys(selected).length} {c("selectedMembers")}
                  </p>
                  <ul className="mt-2 flex max-h-48 flex-wrap gap-x-4 gap-y-2 overflow-y-auto text-sm text-muted-foreground">
                    {Object.entries(selected).map(([id, name]) => (
                      <li key={id}>{name}</li>
                    ))}
                  </ul>
                </div>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="internal-title">{c("subject")}</Label>
                  <Input id="internal-title" maxLength={160} value={title} onChange={(e) => setTitle(e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="internal-body">{c("body")}</Label>
                  <Textarea
                    id="internal-body"
                    rows={4}
                    maxLength={10000}
                    value={body}
                    onChange={(e) => setBody(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="internal-search">{c("memberSearch")}</Label>
                  <Input
                    id="internal-search"
                    value={search}
                    onChange={(e) => {
                      setSearch(e.target.value);
                      setPage(0);
                    }}
                  />
                </div>
                <p className="text-sm text-muted-foreground">
                  {Object.keys(selected).length}/100 {c("selectedMembers")}
                </p>
                {query.isError ? (
                  <ErrorState retry={() => void query.refetch()} />
                ) : (
                  <div className="overflow-hidden rounded-lg border">
                    <DataTable
                      columns={choices}
                      data={query.data?.content ?? []}
                      getRowId={(r) => r.id}
                      isLoading={query.isLoading}
                    />
                    {query.data && <PaginationBar {...query.data} page={page} onPageChange={setPage} />}
                  </div>
                )}
                {Object.keys(selected).length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {Object.entries(selected).map(([id, name]) => (
                      <Button
                        key={id}
                        size="sm"
                        variant="secondary"
                        aria-label={`${c("remove")} ${name}`}
                        onClick={() =>
                          setSelected((prev) => {
                            const next = { ...prev };
                            delete next[id];
                            return next;
                          })
                        }
                      >
                        {name} ×
                      </Button>
                    ))}
                  </div>
                )}
              </div>
            )}
            {send.isError && (
              <p role="alert" className="text-sm text-destructive">
                {send.error.message || c("error")}
              </p>
            )}
            <DialogFooter>
              <Button variant="ghost" disabled={send.isPending} onClick={() => (review ? setReview(false) : close())}>
                {c(review ? "back" : "dismiss")}
              </Button>
              <Button
                disabled={send.isPending || !title.trim() || !body.trim() || !Object.keys(selected).length}
                onClick={() => (review ? send.mutate() : setReview(true))}
              >
                {c(review ? "send" : "reviewSend")}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
