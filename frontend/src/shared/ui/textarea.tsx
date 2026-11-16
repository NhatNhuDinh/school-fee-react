import * as React from "react";

import { cn } from "@/shared/lib/utils";
import { fieldVariants } from "@/shared/ui/field-control";

/** A multi-line field; same look as `Input`, with a height that follows its content. */
function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(fieldVariants({ size: "none" }), "field-sizing-content min-h-16", className)}
      {...props}
    />
  );
}

export { Textarea };
