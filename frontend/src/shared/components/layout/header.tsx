"use client";

import { PageActionsSlot, PageTitleSlot } from "@/shared/components/layout/page-slots";
import { AcademicYearSelect } from "@/shared/components/academic-year/academic-year-select";
import { NotificationBell } from "@/shared/components/layout/notification-bell";
import { SidebarTrigger } from "@/shared/ui/sidebar";
import { cn } from "@/shared/lib/utils";

/**
 * Sticky application bar.
 *
 * Frosted rather than opaque, so content scrolling underneath stays faintly
 * visible — the detail that gives the Apex shell its depth. It holds the screen's name and actions (put there by the
 * screen), the school year, and notifications; mode, theme and the account live in the rail.
 */
export function Header() {
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
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <SidebarTrigger
            aria-label="Mở menu"
            className="size-9 rounded-lg text-muted-foreground hover:bg-accent hover:text-foreground md:hidden"
          />

          {/* The screen's name, put here by the screen (see page-slots.tsx). */}
          <PageTitleSlot className="flex min-w-0 flex-1 items-center gap-1" />
        </div>

        <div className="flex items-center gap-1">
          <PageActionsSlot className="me-3 flex items-center gap-2 empty:hidden" />
          <AcademicYearSelect />
          <NotificationBell />
        </div>
      </div>
    </header>
  );
}

Header.displayName = "Header";
