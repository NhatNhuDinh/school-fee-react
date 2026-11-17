import * as React from "react";

import { cn } from "@/shared/lib/utils";

/**
 * The data table's primitives.
 *
 * This is Fluent 2's data grid, measured against its own source rather than
 * remembered:
 *
 * **The header is the same type as the body** — regular weight, the body's
 * ink colour, no band behind it; only a hairline (`border`) under the row sets
 * it apart. It is 32px tall (`h-8`). A sortable header gets a hover fill from
 * `DataTable`; there is nothing else to it. (`--table-header` is the card
 * colour in the Fluent preset, and a brand tint in the older ones.)
 *
 * **Rows have a fixed height, not a padded one.** `h-11` (44px, plus the 1px
 * hairline) on the cell rather than vertical padding alone means a row with a
 * chip in it is exactly as tall as a row with plain text, so the eye can track
 * across the table without the baseline moving. Cells take 8px each side.
 * Heights are `calc(var(--spacing) * n)`, so they follow the density setting.
 *
 * **Hover is the subtle fill (`accent`); a selected row is the brand wash**
 * (`brand-subtle`, Fluent's BrandBackground2) with no bar beside it.
 */
function Table({
  className,
  containerClassName,
  ...props
}: React.ComponentProps<"table"> & {
  /** Classes for the scrolling wrapper — a `fill` table makes it the flexible, both-axes scroller. */
  containerClassName?: string;
}) {
  return (
    <div
      data-slot="table-container"
      className={cn(
        "scrollbar-slim relative w-full max-w-full overflow-x-auto",
        containerClassName
      )}
    >
      <table
        data-slot="table"
        className={cn(
          "w-full caption-bottom border-collapse text-sm",
          className
        )}
        {...props}
      />
    </div>
  );
}

function TableHeader({
  className,
  sticky,
  ...props
}: React.ComponentProps<"thead"> & {
  /**
   * Pin the header while the body scrolls. Only useful inside a container that
   * scrolls vertically — in a page-scrolled table it pins to nothing.
   */
  sticky?: boolean;
}) {
  return (
    <thead
      data-slot="table-header"
      className={cn(
        "[&_tr]:border-b [&_tr]:border-border [&_tr:hover]:bg-transparent",
        sticky && "sticky top-0 z-10",
        className
      )}
      {...props}
    />
  );
}

function TableBody({ className, ...props }: React.ComponentProps<"tbody">) {
  return (
    <tbody
      data-slot="table-body"
      className={cn("[&_tr:last-child]:border-0", className)}
      {...props}
    />
  );
}

function TableFooter({ className, ...props }: React.ComponentProps<"tfoot">) {
  return (
    <tfoot
      data-slot="table-footer"
      className={cn(
        "bg-surface-subtle border-t border-border font-medium [&>tr]:last:border-b-0",
        className
      )}
      {...props}
    />
  );
}

function TableRow({ className, ...props }: React.ComponentProps<"tr">) {
  return (
    <tr
      data-slot="table-row"
      className={cn(
        // `group/row`: a frozen cell paints over the row, so it has to paint the row's hover itself — it asks the row.
        "group/row border-b border-border transition-colors duration-(--dur-faster)",
        "hover:bg-accent data-[state=selected]:bg-brand-subtle",
        className
      )}
      {...props}
    />
  );
}

/**
 * Column alignment. `right` also switches on tabular figures, because the only
 * reason to right-align a column is that it holds numbers meant to line up.
 */
const ALIGN: Record<"left" | "center" | "right", string> = {
  left: "text-left",
  center: "text-center",
  right: "text-right tabular-nums",
};

function TableHead({
  className,
  align = "left",
  ...props
}: Omit<React.ComponentProps<"th">, "align"> & {
  align?: "left" | "center" | "right";
}) {
  return (
    <th
      data-slot="table-head"
      className={cn(
        "text-table-header-foreground bg-table-header text-sm font-normal",
        "h-8 px-2 align-middle whitespace-nowrap",
        "[&:has([role=checkbox])]:pr-0 [&>[role=checkbox]]:translate-y-[2px]",
        ALIGN[align],
        className
      )}
      {...props}
    />
  );
}

function TableCell({
  className,
  align = "left",
  ...props
}: Omit<React.ComponentProps<"td">, "align"> & {
  align?: "left" | "center" | "right";
}) {
  return (
    <td
      data-slot="table-cell"
      className={cn(
        "h-11 px-2 py-1 align-middle whitespace-nowrap",
        // Every cell paints the row's hover itself, in step. A frozen cell has to (it covers the row), and if the plain ones
        // left it to the row's own fade, a row would be half one colour and half another for the length of the transition.
        "transition-colors duration-(--dur-faster) group-hover/row:bg-accent",
        "[&:has([role=checkbox])]:pr-0 [&>[role=checkbox]]:translate-y-[2px]",
        ALIGN[align],
        className
      )}
      {...props}
    />
  );
}

function TableCaption({
  className,
  ...props
}: React.ComponentProps<"caption">) {
  return (
    <caption
      data-slot="table-caption"
      className={cn("text-muted-foreground mt-3 text-xs", className)}
      {...props}
    />
  );
}

export {
  Table,
  TableHeader,
  TableBody,
  TableFooter,
  TableHead,
  TableRow,
  TableCell,
  TableCaption,
};
