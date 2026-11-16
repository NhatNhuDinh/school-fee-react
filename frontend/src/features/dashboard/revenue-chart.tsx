import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { DASHBOARD_WINDOWS, type Dashboard } from "@/domains/fee-payment/dashboard";
import { cn } from "@/shared/lib/utils";
import { compact, dayLabel, dong } from "./money";
import { Panel } from "./panel";

/**
 * Money in, day by day, split by how it arrived. Two soft areas rather than stacked columns: the
 * question this answers is "is it a busy week", and a smooth edge shows that faster than bars do.
 */
export function RevenueChart({
  data,
  days,
  onDays,
}: {
  data: Dashboard;
  days: number;
  onDays: (days: number) => void;
}) {
  const points = data.daily.map((d) => ({ ...d, label: dayLabel(d.date), total: d.cash + d.transfer }));
  const windowLabel = DASHBOARD_WINDOWS.find((w) => w.days === days)?.label ?? `${days} ngày`;

  return (
    <Panel
      title="Tiền thu theo ngày"
      action={
        <div className="bg-muted inline-flex rounded-lg p-0.5">
          {DASHBOARD_WINDOWS.map((w) => (
            <button
              key={w.days}
              type="button"
              onClick={() => onDays(w.days)}
              className={cn(
                "rounded-md px-3 py-1 text-xs font-medium transition-colors",
                w.days === days ? "bg-card text-foreground shadow-e1" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {w.label}
            </button>
          ))}
        </div>
      }
    >
      <div className="flex flex-wrap items-end gap-x-8 gap-y-2">
        <div>
          <div className="text-muted-foreground text-xs font-medium">Thu {windowLabel.toLowerCase()}</div>
          <div className="text-foreground text-3xl font-bold tracking-tight tabular-nums">
            {dong(data.cash + data.transfer)}
          </div>
        </div>
        <Legend color="var(--chart-1)" label="Chuyển khoản" value={dong(data.transfer)} />
        <Legend color="var(--gold-500)" label="Tiền mặt" value={dong(data.cash)} />
      </div>

      <div className="mt-4 h-64">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id="fill-transfer" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--chart-1)" stopOpacity={0.32} />
                <stop offset="100%" stopColor="var(--chart-1)" stopOpacity={0.02} />
              </linearGradient>
              <linearGradient id="fill-cash" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--gold-400)" stopOpacity={0.45} />
                <stop offset="100%" stopColor="var(--gold-400)" stopOpacity={0.03} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} stroke="var(--border)" />
            <XAxis
              dataKey="label"
              tickLine={false}
              axisLine={false}
              tickMargin={10}
              minTickGap={24}
              tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
            />
            <YAxis
              width={48}
              domain={[0, niceMax]}
              tickCount={5}
              allowDecimals={false}
              tickLine={false}
              axisLine={false}
              tickFormatter={compact}
              tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
            />
            <Tooltip cursor={{ stroke: "var(--primary)", strokeWidth: 1 }} content={<Tip />} />
            <Area
              type="monotone"
              dataKey="transfer"
              isAnimationActive={false}
              stroke="var(--chart-1)"
              strokeWidth={2.5}
              fill="url(#fill-transfer)"
              activeDot={{ r: 5, strokeWidth: 3, stroke: "var(--chart-1)", fill: "var(--card)" }}
            />
            <Area
              type="monotone"
              dataKey="cash"
              isAnimationActive={false}
              stroke="var(--gold-500)"
              strokeWidth={2.5}
              fill="url(#fill-cash)"
              activeDot={{ r: 5, strokeWidth: 3, stroke: "var(--gold-500)", fill: "var(--card)" }}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </Panel>
  );
}

/** A top for the axis that lands on a round step, so the ticks read 0, 5, 10, 15, 20 and not 0, 5, 10, 19. */
function niceMax(max: number): number {
  const top = Math.max(max, 1_000_000);
  const step = 10 ** Math.floor(Math.log10(top)) / 2;
  return Math.ceil(top / step) * step;
}

function Legend({ color, label, value }: { color: string; label: string; value: string }) {
  return (
    <div>
      <div className="flex items-center gap-1.5 text-xs font-medium" style={{ color }}>
        <span className="size-2 rounded-full" style={{ background: color }} />
        {label}
      </div>
      <div className="text-foreground text-base font-semibold tabular-nums">{value}</div>
    </div>
  );
}

type TipProps = {
  active?: boolean;
  payload?: { payload: { date: string; label: string; cash: number; transfer: number; total: number } }[];
};

/** Dark, like the sidebar: a tooltip is the one thing on the chart that should stand off it. */
function Tip({ active, payload }: TipProps) {
  const row = payload?.[0]?.payload;
  if (!active || !row) return null;
  return (
    <div className="bg-primary text-primary-foreground shadow-e3 rounded-lg px-3.5 py-2.5">
      <div className="text-xs opacity-75">{row.label}</div>
      <div className="mt-0.5 text-base font-bold tabular-nums">{dong(row.total)}</div>
      <div className="text-gold-400 mt-0.5 text-xs font-medium">
        CK {compact(row.transfer)} · TM {compact(row.cash)}
      </div>
    </div>
  );
}
