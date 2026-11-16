import { useNavigate } from "react-router";

import type { Dashboard } from "@/domains/fee-payment/dashboard";
import { PAYMENT_METHOD_LABEL } from "@/domains/fee-payment/types";
import { cn } from "@/shared/lib/utils";
import { Bar2, Empty } from "./breakdown";
import { dong } from "./money";
import { Panel } from "./panel";

/** First letter of the first and last word: "Bùi Tuấn Kiệt" is "BK". */
function initials(name: string): string {
  const words = name.trim().split(/\s+/);
  return (words.length > 1 ? words[0][0] + words[words.length - 1][0] : words[0]?.slice(0, 2) ?? "?").toUpperCase();
}

const AVATAR = [
  "bg-info-subtle text-info-text",
  "bg-warning-subtle text-warning-text",
  "bg-success-subtle text-success-text",
  "bg-gold-subtle text-gold-text",
  "bg-accent text-accent-foreground",
];

const time = (iso: string) => new Date(iso).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });

/** The last receipts, newest first: who paid, how, how much, and when. */
export function RecentReceipts({ data }: { data: Dashboard }) {
  const navigate = useNavigate();
  return (
    <Panel
      title="Phiếu thu gần đây"
      action={
        <button
          type="button"
          onClick={() => navigate("/fee_payments/history")}
          className="text-brand text-xs font-medium hover:underline"
        >
          Xem tất cả →
        </button>
      }
    >
      {data.recent.length === 0 ? (
        <Empty>Chưa có phiếu thu nào</Empty>
      ) : (
        <ul className="divide-y">
          {data.recent.map((receipt, i) => (
            <li key={receipt.id} className="flex items-center gap-3.5 py-3 first:pt-0 last:pb-0">
              <span
                className={cn(
                  "flex size-9 shrink-0 items-center justify-center rounded-full text-xs font-semibold",
                  AVATAR[i % AVATAR.length],
                )}
              >
                {initials(receipt.studentName)}
              </span>
              <div className="min-w-0 flex-1">
                <div className="text-foreground truncate text-sm font-semibold">{receipt.studentName}</div>
                <div className="text-muted-foreground truncate text-xs">{receipt.className}</div>
              </div>
              <span
                className={cn(
                  "shrink-0 rounded-full px-2.5 py-1 text-[11px] font-medium",
                  receipt.method === "TRANSFER" ? "bg-info-subtle text-info-text" : "bg-gold-subtle text-gold-text",
                )}
              >
                {PAYMENT_METHOD_LABEL[receipt.method]}
              </span>
              <span className="text-foreground w-32 shrink-0 text-right text-sm font-semibold tabular-nums">
                {dong(receipt.amount)}
              </span>
              <span className="text-muted-foreground w-12 shrink-0 text-right text-xs tabular-nums">
                {time(receipt.createdAt)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

/** The classes owing the most, as bars against the worst of them. */
export function TopDebts({ data }: { data: Dashboard }) {
  const worst = Math.max(...data.classes.map((c) => c.outstanding), 1);
  return (
    <Panel title="Lớp còn nợ nhiều nhất">
      {data.classes.length === 0 ? (
        <Empty>Không có lớp nào còn nợ</Empty>
      ) : (
        <ul className="space-y-4">
          {data.classes.map((row, i) => (
            <li key={row.name}>
              <div className="mb-1.5 flex items-baseline justify-between gap-3 text-sm">
                <span className="text-foreground font-medium">
                  <span className="text-muted-foreground mr-3 tabular-nums">{i + 1}</span>
                  {row.name}
                </span>
                <span className={cn("font-semibold tabular-nums", i < 2 ? "text-warning-text" : "text-foreground")}>
                  {dong(row.outstanding)}
                </span>
              </div>
              <Bar2 pct={Math.round((row.outstanding / worst) * 100)} tone={i < 2 ? "warning" : "primary"} />
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}
