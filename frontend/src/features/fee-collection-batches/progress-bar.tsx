import { cn } from "@/shared/lib/utils";

/**
 * How much of what is owed has been collected, as a thin bar. Green because it is money
 * arriving; the track is the muted fill so an empty bar still reads as a bar. Drawn by hand
 * rather than with the shell's `Progress`, which is the brand colour and a different job.
 */
export function ProgressBar({ percent, className }: { percent: number; className?: string }) {
  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={percent}
      className={cn("bg-muted h-1.5 w-full overflow-hidden rounded-full", className)}
    >
      <div className="bg-success h-full rounded-full transition-all" style={{ width: `${percent}%` }} />
    </div>
  );
}
