import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { communicationApi } from "@/api/communication-api";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { EmptyState, ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { communicationDate, useCommunicationCopy } from "./communication-copy";
export function CommunicationThread({
  id,
  admin = false,
  canReply = false,
}: {
  id: string;
  admin?: boolean;
  canReply?: boolean;
}) {
  const c = useCommunicationCopy(),
    cache = useQueryClient();
  const [page, setPage] = useState(0),
    [text, setText] = useState("");
  const command = useRef(crypto.randomUUID());
  const key = [admin ? "admin" : "client", "communications", id, "replies"];
  const query = useQuery({ queryKey: [...key, page], queryFn: () => communicationApi.thread(id, admin, page) });
  const send = useMutation({
    mutationFn: () => communicationApi.reply(id, admin, command.current, text.trim()),
    onSuccess: () => {
      setText("");
      command.current = crypto.randomUUID();
      setPage(0);
      void cache.invalidateQueries({ queryKey: [admin ? "admin" : "client", "communications"] });
    },
  });
  return (
    <section className="space-y-4 border-t pt-5">
      <h3 className="font-semibold">{c("replies")}</h3>
      {query.isLoading ? (
        <LoadingState rows={2} />
      ) : query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : !query.data?.content.length ? (
        <EmptyState title={c("noReplies")} />
      ) : (
        <ol className="space-y-3">
          {query.data.content.map((reply) => (
            <li
              key={reply.id}
              className={`max-w-2xl rounded-lg border p-4 ${reply.fromAdmin ? "bg-muted/60" : "ms-auto bg-card"}`}
            >
              <div className="mb-2 flex justify-between gap-3 text-xs text-muted-foreground">
                <span>{c(reply.fromAdmin ? "team" : "client")}</span>
                <time>{communicationDate(reply.createdAt)}</time>
              </div>
              <p dir="auto" className="whitespace-pre-wrap break-words text-sm">
                {reply.replyBody}
              </p>
            </li>
          ))}
        </ol>
      )}
      {query.data && <PaginationBar {...query.data} onPageChange={setPage} />}
      {canReply && (
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            send.mutate();
          }}
        >
          <Label htmlFor={`reply-${id}`}>{c("replyLabel")}</Label>
          <Textarea
            id={`reply-${id}`}
            maxLength={4000}
            value={text}
            onChange={(e) => {
              setText(e.target.value);
              command.current = crypto.randomUUID();
            }}
            rows={3}
          />
          <Button disabled={!text.trim() || send.isPending} type="submit">
            {c("sendReply")}
          </Button>
          {send.isError && (
            <p role="alert" className="text-sm text-destructive">
              {c("error")}
            </p>
          )}
        </form>
      )}
    </section>
  );
}
