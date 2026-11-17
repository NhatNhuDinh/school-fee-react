"use client";

import React from "react";
import {
  useMenu,
  useLink,
  useActiveAuthProvider,
  useGetIdentity,
  useLogout,
  usePermissions,
  type TreeMenuItem,
} from "@refinedev/core";
import { useKBar } from "@refinedev/kbar";
import {
  ChevronRight,
  ListIcon,
  LogOutIcon,
  PanelLeftIcon,
  SearchIcon,
  UserRoundIcon,
} from "lucide-react";
import { Link } from "react-router";
import { BrandMark, BrandWordmark } from "@/shared/components/layout/brand-logo";
import { ThemeCustomizer } from "@/shared/components/theme/theme-customizer";
import { ThemeToggle } from "@/shared/components/theme/theme-toggle";
import { permissionFor } from "@/shared/lib/permissions";

import {
  Sidebar as ShadcnSidebar,
  SidebarContent as ShadcnSidebarContent,
  SidebarFooter as ShadcnSidebarFooter,
  SidebarHeader as ShadcnSidebarHeader,
  SidebarRail as ShadcnSidebarRail,
  useSidebar as useShadcnSidebar,
} from "@/shared/ui/sidebar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/shared/ui/dropdown-menu";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/shared/ui/collapsible";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/shared/ui/tooltip";
import { Button } from "@/shared/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/shared/ui/avatar";
import { cn } from "@/shared/lib/utils";

type Identity = {
  fullName?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  avatar?: string;
};

/* --------------------------------------------------------------------------
   Shared class recipes. Every nav row — link, collapsible trigger, dropdown
   trigger — goes through `navRowClass` so the three stay visually identical.
   -------------------------------------------------------------------------- */

function navRowClass({
  isSelected,
  isCollapsed,
  isChild,
}: {
  isSelected?: boolean;
  isCollapsed?: boolean;
  /** A row inside an opened group: the same row, indented a step so it reads as belonging to its parent. */
  isChild?: boolean;
}) {
  return cn(
    "group/nav focus-ring relative flex w-full items-center rounded-lg",
    "transition-colors duration-(--dur-faster)",
    isCollapsed
      ? "h-10 justify-center px-0 text-sm"
      : isChild
        ? "h-9 gap-2.5 ps-[30px] pe-2.5 text-sm"
        : "h-9 gap-2.5 px-2.5 text-sm",
    isSelected
      ? // The selected row is a brand wash with brand-coloured, semibold type. No bar and no gold: the fill alone says
        // where you are, which is what lets it read the same on a light rail and a dark one.
        "bg-sidebar-selected text-sidebar-selected-foreground font-semibold [&_svg]:text-sidebar-selected-foreground"
      : "text-sidebar-foreground/85 hover:bg-sidebar-accent hover:text-sidebar-foreground"
  );
}

function getDisplayName(item: TreeMenuItem) {
  return item.meta?.label ?? item.label ?? item.name;
}

function getIcon(item: TreeMenuItem) {
  return item.meta?.icon ?? item.icon ?? <ListIcon />;
}

/* --------------------------------------------------------------------------
   Access control. `useMenu` does not apply it — it only drops items marked
   `meta.hide` or with neither a list route nor children — so the filtering
   happens here, against the same permission codes the access control provider
   answers `can` from.
   -------------------------------------------------------------------------- */

/**
 * The menu tree with everything this account cannot reach removed.
 *
 * A leaf survives if its list permission is held. A section header survives if
 * anything under it did, which is what makes the whole Administration group
 * disappear for an account with no admin permissions rather than leaving an
 * empty heading behind.
 *
 * With no access control provider configured, `permissions` is undefined and
 * nothing is filtered — the shell stays usable in an app that has not opted
 * into any of this.
 */
function useAccessibleMenuItems(items: TreeMenuItem[]): TreeMenuItem[] {
  const { data: permissions } = usePermissions<string[]>({});

  return React.useMemo(() => {
    if (!permissions) return items;

    const filter = (nodes: TreeMenuItem[]): TreeMenuItem[] =>
      nodes.flatMap((node) => {
        const children = filter((node.children ?? []) as TreeMenuItem[]);
        if (children.length > 0) {
          return [{ ...node, children }];
        }
        // A node with no surviving children is only kept if it is a real
        // destination in its own right and this account may open it.
        if (!node.route) return [];

        const required = permissionFor(node.name, "list");
        return !required || permissions.includes(required) ? [node] : [];
      });

    return filter(items);
  }, [items, permissions]);
}

