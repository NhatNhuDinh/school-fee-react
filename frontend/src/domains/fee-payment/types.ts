/** Payload shapes of `/api/fee_payments`. Money is always taken whole, so no request carries an amount. */

export type PaymentMethod = "CASH" | "TRANSFER";

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  CASH: "Tiền mặt",
  TRANSFER: "Chuyển khoản",
};

export type StudentHit = {
  enrollmentId: number;
  studentCode: string;
  fullName: string;
  dateOfBirth: string | null;
  className: string;
  classGroupName: string;
  outstanding: number;
};

export type DebtLine = {
  receivableId: number;
  itemName: string;
  unitName: string | null;
  quantity: number;
  receivable: number;
  collected: number;
  outstanding: number;
};

export type BatchDebt = {
  batchId: number;
  batchName: string;
  scopeLabel: string;
  batchStudentId: number;
  receivable: number;
  collected: number;
  outstanding: number;
  lines: DebtLine[];
};

export type StudentDebts = {
  enrollmentId: number;
  studentCode: string;
  fullName: string;
  className: string | null;
  classGroupName: string | null;
  batches: BatchDebt[];
};

export type TransferInfo = {
  configured: boolean;
  bank: string | null;
  accountNumber: string | null;
  accountHolder: string | null;
  content: string;
  amount: number;
  qrUrl: string | null;
};

export type ClassRow = {
  classId: number;
  className: string;
  classGroupName: string;
  studentCount: number;
  owingStudentCount: number;
  receivable: number;
  collected: number;
  outstanding: number;
};

export type ReceiptLine = {
  itemName: string;
  unitName: string | null;
  quantity: number;
  amount: number;
};

export type Receipt = {
  id: number;
  receiptNo: number;
  receiptCode: string;
  batchId: number;
  batchName: string;
  studentCode: string;
  studentName: string;
  className: string;
  method: PaymentMethod;
  amount: number;
  paidOn: string;
  status: "ACTIVE" | "CANCELLED";
  collectorName: string | null;
  transferContent: string | null;
  cancelledByName: string | null;
  lines: ReceiptLine[];
};

export type Collected = { receipts: Receipt[]; total: number; studentCount: number };

export type History = { rows: Receipt[]; total: number; amount: number };

/** Where a class stands, read off its money. */
export function classStatus(row: Pick<ClassRow, "receivable" | "collected" | "outstanding">): "none" | "partial" | "paid" {
  if (row.outstanding <= 0 && row.receivable > 0) return "paid";
  if (row.collected <= 0) return "none";
  return "partial";
}
