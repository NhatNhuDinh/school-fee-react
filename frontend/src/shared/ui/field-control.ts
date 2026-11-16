import { cva } from "class-variance-authority";

/**
 * What a text field looks like — one definition for `Input`, `Textarea` and the `Select` trigger, so
 * they cannot drift apart.
 *
 * Fluent 2's field is not a box with an even edge. It has a hairline on three sides and a **darker
 * line under it** (`stroke-accessible`), because the underline is what says "type here". On focus
 * there is no ring: that bottom line turns brand blue and thickens from 1px to 2px (the 1px border
 * plus a 1px inset shadow). On hover the edge darkens a step. Invalid turns the underline red.
 *
 * Disabled is its own set of colours (`disabled-*`), not the same field at half opacity.
 *
 * `outline-none` is deliberate: the global `:focus-visible` ring in `index.css` is the safety net
 * for things with no focus treatment of their own, and this has one.
 */
export const fieldVariants = cva(
  [
    "bg-card text-foreground flex w-full min-w-0 outline-none",
    "placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground",
    "transition-[border-color,box-shadow] duration-(--dur-faster) ease-(--curve-easy-ease)",
    "disabled:cursor-not-allowed disabled:border-disabled-line disabled:bg-transparent disabled:text-disabled-foreground disabled:placeholder:text-disabled-foreground",
    "aria-invalid:border-destructive",
  ],
  {
    variants: {
      appearance: {
        outline: [
          "border-input border-b-stroke-accessible rounded-md border",
          "hover:border-stroke-hover hover:border-b-stroke-accessible-hover",
          "focus-visible:border-b-stroke-brand focus-visible:shadow-[inset_0_-1px_0_0_var(--stroke-brand)]",
          "aria-invalid:border-b-destructive aria-invalid:shadow-[inset_0_-1px_0_0_var(--destructive)]",
        ],
        /**
         * No edge, no fill and no focus treatment of its own: for an input that sits inside a wrapper
         * which *is* the field (a search box with an icon and a clear button), and so draws the edge.
         */
        bare: "border-0 bg-transparent focus-visible:shadow-none",
        /** Only the line underneath, for a field set into a table row or a dense form. */
        underline: [
          "border-b-stroke-accessible rounded-none border-0 border-b bg-transparent",
          "hover:border-b-stroke-accessible-hover",
          "focus-visible:border-b-stroke-brand focus-visible:shadow-[inset_0_-1px_0_0_var(--stroke-brand)]",
          "aria-invalid:border-b-destructive",
        ],
      },
      /** Heights are Fluent's three: 24, 32, 40. `default` follows the density setting. */
      size: {
        sm: "h-control-sm px-2 text-xs",
        // 16px on a phone so iOS does not zoom the page when the field takes focus.
        default: "h-control px-2.5 text-base md:text-sm",
        lg: "h-control-lg px-3 text-base",
        /** A multi-line field decides its own height. */
        none: "px-2.5 py-1.5 text-base md:text-sm",
      },
    },
    defaultVariants: { appearance: "outline", size: "default" },
  }
);
