import type { Icon } from "@phosphor-icons/react";
import { HexagonIcon, ListIcon, MoonIcon, SignOutIcon, SunIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router";
import { getLanguageDirection, normalizeLanguage } from "@/app/i18n";
import { useTheme } from "@/app/theme-provider";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

export type ProductNavigationItem = { label: string; to: string; icon: Icon; end?: boolean; visible?: boolean };
export type ProductNavigationGroup = { label?: string; items: ProductNavigationItem[] };

function Brand() {
  return (
    <div className="flex items-center gap-3">
      <span className="relative grid size-9 place-items-center text-sidebar-primary">
        <HexagonIcon aria-hidden="true" className="absolute size-9" weight="fill" />
        <span className="relative text-xs font-black tracking-[-0.06em] text-sidebar-primary-foreground">H</span>
      </span>
      <span className="text-[15px] font-bold tracking-tight text-sidebar-accent-foreground">HiveApp</span>
    </div>
  );
}

function Navigation({
  groups,
  label,
  onNavigate,
}: {
  groups: ProductNavigationGroup[];
  label: string;
  onNavigate?: () => void;
}) {
  return (
    // min-h-0 + overflow: the nav scrolls inside the pinned sidebar. Letting the column grow
    // instead pushed content past the painted background, which only covered one viewport.
    <nav aria-label={label} className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto px-3 py-5">
      {groups.map((group, index) => {
        const items = group.items.filter((item) => item.visible !== false);
        if (items.length === 0) return null;
        return (
          <div className="space-y-1" key={group.label ?? index}>
            {group.label ? (
              <p className="px-3 pb-1 text-[10px] font-semibold tracking-[0.13em] text-sidebar-foreground/45 uppercase">
                {group.label}
              </p>
            ) : null}
            {items.map((item) => {
              const ItemIcon = item.icon;
              return (
                <NavLink
                  className={({ isActive }) =>
                    cn(
                      "flex min-h-10 items-center gap-3 rounded-lg px-3 text-sm font-medium text-sidebar-foreground/72 transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring",
                      isActive && "bg-sidebar-foreground/[0.08] font-semibold text-sidebar-accent-foreground",
                    )
                  }
                  end={item.end}
                  key={item.to}
                  onClick={onNavigate}
                  to={item.to}
                >
                  {({ isActive }) => (
                    <>
                      <ItemIcon
                        aria-hidden="true"
                        className="size-[18px] shrink-0"
                        weight={isActive ? "fill" : "regular"}
                      />
                      <span>{item.label}</span>
                    </>
                  )}
                </NavLink>
              );
            })}
          </div>
        );
      })}
    </nav>
  );
}

function Sidebar({
  groups,
  label,
  email,
  onLogout,
  onNavigate,
}: {
  groups: ProductNavigationGroup[];
  label: string;
  email: string;
  onLogout: () => void;
  onNavigate?: () => void;
}) {
  const initials = email.slice(0, 2).toUpperCase();
  return (
    <div className="flex h-full flex-col bg-sidebar text-sidebar-foreground">
      <div className="flex h-[68px] items-center px-5">
        <Brand />
      </div>
      <Separator className="bg-sidebar-border" />
      <Navigation groups={groups} label={label} onNavigate={onNavigate} />
      <div className="border-t border-sidebar-border p-3">
        <div className="flex w-full items-center gap-3 p-2 text-start">
          <Avatar className="size-9 border border-sidebar-border">
            <AvatarFallback className="bg-sidebar-accent text-xs font-semibold text-sidebar-accent-foreground">
              {initials}
            </AvatarFallback>
          </Avatar>
          <span className="min-w-0 flex-1">
            <span className="block text-[10px] text-sidebar-foreground/48">Session active</span>
            <span className="block truncate text-xs font-semibold text-sidebar-accent-foreground">{email}</span>
          </span>
          <Button
            aria-label="Se déconnecter"
            className="text-sidebar-foreground/60 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
            onClick={onLogout}
            size="icon-sm"
            variant="ghost"
          >
            <SignOutIcon />
          </Button>
        </div>
      </div>
    </div>
  );
}

export function ProductShell({
  groups,
  label,
  email,
  context,
  onLogout,
  children,
}: {
  groups: ProductNavigationGroup[];
  label: string;
  email: string;
  context?: ReactNode;
  onLogout: () => void;
  children: ReactNode;
}) {
  const { t, i18n } = useTranslation();
  const { theme, toggleTheme } = useTheme();
  const direction = getLanguageDirection(i18n.resolvedLanguage);
  const language = normalizeLanguage(i18n.resolvedLanguage);
  const toggleLanguage = () => void i18n.changeLanguage(language === "fr" ? "ar" : "fr");
  return (
    <div className="min-h-dvh bg-background lg:grid lg:grid-cols-[var(--shell-sidebar-width)_minmax(0,1fr)]">
      <aside className="sticky top-0 hidden h-dvh border-e border-sidebar-border lg:block">
        <Sidebar email={email} groups={groups} label={label} onLogout={onLogout} />
      </aside>
      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex min-h-[68px] items-center justify-between border-b bg-background/94 px-4 backdrop-blur-md md:px-6 lg:px-8">
          <div className="flex min-w-0 items-center gap-3">
            <Sheet>
              <SheetTrigger asChild>
                <Button aria-label={t("common.openNavigation")} className="lg:hidden" size="icon" variant="outline">
                  <ListIcon />
                </Button>
              </SheetTrigger>
              <SheetContent
                className="w-[18rem] border-none p-0"
                closeLabel={t("common.close")}
                side={direction === "rtl" ? "right" : "left"}
              >
                <SheetHeader className="sr-only">
                  <SheetTitle>{label}</SheetTitle>
                  <SheetDescription>{t("common.openNavigation")}</SheetDescription>
                </SheetHeader>
                <Sidebar email={email} groups={groups} label={label} onLogout={onLogout} />
              </SheetContent>
            </Sheet>
            <div className="hidden lg:block">
              <p className="truncate text-sm font-semibold">{label}</p>
            </div>
            {context}
          </div>
          <div className="flex items-center gap-1">
            <Button className="text-xs font-semibold" onClick={toggleLanguage} size="sm" variant="ghost">
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
                  {theme === "light" ? <MoonIcon /> : <SunIcon />}
                </Button>
              </TooltipTrigger>
              <TooltipContent>{theme === "light" ? t("common.darkTheme") : t("common.lightTheme")}</TooltipContent>
            </Tooltip>
          </div>
        </header>
        {/* Full-height column so a page can hand its table the leftover space instead of
            guessing at a viewport calculation. Scrolling moves inside here, which keeps the
            sticky header where it already was. 68px is the header's declared min-height. */}
        <main className="px-4 py-6 md:px-6 md:py-8 lg:px-8" id="main-content">
          <div className="mx-auto max-w-[1540px]">{children}</div>
        </main>
      </div>
    </div>
  );
}
