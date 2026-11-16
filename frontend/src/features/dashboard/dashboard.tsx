import { useCan, useGetIdentity } from "@refinedev/core";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import React from "react";
import { useNavigate } from "react-router";

import type { Identity } from "@/shared/api/session";
import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
import { NoAcademicYear } from "@/shared/components/academic-year/no-academic-year";
import { Button } from "@/shared/ui/button";
import { RecentReceipts, TopDebts } from "./activity";
import { fetchDashboard } from "./api";
import { BatchProgress, GroupBars, ItemsDonut, ProgressDonut } from "./breakdown";
import { KpiStrip } from "./kpi-strip";
import { RevenueChart } from "./revenue-chart";

/**
 * The landing screen: where the year stands, and how the money has been arriving.
 *
 * Not a data resource of its own — it reads `/api/fee_payments/dashboard`, so what it shows is what
 * `fee_payments:read` allows. An account without that grant (or a system administrator, who belongs to
 * no school and so has no year) gets the plain name of the application instead of an error.
 */
export const Dashboard = () => {
  const { enabled, years, selected, isLoading } = useAcademicYear();
  const { data: canRead } = useCan({ resource: "fee_payments", action: "list" });

  // A school with no year has nothing to work in yet. Waiting for the list first, so the prompt does
  // not flash up for a school that has years.
  if (enabled && !isLoading && years.length === 0) return <NoAcademicYear />;
  if (!enabled || !selected || !canRead?.can) return <Plain />;

  return <YearDashboard yearId={selected.id} />;
};

function YearDashboard({ yearId }: { yearId: number }) {
  const navigate = useNavigate();
  const { data: identity } = useGetIdentity<Identity>();
  const [days, setDays] = React.useState(7);

  const { data, isError, isPending } = useQuery({
    queryKey: ["fee_dashboard", yearId, days],
    queryFn: () => fetchDashboard(yearId, days),
    placeholderData: keepPreviousData,
    // The numbers move whenever a receipt is taken; a minute is fresh enough for a page nobody stares at.
    staleTime: 30_000,
  });

  return (
    <div className="space-y-4.5">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="text-muted-foreground text-xs font-medium">Trang chủ</div>
          <h1 className="text-foreground text-2xl font-bold tracking-tight sm:text-3xl">
            {greeting()}
            {identity?.fullName ? `, ${identity.fullName}` : ""}
          </h1>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          <Button type="button" variant="outline" onClick={() => navigate("/fee_payments/review")}>
            Rà soát giao dịch{data && data.totals.review > 0 ? ` (${data.totals.review})` : ""}
          </Button>
          <Button type="button" variant="outline" onClick={() => navigate("/fee_collection_batches")}>
            Tạo đợt thu
          </Button>
          <Button type="button" onClick={() => navigate("/fee_payments")}>
            Thu tiền
          </Button>
        </div>
      </header>

      {isPending ? (
        <div className="text-muted-foreground py-24 text-center text-sm">Đang tải số liệu…</div>
      ) : isError || !data ? (
        <div className="text-muted-foreground py-24 text-center text-sm">Không tải được số liệu trang chủ.</div>
      ) : (
        <>
          <KpiStrip data={data} />
          <div className="grid gap-4.5 xl:grid-cols-[minmax(0,1fr)_27rem]">
            <RevenueChart data={data} days={days} onDays={setDays} />
            <ProgressDonut data={data} />
          </div>
          <div className="grid gap-4.5 lg:grid-cols-3">
            <BatchProgress data={data} />
            <ItemsDonut data={data} />
            <GroupBars data={data} />
          </div>
          <div className="grid gap-4.5 xl:grid-cols-[minmax(0,1fr)_27rem]">
            <RecentReceipts data={data} />
            <TopDebts data={data} />
          </div>
        </>
      )}
    </div>
  );
}

/** Morning, afternoon or evening by the browser's clock, the way a counter greets. */
function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 11) return "Chào buổi sáng";
  if (hour < 14) return "Chào buổi trưa";
  if (hour < 18) return "Chào buổi chiều";
  return "Chào buổi tối";
}

function Plain() {
  return (
    <div className="flex min-h-[70dvh] items-center justify-center">
      <h1 className="text-4xl font-bold tracking-tight text-foreground sm:text-5xl">Hệ thống thu phí trường học</h1>
    </div>
  );
}
