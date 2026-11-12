/**
 * The `fee_collection_batches` resource (đợt thu) as the API reports it —
 * `FeeCollectionBatchResponse` and its siblings on the backend. Kept in step by hand,
 * like the rest of the API contract.
 */
export type FeeCollectionBatch = {
  id: number;
  academicYear: { id: number; name: string };
  name: string;
  /** 1–12. */
  collectionMonth: number;
  scope: FeeCollectionScope;
  /** Set only when the scope is `CLASS_GROUP`. */
  scopeClassGroup: Named | null;
  /** Set only when the scope is `CLASS`. */
  scopeClass: Named | null;
  feeItemCount: number;
  studentCount: number;
  receivableAmount: number;
  collectedAmount: number;
  /** Read off the money by the API; never set by a person. */
  status: FeeCollectionBatchStatus;
  createdAt: string;
  updatedAt: string | null;
  _can?: Record<string, boolean>;
};

type Named = { id: number; name: string };

export type FeeCollectionScope = "ALL_SCHOOL" | "CLASS_GROUP" | "CLASS";
export type FeeCollectionBatchStatus = "NOT_STARTED" | "COLLECTING" | "COMPLETED";

export const BATCH_SCOPE_LABELS: Record<FeeCollectionScope, string> = {
  ALL_SCHOOL: "Toàn trường",
  CLASS_GROUP: "Theo khối",
  CLASS: "Theo lớp",
};

export const BATCH_STATUS_LABELS: Record<FeeCollectionBatchStatus, string> = {
  NOT_STARTED: "Chưa thu",
  COLLECTING: "Đang thu",
  COMPLETED: "Hoàn thành",
};

/** What a đợt is for, in a few words: "Toàn trường", "Khối 10", "Lớp 10A1". */
export function batchScopeName(batch: Pick<FeeCollectionBatch, "scope" | "scopeClassGroup" | "scopeClass">): string {
  switch (batch.scope) {
    case "CLASS_GROUP":
      return batch.scopeClassGroup?.name ?? "Theo khối";
    case "CLASS":
      return batch.scopeClass ? `Lớp ${batch.scopeClass.name}` : "Theo lớp";
    default:
      return "Toàn trường";
  }
}

/** Share of the receivable that has been collected, 0–100, whole percent. */
export function collectedPercent(receivable: number, collected: number): number {
  if (!receivable || receivable <= 0) return 0;
  return Math.min(100, Math.round((collected / receivable) * 100));
}

/** One khoản thu of a đợt, with what it comes to over the students charged for it. */
export type FeeCollectionBatchItem = {
  /** The id of the đợt's own row; the key of a student's `amounts`. */
  id: number;
  feeItemId: number;
  name: string;
  unitName: string;
  mandatory: boolean;
  quantity: number;
  sequence: number;
  studentCount: number;
  /** Differ only for a khoản thu priced by khối or area. */
  minUnitPrice: number | null;
  maxUnitPrice: number | null;
  receivableAmount: number;
  collectedAmount: number;
};

/** One student of a đợt, with what they owe. A khoản thu that does not apply is absent from `amounts`. */
export type FeeCollectionBatchStudent = {
  id: number;
  enrollmentId: number;
  studentCode: string;
  fullName: string;
  schoolClassId: number;
  schoolClassName: string;
  receivableAmount: number;
  collectedAmount: number;
  amounts: Record<string, { receivable: number; collected: number }>;
};

export type FeeCollectionBatchStudents = {
  rows: FeeCollectionBatchStudent[];
  total: number;
};

/** One khoản thu that could go in a đợt, as the preview lists it. */
export type PreviewCandidate = {
  feeItemId: number;
  name: string;
  unitName: string;
  mandatory: boolean;
  scope: "ALL_SCHOOL" | "CLASS_GROUP" | "CLASS" | "UNASSIGNED";
  scopeName: string | null;
  applicableStudents: number;
  minUnitPrice: number | null;
  maxUnitPrice: number | null;
  studentsWithoutPrice: number;
  /** False for a unit that counts periods (tháng, quý, kỳ, năm): the quantity is always 1. */
  quantityEditable: boolean;
  selected: boolean;
  quantity: number;
  amount: number;
};

export type FeeCollectionBatchPreview = {
  studentCount: number;
  itemCount: number;
  receivableAmount: number;
  candidates: PreviewCandidate[];
  problems: string[];
};

export type SyncResult = {
  added: number;
  removed: number;
  keptBecauseCollected: number;
};

/** What a khoản thu is worth per student, for a row's "Đơn giá" cell. */
export function priceRange(min: number | null, max: number | null): "none" | "single" | "range" {
  if (min === null || max === null) return "none";
  return min === max ? "single" : "range";
}

/** The months of a school year in the order a school lives them: September first. */
export const SCHOOL_MONTHS = [9, 10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8] as const;

/** A sensible default name for a new đợt: "Đợt thu tháng 10/2026". */
export function defaultBatchName(month: number, yearName: string): string {
  const [from, to] = yearName.split("-").map(Number);
  // September to December belong to the year the school year starts in, January to August to the next.
  const calendarYear = month >= 9 ? from : to;
  return calendarYear ? `Đợt thu tháng ${month}/${calendarYear}` : `Đợt thu tháng ${month}`;
}

/** The lines the API takes: the khoản thu chosen and how many of each. */
export type BatchItemLine = { feeItemId: number; quantity: number };
