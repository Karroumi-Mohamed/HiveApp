import type { Icon } from "@phosphor-icons/react";
import {
  CaretDownIcon,
  HexagonIcon,
  ListIcon,
  MoonIcon,
  SidebarSimpleIcon,
  SignOutIcon,
  SunIcon,
} from "@phosphor-icons/react";
import { type ReactNode, useEffect, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, useLocation } from "react-router";
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

const SIDEBAR_COLLAPSED_KEY = "hiveapp_sidebar_collapsed";

function Brand({
  collapsed,
  collapseLabel,
  direction,
  expandLabel,
  onToggleCollapse,
}: {
  collapsed?: boolean;
  collapseLabel: string;
  direction: "ltr" | "rtl";
  expandLabel: string;
  onToggleCollapse?: () => void;
}) {
  const mark = (
    <span className="relative grid size-9 shrink-0 place-items-center text-primary">
      <HexagonIcon aria-hidden="true" className="absolute size-9" weight="fill" />
      <span className="relative text-xs font-black tracking-[-0.06em] text-primary-foreground">H</span>
    </span>
  );

  if (collapsed && onToggleCollapse) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            aria-label={expandLabel}
            className="rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring"
            onClick={onToggleCollapse}
            type="button"
          >
            {mark}
          </button>
        </TooltipTrigger>
        <TooltipContent side={direction === "rtl" ? "left" : "right"}>{expandLabel}</TooltipContent>
      </Tooltip>
    );
  }

  return (
    <div className="flex w-full items-center justify-between gap-3">
      <div className="flex min-w-0 items-center gap-3">
        {mark}
        {!collapsed && <span className="truncate text-[15px] font-bold tracking-tight text-foreground">HiveApp</span>}
      </div>
      {!collapsed && onToggleCollapse ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              aria-label={collapseLabel}
              className="text-muted-foreground hover:bg-sidebar-accent hover:text-foreground"
              onClick={onToggleCollapse}
              size="icon-sm"
              variant="ghost"
            >
              <SidebarSimpleIcon className="size-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>{collapseLabel}</TooltipContent>
        </Tooltip>
      ) : null}
    </div>
  );
}

function NavItem({
  item,
  collapsed,
  direction,
  onNavigate,
}: {
  item: ProductNavigationItem;
  collapsed?: boolean;
  direction: "ltr" | "rtl";
  onNavigate?: () => void;
}) {
  const ItemIcon = item.icon;
  const link = (
    <NavLink
      aria-label={collapsed ? item.label : undefined}
      className={({ isActive }) =>
        cn(
          "group flex min-h-9 items-center gap-3 rounded-lg text-sm font-medium text-sidebar-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring",
          collapsed ? "size-9 justify-center p-0" : "px-0 pe-3",
          isActive && "font-semibold text-sidebar-accent-foreground",
        )
      }
      end={item.end}
      onClick={onNavigate}
      to={item.to}
    >
      {({ isActive }) => (
        <>
          <span className="grid size-9 shrink-0 place-items-center">
            <ItemIcon
              aria-hidden="true"
              className={cn(
                "size-[18px] shrink-0 transition-colors",
                isActive ? "text-primary" : "text-muted-foreground group-hover:text-foreground",
              )}
              weight={isActive ? "fill" : "regular"}
            />
          </span>
          {!collapsed && <span className="truncate">{item.label}</span>}
        </>
      )}
    </NavLink>
  );

  if (collapsed) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>{link}</TooltipTrigger>
        <TooltipContent side={direction === "rtl" ? "left" : "right"}>{item.label}</TooltipContent>
      </Tooltip>
    );
  }

  return link;
}