/* --------------------------------------------------------------------------
   Sidebar
   -------------------------------------------------------------------------- */

export function Sidebar() {
  const { open, isMobile } = useShadcnSidebar();
  const { menuItems, selectedKey } = useMenu();
  const visibleItems = useAccessibleMenuItems(menuItems);
  const isCollapsed = !open && !isMobile;

  return (
    <ShadcnSidebar
      collapsible="icon"
      className="border-none"
      data-slot="app-sidebar"
    >
      <ShadcnSidebarRail />
      <SidebarBrand />

      <ShadcnSidebarContent
        className={cn(
          "scrollbar-fade gap-3 overflow-x-hidden border-r border-sidebar-border pb-4 pt-2",
          isCollapsed ? "px-2" : "px-3"
        )}
      >
        {!isCollapsed && <SidebarSearch />}
        {visibleItems.map((item: TreeMenuItem) => (
          <SidebarItem
            key={item.key || item.name}
            item={item}
            selectedKey={selectedKey}
            isCollapsed={isCollapsed}
          />
        ))}
      </ShadcnSidebarContent>

      <SidebarUser isCollapsed={isCollapsed} />
    </ShadcnSidebar>
  );
}

/* --------------------------------------------------------------------------
   Brand block — the mark, the name, and the way to fold the rail. Nothing else: the school the account belongs to
   is in the account menu, not here.
   -------------------------------------------------------------------------- */

/** The small icon buttons in the rail's header, in the rail's own colours rather than the page's. */
const RAIL_BUTTON =
  "size-8 shrink-0 rounded-lg text-sidebar-foreground/60 hover:bg-sidebar-accent hover:text-sidebar-foreground";

function SidebarBrand() {
  const { open, isMobile, toggleSidebar } = useShadcnSidebar();
  const isCollapsed = !open && !isMobile;

  return (
    <ShadcnSidebarHeader
      className={cn(
        "h-16 shrink-0 flex-row items-center gap-1 overflow-hidden border-r border-sidebar-border p-0",
        isCollapsed ? "justify-center px-2" : "ps-4 pe-2"
      )}
    >
      {isCollapsed ? (
        // Collapsed, the mark doubles as the way back out of the icon rail — otherwise only the hairline rail or
        // Ctrl/Cmd+B can expand it.
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              onClick={toggleSidebar}
              aria-label="Mở rộng thanh bên"
              className="rounded-lg transition-opacity hover:opacity-85 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring"
            >
              <BrandMark />
            </button>
          </TooltipTrigger>
          <TooltipContent side="right">Mở rộng thanh bên</TooltipContent>
        </Tooltip>
      ) : (
        <>
          <BrandMark className="me-1.5 size-[34px]" />
          <BrandWordmark className="min-w-0 flex-1 truncate" />
          {/* Mode and theme belong to how you work, not to the page, so they sit with the brand rather than in the
              bar; the account menu is the row at the foot of the rail. */}
          <ThemeToggle className={RAIL_BUTTON} />
          <ThemeCustomizer className={RAIL_BUTTON} />
          <Button
            variant="ghost"
            size="icon"
            onClick={toggleSidebar}
            aria-label="Thu gọn thanh bên"
            className={RAIL_BUTTON}
          >
            <PanelLeftIcon className="size-4" />
          </Button>
        </>
      )}
    </ShadcnSidebarHeader>
  );
}

/**
 * Opens the command palette, the same thing ⌘K does. It lives here rather than in the top bar: finding a screen is
 * what the rail is for, and the bar keeps only what belongs to the session (the year, notifications, the account).
 */
function SidebarSearch() {
  const { query } = useKBar();

  return (
    <button
      type="button"
      onClick={() => query.toggle()}
      className={cn(
        "focus-ring mb-1 flex h-9 w-full shrink-0 items-center gap-2 rounded-full border border-sidebar-border",
        "bg-sidebar-field ps-3 pe-2 text-start text-[13px] text-sidebar-foreground/60",
        "transition-colors hover:border-sidebar-foreground/30"
      )}
    >
      <SearchIcon className="size-4 shrink-0" />
      <span className="flex-1 truncate">Tìm kiếm nhanh…</span>
      <kbd className="pointer-events-none rounded-md border border-sidebar-border px-1.5 py-0.5 font-sans text-[11px] font-medium">
        ⌘K
      </kbd>
    </button>
  );
}

