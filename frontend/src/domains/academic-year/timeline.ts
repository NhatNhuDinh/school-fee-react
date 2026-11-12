/**
 * What a year and its terms look like laid along one axis.
 *
 * Pure arithmetic over `YYYY-MM-DD` strings, so the form can draw the year as
 * somebody types it. Dates are counted as whole days in UTC: a browser in
 * another timezone must not move a term across a day boundary.
 */

type TermInput = { name?: string; startDate?: string; endDate?: string };

export type TimelineTerm = {
  /** Position in the list the caller passed, so a form row can find its term. */
  index: number;
  name: string;
  startDate: string;
  endDate: string;
  /** Inclusive: a term that starts and ends on the same day lasts one day. */
  days: number;
  /** Where the bar starts and how wide it is, as a percentage of the year. */
  left: number;
  width: number;
  /** Reaches before the year starts or after it ends. */
  outside: boolean;
  /** Shares at least one day with another term. */
  overlaps: boolean;
};

export type TimelineGap = {
  days: number;
  startDate: string;
  endDate: string;
};

export type YearTimeline = {
  startDate: string;
  endDate: string;
  totalDays: number;
  months: { label: string; left: number }[];
  terms: TimelineTerm[];
  gaps: TimelineGap[];
  /** Share of the year inside some term, 0–1. */
  coverage: number;
  /** Where today falls on the axis (0–100), or `null` when it is outside the year. */
  todayLeft: number | null;
  /** Terms that could not be drawn because a date is missing or backwards. */
  incomplete: number;
};

const DAY = 86_400_000;
const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})/;

/** `2026-09-05` -> whole days since the epoch, or `null` when it is not a date. */
function toDay(value?: string): number | null {
  const match = value ? DATE_ONLY.exec(value) : null;
  if (!match) return null;
  const time = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return Number.isNaN(time) ? null : Math.floor(time / DAY);
}

function toIso(day: number): string {
  return new Date(day * DAY).toISOString().slice(0, 10);
}

/**
 * `null` until the year itself has two usable dates in the right order —
 * nothing can be laid out against an axis that has no ends.
 */
export function buildTimeline(
  startDate: string | undefined,
  endDate: string | undefined,
  periods: TermInput[] | undefined,
  now: Date = new Date()
): YearTimeline | null {
  const yearStart = toDay(startDate);
  const yearEnd = toDay(endDate);
  if (yearStart === null || yearEnd === null || yearEnd < yearStart) return null;

  const today = Math.floor(
    Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / DAY
  );
  const totalDays = yearEnd - yearStart + 1;
  const percent = (day: number) => ((day - yearStart) / totalDays) * 100;

  const drawn = (periods ?? [])
    .map((period, index) => ({
      index,
      name: period.name?.trim() || `Học kỳ ${index + 1}`,
      start: toDay(period.startDate),
      end: toDay(period.endDate),
    }))
    .filter(
      (p): p is { index: number; name: string; start: number; end: number } =>
        p.start !== null && p.end !== null && p.end >= p.start
    );

  const incomplete = (periods ?? []).length - drawn.length;
  const byStart = [...drawn].sort((a, b) => a.start - b.start);

  const terms: TimelineTerm[] = drawn.map((p) => {
    // Clamped so a term that overruns the year still draws inside the track;
    // `outside` is what tells the reader that it overruns.
    const from = Math.max(p.start, yearStart);
    const to = Math.min(p.end, yearEnd);
    return {
      index: p.index,
      name: p.name,
      startDate: toIso(p.start),
      endDate: toIso(p.end),
      days: p.end - p.start + 1,
      left: to >= from ? percent(from) : 0,
      width: to >= from ? percent(to + 1) - percent(from) : 0,
      outside: p.start < yearStart || p.end > yearEnd,
      overlaps: byStart.some(
        (o) => o.index !== p.index && o.start <= p.end && p.start <= o.end
      ),
    };
  });

  // Walk the terms in date order, remembering the last day any of them reaches.
  // A term that starts after that day leaves a gap; one that starts before it
  // overlaps, and only its new days count towards coverage.
  const gaps: TimelineGap[] = [];
  let coveredUntil = yearStart - 1;
  let covered = 0;
  let seen = false;
  for (const p of byStart) {
    const from = Math.max(p.start, yearStart);
    const to = Math.min(p.end, yearEnd);
    if (to < from) continue;
    if (seen && from > coveredUntil + 1) {
      gaps.push({
        days: from - coveredUntil - 1,
        startDate: toIso(coveredUntil + 1),
        endDate: toIso(from - 1),
      });
    }
    const newFrom = Math.max(from, coveredUntil + 1);
    if (to >= newFrom) covered += to - newFrom + 1;
    coveredUntil = Math.max(coveredUntil, to);
    seen = true;
  }

  // One tick per month boundary inside the year, plus the year's own first
  // month at the origin.
  const first = new Date(yearStart * DAY);
  const months = [{ label: `T${first.getUTCMonth() + 1}`, left: 0 }];
  let year = first.getUTCFullYear();
  let month = first.getUTCMonth() + 1;
  for (;;) {
    if (month > 11) {
      month = 0;
      year += 1;
    }
    const day = Date.UTC(year, month, 1) / DAY;
    if (day > yearEnd) break;
    months.push({ label: `T${month + 1}`, left: percent(day) });
    month += 1;
  }

  return {
    startDate: toIso(yearStart),
    endDate: toIso(yearEnd),
    totalDays,
    months,
    terms,
    gaps,
    coverage: Math.min(1, covered / totalDays),
    todayLeft: today >= yearStart && today <= yearEnd ? percent(today) : null,
    incomplete,
  };
}

/** The five-step chart ramp from `theme.css`, so a term keeps its colour everywhere. */
export function termColor(index: number): string {
  return `var(--chart-${(index % 5) + 1})`;
}
