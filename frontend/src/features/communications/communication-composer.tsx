import { BellIcon, TagIcon, WarningIcon } from "@phosphor-icons/react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Link, useBlocker, useNavigate, useParams, useSearchParams } from "react-router";
import { type CommunicationDraft, type CommunicationPublication, communicationApi } from "@/api/communication-api";
import { adminPermissions as p } from "@/auth/permissions";
import { useAdminSession } from "@/auth/session-provider";
import { createDataColumns, DataTable } from "@/components/patterns/data-table";
import { PageHeader } from "@/components/patterns/page-header";
import { PaginationBar } from "@/components/patterns/pagination-bar";
import { ErrorState, LoadingState, PermissionState } from "@/components/patterns/remote-state";
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
import { communicationDate, useCommunicationCopy } from "./communication-copy";
import { communicationDraftValid, localDateTime } from "./communication-rules";
import { NotificationOfferPicker } from "./notification-offer-picker";

const icons = { NOTICE: BellIcon, WARNING: WarningIcon, OFFER: TagIcon };
const blank: CommunicationDraft = {
  kind: "NOTICE",
  purpose: "SERVICE",
  messageTitle: "",
  messageBody: "",
  accountIds: [],
  email: false,
  offerId: null,
  availableAt: null,
  expiresAt: null,
};
const columns = createDataColumns<{ id: string; name: string }>();