/* --------------------------------------------------------------------------
   Menu items
   -------------------------------------------------------------------------- */

type MenuItemProps = {
  item: TreeMenuItem;
  selectedKey?: string;
  isCollapsed: boolean;
  depth?: number;
};

function SidebarItem(props: MenuItemProps) {
  const { item, isCollapsed } = props;

  // `meta.group: true` marks a section header rather than a navigable resource.
  if (item.meta?.group) {
    return <SidebarGroup {...props} />;
  }

  if (item.children && item.children.length > 0) {
    // No room to expand a subtree in the icon rail — flyout instead.
    return isCollapsed ? (
      <SidebarSubmenuFlyout {...props} />
    ) : (
      <SidebarSubmenu {...props} />
    );
  }

  return <SidebarLink {...props} />;
}

function SidebarGroup({ item, selectedKey, isCollapsed }: MenuItemProps) {
  const children = item.children ?? [];

  // Collapsed: the label has nowhere to go, so a hairline stands in for it.
  if (isCollapsed) {
    return (
      <div className="flex flex-col gap-1 border-t border-sidebar-border pt-3 first:border-t-0 first:pt-0">
        {children.map((child) => (
          <SidebarItem
            key={child.key || child.name}
            item={child}
            selectedKey={selectedKey}
            isCollapsed
          />
        ))}
      </div>
    );
  }

  return (
    <div className="flex flex-col">
      <span className="text-overline px-3 pb-1.5 text-sidebar-foreground/60">
        {getDisplayName(item)}
      </span>
      <div className="flex flex-col gap-0.5">
        {children.map((child) => (
          <SidebarItem
            key={child.key || child.name}
            item={child}
            selectedKey={selectedKey}
            isCollapsed={false}
          />
        ))}
      </div>
    </div>
  );
}

