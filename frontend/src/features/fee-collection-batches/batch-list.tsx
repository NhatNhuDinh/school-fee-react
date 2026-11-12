import { useCan, useList } from "@refinedev/core";
import { useTable } from "@refinedev/react-table";
import { createColumnHelper } from "@tanstack/react-table";
import { Eye, Plus } from "lucide-react";
import React from "react";
import { Link, useNavigate } from "react-router";

import type { AcademicYear } from "@/domains/academic-year/types";
import {
  batchScopeName,
  BATCH_STATUS_LABELS,
  collectedPercent,
  SCHOOL_MONTHS,
  type FeeCollectionBatch,
  type FeeCollectionBatchStatus,
} from "@/domains/fee-collection-batch/types";
import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
import { NoAcademicYear } from "@/shared/components/academic-year/no-academic-year";
import { DeleteButton } from "@/shared/components/buttons/delete";
import {
  actionsColumnWidth,
  DataTable,
  RESIZABLE_COLUMN_DEFAULTS,
  sttColumn,
} from "@/shared/components/data-table/data-table";
import { DataTableQuickFilter } from "@/shared/components/data-table/data-table-quick-filter";
import { ListToolbar, ListView, ListViewHeader } from "@/shared/components/views/list-view";
import { formatCurrency, formatNumber } from "@/shared/lib/format";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";
import { BatchFormDialog } from "./batch-form-dialog";
import { BatchStatusBadge } from "./batch-status-badge";
import { ProgressBar } from "./progress-bar";
import { Segmented } from "./segmented";

type StatusFilter = "any" | FeeCollectionBatchStatus;

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "any", label: "Tất cả" },
  { value: "NOT_STARTED", label: BATCH_STATUS_LABELS.NOT_STARTED },
  { value: "COLLECTING", label: BATCH_STATUS_LABELS.COLLECTING },
  { value: "COMPLETED", label: BATCH_STATUS_LABELS.COMPLETED },
];

/**
 * The đợt thu of the year chosen in the header: what each is, who it is for and how much of
 * it has come in. Making one is a dialog over this page; opening one goes to its own page,
 * because a đợt has a student list and a khoản thu list that do not fit in a row.
 *
 * Nothing here is a tab per month. The month is a filter and so is the status, both beside
 * the search — a list of twelve tabs says "twelve screens" when it is one list.
 */
export const FeeCollectionBatchList = () => {
  const { enabled, selected: year, isLoading: yearsLoading } = useAcademicYear();

  if (!enabled) {
    return (
      <ListView>
        <ListViewHeader />
        <Card className="text-muted-foreground p-10 text-center text-sm">
          Đợt thu thuộc một năm học của một trường. Hãy đăng nhập bằng tài khoản của trường để xem.
        </Card>
      </ListView>
    );
  }
  if (!yearsLoading && !year) return <NoAcademicYear />;
  if (!year) return null;

  return <BatchesOfYear year={year} />;
};

