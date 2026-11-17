"use client";

import {
  useActiveAuthProvider,
  useGetIdentity,
  useLogout,
} from "@refinedev/core";
import { LogOutIcon, UserRoundIcon } from "lucide-react";
import { Link } from "react-router";

import { BrandMark, BrandWordmark } from "@/shared/components/layout/brand-logo";
import { AcademicYearSelect } from "@/shared/components/academic-year/academic-year-select";
import { NotificationBell } from "@/shared/components/layout/notification-bell";
import { ThemeCustomizer } from "@/shared/components/theme/theme-customizer";
import { ThemeToggle } from "@/shared/components/theme/theme-toggle";
import { Avatar, AvatarFallback, AvatarImage } from "@/shared/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/shared/ui/dropdown-menu";
import { SidebarTrigger, useSidebar } from "@/shared/ui/sidebar";
import { cn } from "@/shared/lib/utils";

type Identity = {
  fullName?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  avatar?: string;
};

/**
 * Sticky application bar.
 *
 * Frosted rather than opaque, so content scrolling underneath stays faintly
 * visible — the detail that gives the Apex shell its depth.
 */
export function Header() {
  const { isMobile } = useSidebar();

  return (
    <header
      data-slot="app-header"
      className={cn(
        "sticky top-0 z-30 flex h-16 shrink-0",
        "border-b border-border surface-blur"
      )}
    >
      {/*
        The bar itself is full-bleed so its bottom border runs the width of the
        viewport; its contents ride the same column as the page below, which is
        what keeps the search field lined up with the breadcrumb and the table.
      */}
      <div
        className={cn(
          "mx-auto flex w-full max-w-(--shell-max-w) items-center",
          "justify-between gap-3 px-(--shell-gutter-x)"
        )}
      >
        <div className="flex min-w-0 items-center gap-2">
          <SidebarTrigger
            aria-label="Mở menu"
            className="size-9 rounded-lg text-muted-foreground hover:bg-accent hover:text-foreground md:hidden"
          />

          {/* On mobile the sidebar is off-canvas, so restate the brand here. */}
          {isMobile && (
            <div className="flex min-w-0 items-center gap-2">
              <BrandMark className="size-7" />
              <BrandWordmark className="truncate" />
            </div>
          )}
        </div>

        <div className="flex items-center gap-1">
          <AcademicYearSelect />
          <NotificationBell />
          <ThemeToggle />
          <ThemeCustomizer />
          <UserMenu />
        </div>
      </div>
    </header>
  );
}

function UserMenu() {
  const authProvider = useActiveAuthProvider();
  const { data: user } = useGetIdentity<Identity>();
  const { mutate: logout, isPending: isLoggingOut } = useLogout();

  if (!authProvider?.getIdentity) return null;

  const name =
    user?.fullName ??
    [user?.firstName, user?.lastName].filter(Boolean).join(" ");

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="Menu người dùng"
        className={cn(
          "ms-1 rounded-full transition-opacity hover:opacity-80",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
        )}
      >
        <Avatar className="size-9">
          {user?.avatar && <AvatarImage src={user.avatar} alt={name} />}
          <AvatarFallback className="bg-primary/10 text-xs font-semibold text-brand">
            {getInitials(name)}
          </AvatarFallback>
        </Avatar>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="min-w-52">
        {(name || user?.email) && (
          <>
            <DropdownMenuLabel className="flex flex-col gap-0.5">
              <span className="truncate text-sm font-medium">{name}</span>
              {user?.email && (
                <span className="truncate text-xs font-normal text-muted-foreground">
                  {user.email}
                </span>
              )}
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
          </>
        )}
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
  );
}

function getInitials(name = "") {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  const first = parts[0][0];
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase();
}

Header.displayName = "Header";
