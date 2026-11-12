import { cn } from "@/shared/lib/utils";
import { formatDate } from "@/shared/lib/format";
import {
  termColor,
  type YearTimeline as Timeline,
} from "@/domains/academic-year/timeline";

const shortDate = (iso: string) => formatDate(iso).slice(0, 5);

type Props = {
  /** `null` until the year has two usable dates. */
  timeline: Timeline | null;
  className?: string;
};

/**
 * The year as one bar, with its terms laid along it.
 *
 * Sized from the width of the element itself (`@container`), not the window:
 * the shell's sidebar takes a quarter of a narrow window, so a viewport
 * breakpoint says "wide" while the room is not. Under ~42rem the dates leave
 * the bars and the legend underneath carries them.
 */
export function YearTimeline({ timeline, className }: Props) {
  if (!timeline) {
    return (
      <p
        className={cn(
          "text-muted-foreground rounded-md border border-dashed p-4 text-center text-sm",
          className
        )}
      >
        Nhập ngày bắt đầu và ngày kết thúc để xem lịch năm học.
      </p>
    );
  }

  const summary = timeline.terms
    .map((t) => `${t.name}: ${formatDate(t.startDate)} đến ${formatDate(t.endDate)}`)
    .join("; ");
  const gap = timeline.gaps.reduce((sum, g) => sum + g.days, 0);

  return (
    <div className={cn("@container flex min-w-0 flex-col gap-4", className)}>
      <div
        role="img"
        aria-label={`Năm học từ ${formatDate(timeline.startDate)} đến ${formatDate(
          timeline.endDate
        )}, ${timeline.totalDays} ngày. ${summary}`}
        // Room under the bar for the "today" pill, which hangs below it.
        className={cn("px-1", timeline.todayLeft !== null && "pb-6")}
      >
        <div className="text-muted-foreground relative h-5 text-[11px] leading-5">
          {timeline.months.map((month, i) => (
            <span
              key={`${month.label}-${i}`}
              className={cn(
                "absolute top-0 whitespace-nowrap",
                // Every other month label drops out when there is not room.
                i % 2 === 1 && "@max-2xl:hidden"
              )}
              style={{ left: `${month.left}%` }}
            >
              {month.label}
            </span>
          ))}
        </div>

        <div className="bg-muted relative h-10 rounded-md @lg:h-12">
          {timeline.months.map((month, i) => (
            <span
              key={`tick-${i}`}
              aria-hidden
              className="bg-border absolute -top-1.5 h-1.5 w-px"
              style={{ left: `${month.left}%` }}
            />
          ))}

          {timeline.terms.map((term) => (
            <div
              key={term.index}
              title={`${term.name} · ${formatDate(term.startDate)} – ${formatDate(
                term.endDate
              )} · ${term.days} ngày`}
              className={cn(
                "absolute inset-y-0 flex min-w-0 flex-col justify-center overflow-hidden rounded-md px-2 text-white",
                (term.outside || term.overlaps) &&
                  "ring-destructive ring-2 ring-offset-1"
              )}
              style={{
                left: `${term.left}%`,
                width: `${term.width}%`,
                background: termColor(term.index),
              }}
            >
              <span className="truncate text-xs leading-4 font-semibold @lg:text-sm">
                {term.name}
              </span>
              <span className="hidden truncate text-[11px] leading-4 opacity-90 @2xl:block">
                {shortDate(term.startDate)} – {shortDate(term.endDate)} · {term.days} ngày
              </span>
            </div>
          ))}

          {timeline.todayLeft !== null && (
            <div
              aria-hidden
              // Neutral rather than red: the terms take the theme's own colour, which
              // can be red, and a red marker over a red bar cannot be seen. The halo
              // keeps the line legible over any bar and over the bare track.
              className="bg-foreground absolute -top-1.5 -bottom-1.5 w-0.5 shadow-[0_0_0_1.5px_var(--card)]"
              style={{ left: `${timeline.todayLeft}%` }}
            >
              <span className="bg-foreground text-background absolute top-full mt-1 -translate-x-[calc(50%-1px)] rounded-full px-2 py-0.5 text-[10px] leading-4 font-semibold whitespace-nowrap">
                Hôm nay
              </span>
            </div>
          )}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm">
        {timeline.terms.map((term) => (
          <span key={term.index} className="flex items-center gap-2">
            <span
              aria-hidden
              className="size-2.5 shrink-0 rounded-[3px]"
              style={{ background: termColor(term.index) }}
            />
            {term.name}
          </span>
        ))}
        <span className="text-muted-foreground @lg:ms-auto">
          {gap > 0 && `Nghỉ ${gap} ngày giữa các học kỳ · `}
          {formatDate(timeline.startDate)} → {formatDate(timeline.endDate)} ·{" "}
          {timeline.totalDays} ngày
        </span>
      </div>
    </div>
  );
}
