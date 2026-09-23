import { BellIcon, PaintBrushIcon, UserCircleIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router";
import { normalizeLanguage } from "@/app/i18n";
import { useTheme } from "@/app/theme-provider";
import { clientPermissions } from "@/auth/permissions";
import { useClientSession } from "@/auth/session-provider";
import { PageHeader } from "@/components/patterns/page-header";
import { PermissionState } from "@/components/patterns/remote-state";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { AdminMePage } from "@/features/admin/me/admin-me-page";
import { ClientMePage } from "@/features/client/me/client-me-page";
import { MarketingPreferences } from "./client-communications-page";
import { useCommunicationCopy } from "./communication-copy";
import { useClientNotificationContext, useOperatorNotificationContext } from "./notification-context";
import type { NotificationContext } from "./notification-inbox";
import { NotificationSettings } from "./notification-settings";

function AppearanceSettings() {
  const c = useCommunicationCopy(),
    { i18n } = useTranslation(),
    { theme, setTheme } = useTheme();
  return (
    <section className="space-y-6 rounded-xl border bg-card p-5 sm:p-6">
      <h2 className="font-semibold">{c("appearance")}</h2>
      <div className="space-y-2">
        <Label htmlFor="interface-language">{c("interfaceLanguage")}</Label>
        <Select value={normalizeLanguage(i18n.language)} onValueChange={(v) => void i18n.changeLanguage(v)}>
          <SelectTrigger id="interface-language" className="w-full sm:w-64">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="fr">Français</SelectItem>
            <SelectItem value="ar">العربية</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div className="flex gap-2">
        {(["light", "dark"] as const).map((value) => (
          <Button
            key={value}
            variant={theme === value ? "secondary" : "outline"}
            aria-pressed={theme === value}
            onClick={() => setTheme(value)}
          >
            {c(value)}
          </Button>
        ))}
      </div>
      <p className="text-sm text-muted-foreground">{c("devicePreferences")}</p>
    </section>
  );
}
export function PersonalSettings({ context, profile }: { context: NotificationContext; profile: ReactNode }) {
  const c = useCommunicationCopy(),
    [params, setParams] = useSearchParams();
  const sections = [
    { key: "profile", label: "profileAccess", icon: UserCircleIcon },
    { key: "appearance", label: "appearance", icon: PaintBrushIcon },
    { key: "notifications", label: "notifications", icon: BellIcon },
  ] as const;
  const selected = sections.find((s) => s.key === params.get("section"))?.key ?? "profile";
  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader title={c("profileSettings")} />
      <div className="grid items-start gap-6 md:grid-cols-[220px_minmax(0,1fr)]">
        <nav aria-label={c("profileSettings")} className="flex flex-wrap gap-1 md:flex-col">
          {sections.map(({ key, label, icon: Icon }) => (
            <Button
              key={key}
              variant={selected === key ? "secondary" : "ghost"}
              className="justify-start"
              aria-current={selected === key ? "page" : undefined}
              onClick={() => setParams({ section: key })}
            >
              <Icon />
              {c(label)}
            </Button>
          ))}
        </nav>
        <div className="min-w-0">
          {selected === "profile" ? (
            profile
          ) : selected === "appearance" ? (
            <AppearanceSettings />
          ) : context.allowed ? (
            <NotificationSettings context={context} />
          ) : (
            <PermissionState />
          )}
        </div>
      </div>
    </div>
  );
}
export function OperatorSettingsPage() {
  return <PersonalSettings context={useOperatorNotificationContext()} profile={<AdminMePage embedded />} />;
}
export function ClientSettingsPage() {
  return <PersonalSettings context={useClientNotificationContext()} profile={<ClientMePage embedded />} />;
}
export function AccountSettingsPage() {
  const session = useClientSession(),
    c = useCommunicationCopy();
  if (session.isB2B || !session.permissions?.isOwner) return <PermissionState />;
  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <PageHeader title={c("accountSettings")} description={c("accountSettingsHint")} />
      {session.can(clientPermissions.communicationsRead) && <MarketingPreferences />}
      <ClientMePage embedded accountSettings />
    </div>
  );
}
