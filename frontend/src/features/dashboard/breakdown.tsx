import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, XAxis, YAxis } from "recharts";

import type { Dashboard } from "@/domains/fee-payment/dashboard";
import { cn } from "@/shared/lib/utils";
import { Donut, LegendRow } from "./donut";
import { compact, dong, percent, tone } from "./money";
import { Panel } from "./panel";

/** The ramp the khoản thu donut walks through: brand, gold, then the chart colours. */
const RAMP = ["var(--chart-1)", "var(--gold-400)", "var(--chart-3)", "var(--chart-5)", "var(--chart-4)"];

/** How much of the year is in, split by how it came in: the ring, and the three figures it is made of. */
export function ProgressDonut({ data }: { data: Dashboard }) {
  const { receivable, collected, outstanding, cashCollected, transferCollected } = data.totals;
  const done = receivable > 0 ? Math.min(100, (collected / receivable) * 100) : 0;
  const share = (n: number) => (receivable > 0 ? `${((n / receivable) * 100).toFixed(1).replace(".", ",")}%` : "0%");

  return (
    <Panel title="Tiến độ thu toàn trường">
      <div className="flex justify-center py-1">
        <Donut
          size={200}
          slices={[
            { name: "Chuyển khoản", value: transferCollected, color: "var(--chart-1)" },
            { name: "Tiền mặt", value: cashCollected, color: "var(--gold-400)" },
            { name: "Còn phải thu", value: outstanding, color: "var(--muted)" },
          ]}
        >
          <span className="text-foreground text-4xl font-bold tracking-tight">{done.toFixed(1).replace(".", ",")}%</span>
          <span className="text-muted-foreground text-xs">đã thu / kế hoạch</span>
        </Donut>
      </div>
      <ul className="mt-5 space-y-3">
        <LegendRow
          color="var(--chart-1)"
          label="Đã thu – chuyển khoản"
          value={`${dong(transferCollected)} · ${share(transferCollected)}`}
        />
        <LegendRow
          color="var(--gold-400)"
          label="Đã thu – tiền mặt"
          value={`${dong(cashCollected)} · ${share(cashCollected)}`}
        />
        <LegendRow color="var(--border)" label="Còn phải thu" value={`${dong(outstanding)} · ${share(outstanding)}`} />
      </ul>
    </Panel>
  );
}

/** The latest đợt and how far each has got: bar colour says whether it is healthy, behind, or done. */
export function BatchProgress({ data }: { data: Dashboard }) {
  return (
    <Panel title="Tiến độ theo đợt thu">
      {data.batches.length === 0 ? (
        <Empty>Chưa có đợt thu nào</Empty>
      ) : (
        <ul className="space-y-4">
          {data.batches.map((batch) => {
            const pct = percent(batch.collected, batch.receivable);
            return (
              <li key={batch.id}>
                <div className="mb-1.5 flex items-baseline justify-between gap-3 text-sm">
                  <span className="text-foreground min-w-0 truncate font-medium">{batch.name}</span>
                  <span className={cn("shrink-0 font-semibold tabular-nums", toneText(tone(pct)))}>{pct}%</span>
                </div>
                <Bar2 pct={pct} tone={tone(pct)} />
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}

/** What the year asks for, by khoản thu. */
export function ItemsDonut({ data }: { data: Dashboard }) {
  const total = data.items.reduce((sum, item) => sum + item.amount, 0);
  return (
    <Panel title="Cơ cấu theo khoản thu">
      {data.items.length === 0 ? (
        <Empty>Chưa có khoản thu</Empty>
      ) : (
        <div className="flex flex-wrap items-center gap-6">
          <Donut
            size={168}
            slices={data.items.map((item, i) => ({ name: item.name, value: item.amount, color: RAMP[i % RAMP.length] }))}
          >
            <span className="text-foreground text-xl font-bold">{compact(total)}</span>
            <span className="text-muted-foreground text-[11px]">phải thu</span>
          </Donut>
          <ul className="min-w-40 flex-1 space-y-3">
            {data.items.map((item, i) => (
              <LegendRow
                key={item.name}
                color={RAMP[i % RAMP.length]}
                label={item.name}
                value={`${percent(item.amount, total)}%`}
              />
            ))}
          </ul>
        </div>
      )}
    </Panel>
  );
}

/** Collected against still owed, one column per khối. */
export function GroupBars({ data }: { data: Dashboard }) {
  const rows = data.groups.map((g) => ({
    name: g.name,
    collected: g.collected,
    owed: Math.max(0, g.receivable - g.collected),
    pct: percent(g.collected, g.receivable),
  }));
  return (
    <Panel
      title="Thu theo khối lớp"
      action={
        <div className="text-muted-foreground flex items-center gap-3 text-xs">
          <Key color="var(--primary)" label="Đã thu" />
          <Key color="var(--muted)" label="Còn nợ" />
        </div>
      }
    >
      {rows.length === 0 ? (
        <Empty>Chưa có số liệu theo khối</Empty>
      ) : (
        <div className="h-56">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={rows} margin={{ top: 22, right: 4, bottom: 0, left: 0 }} barCategoryGap="28%">
              <CartesianGrid vertical={false} stroke="var(--border)" />
              <XAxis
                dataKey="name"
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                tick={{ fontSize: 12, fill: "var(--muted-foreground)" }}
              />
              <YAxis hide />
              <Bar dataKey="collected" stackId="s" fill="var(--primary)" isAnimationActive={false} />
              <Bar dataKey="owed" stackId="s" fill="var(--muted)" radius={[8, 8, 0, 0]} isAnimationActive={false}>
                <LabelList
                  content={(props) => {
                    const { x, y, width, index } = props as { x: number; y: number; width: number; index: number };
                    return (
                      <text
                        x={x + width / 2}
                        y={y - 8}
                        textAnchor="middle"
                        className="fill-foreground text-[13px] font-semibold"
                      >
                        {rows[index]?.pct}%
                      </text>
                    );
                  }}
                />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </Panel>
  );
}

function Key({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="size-2.5 rounded-sm" style={{ background: color }} />
      {label}
    </span>
  );
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <div className="text-muted-foreground flex flex-1 items-center justify-center py-10 text-sm">{children}</div>;
}

const TONE_BAR = {
  success: "bg-success",
  primary: "bg-primary",
  warning: "bg-gold-400",
} as const;

const TONE_TEXT = {
  success: "text-success-text",
  primary: "text-foreground",
  warning: "text-warning-text",
} as const;

const toneText = (t: keyof typeof TONE_TEXT) => TONE_TEXT[t];

/** A thin bar on a muted track; the fill is rounded and keeps its end even at a few percent. */
export function Bar2({ pct, tone: t }: { pct: number; tone: keyof typeof TONE_BAR }) {
  return (
    <div className="bg-muted h-2 w-full overflow-hidden rounded-full">
      <div className={cn("h-full rounded-full", TONE_BAR[t])} style={{ width: `${Math.max(pct, pct > 0 ? 3 : 0)}%` }} />
    </div>
  );
}
