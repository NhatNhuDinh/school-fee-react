import type {
  ClassRow,
  Collected,
  History,
  PaymentMethod,
  Receipt,
  StudentDebts,
  StudentHit,
  TransferInfo,
} from "@/domains/fee-payment/types";
import { kyInstance } from "@/shared/api/data";

// The ky instance already carries the API root as its prefix, so paths here are relative.
const BASE = "fee_payments";

export type BatchOption = { id: number; name: string };

/** The year's đợt thu, newest first, for the pickers. */
export function fetchBatches(yearId: number): Promise<BatchOption[]> {
  return kyInstance
    .get("fee_collection_batches", {
      searchParams: { "academicYear.id": yearId, _start: 0, _end: 200, _sort: "createdAt", _order: "desc" },
    })
    .json<BatchOption[]>();
}

export function searchStudents(yearId: number, q: string): Promise<StudentHit[]> {
  return kyInstance.get(`${BASE}/students`, { searchParams: { academicYearId: yearId, q } }).json<StudentHit[]>();
}

export function fetchDebts(yearId: number, enrollmentId: number): Promise<StudentDebts> {
  return kyInstance
    .get(`${BASE}/students/${enrollmentId}/debts`, { searchParams: { academicYearId: yearId } })
    .json<StudentDebts>();
}

export function fetchTransferInfo(yearId: number, amount: number): Promise<TransferInfo> {
  return kyInstance.get(`${BASE}/transfer-info`, { searchParams: { academicYearId: yearId, amount } }).json<TransferInfo>();
}

export function collectStudent(input: {
  academicYearId: number;
  enrollmentId: number;
  method: PaymentMethod;
  paidOn: string;
  receivableIds: number[];
  transferContent?: string;
}): Promise<Collected> {
  return kyInstance.post(`${BASE}/collect-student`, { json: input }).json<Collected>();
}

export function fetchClasses(yearId: number, batchId: number): Promise<ClassRow[]> {
  return kyInstance.get(`${BASE}/classes`, { searchParams: { academicYearId: yearId, batchId } }).json<ClassRow[]>();
}

export function collectClasses(input: {
  academicYearId: number;
  batchId: number;
  classIds: number[];
  paidOn: string;
}): Promise<Collected> {
  return kyInstance.post(`${BASE}/collect-classes`, { json: input }).json<Collected>();
}

export function fetchHistory(
  yearId: number,
  filter: { from?: string; to?: string; batchId?: number | null; q?: string }
): Promise<History> {
  const searchParams: Record<string, string | number> = { academicYearId: yearId };
  if (filter.from) searchParams.from = filter.from;
  if (filter.to) searchParams.to = filter.to;
  if (filter.batchId) searchParams.batchId = filter.batchId;
  if (filter.q?.trim()) searchParams.q = filter.q.trim();
  return kyInstance.get(`${BASE}/history`, { searchParams }).json<History>();
}

export function fetchReceipts(yearId: number, ids: number[]): Promise<Receipt[]> {
  return kyInstance
    .get(`${BASE}/receipts`, { searchParams: { academicYearId: yearId, ids: ids.join(",") } })
    .json<Receipt[]>();
}

export function cancelReceipts(yearId: number, ids: number[]): Promise<{ cancelled: number }> {
  return kyInstance.post(`${BASE}/cancel`, { json: { academicYearId: yearId, ids } }).json<{ cancelled: number }>();
}

/** Today as the `yyyy-MM-dd` string the API takes, in the browser's zone. */
export function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

/** What went wrong, in the API's own words when it gave them. */
export function errorMessage(error: unknown): string {
  const e = error as { message?: string; errors?: Record<string, unknown> } | null;
  return e?.message ?? "Không thực hiện được, vui lòng thử lại";
}
