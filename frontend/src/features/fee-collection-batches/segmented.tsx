import { cn } from "@/shared/lib/utils";

type Option<T extends string> = { value: T; label: string };

/**
 * A short row of mutually exclusive choices, drawn as one control. Used where a
 * select would hide a choice that is only three words long — a đợt thu's scope, or the
 * status filter above its list.
 */
export function Segmented<T extends string>({
  value,
  onChange,
  options,
  className,
  size = "md",
  label,
}: {
  value: T;
  onChange: (value: T) => void;
  options: Option<T>[];
  className?: string;
  size?: "sm" | "md";
  /** What the group is for, read out by assistive technology. */
  label: string;
}) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={cn("bg-muted inline-flex items-center gap-0.5 rounded-lg p-0.5", className)}
    >
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(option.value)}
            className={cn(
              "rounded-md font-medium transition-colors outline-none",
              "focus-visible:ring-ring/50 focus-visible:ring-2",
              size === "sm" ? "px-3 py-1 text-[13px]" : "px-3.5 py-1.5 text-sm",
              active
                ? "bg-card text-foreground shadow-e1"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
