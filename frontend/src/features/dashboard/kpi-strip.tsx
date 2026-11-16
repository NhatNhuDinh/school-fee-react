import { Bar, BarChart, Line, LineChart, ResponsiveContainer, YAxis } from "recharts";
import { useNavigate } from "react-router";

import type { Dashboard } from "@/domains/fee-payment/dashboard";
import { formatNumber } from "@/shared/lib/format";
import { dong } from "./money";

/**
 * The four numbers a cashier looks at first, as one strip rather than four boxes: a hairline between
 * them, a small trend under each. The trends are the last days' receipts, so they move when money does.
 */
export function KpiStrip({ data }: { data: Dashboard }) {
  const navigate = useNavigate();
  const { totals, daily, batches } = data;

  // Cumulative collected over the window; what is owed is whatever the window had left to collect, run backwards.
  let running = 0;
  const windowTotal = daily.reduce((sum, d) => sum + d.cash + d.transfer, 0);
  const collected = daily.map((d) => {
    running += d.cash + d.transfer;
    return { v: running };
  });
  const outstanding = collected.map((point) => ({ v: Math.max(0, totals.outstanding + (windowTotal - point.v)) }));
  const receivable =
    batches.length >= 2
      ? [...batches].reverse().map((b) => ({ v: b.receivable }))
      : [{ v: totals.receivable }, { v: totals.receivable }];
  const receipts = daily.slice(-14).map((d) => ({ v: d.receipts }));

  return (
    <div className="bg-card grid divide-y overflow-hidden rounded-xl border sm:grid-cols-2 sm:divide-y-0 lg:grid-cols-4 lg:divide-x">
      <Metric label="Tổng phải thu" value={dong(totals.receivable)}>
        <Trend points={receivable} color="var(--chart-1)" />
      </Metric>
      <Metric label="Đã thu" value={dong(totals.collected)}>
        <Trend points={collected} color="var(--gold-500)" />
      </Metric>
      <Metric label="Còn nợ" value={dong(totals.outstanding)}>
        <Trend points={outstanding} color="var(--warning)" />
      </Metric>
      <Metric
        label="Cần rà soát"
        value={`${formatNumber(totals.review)} giao dịch`}
        onClick={() => navigate("/fee_payments/review")}
      >
        <Bars points={receipts} />
      </Metric>
    </div>
  );
}

function Metric({
  label,
  value,
  onClick,
  children,
}: {
  label: string;
  value: string;
  onClick?: () => void;
  children: React.ReactNode;
}) {
  const body = (
    <>
      <div className="text-muted-foreground text-xs font-medium">{label}</div>
      <div className="text-foreground mt-1 text-2xl font-semibold tracking-tight tabular-nums">{value}</div>
      <div className="mt-3 h-10">{children}</div>
    </>
  );
  return onClick ? (
    <button type="button" onClick={onClick} className="hover:bg-accent/40 p-5 text-left transition-colors">
      {body}
    </button>
  ) : (
    <div className="p-5">{body}</div>
  );
}

function Trend({ points, color }: { points: { v: number }[]; color: string }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={points} margin={{ top: 4, right: 2, bottom: 4, left: 2 }}>
        <YAxis hide domain={["dataMin", "dataMax"]} />
        <Line type="monotone" dataKey="v" stroke={color} strokeWidth={2} dot={false} isAnimationActive={false} />
      </LineChart>
    </ResponsiveContainer>
  );
}

function Bars({ points }: { points: { v: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={points} margin={{ top: 4, right: 2, bottom: 0, left: 2 }} barCategoryGap={4}>
        <YAxis hide domain={[0, "dataMax"]} />
        <Bar dataKey="v" fill="var(--gold-400)" radius={3} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  );
}
