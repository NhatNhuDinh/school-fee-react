"use client";

import * as React from "react";
import * as TabsPrimitive from "@radix-ui/react-tabs";

import { cn } from "@/shared/lib/utils";

function Tabs({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Root>) {
  return (
    <TabsPrimitive.Root
      data-slot="tabs"
      className={cn("flex flex-col gap-2", className)}
      {...props}
    />
  );
}

/** Fluent's tab list is a row of tabs on the page itself — no tray behind them. */
function TabsList({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      data-slot="tabs-list"
      className={cn("inline-flex w-fit items-center gap-1", className)}
      {...props}
    />
  );
}

/**
 * A tab is text with a hover fill; the selected one is semibold with a 2px brand line under it. The
 * line is drawn as a pseudo-element inset from the tab's edges (Fluent does the same), so the hover
 * fill still covers the whole tab.
 */
function TabsTrigger({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      data-slot="tabs-trigger"
      className={cn(
        "focus-ring text-neutral-text relative inline-flex h-control items-center justify-center gap-1.5 rounded-md px-3 text-sm whitespace-nowrap",
        "transition-colors duration-(--dur-faster) ease-(--curve-easy-ease)",
        "hover:bg-accent hover:text-foreground",
        "after:absolute after:inset-x-3 after:bottom-0 after:h-0.5 after:rounded-full after:bg-transparent after:content-['']",
        "data-[state=active]:text-foreground data-[state=active]:font-semibold data-[state=active]:after:bg-brand",
        "disabled:pointer-events-none disabled:text-disabled-foreground [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
        className
      )}
      {...props}
    />
  );
}

function TabsContent({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Content>) {
  return (
    <TabsPrimitive.Content
      data-slot="tabs-content"
      className={cn("flex-1 outline-none", className)}
      {...props}
    />
  );
}

export { Tabs, TabsList, TabsTrigger, TabsContent };