function NavigationGroupSection({
  group,
  collapsed,
  direction,
  onNavigate,
}: {
  group: ProductNavigationGroup;
  collapsed?: boolean;
  direction: "ltr" | "rtl";
  onNavigate?: () => void;
}) {
  const location = useLocation();
  const itemsId = useId();
  const hasActiveChild = group.items.some((item) =>
    item.end ? location.pathname === item.to : location.pathname.startsWith(item.to),
  );
  const [isOpen, setIsOpen] = useState(true);

  useEffect(() => {
    if (hasActiveChild) {
      setIsOpen(true);
    }
  }, [hasActiveChild]);

  const items = group.items.filter((item) => item.visible !== false);
  if (items.length === 0) return null;

  if (collapsed) {
    return (
      <div className="flex flex-col items-center gap-1">
        {group.label ? <Separator className="my-1.5 w-6 bg-sidebar-border" /> : null}
        {items.map((item) => (
          <NavItem collapsed={collapsed} direction={direction} item={item} key={item.to} onNavigate={onNavigate} />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-1">
      {group.label ? (
        <button
          aria-controls={itemsId}
          aria-expanded={isOpen}
          className="flex w-full items-center justify-between rounded-md px-2.5 py-1 text-start text-xs font-semibold text-muted-foreground/85 transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-sidebar-ring"
          onClick={() => setIsOpen((prev) => !prev)}
          type="button"
        >
          <span>{group.label}</span>
          <CaretDownIcon
            className={cn(
              "size-3 text-muted-foreground/60 transition-transform duration-200",
              !isOpen && "-rotate-90 rtl:rotate-90",
            )}
          />
        </button>
      ) : null}
      {isOpen ? (
        <div className="space-y-0.5" id={itemsId}>
          {items.map((item) => (
            <NavItem collapsed={collapsed} direction={direction} item={item} key={item.to} onNavigate={onNavigate} />
          ))}
        </div>
      ) : null}
    </div>
  );
}

function Navigation({
  groups,
  label,
  collapsed,
  direction,
  onNavigate,
}: {
  groups: ProductNavigationGroup[];
  label: string;
  collapsed?: boolean;
  direction: "ltr" | "rtl";
  onNavigate?: () => void;
}) {
  return (
    <nav
      aria-label={label}
      className={cn(
        "sidebar-nav-scroll flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto py-4",
        collapsed ? "sidebar-nav-scroll--compact items-center px-2" : "px-3.5",
      )}
    >
      {groups.map((group, index) => (
        <NavigationGroupSection
          collapsed={collapsed}
          direction={direction}
          group={group}
          key={group.label ?? index}
          onNavigate={onNavigate}
        />
      ))}
    </nav>
  );
}

function Sidebar({
  groups,
  label,
  email,
  collapsed,
  direction,
  onLogout,
  onToggleCollapse,
  onNavigate,
}: {
  groups: ProductNavigationGroup[];
  label: string;
  email: string;
  collapsed?: boolean;
  direction: "ltr" | "rtl";
  onLogout: () => void;
  onToggleCollapse?: () => void;
  onNavigate?: () => void;
}) {
  const { t } = useTranslation();
  const initials = email.slice(0, 2).toUpperCase();
  return (
    <div className="flex h-full flex-col bg-sidebar text-sidebar-foreground">
      <div
        className={cn(
          "flex h-[68px] items-center border-b border-sidebar-border",
          collapsed ? "justify-center px-2" : "px-3.5",
        )}
      >
        <Brand
          collapsed={collapsed}
          collapseLabel={t("common.collapseNavigation")}
          direction={direction}
          expandLabel={t("common.expandNavigation")}
          onToggleCollapse={onToggleCollapse}
        />
      </div>
      <Navigation collapsed={collapsed} direction={direction} groups={groups} label={label} onNavigate={onNavigate} />
      <div className="border-t border-sidebar-border p-2.5">
        {collapsed ? (
          <div className="flex flex-col items-center gap-2">
            <Tooltip>
              <TooltipTrigger asChild>
                <Avatar className="size-9 border border-sidebar-border">
                  <AvatarFallback className="bg-primary/10 text-xs font-semibold text-primary">
                    {initials}
                  </AvatarFallback>
                </Avatar>
              </TooltipTrigger>
              <TooltipContent side={direction === "rtl" ? "left" : "right"}>
                <div className="text-xs">
                  <p className="font-semibold">{email}</p>
                  <p className="text-[10px] text-muted-foreground">{t("common.activeSession")}</p>
                </div>
              </TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  aria-label={t("common.signOut")}
                  className="text-muted-foreground hover:bg-sidebar-accent hover:text-foreground"
                  onClick={onLogout}
                  size="icon-sm"
                  variant="ghost"
                >
                  <SignOutIcon />
                </Button>
              </TooltipTrigger>
              <TooltipContent side={direction === "rtl" ? "left" : "right"}>{t("common.signOut")}</TooltipContent>
            </Tooltip>
          </div>
        ) : (
          <div className="flex w-full items-center gap-3 rounded-lg border border-sidebar-border/60 bg-background/50 p-2 text-start">
            <Avatar className="size-8 border border-sidebar-border">
              <AvatarFallback className="bg-primary/10 text-xs font-semibold text-primary">{initials}</AvatarFallback>
            </Avatar>
            <span className="min-w-0 flex-1">
              <span className="block text-[10px] text-muted-foreground">{t("common.activeSession")}</span>
              <span className="block truncate text-xs font-semibold text-foreground">{email}</span>
            </span>
            <Button
              aria-label={t("common.signOut")}
              className="text-muted-foreground hover:bg-sidebar-accent hover:text-foreground"
              onClick={onLogout}
              size="icon-sm"
              variant="ghost"
            >
              <SignOutIcon />
            </Button>
          </div>
        )}
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

  const [isCollapsed, setIsCollapsed] = useState(() => {
    try {
      return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "true";
    } catch {
      return false;
    }
  });

  const toggleCollapse = () => {
    setIsCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(next));
      } catch {}
      return next;
    });
  };

  return (
    <div
      className={cn(
        "min-h-dvh bg-background lg:grid transition-[grid-template-columns] duration-200",
        isCollapsed
          ? "lg:grid-cols-[4.25rem_minmax(0,1fr)]"
          : "lg:grid-cols-[var(--shell-sidebar-width)_minmax(0,1fr)]",
      )}
    >
      <aside className="sticky top-0 hidden h-dvh border-e border-sidebar-border lg:block">
        <Sidebar
          collapsed={isCollapsed}
          direction={direction}
          email={email}
          groups={groups}
          label={label}
          onLogout={onLogout}
          onToggleCollapse={toggleCollapse}
        />
      </aside>
      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex h-[68px] items-center justify-between border-b bg-background/94 px-4 backdrop-blur-md md:px-6 lg:px-8">
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
                <Sidebar
                  collapsed={false}
                  direction={direction}
                  email={email}
                  groups={groups}
                  label={label}
                  onLogout={onLogout}
                />
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
