"use client";

import * as React from "react";
import * as CheckboxPrimitive from "@radix-ui/react-checkbox";
import { CheckIcon, MinusIcon } from "lucide-react";

import { cn } from "@/shared/lib/utils";

/**
 * Checkbox, in Fluent 2's style: a 16px square with a 2px corner and an edge in `stroke-accessible`
 * (darker than an ordinary hairline, because it is the whole control). Checked or indeterminate turns
 * it solid `compound` with a tick or a dash in the opposite colour; hover and press step through the
 * `compound-*` shades; disabled takes the `disabled-*` colours. Sizes are literal pixels — Fluent's —
 * so the density setting does not make a 14px checkbox.
 */
function Checkbox({
  className,
  ...props
}: React.ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      data-slot="checkbox"
      className={cn(
        "peer group focus-ring inline-flex size-[16px] shrink-0 items-center justify-center rounded-sm border border-stroke-accessible bg-card text-on-compound",
        "transition-colors duration-(--dur-faster) ease-(--curve-easy-ease)",
        "hover:border-stroke-accessible-hover",
        "data-[state=checked]:border-compound data-[state=checked]:bg-compound data-[state=indeterminate]:border-compound data-[state=indeterminate]:bg-compound",
        "hover:data-[state=checked]:border-compound-hover hover:data-[state=checked]:bg-compound-hover hover:data-[state=indeterminate]:border-compound-hover hover:data-[state=indeterminate]:bg-compound-hover",
        "active:data-[state=checked]:bg-compound-pressed active:data-[state=indeterminate]:bg-compound-pressed",
        "disabled:cursor-not-allowed disabled:border-disabled-line disabled:bg-transparent disabled:text-disabled-foreground disabled:data-[state=checked]:bg-disabled disabled:data-[state=indeterminate]:bg-disabled",
        "aria-invalid:border-destructive",
        className
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator
        data-slot="checkbox-indicator"
        className="flex items-center justify-center text-current"
      >
        <CheckIcon
          strokeWidth={3}
          className="size-[12px] group-data-[state=indeterminate]:hidden"
        />
        <MinusIcon
          strokeWidth={3}
          className="hidden size-[12px] group-data-[state=indeterminate]:block"
        />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}

export { Checkbox };
