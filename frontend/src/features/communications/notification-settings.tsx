import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { communicationApi, type NotificationSetting } from "@/api/communication-api";
import { ErrorState, LoadingState } from "@/components/patterns/remote-state";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useCommunicationCopy } from "./communication-copy";
import type { NotificationContext } from "./notification-inbox";

export function NotificationSettings({ context }: { context: NotificationContext }) {
  const c = useCommunicationCopy(),
    cache = useQueryClient();
  const key = [context.platform ? "admin" : "client", "notifications", context.identity, "settings"];
  const query = useQuery({ queryKey: key, queryFn: () => communicationApi.settings(context.platform) });
  const language = useQuery({
    queryKey: [...key, "language"],
    queryFn: () => communicationApi.language(context.platform),
  });
  const saveLanguage = useMutation({
    mutationFn: (value: "fr" | "ar") => communicationApi.setLanguage(value, context.platform),
    onSuccess: () => void cache.invalidateQueries({ queryKey: [...key, "language"] }),
  });
  const save = useMutation({
    mutationFn: (setting: NotificationSetting) => communicationApi.setting(setting, context.platform),
    onSuccess: () => void cache.invalidateQueries({ queryKey: [key[0], "notifications"] }),
  });
  return (
    <section className="rounded-xl border bg-card p-5">
      <h2 className="font-semibold">{c("personalSettings")}</h2>
      <p className="my-3 max-w-prose text-sm text-muted-foreground">{c("requiredHint")}</p>
      <div className="my-4 space-y-2">
        <Label htmlFor="notification-language">{c("emailLanguage")}</Label>
        {language.isError ? (
          <ErrorState retry={() => void language.refetch()} />
        ) : (
          <Select
            value={language.data?.language ?? "fr"}
            disabled={language.isLoading || !context.preferences || saveLanguage.isPending}
            onValueChange={(v) => saveLanguage.mutate(v as "fr" | "ar")}
          >
            <SelectTrigger id="notification-language" className="w-56">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="fr">Français</SelectItem>
              <SelectItem value="ar">العربية</SelectItem>
            </SelectContent>
          </Select>
        )}
        {saveLanguage.isError && (
          <p role="alert" className="text-sm text-destructive">
            {c("error")}
          </p>
        )}
      </div>
      {query.isLoading ? (
        <LoadingState rows={3} />
      ) : query.isError ? (
        <ErrorState retry={() => void query.refetch()} />
      ) : (
        <div className="space-y-2">
          {query.data?.map((setting) => (
            <div
              key={setting.topic}
              className="flex flex-wrap items-center justify-between gap-2 border-t py-2 text-sm"
            >
              <span className="font-medium">{c(setting.topic)}</span>
              <div className="flex flex-wrap gap-4">
                {(["inAppEnabled", "emailEnabled"] as const).map((field) => (
                  <label
                    key={field}
                    htmlFor={`setting-${setting.topic}-${field}`}
                    className="flex min-h-11 items-center gap-2"
                  >
                    <Checkbox
                      id={`setting-${setting.topic}-${field}`}
                      checked={setting[field]}
                      disabled={!context.preferences || save.isPending}
                      onCheckedChange={(v) => save.mutate({ ...setting, [field]: v === true })}
                      aria-label={`${c(setting.topic)} · ${c(field === "inAppEnabled" ? "inbox" : "delivery")}`}
                    />
                    {c(field === "inAppEnabled" ? "inbox" : "delivery")}
                  </label>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
      {save.isError && (
        <p role="alert" className="text-sm text-destructive">
          {c("error")}
        </p>
      )}
    </section>
  );
}
