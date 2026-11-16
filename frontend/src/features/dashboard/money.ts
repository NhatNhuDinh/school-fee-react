import { formatNumber } from "@/shared/lib/format";

/** `1.534.528 VND`. */
export const dong = (value: number) => `${formatNumber(Math.round(value))} VND`;

/**
 * The short form an axis or a tooltip has room for: `142tr`, `3,5 tỷ`. Under a million it is the
 * plain number, because "0,4tr" reads worse than "400.000".
 */
export function compact(value: number): string {
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) return `${trim(value / 1_000_000_000)} tỷ`;
  if (abs >= 1_000_000) return `${trim(value / 1_000_000)}tr`;
  return formatNumber(Math.round(value));
}

function trim(value: number): string {
  const rounded = Math.round(value * 10) / 10;
  return rounded.toLocaleString("vi-VN", { maximumFractionDigits: 1 });
}

/** A share as a whole percent, never over 100 and never negative. */
export function percent(part: number, whole: number): number {
  if (whole <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((part / whole) * 100)));
}

const WEEKDAY = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];

/** `T2 10/11` from `2026-11-10`, read as a calendar date so no timezone moves it a day. */
export function dayLabel(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  const weekday = WEEKDAY[new Date(y, m - 1, d).getDay()];
  return `${weekday} ${String(d).padStart(2, "0")}/${String(m).padStart(2, "0")}`;
}

/** Whether a share of what is owed is healthy (green), middling (brand) or behind (warning). */
export function tone(pct: number): "success" | "primary" | "warning" {
  if (pct >= 90) return "success";
  if (pct >= 50) return "primary";
  return "warning";
}
