/**
 * The `fee_items` resource (khoản thu) as the API reports it — `FeeItemResponse`
 * on the backend. Kept in step by hand, like the rest of the API contract.
 */
export type FeeItem = {
  id: number;
  organization: { id: number; code: string; name: string };
  academicYear: { id: number; name: string };
  name: string;
  scope: FeeScope;
  /** Set only when the scope is `CLASS_GROUP`. */
  scopeClassGroup: Named | null;
  /** Set only when the scope is `CLASS`. */
  scopeClass: Named | null;
  priceMode: FeePriceMode;
  /** The one price, VAT included. Null unless the price mode is `SINGLE`. */
  basePrice: number | null;
  measurementUnit: Named;
  vatRate: number;
  nature: "REGULAR" | "COLLECT_ON_BEHALF";
  collectionCycle: FeeCollectionCycle;
  discountEligible: boolean;
  mandatory: boolean;
  serviceFee: boolean;
  refundable: boolean;
  invoiceEnabled: boolean;
  receiptEnabled: boolean;
  internalCollection: boolean;
  invoiceRate: number;
  active: boolean;
  prices: FeeItemPrice[];
  collectionSchedules: FeeCollectionSchedule[];
  createdAt: string;
  updatedAt: string | null;
  _can?: Record<string, boolean>;
};

type Named = { id: number; name: string };

export type FeeItemPrice = {
  id: number;
  classGroup: Named;
  areaName: string | null;
  amount: number;
};

export type FeeCollectionSchedule = {
  id: number;
  /** Which collection: the quarter, the period's order, or 1 for a year. */
  sequence: number;
  collectionMonth: number;
  academicPeriod: Named | null;
};

export type FeeScope = "ALL_SCHOOL" | "CLASS_GROUP" | "CLASS" | "UNASSIGNED";
export type FeePriceMode = "SINGLE" | "MULTIPLE";
export type FeeCollectionCycle = "MONTHLY" | "QUARTERLY" | "SEMESTER" | "ACADEMIC_YEAR";

export const FEE_SCOPE_LABELS: Record<FeeScope, string> = {
  ALL_SCHOOL: "Toàn trường",
  CLASS_GROUP: "Theo khối",
  CLASS: "Theo lớp",
  UNASSIGNED: "Chưa xếp lớp",
};

export const FEE_PRICE_MODE_LABELS: Record<FeePriceMode, string> = {
  SINGLE: "Một đơn giá",
  MULTIPLE: "Nhiều đơn giá",
};

export const FEE_CYCLE_LABELS: Record<FeeCollectionCycle, string> = {
  MONTHLY: "Tháng",
  QUARTERLY: "Quý",
  SEMESTER: "Học kỳ",
  ACADEMIC_YEAR: "Năm học",
};

/** The flags that read as rules, in the order a list shows them. */
export const FEE_RULE_FLAGS = [
  { key: "mandatory", label: "Bắt buộc" },
  { key: "serviceFee", label: "Dịch vụ" },
  { key: "discountEligible", label: "Miễn giảm" },
  { key: "invoiceEnabled", label: "Hóa đơn" },
  { key: "receiptEnabled", label: "Chứng từ" },
  { key: "refundable", label: "Hoàn trả" },
  { key: "internalCollection", label: "Thu nội bộ" },
] as const satisfies readonly { key: keyof FeeItem; label: string }[];

export type FeeRuleFlag = (typeof FEE_RULE_FLAGS)[number]["key"];

// ---- collection dates

/** The slice of a year the dates are worked out from. */
export type YearForSchedule = {
  startDate: string;
  endDate: string;
  periods: { id?: number; name: string; startDate: string; endDate: string }[];
};

/** One collection a cycle calls for, and the months it may fall due in. */
export type ScheduleTemplate = {
  sequence: number;
  /** "Quý 1", "Học kỳ 1", "Năm học". */
  label: string;
  months: number[];
  defaultMonth: number;
};

/** The months from one date to another, inclusive, by month number. Dates are `YYYY-MM-DD`. */
function monthsBetween(start: string, end: string): number[] {
  const [startYear, startMonth] = start.split("-").map(Number);
  const [endYear, endMonth] = end.split("-").map(Number);
  if (!startYear || !endYear) return [];
  const months: number[] = [];
  let year = startYear;
  let month = startMonth;
  while (year < endYear || (year === endYear && month <= endMonth)) {
    if (!months.includes(month)) months.push(month);
    month += 1;
    if (month > 12) {
      month = 1;
      year += 1;
    }
  }
  return months;
}

/**
 * The collections a cycle calls for. The same rule the API checks: a quarter
 * allows its own three months, a period the months it spans, a year the months of
 * the year. Monthly has none — it is collected every month.
 */
export function scheduleTemplates(
  cycle: FeeCollectionCycle,
  year: YearForSchedule | null | undefined
): ScheduleTemplate[] {
  if (!year) return [];
  switch (cycle) {
    case "MONTHLY":
      return [];
    case "QUARTERLY":
      return [1, 2, 3, 4].map((quarter) => {
        const first = (quarter - 1) * 3 + 1;
        return {
          sequence: quarter,
          label: `Quý ${quarter}`,
          months: [first, first + 1, first + 2],
          defaultMonth: first,
        };
      });
    case "SEMESTER":
      return [...year.periods]
        .sort((a, b) => a.startDate.localeCompare(b.startDate))
        .map((period, index) => {
          const months = monthsBetween(period.startDate, period.endDate);
          return {
            sequence: index + 1,
            label: period.name,
            months,
            defaultMonth: months[0] ?? 1,
          };
        });
    case "ACADEMIC_YEAR": {
      const months = monthsBetween(year.startDate, year.endDate);
      return [{ sequence: 1, label: "Năm học", months, defaultMonth: months[0] ?? 1 }];
    }
  }
}

// ---- what a list row says

export function scopeSummary(item: FeeItem): string {
  switch (item.scope) {
    case "CLASS_GROUP":
      return `Theo khối: ${item.scopeClassGroup?.name ?? ""}`;
    case "CLASS":
      return `Lớp ${item.scopeClass?.name ?? ""}`;
    default:
      return FEE_SCOPE_LABELS[item.scope];
  }
}

/** The collection months of an item, as `T9 · T1`; monthly has no months to list. */
export function scheduleSummary(item: FeeItem): string {
  if (item.collectionCycle === "MONTHLY") return "Hàng tháng";
  return item.collectionSchedules
    .map((schedule) => `T${schedule.collectionMonth}`)
    .join(" · ");
}

// ---- what the dialog holds

/** What the add and edit dialog holds. Numbers are text until they are sent. */
export type FeeItemFormValues = {
  name: string;
  scope: FeeScope;
  scopeClassGroupId: string;
  scopeClassId: string;
  priceMode: FeePriceMode;
  basePrice: string;
  measurementUnitId: string;
  vatRate: string;
  collectionCycle: FeeCollectionCycle;
  /** The month chosen for each collection, at its sequence minus one. */
  months: string[];
  discountEligible: boolean;
  mandatory: boolean;
  serviceFee: boolean;
  refundable: boolean;
  invoiceEnabled: boolean;
  receiptEnabled: boolean;
  internalCollection: boolean;
  invoiceRate: string;
  active: boolean;
  prices: { classGroupId: string; areaName: string; amount: string }[];
};
