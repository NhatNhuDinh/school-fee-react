import type {
  ClassRow,
  Collected,
  History,
  PaymentMethod,
  Receipt,
  StudentDebts,
  StudentHit,
  BankRow,
  CheckSummary,
  TransferRequest,
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

// ---- transfer requests (the QR) and bank lines

const post = <T>(path: string, json: unknown) => kyInstance.post(path, { json }).json<T>();

export function createTransferRequest(
  yearId: number,
  enrollmentId: number,
  receivableIds: number[]
): Promise<TransferRequest> {
  return post(`${BASE}/transfer-requests`, { academicYearId: yearId, enrollmentId, receivableIds });
}

export function getTransferRequest(yearId: number, id: number): Promise<TransferRequest> {
  return kyInstance
    .get(`${BASE}/transfer-requests/${id}`, { searchParams: { academicYearId: yearId } })
    .json<TransferRequest>();
}

export function waitingTransfers(yearId: number): Promise<TransferRequest[]> {
  return kyInstance
    .get(`${BASE}/transfer-requests`, { searchParams: { academicYearId: yearId } })
    .json<TransferRequest[]>();
}

export const deferTransfer = (yearId: number, id: number) =>
  post<TransferRequest>(`${BASE}/transfer-requests/${id}/defer`, { academicYearId: yearId });
export const cancelTransfer = (yearId: number, id: number) =>
  post<TransferRequest>(`${BASE}/transfer-requests/${id}/cancel`, { academicYearId: yearId });
export const confirmTransfer = (yearId: number, id: number) =>
  post<TransferRequest>(`${BASE}/transfer-requests/${id}/confirm`, { academicYearId: yearId });
export const checkTransfer = (yearId: number, id: number) =>
  post<{ summary: CheckSummary; request: TransferRequest }>(`${BASE}/transfer-requests/${id}/check`, {
    academicYearId: yearId,
  });

/** What narrows a list of bank lines: the date range, the search box and, optionally, one status. */
export type BankFilter = { from?: string; to?: string; q?: string; status?: string };

function filterParams(filter: BankFilter): Record<string, string> {
  const params: Record<string, string> = {};
  if (filter.from) params.from = filter.from;
  if (filter.to) params.to = filter.to;
  if (filter.q?.trim()) params.q = filter.q.trim();
  if (filter.status) params.status = filter.status;
  return params;
}

export function fetchBankRows(
  yearId: number,
  tab: "all" | "review" | "processed",
  filter: BankFilter = {}
): Promise<BankRow[]> {
  const searchParams = { academicYearId: yearId, tab, ...filterParams(filter) };
  return kyInstance.get(`${BASE}/bank-transactions`, { searchParams }).json<BankRow[]>();
}

export type PaymentSummary = { review: number; waiting: number; all: number; processed: number };

/**
 * `review` is every line still waiting for a person, whatever the filters; `all` and `processed` are the two tabs
 * of the review screen under the same date range and search as its list.
 */
export function fetchPaymentSummary(yearId: number, filter: BankFilter = {}): Promise<PaymentSummary> {
  const counted = { ...filter, status: undefined };
  return kyInstance
    .get(`${BASE}/bank-transactions/summary`, { searchParams: { academicYearId: yearId, ...filterParams(counted) } })
    .json<PaymentSummary>();
}

export const checkBank = (yearId: number, days?: number) =>
  post<CheckSummary>(`${BASE}/bank-transactions/check`, { academicYearId: yearId, days });
export const ignoreBankRow = (yearId: number, id: number, note: string) =>
  post<{ ok: true }>(`${BASE}/bank-transactions/${id}/ignore`, { academicYearId: yearId, note });
export const undoBankRow = (yearId: number, id: number) =>
  post<{ ok: true }>(`${BASE}/bank-transactions/${id}/undo`, { academicYearId: yearId });
export const assignBankRow = (yearId: number, id: number, enrollmentId: number, receivableIds: number[]) =>
  post<{ ok: true }>(`${BASE}/bank-transactions/${id}/assign`, { academicYearId: yearId, enrollmentId, receivableIds });

/** The result of a check, in one line a person can read. */
export function describeCheck(summary: CheckSummary): string {
  if (summary.fetched === 0) return "Không có giao dịch tiền vào trong khoảng đã kiểm tra";
  const parts = [`${summary.fetched} giao dịch`];
  if (summary.matched) parts.push(`${summary.matched} đã khớp và ghi nhận`);
  if (summary.needsReview) parts.push(`${summary.needsReview} cần rà soát`);
  if (summary.duplicates) parts.push(`${summary.duplicates} đã có từ trước`);
  return parts.join(" · ");
}
