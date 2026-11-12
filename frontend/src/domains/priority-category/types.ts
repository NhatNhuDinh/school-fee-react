/**
 * The `priority_categories` resource (diện ưu tiên) as the API reports it —
 * `PriorityCategoryResponse` on the backend. Kept in step by hand, like the rest
 * of the API contract.
 */
export type PriorityCategory = {
  id: number;
  organization: { id: number; code: string; name: string };
  academicYear: { id: number; name: string };
  name: string;
  discountType: DiscountType;
  discountValue: number;
  applicationPeriod: ApplicationPeriod;
  /** Only set for a `CUSTOM` period; null otherwise. */
  fromMonth: number | null;
  toMonth: number | null;
  createdAt: string;
  updatedAt: string | null;
};

export type DiscountType = "PERCENT" | "AMOUNT";

export type ApplicationPeriod =
  | "FULL_YEAR"
  | "SEMESTER_ONE"
  | "SEMESTER_TWO"
  | "CUSTOM";

export const DISCOUNT_TYPE_LABELS: Record<DiscountType, string> = {
  PERCENT: "Phần trăm (%)",
  AMOUNT: "Số tiền (đ)",
};

export const APPLICATION_PERIOD_LABELS: Record<ApplicationPeriod, string> = {
  FULL_YEAR: "Cả năm",
  SEMESTER_ONE: "Học kỳ I",
  SEMESTER_TWO: "Học kỳ II",
  CUSTOM: "Tùy chọn",
};

/** What the create and edit forms hold. Numbers are strings until submitted. */
export type PriorityCategoryFormValues = {
  academicYear: { id: string };
  name: string;
  discountType: DiscountType;
  discountValue: string;
  applicationPeriod: ApplicationPeriod;
  fromMonth: string;
  toMonth: string;
};

/** `50 %` or `1.000.000 đ`, the way the list and the filter show a discount. */
export function formatDiscount(type: DiscountType, value: number): string {
  const number = new Intl.NumberFormat("vi-VN").format(value);
  return type === "PERCENT" ? `${number} %` : `${number} đ`;
}

/** `Cả năm`, or `Tháng 9 – 12` for a custom period. */
export function formatPeriod(
  period: ApplicationPeriod,
  fromMonth: number | null,
  toMonth: number | null
): string {
  if (period === "CUSTOM" && fromMonth && toMonth) {
    return `Tháng ${fromMonth} – ${toMonth}`;
  }
  return APPLICATION_PERIOD_LABELS[period];
}
