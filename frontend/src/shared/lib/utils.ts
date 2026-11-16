import { ClassValue, clsx } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

/**
 * `cn` — merge class lists, last conflicting utility wins.
 *
 * The merge is *extended* rather than stock, and it has to be. `tailwind-merge`
 * resolves conflicts from a table of class groups it ships with, and a custom
 * utility of ours is not in it — so it guesses from the prefix. `text-overline`
 * looks exactly like `text-<size>` / `text-<colour>`, which means
 *
 *     cn("text-overline", "text-muted-foreground")
 *
 * dropped the overline entirely and left the header looking like body text.
 * That failure is silent: the class is simply not in the output, so there is
 * nothing to inspect in the stylesheet and nothing wrong in the source.
 *
 * Registering it as its own group is what makes the two coexist — one sets a
 * whole type style, the other sets a colour, and neither should evict the
 * other. Any future custom utility whose name starts with a Tailwind namespace
 * belongs in this list for the same reason.
 *
 * The Fluent additions are the same problem in another shape. `h-control` is a
 * height that `tailwind-merge` does not know is a height, so
 *
 *     cn("h-control", "h-auto")
 *
 * kept *both*, and whichever the stylesheet declares last won — the field's own
 * height beat the caller's override, and a bare input inside a search box
 * stretched to the box's full height. They are added to the groups they belong
 * to (height, min-height, size, font size, corner radius), so a caller's
 * override evicts them as it would evict `h-9`.
 */
// The type parameter is how a *new* group id is declared; `extend.classGroups`
// otherwise only accepts the ids tailwind-merge already knows.
const twMerge = extendTailwindMerge<"dth-type">({
  extend: {
    classGroups: {
      "dth-type": ["text-overline"],
      h: [{ h: ["control", "control-sm", "control-md", "control-lg"] }],
      "min-h": [{ "min-h": ["control"] }],
      size: [{ size: ["control", "control-sm", "control-md", "control-lg"] }],
      // Fluent's type ramp (index.css `--text-*`).
      "font-size": [
        {
          text: [
            "caption2",
            "caption1",
            "body1",
            "subtitle2",
            "subtitle1",
            "title3",
            "title2",
            "title1",
            "large-title",
          ],
        },
      ],
      rounded: [{ rounded: ["card"] }],
    },
  },
});

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
