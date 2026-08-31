import type { Icon } from "@phosphor-icons/react";
import {
  BellIcon,
  CaretDownIcon,
  CreditCardIcon,
  CubeIcon,
  GearSixIcon,
  HexagonIcon,
  ListIcon,
  MoonIcon,
  ShieldCheckIcon,
  SquaresFourIcon,
  SunIcon,
  UsersThreeIcon,
} from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { getLanguageDirection, normalizeLanguage } from "@/app/i18n";
import { useTheme } from "@/app/theme-provider";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

type NavigationItem = {
  label: string;
  icon: Icon;
  active?: boolean;
};

type NavigationGroup = {
  label?: string;
  items: NavigationItem[];
};

function BrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <div className="flex items-center gap-3">
      <span className="relative grid size-9 place-items-center text-sidebar-primary">
        <HexagonIcon aria-hidden="true" className="absolute size-9" weight="fill" />
        <span className="relative text-xs font-black tracking-[-0.06em] text-sidebar-primary-foreground">H</span>
      </span>
      {!compact ? (
        <span className="text-[15px] font-bold tracking-tight text-sidebar-accent-foreground">HiveApp</span>
      ) : null}
    </div>
  );
}

function SidebarNavigation({ onNavigate }: { onNavigate?: () => void }) {
  const { t } = useTranslation();
  const groups: NavigationGroup[] = [
    {
      items: [{ label: t("shell.overview"), icon: SquaresFourIcon, active: true }],
    },
    {
      label: t("shell.access"),
      items: [
        { label: t("shell.operators"), icon: UsersThreeIcon },
        { label: t("shell.roles"), icon: ShieldCheckIcon },
      ],
    },
    {
      label: t("shell.commercial"),
      items: [
        { label: t("shell.plans"), icon: CubeIcon },
        { label: t("shell.subscriptions"), icon: CreditCardIcon },
      ],
    },
    {
      label: t("shell.platform"),
      items: [
        { label: t("shell.features"), icon: HexagonIcon },
        { label: t("shell.settings"), icon: GearSixIcon },
      ],
    },
  ];

  return (
    <nav aria-label={t("shell.platformAdmin")} className="flex flex-1 flex-col gap-5 px-3 py-5">
      {groups.map((group, groupIndex) => (
        <div className="space-y-1" key={group.label ?? groupIndex}>
          {group.label ? (
            <p className="px-3 pb-1 text-[10px] font-semibold tracking-[0.13em] text-sidebar-foreground/45 uppercase">
              {group.label}
            </p>
          ) : null}
          {group.items.map((item) => {
            const ItemIcon = item.icon;
            return (
              <button
                className={cn(
                  "group relative flex h-10 w-full items-center gap-3 rounded-lg px-3 text-sm font-medium text-sidebar-foreground/72 transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:ring-2 focus-visible:ring-sidebar-ring focus-visible:outline-none",
                  item.active && "bg-sidebar-foreground/[0.08] font-semibold text-sidebar-accent-foreground",
                )}
                key={item.label}
                onClick={onNavigate}
                type="button"
              >
                <ItemIcon
                  aria-hidden="true"
                  className="size-[18px] shrink-0"
                  weight={item.active ? "fill" : "regular"}
                />
                <span>{item.label}</span>
              </button>
            );
          })}
        </div>
      ))}
    </nav>
  );
}