function BatchesOfYear({ year }: { year: AcademicYear }) {
  const navigate = useNavigate();
  const [creating, setCreating] = React.useState(false);
  const [month, setMonth] = React.useState<"any" | number>("any");
  const [status, setStatus] = React.useState<StatusFilter>("any");
  const { data: canWrite } = useCan({ resource: "fee_collection_batches", action: "create" });

  const columns = React.useMemo(() => {
    const columnHelper = createColumnHelper<FeeCollectionBatch>();

    return [
      sttColumn<FeeCollectionBatch>(),
      columnHelper.accessor("name", {
        id: "name",
        size: 280,
        header: "Đợt thu",
        enableSorting: true,
        cell: ({ row }) => (
          <div className="flex flex-col leading-tight">
            <Link
              to={`/fee_collection_batches/show/${row.original.id}`}
              className="font-medium hover:underline"
            >
              {row.original.name}
            </Link>
            <span className="text-muted-foreground text-xs">{batchScopeName(row.original)}</span>
          </div>
        ),
      }),
      columnHelper.accessor("collectionMonth", {
        id: "collectionMonth",
        size: 100,
        minSize: 100,
        header: "Tháng",
        enableSorting: true,
        cell: ({ getValue }) => `Tháng ${getValue()}`,
      }),
      columnHelper.accessor("studentCount", {
        id: "studentCount",
        size: 120,
        minSize: 120,
        header: "Học sinh",
        enableSorting: true,
        cell: ({ getValue }) => <span className="tabular-nums">{formatNumber(getValue())}</span>,
      }),
      columnHelper.accessor("feeItemCount", {
        id: "feeItemCount",
        size: 140,
        minSize: 140,
        header: "Khoản thu",
        enableSorting: true,
        cell: ({ getValue }) => <span className="tabular-nums">{getValue()}</span>,
      }),
      columnHelper.accessor("receivableAmount", {
        id: "receivableAmount",
        size: 150,
        minSize: 150,
        header: "Phải thu",
        enableSorting: true,
        cell: ({ getValue }) => <span className="font-semibold tabular-nums">{formatCurrency(getValue())}</span>,
      }),
      columnHelper.display({
        id: "progress",
        header: "Tiến độ thu",
        enableSorting: false,
        size: 200,
        minSize: 200,
        cell: ({ row }) => {
          const { receivableAmount, collectedAmount } = row.original;
          const percent = collectedPercent(receivableAmount, collectedAmount);
          return (
            <div className="flex w-full flex-col gap-1.5">
              <span className="text-xs tabular-nums">
                <span className="font-medium">{formatCurrency(collectedAmount)}</span>
                <span className="text-muted-foreground"> · {percent}%</span>
              </span>
              <ProgressBar percent={percent} />
            </div>
          );
        },
      }),
      columnHelper.accessor("status", {
        id: "status",
        size: 140,
        minSize: 130,
        header: "Trạng thái",
        enableSorting: false,
        cell: ({ getValue }) => <BatchStatusBadge status={getValue()} />,
      }),
      columnHelper.display({
        id: "actions",
        header: "Thao tác",
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex gap-1">
            <Button asChild variant="ghost" size="icon-sm" aria-label="Xem đợt thu">
              <Link to={`/fee_collection_batches/show/${row.original.id}`}>
                <Eye />
              </Link>
            </Button>
            <DeleteButton recordItemId={row.original.id} iconOnly size="icon-sm" />
          </div>
        ),
        ...actionsColumnWidth(2),
      }),
    ];
  }, []);

  const table = useTable({
    columns,
    getRowId: (row) => String(row.id),
    enableColumnResizing: true,
    columnResizeMode: "onChange",
    defaultColumn: RESIZABLE_COLUMN_DEFAULTS,
    initialState: {
      columnPinning: { right: ["actions"] },
    },
    refineCoreProps: {
      resource: "fee_collection_batches",
      pagination: { pageSize: 50 },
      filters: {
        permanent: [
          { field: "academicYear.id", operator: "eq", value: year.id },
          // Always present, with a value that changes: a permanent filter that is dropped
          // from this list is not dropped from the query. "any" restricts nothing.
          { field: "collectionMonth", operator: "eq", value: month },
          { field: "status", operator: "eq", value: status },
        ],
      },
    },
  });

  return (
    // One screen tall, so the pager stays on screen and only the rows scroll —
    // the same arithmetic as the students list; see CLAUDE.md, "A table fills the screen".
    <ListView className="h-[calc(100svh-4rem-2*var(--shell-gutter-y))] min-h-[36rem]">
      <ListViewHeader
        canCreate={false}
        actions={
          canWrite?.can ? (
            <Button type="button" onClick={() => setCreating(true)}>
              <Plus />
              Lập đợt thu
            </Button>
          ) : undefined
        }
      />
      <YearSummary yearId={year.id} />
      <div className="min-h-0 flex-1">
        <DataTable
          fill
          table={table}
          toolbar={
            <ListToolbar
              compact
              table={table}
              search={<DataTableQuickFilter table={table} placeholder="Tìm theo tên đợt thu…" />}
              filters={
                <div className="flex flex-wrap items-center gap-2">
                  <Select
                    value={String(month)}
                    onValueChange={(value) => setMonth(value === "any" ? "any" : Number(value))}
                  >
                    <SelectTrigger className="w-48" aria-label="Tháng thu">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="any">Tháng thu: Tất cả</SelectItem>
                      {SCHOOL_MONTHS.map((m) => (
                        <SelectItem key={m} value={String(m)}>
                          Tháng {m}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Segmented label="Trạng thái" size="sm" value={status} onChange={setStatus} options={STATUS_OPTIONS} />
                </div>
              }
            />
          }
        />
      </div>

      {creating && (
        <BatchFormDialog
          year={year}
          onClose={() => setCreating(false)}
          onCreated={(batch) => navigate(`/fee_collection_batches/show/${batch.id}`)}
        />
      )}
    </ListView>
  );
}

/**
 * Four numbers for the whole year, whatever the filters say: how many đợt, what is owed,
 * what has come in, what is left. Read from the year's đợt rather than added up from the
 * visible page, so it does not change when somebody types in the search.
 */
function YearSummary({ yearId }: { yearId: number }) {
  const {
    result: { data },
  } = useList<FeeCollectionBatch>({
    resource: "fee_collection_batches",
    pagination: { currentPage: 1, pageSize: 500 },
    filters: [{ field: "academicYear.id", operator: "eq", value: yearId }],
  });
  const batches = data ?? [];
  const receivable = batches.reduce((sum, b) => sum + b.receivableAmount, 0);
  const collected = batches.reduce((sum, b) => sum + b.collectedAmount, 0);
  const students = batches.reduce((max, b) => Math.max(max, b.studentCount), 0);
  const percent = collectedPercent(receivable, collected);

  return (
    <Card className="grid gap-0 p-0 sm:grid-cols-2 lg:grid-cols-4 lg:divide-x">
      <Metric label="Số đợt thu" value={formatNumber(batches.length)} hint={`Tối đa ${formatNumber(students)} học sinh một đợt`} />
      <Metric label="Tổng phải thu" value={formatCurrency(receivable)} hint="Cộng mọi đợt của năm học" />
      <Metric
        label="Đã thu"
        value={formatCurrency(collected)}
        tone="success"
        hint={`${percent}% tổng phải thu`}
      />
      <Metric
        label="Còn phải thu"
        value={formatCurrency(Math.max(receivable - collected, 0))}
        tone={receivable - collected > 0 ? "warning" : undefined}
        hint={`${100 - (receivable > 0 ? percent : 100)}% tổng phải thu`}
      />
    </Card>
  );
}

function Metric({
  label,
  value,
  hint,
  tone,
}: {
  label: string;
  value: string;
  hint: string;
  tone?: "success" | "warning";
}) {
  return (
    <div className="flex flex-col gap-1 px-5 py-4">
      <span className="text-overline text-muted-foreground">{label}</span>
      <span
        className={
          tone === "success"
            ? "text-success-text text-xl font-bold tabular-nums"
            : tone === "warning"
              ? "text-warning-text text-xl font-bold tabular-nums"
              : "text-xl font-bold tabular-nums"
        }
      >
        {value}
      </span>
      <span className="text-muted-foreground text-xs">{hint}</span>
    </div>
  );
}
