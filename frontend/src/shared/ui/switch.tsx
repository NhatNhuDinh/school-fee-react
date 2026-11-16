"use client";

import * as React from "react";
import * as SwitchPrimitive from "@radix-ui/react-switch";

import { cn } from "@/shared/lib/utils";

/**
 * Switch, in Fluent 2's style: a 40×20px track with a hairline edge in `stroke-accessible` and a
 * 14px thumb. On, the track fills `compound` and the thumb goes white. Literal pixels, as Fluent
 * draws it — it does not scale with density.
 */
function Switch({
  className,
  ...props
}: React.ComponentProps<typeof SwitchPrimitive.Root>) {
  return (
    <SwitchPrimitive.Root
      data-slot="switch"
      className={cn(
        "peer group focus-ring inline-flex h-[20px] w-[40px] shrink-0 items-center rounded-full border border-stroke-accessible bg-transparent",
        "transition-colors duration-(--dur-faster) ease-(--curve-easy-ease)",
        "hover:border-stroke-accessible-hover",
        "data-[state=checked]:border-compound data-[state=checked]:bg-compound hover:data-[state=checked]:border-compound-hover hover:data-[state=checked]:bg-compound-hover",
        "disabled:cursor-not-allowed disabled:border-disabled-line disabled:data-[state=checked]:border-disabled disabled:data-[state=checked]:bg-disabled",
        className
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb
        data-slot="switch-thumb"
        className={cn(
          "pointer-events-none ml-[3px] block size-[14px] rounded-full bg-stroke-accessible transition-[translate,background-color] duration-(--dur-fast) ease-(--curve-easy-ease)",
          "group-hover:bg-stroke-accessible-hover data-[state=checked]:translate-x-[18px] data-[state=checked]:bg-on-compound group-hover:data-[state=checked]:bg-on-compound",
          "group-disabled:bg-disabled-foreground group-disabled:data-[state=checked]:bg-disabled-foreground"
        )}
      />
    </SwitchPrimitive.Root>
  );
}

export { Switch };
