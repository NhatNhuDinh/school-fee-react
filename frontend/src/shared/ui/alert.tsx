import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/shared/lib/utils";

/**
 * Fluent's message bar: a 4px corner, a hairline edge, a wash of the status colour, and an icon in
 * that status's text colour — put a lucide icon as the first child. The status is carried by the
 * icon and the wash together, so it never rests on colour alone.
 */
const alertVariants = cva(
  "relative w-full rounded-md border px-3 py-2.5 text-sm grid has-[>svg]:grid-cols-[calc(var(--spacing)*4)_1fr] grid-cols-[0_1fr] has-[>svg]:gap-x-2.5 gap-y-0.5 items-start [&>svg]:size-4 [&>svg]:translate-y-0.5",
  {
    variants: {
      variant: {
        default: "bg-card text-card-foreground [&>svg]:text-muted-foreground",
        info: "border-brand/30 bg-info-subtle text-foreground [&>svg]:text-info-text",
        success:
          "border-success/30 bg-success-subtle text-foreground [&>svg]:text-success-text",
        warning:
          "border-warning/30 bg-warning-subtle text-foreground [&>svg]:text-warning-text",
        destructive:
          "border-destructive/30 bg-destructive-subtle text-foreground [&>svg]:text-destructive-text",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

function Alert({
  className,
  variant,
  ...props
}: React.ComponentProps<"div"> & VariantProps<typeof alertVariants>) {
  return (
    <div
      data-slot="alert"
      role="alert"
      className={cn(alertVariants({ variant }), className)}
      {...props}
    />
  );
}

function AlertTitle({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="alert-title"
      className={cn(
        "col-start-2 line-clamp-1 min-h-4 font-medium tracking-tight",
        className
      )}
      {...props}
    />
  );
}

function AlertDescription({
  className,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="alert-description"
      className={cn(
        "text-muted-foreground col-start-2 grid justify-items-start gap-1 text-sm [&_p]:leading-relaxed",
        className
      )}
      {...props}
    />
  );
}

export { Alert, AlertTitle, AlertDescription };
