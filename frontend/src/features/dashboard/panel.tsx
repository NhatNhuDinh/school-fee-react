import type { ReactNode } from "react";

import { cn } from "@/shared/lib/utils";

/** One card of the home page: a title on the left, an optional control on the right, then the body. */
export function Panel({
  title,
  action,
  children,
  className,
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("bg-card flex min-w-0 flex-col rounded-xl border p-5", className)}>
      <header className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-foreground text-base font-semibold">{title}</h2>
        {action}
      </header>
      {children}
    </section>
  );
}
