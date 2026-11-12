import { DetailPanel } from "@/shared/components/views/record-detail";
import { cn } from "@/shared/lib/utils";
import { formatDate } from "@/shared/lib/format";
import type { YearTimeline } from "@/domains/academic-year/timeline";

type Tone = "info" | "warning" | "destructive";

const TONES: Record<Tone, string> = {
  info: "bg-info-subtle text-info-text",
  warning: "bg-warning-subtle text-warning-text",
  destructive: "bg-destructive-subtle text-destructive-text",
};

/**
 * What is worth telling somebody about the terms they have laid out.
 *
 * These mirror what the API refuses ("inside the year", "no overlap"), so the
 * form says so while the dates are being typed rather than after Save. Gaps are
 * only reported, never refused: a break between terms is normal.
 */
function notices(timeline: YearTimeline | null): { tone: Tone; text: string }[] {
  if (!timeline) {
    return [{ tone: "info", text: "Nhập hai ngày của năm học để bắt đầu." }];
  }
  const out: { tone: Tone; text: string }[] = [];
  if (timeline.terms.length === 0 && timeline.incomplete === 0) {
    out.push({ tone: "info", text: "Chưa có học kỳ nào." });
  }
  if (timeline.incomplete > 0) {
    out.push({
      tone: "warning",
      text: `${timeline.incomplete} học kỳ chưa đủ ngày hợp lệ nên chưa được vẽ.`,
    });
  }
  for (const term of timeline.terms.filter((t) => t.outside)) {
    out.push({ tone: "destructive", text: `${term.name} nằm ngoài năm học.` });
  }
  for (const term of timeline.terms.filter((t) => t.overlaps)) {
    out.push({ tone: "destructive", text: `${term.name} trùng với học kỳ khác.` });
  }
  for (const gap of timeline.gaps.slice(0, 3)) {
    out.push({
      tone: "info",
      text: `Nghỉ ${gap.days} ngày giữa các học kỳ (${formatDate(
        gap.startDate
      )} – ${formatDate(gap.endDate)}).`,
    });
  }
  return out;
}

type Props = {
  /** The derived name, e.g. `2026-2027`. */
  name: string;
  /** Left out when the school is not known yet (a system administrator is still choosing). */
  school?: string;
  timeline: YearTimeline | null;
  className?: string;
};

/** The live summary beside the year form: the answer to "what will this save". */
export function YearSummary({ name, school, timeline, className }: Props) {
  const rows: [string, string][] = timeline
    ? [
        ["Thời lượng", `${timeline.totalDays} ngày`],
        ["Số học kỳ", String(timeline.terms.length)],
        ["Phủ năm học", `${Math.round(timeline.coverage * 100)}%`],
      ]
    : [];

  return (
    <DetailPanel
      title="Tóm tắt"
      className={cn("@container", className)}
      bodyClassName="space-y-4 p-4"
    >
      <div className="min-w-0">
        <div className="text-2xl font-bold tracking-tight">{name}</div>
        {school && (
          <div className="text-muted-foreground truncate text-sm">{school}</div>
        )}
      </div>

      {timeline && (
        <>
          <dl className="grid grid-cols-3 gap-3 border-t pt-4 @xs:grid-cols-1 @xs:gap-2">
            {rows.map(([label, value]) => (
              <div
                key={label}
                className="flex min-w-0 flex-col gap-0.5 @xs:flex-row @xs:items-baseline @xs:justify-between"
              >
                <dt className="text-muted-foreground text-xs @xs:text-sm">{label}</dt>
                <dd className="text-sm font-semibold">{value}</dd>
              </div>
            ))}
          </dl>
          <div
            role="progressbar"
            aria-label="Phần năm học nằm trong các học kỳ"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(timeline.coverage * 100)}
            className="bg-muted h-2 overflow-hidden rounded-full"
          >
            <div
              className="bg-primary h-full rounded-full"
              style={{ width: `${timeline.coverage * 100}%` }}
            />
          </div>
        </>
      )}

      <ul className="space-y-2">
        {notices(timeline).map((n, i) => (
          <li key={i} className={cn("rounded-md px-3 py-2 text-xs leading-5", TONES[n.tone])}>
            {n.text}
          </li>
        ))}
      </ul>
    </DetailPanel>
  );
}