function SidebarSubmenu({ item, selectedKey, depth = 0 }: MenuItemProps) {
  const children = item.children ?? [];
  const hasSelectedChild = children.some(
    (child) => child.key === selectedKey
  );

  return (
    <Collapsible defaultOpen={hasSelectedChild} className="group/collapsible">
      <CollapsibleTrigger className={navRowClass({ isCollapsed: false })}>
        <NavIcon item={item} />
        <span className="flex-1 truncate text-left">
          {getDisplayName(item)}
        </span>
        <ChevronRight
          className={cn(
            "size-4 shrink-0 transition-transform duration-200",
            "group-data-[state=open]/collapsible:rotate-90"
          )}
        />
      </CollapsibleTrigger>

      <CollapsibleContent className="overflow-hidden data-[state=closed]:animate-collapsible-up data-[state=open]:animate-collapsible-down">
        <div className="mt-0.5 flex flex-col gap-0.5">
          {children.map((child) => (
            <SidebarItem
              key={child.key || child.name}
              item={child}
              selectedKey={selectedKey}
              isCollapsed={false}
              depth={depth + 1}
            />
          ))}
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}

function SidebarSubmenuFlyout({ item, selectedKey }: MenuItemProps) {
  const Link = useLink();
  const children = item.children ?? [];

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className={navRowClass({ isCollapsed: true })}>
        <NavIcon item={item} />
        <span className="sr-only">{getDisplayName(item)}</span>
      </DropdownMenuTrigger>

      <DropdownMenuContent side="right" align="start" className="min-w-48">
        <DropdownMenuLabel>{getDisplayName(item)}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {children.map((child) => {
          const isSelected = child.key === selectedKey;

          return (
            <DropdownMenuItem key={child.key || child.name} asChild>
              <Link
                to={child.route || ""}
                className={cn(
                  "flex w-full items-center gap-2",
                  isSelected && "bg-accent text-accent-foreground"
                )}
              >
                <span className="[&_svg]:size-4">{getIcon(child)}</span>
                <span className="truncate">{getDisplayName(child)}</span>
              </Link>
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function SidebarLink({ item, selectedKey, isCollapsed, depth = 0 }: MenuItemProps) {
  const Link = useLink();
  const { isMobile, setOpenMobile } = useShadcnSidebar();
  const isSelected = item.key === selectedKey;
  const isChild = depth > 0 && !isCollapsed;
  const label = getDisplayName(item);
  const badge = item.meta?.badge as React.ReactNode | undefined;

  const row = (
    <Link
      to={item.route || ""}
      aria-current={isSelected ? "page" : undefined}
      // Tapping a link on mobile should close the drawer behind it.
      onClick={() => isMobile && setOpenMobile(false)}
      className={navRowClass({ isSelected, isCollapsed, isChild })}
    >
      <NavIcon item={item} isSelected={isSelected} />
      {isCollapsed ? (
        <span className="sr-only">{label}</span>
      ) : (
        <>
          <span className="flex-1 truncate">{label}</span>
          {badge != null && (
            <span
              className={cn(
                "flex h-5 min-w-5 items-center justify-center rounded-full px-1.5",
                "bg-sidebar-primary/15 text-[10px] font-semibold text-sidebar-primary"
              )}
            >
              {badge}
            </span>
          )}
        </>
      )}
    </Link>
  );

  if (!isCollapsed) return row;

  return (
    <Tooltip>
      <TooltipTrigger asChild>{row}</TooltipTrigger>
      <TooltipContent side="right">{label}</TooltipContent>
    </Tooltip>
  );
}

function NavIcon({
  item,
  isSelected,
}: {
  item: TreeMenuItem;
  isSelected?: boolean;
}) {
  return (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center [&_svg]:size-[18px]",
        isSelected ? "text-sidebar-selected-foreground" : "text-current"
      )}
    >
      {getIcon(item)}
    </span>
  );
}

/* --------------------------------------------------------------------------
   Footer — only rendered when an auth provider can actually identify someone.
   -------------------------------------------------------------------------- */

function SidebarUser({ isCollapsed }: { isCollapsed: boolean }) {
  const authProvider = useActiveAuthProvider();
  const { data: user } = useGetIdentity<Identity>();
  const { mutate: logout, isPending: isLoggingOut } = useLogout();

  if (!authProvider?.getIdentity) return null;

  const name = user?.fullName ?? [user?.firstName, user?.lastName].filter(Boolean).join(" ");
  const avatar = (
    <Avatar className="size-8 shrink-0">
      {user?.avatar && <AvatarImage src={user.avatar} alt={name} />}
      <AvatarFallback className="bg-sidebar-accent text-xs font-semibold text-sidebar-accent-foreground">
        {getInitials(name)}
      </AvatarFallback>
    </Avatar>
  );

  return (
    <ShadcnSidebarFooter className="border-r border-sidebar-border p-2">
      <DropdownMenu>
        <DropdownMenuTrigger
          className={cn(
            "flex w-full items-center gap-2 rounded-lg p-1.5 text-left",
            "transition-colors hover:bg-sidebar-accent/60",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring",
            isCollapsed && "justify-center"
          )}
        >
          {avatar}
          {!isCollapsed && (
            <div className="flex min-w-0 flex-1 flex-col">
              <span className="truncate text-sm font-medium text-sidebar-foreground">
                {name || "Tài khoản"}
              </span>
              {user?.email && (
                <span className="truncate text-xs text-sidebar-foreground/60">
                  {user.email}
                </span>
              )}
            </div>
          )}
        </DropdownMenuTrigger>

        <DropdownMenuContent side="top" align="start" className="min-w-52">
          {name && <DropdownMenuLabel className="truncate">{name}</DropdownMenuLabel>}
          <DropdownMenuSeparator />
          <DropdownMenuItem asChild>
            <Link to="/profile">
              <UserRoundIcon className="size-4" />
              <span>Hồ sơ</span>
            </Link>
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onClick={() => logout()}
            className="text-destructive focus:text-destructive"
          >
            <LogOutIcon className="size-4" />
            <span>{isLoggingOut ? "Đang đăng xuất…" : "Đăng xuất"}</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </ShadcnSidebarFooter>
  );
}

function getInitials(name = "") {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  const first = parts[0][0];
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase();
}

Sidebar.displayName = "Sidebar";
