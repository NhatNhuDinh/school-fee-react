import * as React from "react";

import { cn } from "@/shared/lib/utils";
import { Input } from "@/shared/ui/input";

/**
 * A time field, the shadcn "time picker" way: a plain `type="time"` input with the browser's
 * own clock icon and spinner hidden, so it matches the other inputs. The value is `HH:mm`.
 */
export function TimeInput({
  className,
  ...props
}: Omit<React.ComponentProps<typeof Input>, "type">) {
  return (
    <Input
      type="time"
      className={cn(
        "appearance-none [&::-webkit-calendar-picker-indicator]:hidden [&::-webkit-calendar-picker-indicator]:appearance-none",
        className
      )}
      {...props}
    />
  );
}