export function CommunicationComposer() {
  const { communicationId } = useParams();
  const session = useAdminSession();
  const allowed =
    session.can(communicationId ? p.customerCommunicationsEdit : p.customerCommunicationsCreate) &&
    session.can(p.customerCommunicationsChoose) &&
    session.can(p.customerCommunicationsRead);
  const query = useQuery({
    queryKey: ["admin", "communications", communicationId],
    queryFn: () => communicationApi.detail(communicationId ?? ""),
    enabled: allowed && !!communicationId,
  });
  if (!allowed) return <PermissionState />;
  if (communicationId && query.isLoading) return <LoadingState />;
  if (communicationId && (query.isError || !query.data)) return <ErrorState retry={() => void query.refetch()} />;
  if (query.data && query.data.state !== "DRAFT") return <PermissionState />;
  return <ComposerForm key={communicationId ?? "new"} initial={query.data} />;
}
function ComposerForm({ initial }: { initial?: CommunicationPublication }) {
  const session = useAdminSession();
  const c = useCommunicationCopy(),
    navigate = useNavigate();
  const [params] = useSearchParams();
  const offerId = params.get("offer");
  const prefillOffer = offerId && /^[0-9a-f-]{36}$/i.test(offerId) ? offerId : null;
  const [draft, setDraft] = useState<CommunicationDraft>(
      initial ?? (prefillOffer ? { ...blank, kind: "OFFER", purpose: "MARKETING", offerId: prefillOffer } : blank),
    ),
    [step, setStep] = useState(0),
    [dirty, setDirty] = useState(false);
  const [search, setSearch] = useState(""),
    [page, setPage] = useState(0),
    [names, setNames] = useState<Record<string, string>>({});
  const complete = useRef(false);
  const change = (next: Partial<CommunicationDraft>) => {
    setDirty(true);
    setDraft((d) => ({ ...d, ...next }));
  };
  const blocker = useBlocker(() => dirty && !complete.current);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (dirty && !complete.current) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const lookup = useQuery({
    queryKey: ["admin", "communications", "choices", search, page],
    queryFn: () => communicationApi.choices(search, page),
    enabled: step === 1,
  });
  const savedRecipients = useQuery({
    queryKey: ["admin", "communications", initial?.id, "selected-recipients"],
    queryFn: () => communicationApi.selectedRecipients(initial?.id ?? ""),
    enabled: !!initial,
  });
  const knownNames = { ...Object.fromEntries((savedRecipients.data ?? []).map((a) => [a.id, a.name])), ...names };
  const save = useMutation({
    mutationFn: () =>
      initial ? communicationApi.edit(initial.id, initial.version, draft) : communicationApi.create(draft),
    onSuccess: (value) => {
      complete.current = true;
      navigate(`/admin/communications/${value.id}`);
    },
  });
  const contentValid = communicationDraftValid({ ...draft, accountIds: ["selected"] });
  const tableColumns = columns.columns([
    columns.display({
      id: "selected",
      header: c("select"),
      cell: ({ row }) => (
        <Checkbox
          aria-label={`${c("select")} ${row.original.name}`}
          checked={draft.accountIds.includes(row.original.id)}
          disabled={
            draft.accountIds.length >= (draft.kind === "OFFER" ? 100 : 500) &&
            !draft.accountIds.includes(row.original.id)
          }
          onCheckedChange={(checked) => {
            change({
              accountIds:
                checked === true
                  ? [...draft.accountIds, row.original.id]
                  : draft.accountIds.filter((id) => id !== row.original.id),
            });
            setNames((n) => ({ ...n, [row.original.id]: row.original.name }));
          }}
        />
      ),
    }),
    columns.accessor("name", { header: c("account") }),
  ]);
  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <Link to="/admin/communications" className="text-sm text-muted-foreground">
        ← {c("title")}
      </Link>
      <PageHeader title={c(initial ? "edit" : "create")} />
      <ol className="flex flex-wrap gap-4 border-b pb-4">
        {["content", "recipients", "review"].map((label, index) => (
          <li
            key={label}
            className={step === index ? "font-semibold text-primary" : "text-muted-foreground"}
            aria-current={step === index ? "step" : undefined}
          >
            {index + 1}. {c(label)}
          </li>
        ))}
      </ol>
      <section className="space-y-5 rounded-xl border bg-card p-5 sm:p-7">
        {step === 0 && (
          <>
            <fieldset>
              <legend className="mb-3 text-sm font-medium">{c("type")}</legend>
              <div className="grid gap-3 sm:grid-cols-3">
                {(["NOTICE", "WARNING", "OFFER"] as const).map((kind) => {
                  const Icon = icons[kind];
                  return (
                    <button
                      type="button"
                      key={kind}
                      disabled={kind === "OFFER" && !session.can(p.offersRead)}
                      aria-pressed={draft.kind === kind}
                      className={`rounded-lg border p-4 text-start focus-visible:ring-2 focus-visible:ring-ring ${draft.kind === kind ? "border-primary bg-primary/5" : "hover:bg-muted/50"}`}
                      onClick={() =>
                        change({
                          kind,
                          offerId: kind === "OFFER" ? draft.offerId : null,
                          purpose: kind === "WARNING" ? "SERVICE" : kind === "OFFER" ? "MARKETING" : draft.purpose,
                        })
                      }
                    >
                      <Icon className={`mb-3 size-6 ${kind === "WARNING" ? "text-warning" : "text-primary"}`} />
                      <span className="block font-semibold">{c(kind)}</span>
                      <span className="mt-1 block text-xs leading-relaxed text-muted-foreground">
                        {c(kind === "NOTICE" ? "noticeHint" : kind === "WARNING" ? "warningHint" : "offerHint")}
                      </span>
                    </button>
                  );
                })}
              </div>
            </fieldset>
            <fieldset className="flex flex-wrap items-center gap-5">
              <legend className="mb-2 text-sm font-medium">{c("purpose")}</legend>
              {(["SERVICE", "MARKETING"] as const).map((purpose) => (
                <label key={purpose} className="flex min-h-11 items-center gap-2 text-sm">
                  <input
                    type="radio"
                    name="purpose"
                    checked={draft.purpose === purpose}
                    disabled={
                      (draft.kind === "WARNING" && purpose === "MARKETING") ||
                      (draft.kind === "OFFER" && purpose === "SERVICE")
                    }
                    onChange={() => change({ purpose })}
                  />
                  {c(purpose)}
                </label>
              ))}
            </fieldset>
            {draft.purpose === "MARKETING" && <p className="text-sm text-muted-foreground">{c("marketingHint")}</p>}
            <div className="space-y-2">
              <Label htmlFor="comm-title">{c("subject")}</Label>
              <Input
                id="comm-title"
                maxLength={160}
                value={draft.messageTitle}
                onChange={(e) => change({ messageTitle: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="comm-body">{c("body")}</Label>
              <Textarea
                id="comm-body"
                rows={7}
                maxLength={10000}
                value={draft.messageBody}
                onChange={(e) => change({ messageBody: e.target.value })}
              />
            </div>
            {draft.kind === "OFFER" && (
              <NotificationOfferPicker id={draft.offerId} onChange={(offerId) => change({ offerId })} />
            )}
            <label htmlFor="comm-email" className="flex min-h-11 items-center gap-3 text-sm">
              <Checkbox
                id="comm-email"
                checked={draft.email}
                onCheckedChange={(value) => change({ email: value === true })}
              />
              {c("email")}
            </label>
            <p className="text-xs text-muted-foreground">{c("emailHint")}</p>
            <div className="grid gap-4 sm:grid-cols-2">
              {(["availableAt", "expiresAt"] as const).map((field) => (
                <div key={field} className="space-y-2">
                  <Label htmlFor={field}>{c(field === "availableAt" ? "available" : "expiry")}</Label>
                  <Input
                    id={field}
                    type="datetime-local"
                    value={localDateTime(draft[field])}
                    onChange={(e) =>
                      change({ [field]: e.target.value ? new Date(e.target.value).toISOString() : null })
                    }
                  />
                </div>
              ))}
            </div>
            <p className="text-xs text-muted-foreground">{c("scheduleHint")}</p>
          </>
        )}
        {step === 1 && (
          <>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="font-semibold">
                {draft.accountIds.length} {c("selected")}
              </p>
              <Input
                aria-label={c("search")}
                className="sm:max-w-xs"
                placeholder={c("search")}
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
              />
            </div>
            <p className="text-sm text-muted-foreground">{c("max")}</p>
            {lookup.isError ? (
              <ErrorState retry={() => void lookup.refetch()} />
            ) : (
              <div className="overflow-hidden rounded-lg border">
                <DataTable
                  columns={tableColumns}
                  data={lookup.data?.content ?? []}
                  getRowId={(r) => r.id}
                  isLoading={lookup.isLoading}
                />
                {lookup.data && <PaginationBar {...lookup.data} page={page} onPageChange={setPage} />}
              </div>
            )}
            {draft.accountIds.length > 0 && (
              <details>
                <summary className="cursor-pointer text-sm font-medium">
                  {c("recipients")} ({draft.accountIds.length})
                </summary>
                {savedRecipients.isError && <ErrorState retry={() => void savedRecipients.refetch()} />}
                <ul className="mt-3 flex max-h-48 flex-wrap gap-2 overflow-y-auto">
                  {draft.accountIds.map((id) => (
                    <li key={id}>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => change({ accountIds: draft.accountIds.filter((v) => v !== id) })}
                        aria-label={`${c("remove")} ${knownNames[id] ?? c("account")}`}
                      >
                        {knownNames[id] ?? c("account")} ×
                      </Button>
                    </li>
                  ))}
                </ul>
              </details>
            )}
          </>
        )}
        {step === 2 && (
          <>
            <div className="flex flex-wrap gap-3 text-sm text-muted-foreground">
              <span>{c(draft.kind)}</span>
              <span>{c(draft.purpose)}</span>
              <span>
                {draft.accountIds.length} {c("selected")}
              </span>
            </div>
            <h2 className="text-xl font-semibold">{draft.messageTitle}</h2>
            <p className="max-w-prose whitespace-pre-wrap break-words leading-relaxed">{draft.messageBody}</p>
            <dl className="grid gap-4 border-t pt-5 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-muted-foreground">{c("availableShort")}</dt>
                <dd>{draft.availableAt ? communicationDate(draft.availableAt) : c("now")}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{c("expiry")}</dt>
                <dd>{communicationDate(draft.expiresAt)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{c("delivery")}</dt>
                <dd>{c(draft.email ? "inAppEmail" : "NOT_REQUESTED")}</dd>
              </div>
            </dl>
            <p className="text-sm text-muted-foreground">{c("draftOnly")}</p>
          </>
        )}
        {save.isError && (
          <p role="alert" className="text-sm text-destructive">
            {save.error.message || c("error")}
          </p>
        )}
        <footer className="flex justify-between gap-3 border-t pt-5">
          <Button variant="ghost" disabled={step === 0 || save.isPending} onClick={() => setStep((s) => s - 1)}>
            {c("back")}
          </Button>
          {step < 2 ? (
            <Button
              disabled={step === 0 ? !contentValid : draft.accountIds.length === 0}
              onClick={() => setStep((s) => s + 1)}
            >
              {c("next")}
            </Button>
          ) : (
            <Button disabled={!communicationDraftValid(draft) || save.isPending} onClick={() => save.mutate()}>
              {c("save")}
            </Button>
          )}
        </footer>
      </section>
      <Dialog
        open={blocker.state === "blocked"}
        onOpenChange={(open) => {
          if (!open && blocker.state === "blocked") blocker.reset();
        }}
      >
        <DialogContent closeLabel={c("dismiss")}>
          <DialogHeader>
            <DialogTitle>{c("discard")}</DialogTitle>
            <DialogDescription>{c("unsaved")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => blocker.state === "blocked" && blocker.reset()}>
              {c("dismiss")}
            </Button>
            <Button onClick={() => blocker.state === "blocked" && blocker.proceed()}>{c("leave")}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
