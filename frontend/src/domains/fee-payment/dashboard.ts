/** Payload of `GET /api/fee_payments/dashboard`: the numbers on the home page. */

export type DashboardTotals = {
  receivable: number;
  collected: number;
  outstanding: number;
  /** Collected so far this year, by how it came in. */
  cashCollected: number;
  transferCollected: number;
  batches: number;
  studentsOwing: number;
  /** Bank lines waiting for a person. */
  review: number;
};

/** One day of receipts; days with none are present with zeros, so a chart has no gaps. */
export type DashboardDay = {
  date: string;
  cash: number;
  transfer: number;
  receipts: number;
};

export type DashboardBatch = { id: number; name: string; receivable: number; collected: number };
export type DashboardSlice = { name: string; amount: number };
export type DashboardGroup = { name: string; receivable: number; collected: number };
export type DashboardClassDebt = { name: string; outstanding: number };

export type DashboardRecent = {
  id: number;
  receiptNo: number;
  studentName: string;
  className: string;
  method: "CASH" | "TRANSFER";
  amount: number;
  createdAt: string;
};

export type Dashboard = {
  totals: DashboardTotals;
  /** Collected inside the chosen window of days. */
  cash: number;
  transfer: number;
  daily: DashboardDay[];
  batches: DashboardBatch[];
  items: DashboardSlice[];
  groups: DashboardGroup[];
  classes: DashboardClassDebt[];
  recent: DashboardRecent[];
};

/** The windows the revenue chart offers, in days. */
export const DASHBOARD_WINDOWS = [
  { days: 7, label: "7 ngày" },
  { days: 30, label: "30 ngày" },
  { days: 120, label: "Học kỳ" },
] as const;
