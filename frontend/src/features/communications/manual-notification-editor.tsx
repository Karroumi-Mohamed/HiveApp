import { PlusIcon } from "@phosphor-icons/react";
import type { AuthoredNotification, NotificationContent, NotificationLanguage } from "@/api/communication-api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useCommunicationCopy } from "./communication-copy";
import { changeOriginalLanguage } from "./manual-notification-rules";

const languageName = { fr: "Français", ar: "العربية" };
export function ManualNotificationEditor({
  value,
  onChange,
  prefix,
}: {
  value: AuthoredNotification;
  onChange: (value: AuthoredNotification) => void;
  prefix: string;
}) {
  const c = useCommunicationCopy(),
    original = value.originalLanguage ?? "fr",
    other = original === "fr" ? "ar" : "fr",
    alternative = value.translations?.[other];
  const fields = (
    content: NotificationContent,
    update: (next: NotificationContent) => void,
    language: NotificationLanguage,
    translated = false,
  ) => (
    <div className="space-y-4" lang={language}>
      <div className="space-y-2">
        <Label htmlFor={translated ? `${prefix}-${language}-title` : `${prefix}-title`}>
          {c("subject")}
          {translated ? ` · ${languageName[language]}` : ""}
        </Label>
        <Input
          dir="auto"
          id={translated ? `${prefix}-${language}-title` : `${prefix}-title`}
          maxLength={160}
          value={content.messageTitle}
          onChange={(e) => update({ ...content, messageTitle: e.target.value })}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor={translated ? `${prefix}-${language}-body` : `${prefix}-body`}>
          {c("body")}
          {translated ? ` · ${languageName[language]}` : ""}
        </Label>
        <Textarea
          dir="auto"
          id={translated ? `${prefix}-${language}-body` : `${prefix}-body`}
          rows={5}
          maxLength={10000}
          value={content.messageBody}
          onChange={(e) => update({ ...content, messageBody: e.target.value })}
        />
      </div>
    </div>
  );
  return (
    <div className="space-y-5">
      <div className="space-y-2">
        <Label htmlFor={`${prefix}-language`}>{c("originalLanguage")}</Label>
        <Select
          value={original}
          onValueChange={(v) => onChange(changeOriginalLanguage(value, v as NotificationLanguage))}
        >
          <SelectTrigger id={`${prefix}-language`} className="w-full sm:w-56">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="fr">Français</SelectItem>
            <SelectItem value="ar">العربية</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {fields(value, (content) => onChange({ ...value, ...content }), original)}
      {alternative ? (
        <section className="space-y-4 rounded-lg border bg-muted/20 p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="font-semibold">
              {languageName[other]} · {c("translation")}
            </h3>
            <Button variant="ghost" size="sm" onClick={() => onChange({ ...value, translations: {} })}>
              {c("removeLanguage")}
            </Button>
          </div>
          {fields(alternative, (content) => onChange({ ...value, translations: { [other]: content } }), other, true)}
          {(!alternative.messageTitle.trim() || !alternative.messageBody.trim()) && (
            <p className="text-sm text-muted-foreground">{c("incompleteTranslation")}</p>
          )}
        </section>
      ) : (
        <Button
          variant="outline"
          onClick={() => onChange({ ...value, translations: { [other]: { messageTitle: "", messageBody: "" } } })}
        >
          <PlusIcon />
          {c("addLanguage")} · {languageName[other]}
        </Button>
      )}
      <p className="max-w-prose text-sm text-muted-foreground">{c("translationHint")}</p>
    </div>
  );
}
export function ManualNotificationPreview({ value }: { value: AuthoredNotification }) {
  const c = useCommunicationCopy();
  const versions = [
    { language: value.originalLanguage, content: value, original: true },
    ...Object.entries(value.translations ?? {}).map(([language, content]) => ({
      language: language as NotificationLanguage,
      content,
      original: false,
    })),
  ];
  return (
    <div className="space-y-4">
      {versions.map(
        ({ language, content, original }) =>
          content && (
            <section
              key={language ?? "original"}
              lang={language ?? undefined}
              className="space-y-2 rounded-lg border p-4"
            >
              <p className="text-xs font-medium text-muted-foreground">
                {c(original ? "original" : "translation")}
                {language ? ` · ${languageName[language]}` : ""}
              </p>
              <h3 dir="auto" className="break-words font-semibold">
                {content.messageTitle}
              </h3>
              <p dir="auto" className="whitespace-pre-wrap break-words leading-relaxed">
                {content.messageBody}
              </p>
            </section>
          ),
      )}
    </div>
  );
}
