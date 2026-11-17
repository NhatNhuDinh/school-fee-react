import { cn } from "@/shared/lib/utils";

/**
 * EduPay's mark: a graduation cap whose tassel ends in a gold coin — school and money in one shape, drawn without a
 * tile behind it so it sits on whatever the rail is. The cap takes `--sidebar-brand` (the brand colour on a light rail,
 * the foreground on a dark one); the coin is the fixed gold and does not follow the preset.
 */
export function BrandMark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 32 32"
      aria-hidden
      className={cn("text-sidebar-brand size-8 shrink-0", className)}
      fill="none"
    >
      <path d="M16 4 1.5 11.5 16 19l14.5-7.5L16 4Z" fill="currentColor" />
      <path
        d="M7 15.6v5.4c0 2.6 4 4.8 9 4.8s9-2.2 9-4.8v-5.4l-9 4.6-9-4.6Z"
        fill="currentColor"
        fillOpacity=".82"
      />
      <path d="M29 12.5v8" stroke="var(--gold-400)" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="29" cy="23.2" r="3" fill="var(--gold-400)" />
      <path
        d="M29 21.9v2.6M27.9 22.7h2.2"
        stroke="var(--sidebar-primary-foreground, #fff)"
        strokeWidth=".9"
        strokeLinecap="round"
      />
    </svg>
  );
}

/**
 * The name, set in two weights of the same face: "Edu" bold, "Pay" light. Both in the brand colour, so the contrast is
 * weight alone — which is what lets it stay one colour on either kind of rail.
 */
export function BrandWordmark({ className }: { className?: string }) {
  return (
    <span
      className={cn("text-sidebar-brand text-lg leading-none tracking-[-0.01em]", className)}
      aria-label="EduPay"
    >
      <span className="font-bold">Edu</span>
      <span className="font-light">Pay</span>
    </span>
  );
}
