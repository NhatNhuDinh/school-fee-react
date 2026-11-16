import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/shared/lib/utils";

/**
 * Button, in Fluent 2's style.
 *
 * **Sizes are Fluent's three** — `sm` 24px, `default` 32px, `lg` 40px — and `default` follows the
 * density setting (`h-control`), so a compact screen gets 24px buttons and a spacious one 40px.
 * `icon`, `icon-sm` and `icon-lg` are the square counterparts, for an icon-only button that has to
 * line up with text buttons of the same size.
 *
 * **Variants, against Fluent's appearances:**
 *  - `default` — *primary*: the brand fill. One per screen.
 *  - `outline` — *secondary*: the card colour with a hairline edge. The everyday button.
 *  - `ghost` — *subtle*: no fill until hovered.
 *  - `secondary` — a filled neutral, for a quiet action inside a panel (not a Fluent appearance).
 *  - `subtle` — a brand-tinted fill, for a repeated action inside a panel (not a Fluent appearance).
 *  - `destructive`, `success` — solid status fills; `destructive` belongs to the one button that
 *    destroys something, never to the Cancel beside it.
 *  - `link` — text that reads as a link.
 *
 * **Hover is a colour, not an opacity**: `hover:bg-primary/90` lets whatever is behind the button
 * through, so the same button would hover to one colour on a card and another on a table row. Every
 * solid variant hovers (and presses) to a real token.
 *
 * **Disabled is not faded.** Fluent gives a disabled control its own three colours (`disabled-*`),
 * so it does not change with whatever is behind it.
 *
 * **Focus** is Fluent's two-line ring (`focus-ring`), shown for the keyboard only.
 */
const buttonVariants = cva(
  [
    "focus-ring inline-flex shrink-0 items-center justify-center gap-1.5 whitespace-nowrap",
    "rounded-md font-semibold",
    "transition-colors duration-(--dur-faster) ease-(--curve-easy-ease)",
    "disabled:pointer-events-none disabled:border-disabled-line disabled:bg-disabled disabled:text-disabled-foreground",
    "[&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
    "aria-invalid:border-destructive",
  ],
  {
    variants: {
      variant: {
        default:
          "bg-primary text-primary-foreground hover:bg-primary-hover active:bg-primary-pressed",
        destructive:
          "bg-destructive text-destructive-foreground hover:bg-destructive/90 active:bg-destructive/80",
        success:
          "bg-success text-success-foreground hover:bg-success/90 active:bg-success/80",
        outline:
          "border border-input bg-card text-foreground hover:border-stroke-hover hover:bg-accent active:border-stroke-pressed active:bg-pressed",
        secondary:
          "bg-secondary text-secondary-foreground hover:bg-selected active:bg-pressed",
        subtle:
          "bg-brand-subtle text-brand hover:bg-brand-subtle-hover",
        ghost: "text-foreground hover:bg-accent active:bg-pressed",
        link: "text-link underline-offset-4 hover:text-link-hover hover:underline active:text-link-pressed",
      },
      size: {
        default: "h-control px-3 text-sm has-[>svg]:px-2.5",
        sm: "h-control-sm gap-1 rounded-sm px-2 text-xs has-[>svg]:px-1.5",
        lg: "h-control-lg gap-2 px-4 text-sm has-[>svg]:px-3",
        icon: "size-control",
        "icon-sm": "size-control-sm rounded-sm",
        "icon-lg": "size-control-lg",
      },
    },
    compoundVariants: [
      // Without a fill or an edge, a disabled button shows nothing behind its label.
      {
        variant: ["ghost", "link", "outline", "subtle"],
        class: "disabled:bg-transparent",
      },
      { variant: ["ghost", "link", "subtle"], class: "disabled:border-transparent" },
    ],
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

function Button({
  className,
  variant,
  size,
  asChild = false,
  ...props
}: React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean;
  }) {
  const Comp = asChild ? Slot : "button";

  return (
    <Comp
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  );
}

export { Button, buttonVariants };