function Sidebar({ mobile = false, onNavigate }: { mobile?: boolean; onNavigate?: () => void }) {
  const { t } = useTranslation();

  return (
    <div
      className={cn(
        "flex h-full flex-col bg-sidebar text-sidebar-foreground",
        !mobile && "border-e border-sidebar-border",
      )}
    >
      <div className="flex h-[72px] items-center px-5">
        <BrandMark />
      </div>
      <Separator className="bg-sidebar-border" />
      <SidebarNavigation onNavigate={onNavigate} />
      <div className="border-t border-sidebar-border p-3">
        <button
          className="flex w-full items-center gap-3 rounded-lg p-2 text-start transition-colors hover:bg-sidebar-accent focus-visible:ring-2 focus-visible:ring-sidebar-ring focus-visible:outline-none"
          type="button"
        >
          <Avatar className="size-9 border border-sidebar-border">
            <AvatarFallback className="bg-sidebar-accent text-xs font-semibold text-sidebar-accent-foreground">
              HM
            </AvatarFallback>
          </Avatar>
          <span className="min-w-0 flex-1">
            <span className="block text-[10px] text-sidebar-foreground/48">{t("shell.signedInAs")}</span>
            <span className="block truncate text-xs font-semibold text-sidebar-accent-foreground">
              admin@hiveapp.ma
            </span>
          </span>
          <CaretDownIcon aria-hidden="true" className="size-4 text-sidebar-foreground/45" />
        </button>
      </div>
    </div>
  );
}

function ShellActions() {
  const { t, i18n } = useTranslation();
  const { theme, toggleTheme } = useTheme();
  const language = normalizeLanguage(i18n.resolvedLanguage);

  const toggleLanguage = () => {
    void i18n.changeLanguage(language === "fr" ? "ar" : "fr");
  };

  return (
    <div className="flex items-center gap-1">
      <Button
        className="hidden text-xs font-semibold sm:inline-flex"
        onClick={toggleLanguage}
        size="sm"
        variant="ghost"
      >
        {language === "fr" ? t("common.arabic") : t("common.french")}
      </Button>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            aria-label={theme === "light" ? t("common.darkTheme") : t("common.lightTheme")}
            onClick={toggleTheme}
            size="icon-sm"
            variant="ghost"
          >
            {theme === "light" ? <MoonIcon aria-hidden="true" /> : <SunIcon aria-hidden="true" />}
          </Button>
        </TooltipTrigger>
        <TooltipContent>{theme === "light" ? t("common.darkTheme") : t("common.lightTheme")}</TooltipContent>
      </Tooltip>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button aria-label={t("common.notifications")} className="relative" size="icon-sm" variant="ghost">
            <BellIcon aria-hidden="true" />
            <span className="absolute end-2 top-2 size-1.5 rounded-full bg-destructive ring-2 ring-background" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>{t("common.notifications")}</TooltipContent>
      </Tooltip>
    </div>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const { t, i18n } = useTranslation();
  const direction = getLanguageDirection(i18n.resolvedLanguage);

  return (
    <div className="min-h-dvh bg-background lg:grid lg:grid-cols-[var(--shell-sidebar-width)_minmax(0,1fr)]">
      <aside className="sticky top-0 hidden h-dvh lg:block">
        <Sidebar />
      </aside>

      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex h-[72px] items-center justify-between border-b bg-background/92 px-4 backdrop-blur-md md:px-6 lg:px-8">
          <div className="flex min-w-0 items-center gap-3">
            <Sheet>
              <SheetTrigger asChild>
                <Button aria-label={t("common.openNavigation")} className="lg:hidden" size="icon" variant="outline">
                  <ListIcon aria-hidden="true" />
                </Button>
              </SheetTrigger>
              <SheetContent
                className="w-[18rem] border-none p-0"
                closeLabel={t("common.close")}
                side={direction === "rtl" ? "right" : "left"}
              >
                <SheetHeader className="sr-only">
                  <SheetTitle>{t("shell.platformAdmin")}</SheetTitle>
                  <SheetDescription>{t("common.openNavigation")}</SheetDescription>
                </SheetHeader>
                <Sidebar mobile />
              </SheetContent>
            </Sheet>
            <div className="lg:hidden">
              <BrandMark compact />
            </div>
            <div className="hidden min-w-0 sm:block">
              <p className="truncate text-sm font-semibold">{t("shell.platformAdmin")}</p>
            </div>
          </div>
          <ShellActions />
        </header>

        <main className="px-4 py-6 md:px-6 md:py-8 lg:px-8" id="main-content">
          <div className="mx-auto max-w-[1540px]">{children}</div>
        </main>

        <span className="sr-only" aria-live="polite">
          {direction === "rtl" ? t("common.arabic") : t("common.french")}
        </span>
      </div>
    </div>
  );
}
