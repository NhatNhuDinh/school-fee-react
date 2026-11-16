import type { Dashboard } from "@/domains/fee-payment/dashboard";
import { kyInstance } from "@/shared/api/data";

// The ky instance already carries the API root as its prefix, so the path is relative.
export function fetchDashboard(yearId: number, days: number): Promise<Dashboard> {
  return kyInstance
    .get("fee_payments/dashboard", { searchParams: { academicYearId: yearId, days } })
    .json<Dashboard>();
}
