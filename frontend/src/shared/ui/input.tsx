import * as React from "react";
import type { VariantProps } from "class-variance-authority";

import { cn } from "@/shared/lib/utils";
import { fieldVariants } from "@/shared/ui/field-control";

type InputProps = React.ComponentProps<"input"> & Pick<VariantProps<typeof fieldVariants>, "appearance">;

/**
 * A single-line field in Fluent 2's style; what it looks like is `field-control`. `appearance="bare"`
 * is for an input inside a wrapper that draws the field's edge itself.
 */
function Input({ className, type, appearance, ...props }: InputProps) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        fieldVariants({ appearance }),
        "file:text-foreground file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium",
        className
      )}
      {...props}
    />
  );
}

export { Input };
export type { InputProps };
