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

/** A transfer asked for at the counter: one code, one amount, paid when the bank reports it. */
export type TransferStatus = "PENDING" | "DEFERRED" | "PAID" | "CANCELLED" | "EXPIRED";

export type TransferRequest = {
  id: number;
  enrollmentId: number;
  studentCode: string;
  studentName: string;
  className: string | null;
  orderCode: string;
  amount: number;
  status: TransferStatus;
  createdAt: string;
  expiresAt: string;
  paidAt: string | null;
  paidBy: "BANK" | "MANUAL" | null;
  createdByName: string | null;
  transfer: TransferInfo | null;
  receipts: Receipt[];
};

/** What "Kiểm tra giao dịch" found. */
export type CheckSummary = {
  fetched: number;
  created: number;
  matched: number;
  needsReview: number;
  duplicates: number;
};

export type BankRow = {
  id: number;
  time: string;
  amount: number;
  content: string | null;
  code: string | null;
  referenceCode: string | null;
  sepayId: number | null;
  source: "WEBHOOK" | "API_CHECK";
  status: "MATCHED" | "NEEDS_REVIEW" | "IGNORED" | "RESOLVED";
  reason: string | null;
  note: string | null;
  suggestedEnrollmentId: number | null;
  suggestedStudentCode: string | null;
  suggestedStudentName: string | null;
  suggestedClassName: string | null;
  handledBy: string | null;
  handledAt: string | null;
};

/** Why a bank line is waiting for a person, in the words the review screen uses. */
export const REASON_LABEL: Record<string, string> = {
  NO_CODE: "Không có mã thanh toán",
  UNKNOWN_CODE: "Không tìm thấy mã thanh toán",
  AMBIGUOUS_CODE: "Mã khớp nhiều yêu cầu",
  AMOUNT_MISMATCH: "Lệch số tiền",
  ALREADY_PAID: "Đã thanh toán trước đó (thừa)",
  REQUEST_CANCELLED: "Yêu cầu đã hủy",
  REQUEST_EXPIRED: "Yêu cầu đã hết hạn",
  DEBT_CHANGED: "Số nợ đã thay đổi",
  PROCESSING_ERROR: "Xử lý tự động thất bại",
  NOT_INCOMING: "Tiền ra",
  WRONG_ACCOUNT: "Không phải tài khoản thu",
  MANUAL_IGNORED: "Đã bỏ qua",
  MANUAL_ASSIGNED: "Đã gán thủ công",
};
