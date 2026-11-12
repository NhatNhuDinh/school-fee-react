import { useCan, useCustom, useCustomMutation, useShow } from "@refinedev/core";
import { ArrowLeft, Pencil, Plus, RefreshCw, Search } from "lucide-react";
import React from "react";
import { useNavigate } from "react-router";

import {
  batchScopeName,
  collectedPercent,
  priceRange,
  type FeeCollectionBatch,
  type FeeCollectionBatchItem,
  type FeeCollectionBatchStudents,
  type SyncResult,
} from "@/domains/fee-collection-batch/types";
import type { StudentStructure } from "@/domains/student/types";
import { API_URL } from "@/shared/api/constants";
import { DeleteButton } from "@/shared/components/buttons/delete";
import { DataTablePagination } from "@/shared/components/data-table/data-table-pagination";
import { Breadcrumb } from "@/shared/components/layout/breadcrumb";
import { ListView } from "@/shared/components/views/list-view";
import { customResult } from "@/shared/hooks/use-custom-result";
import { formatCurrency, formatNumber } from "@/shared/lib/format";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Input } from "@/shared/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/shared/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/shared/ui/tabs";
import { BatchItemsDialog } from "./batch-items-dialog";
import { BatchStatusBadge } from "./batch-status-badge";
import { NotificationsTab } from "./notifications-tab";
import { ProgressBar } from "./progress-bar";
import { Segmented } from "./segmented";

type DebtFilter = "any" | "owing" | "paid";

const DEBT_OPTIONS: { value: DebtFilter; label: string }[] = [
  { value: "any", label: "Tất cả" },
  { value: "owing", label: "Còn nợ" },
  { value: "paid", label: "Đã thu đủ" },
];

/**
 * One đợt thu: what it is and how much has come in, then its students and its khoản thu on
 * two tabs. The id comes from the URL, like every other show page here.
 *
 * Everything that changes the đợt goes through the API and then asks every read on the page to
 * run again (`tick` is part of each query), because the totals are computed on the server and
 * a figure patched in the browser would be a second opinion about money.
 */
export const FeeCollectionBatchShow = () => {
  const navigate = useNavigate();
  const { result: batch, query } = useShow<FeeCollectionBatch>({});
  const [tab, setTab] = React.useState<"students" | "items" | "notifications">("students");
  const [editingItems, setEditingItems] = React.useState(false);
  const [composing, setComposing] = React.useState(false);
  const { data: canNotify } = useCan({ resource: "fee_notifications", action: "create" });
  const [tick, setTick] = React.useState(0);

  const batchId = batch?.id;
  const { result: itemsResult } = useCustom<FeeCollectionBatchItem[]>({
    url: `${API_URL}/fee_collection_batches/${batchId}/items`,
    method: "get",
    config: { query: { _v: tick } },
    queryOptions: { enabled: batchId !== undefined, placeholderData: (previous) => previous },
  });
  const items = customResult(itemsResult?.data as unknown as { length: number } | undefined, "length") as
    | FeeCollectionBatchItem[]
    | undefined;

  const { mutate: sync, mutation: syncing } = useCustomMutation<SyncResult>();

  function refresh() {
    setTick((value) => value + 1);
    void query.refetch();
  }

  function syncRegistrations() {
    if (!batch) return;
    sync(
      {
        url: `${API_URL}/fee_collection_batches/${batch.id}/sync`,
        method: "post",
        config: { headers: { "Content-Type": "application/json" } },
        values: {},
        successNotification: (response) => {
          const result = (response as { data?: SyncResult } | undefined)?.data;
          const kept = result?.keptBecauseCollected
            ? `, giữ ${result.keptBecauseCollected} khoản đã thu tiền`
            : "";
          return {
            type: "success",
            message: result
              ? `Đã cập nhật theo đăng ký: thêm ${result.added}, bỏ ${result.removed}${kept}`
              : "Đã cập nhật theo đăng ký",
          };
        },
      },
      { onSuccess: refresh }
    );
  }

  if (!batch) {
    return (
      <ListView>
        <Breadcrumb />
        <div className="text-muted-foreground p-10 text-center text-sm">
          {query.isLoading ? "Đang tải…" : "Không tìm thấy đợt thu này."}
        </div>
      </ListView>
    );
  }

  const canWrite = batch._can?.write === true;
  const canDelete = batch._can?.delete === true && batch.collectedAmount <= 0;
  const mandatory = items?.filter((item) => item.mandatory).length;
  const optional = items ? items.length - (mandatory ?? 0) : undefined;
  const percent = collectedPercent(batch.receivableAmount, batch.collectedAmount);
  const remaining = Math.max(batch.receivableAmount - batch.collectedAmount, 0);

  return (
    // One screen tall, so the pager stays on screen and only the rows scroll — the same
    // arithmetic as every table; see CLAUDE.md, "A table fills the screen".
    <ListView className="h-[calc(100svh-4rem-2*var(--shell-gutter-y))] min-h-[40rem]">
      <div className="flex flex-col gap-3">
        <Breadcrumb />
        <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-3">
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label="Quay lại danh sách"
                onClick={() => navigate("/fee_collection_batches")}
              >
                <ArrowLeft />
              </Button>
              <h1 className="truncate text-3xl font-bold leading-9 tracking-tight">{batch.name}</h1>
              <BatchStatusBadge status={batch.status} />
            </div>
          </div>
          <div data-print="hide" className="flex shrink-0 items-center gap-2">
            {canWrite && (
              <>
                <Button type="button" variant="outline" disabled={syncing.isPending} onClick={syncRegistrations}>
                  <RefreshCw className={cn(syncing.isPending && "animate-spin")} />
                  Cập nhật theo đăng ký
                </Button>
                <Button type="button" variant="outline" onClick={() => setEditingItems(true)}>
                  <Pencil />
                  Cập nhật khoản thu
                </Button>
              </>
            )}
            {canDelete && (
              <DeleteButton
                resource="fee_collection_batches"
                recordItemId={batch.id}
                variant="outline"
                onSuccess={() => navigate("/fee_collection_batches")}
              />
            )}
          </div>
        </div>
      </div>

      <Card className="grid gap-0 p-0 sm:grid-cols-2 lg:grid-cols-4 lg:divide-x">
        <Metric label="Học sinh" value={formatNumber(batch.studentCount)} hint={batchScopeName(batch)} />
        <Metric
          label="Khoản thu"
          value={String(batch.feeItemCount)}
          hint={items ? `${mandatory} bắt buộc · ${optional} tự chọn` : "…"}
        />
        <Metric
          label="Tổng phải thu"
          value={formatCurrency(batch.receivableAmount)}
          hint={
            batch.studentCount > 0
              ? `Trung bình ${formatCurrency(Math.round(batch.receivableAmount / batch.studentCount))} / học sinh`
              : "Chưa có học sinh"
          }
        />
        <div className="flex flex-col gap-1 px-5 py-4">
          <span className="text-overline text-muted-foreground">Đã thu</span>
          <span className="text-success-text text-xl font-bold tabular-nums">
            {formatCurrency(batch.collectedAmount)}
          </span>
          <ProgressBar percent={percent} className="my-0.5" />
          <span className="text-muted-foreground text-xs tabular-nums">
            {percent}% · còn {formatCurrency(remaining)}
          </span>
        </div>
      </Card>

      <Card className="flex min-h-0 flex-1 flex-col gap-0 overflow-hidden py-0">
        <Tabs value={tab} onValueChange={(value) => setTab(value as "students" | "items" | "notifications")} className="relative shrink-0 gap-0">
          <TabsList className="h-auto w-full justify-start rounded-none border-b bg-transparent p-0 px-3">
            <TabsTrigger value="students" className={tabClass}>
              Học sinh
            </TabsTrigger>
            <TabsTrigger value="items" className={tabClass}>
              Khoản thu
            </TabsTrigger>
            <TabsTrigger value="notifications" className={tabClass}>
              Thông báo
            </TabsTrigger>
          </TabsList>
          {tab === "notifications" && canNotify?.can && (
            <Button
              type="button"
              size="sm"
              className="absolute end-3 top-1/2 -translate-y-1/2"
              onClick={() => setComposing(true)}
            >
              <Plus />
              Lập đợt thông báo
            </Button>
          )}
        </Tabs>

        {tab === "students" ? (
          <StudentsTab batch={batch} items={items} tick={tick} />
        ) : tab === "items" ? (
          <ItemsTab items={items} />
        ) : (
          <NotificationsTab batch={batch} onChanged={refresh} composing={composing} setComposing={setComposing} />
        )}
      </Card>

      {editingItems && (
        <BatchItemsDialog batch={batch} onClose={() => setEditingItems(false)} onSaved={refresh} />
      )}
    </ListView>
  );
};

const tabClass =
  "flex-none data-[state=active]:text-primary data-[state=active]:border-primary rounded-none border-0 border-b-2 border-transparent px-3 py-2.5 shadow-none data-[state=active]:bg-transparent data-[state=active]:shadow-none";

function Metric({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <div className="flex flex-col gap-1 px-5 py-4">
      <span className="text-overline text-muted-foreground">{label}</span>
      <span className="text-xl font-bold tabular-nums">{value}</span>
      <span className="text-muted-foreground text-xs">{hint}</span>
    </div>
  );
}

// ---- tab: students

function StudentsTab({
  batch,
  items,
  tick,
}: {
  batch: FeeCollectionBatch;
  items: FeeCollectionBatchItem[] | undefined;
  tick: number;
}) {
  const [search, setSearch] = React.useState("");
  const [term, setTerm] = React.useState("");
  const [debt, setDebt] = React.useState<DebtFilter>("any");
  const [classId, setClassId] = React.useState<"any" | number>("any");
  const [page, setPage] = React.useState(1);
  const [pageSize, setPageSize] = React.useState(50);

  React.useEffect(() => {
    const timer = setTimeout(() => {
      setTerm(search.trim());
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  // The lớp to filter by: those the đợt reaches, in the order the school lists them.
  const { result: structureResult } = useCustom<StudentStructure>({
    url: `${API_URL}/students/structure`,
    method: "get",
    config: { query: { academicYearId: batch.academicYear.id } },
  });
  const structure = customResult(structureResult?.data, "groups");
  const classes = React.useMemo(() => {
    const all = (structure?.groups ?? []).flatMap((group) =>
      group.classes.map((klass) => ({ id: klass.id, name: klass.name, groupId: group.id }))
    );
    if (batch.scope === "CLASS_GROUP") return all.filter((c) => c.groupId === batch.scopeClassGroup?.id);
    if (batch.scope === "CLASS") return all.filter((c) => c.id === batch.scopeClass?.id);
    return all;
  }, [structure, batch.scope, batch.scopeClassGroup, batch.scopeClass]);

  const query: Record<string, string | number> = {
    _start: (page - 1) * pageSize,
    _end: page * pageSize,
    debt,
    _v: tick,
  };
  if (term) query.q = term;
  if (classId !== "any") query["schoolClass.id"] = classId;

  const { result, query: request } = useCustom<FeeCollectionBatchStudents>({
    url: `${API_URL}/fee_collection_batches/${batch.id}/students`,
    method: "get",
    config: { query },
    queryOptions: { placeholderData: (previous) => previous },
  });
  const data = customResult(result?.data, "rows");
  const rows = data?.rows ?? [];
  const total = data?.total ?? 0;
  const pageCount = Math.max(Math.ceil(total / pageSize), 1);

  return (
    <>
      <div className="flex shrink-0 flex-wrap items-center gap-2 px-4 py-2.5">
        <div className="relative min-w-60 flex-1">
          <Search className="text-muted-foreground absolute start-3 top-1/2 size-4 -translate-y-1/2" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Tìm theo mã hoặc họ tên học sinh…"
            className="ps-9"
          />
        </div>
        <Select
          value={String(classId)}
          onValueChange={(value) => {
            setClassId(value === "any" ? "any" : Number(value));
            setPage(1);
          }}
        >
          <SelectTrigger className="w-40" aria-label="Lớp">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="any">Lớp: Tất cả</SelectItem>
            {classes.map((klass) => (
              <SelectItem key={klass.id} value={String(klass.id)}>
                {klass.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Segmented
          label="Công nợ"
          size="sm"
          value={debt}
          onChange={(value) => {
            setDebt(value);
            setPage(1);
          }}
          options={DEBT_OPTIONS}
        />
      </div>

      <Table
        containerClassName="min-h-0 flex-1 overflow-y-auto"
        style={{ minWidth: 580 + (items?.length ?? 0) * 140 }}
      >
        <TableHeader sticky>
          <TableRow>
            <TableHead className="bg-surface-subtle sticky left-0 z-20 w-14 min-w-14 ps-5">STT</TableHead>
            <TableHead className="bg-surface-subtle sticky left-14 z-20 min-w-60">Học sinh</TableHead>
            <TableHead className="w-24">Lớp</TableHead>
            {(items ?? []).map((item) => (
              <TableHead key={item.id} className="w-[140px] text-end">
                {item.name}
              </TableHead>
            ))}
            <TableHead className="w-36 text-end">Phải thu</TableHead>
            <TableHead className="w-36 text-end">Đã thu</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((student, index) => {
            const paidInFull = student.receivableAmount > 0 && student.collectedAmount >= student.receivableAmount;
            return (
              <TableRow key={student.id}>
                <TableCell className="bg-card text-muted-foreground sticky left-0 z-10 w-14 min-w-14 ps-5 tabular-nums">
                  {(page - 1) * pageSize + index + 1}
                </TableCell>
                <TableCell className="bg-card sticky left-14 z-10">
                  <div className="flex flex-col leading-tight">
                    <span className="font-medium">{student.fullName}</span>
                    <span className="text-muted-foreground text-xs">{student.studentCode}</span>
                  </div>
                </TableCell>
                <TableCell>{student.schoolClassName}</TableCell>
                {(items ?? []).map((item) => {
                  const amount = student.amounts[String(item.id)];
                  return (
                    <TableCell key={item.id} className="text-end tabular-nums">
                      {amount ? formatNumber(amount.receivable) : <span className="text-muted-foreground">—</span>}
                    </TableCell>
                  );
                })}
                <TableCell className="text-end font-semibold tabular-nums">
                  {formatCurrency(student.receivableAmount)}
                </TableCell>
                <TableCell
                  className={cn(
                    "text-end font-semibold tabular-nums",
                    paidInFull
                      ? "text-success-text"
                      : student.collectedAmount > 0
                        ? "text-warning-text"
                        : "text-muted-foreground"
                  )}
                >
                  {formatCurrency(student.collectedAmount)}
                </TableCell>
              </TableRow>
            );
          })}
          {rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={(items?.length ?? 0) + 5} className="text-muted-foreground py-10 text-center">
                {request.isLoading ? "Đang tải…" : "Không có học sinh nào khớp"}
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>

      <div className="mt-auto shrink-0 border-t px-3 py-2">
        <DataTablePagination
          currentPage={page}
          pageCount={pageCount}
          setCurrentPage={setPage}
          pageSize={pageSize}
          setPageSize={(size) => {
            setPageSize(size);
            setPage(1);
          }}
          total={total}
        />
      </div>
    </>
  );
}

// ---- tab: khoản thu

function ItemsTab({ items }: { items: FeeCollectionBatchItem[] | undefined }) {
  return (
    <Table containerClassName="min-h-0 flex-1 overflow-y-auto" style={{ minWidth: 960 }}>
      <TableHeader sticky>
        <TableRow>
          <TableHead className="w-14 min-w-14 ps-5">STT</TableHead>
          <TableHead>Khoản thu</TableHead>
          <TableHead className="w-28">Loại</TableHead>
          <TableHead className="w-44 text-end">Đơn giá</TableHead>
          <TableHead className="w-28 text-end">Số lượng</TableHead>
          <TableHead className="w-36 text-end">Học sinh áp dụng</TableHead>
          <TableHead className="w-40 text-end">Phải thu</TableHead>
          <TableHead className="w-40 text-end">Đã thu</TableHead>
          <TableHead className="w-44">Tiến độ</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {(items ?? []).map((item, index) => {
          const percent = collectedPercent(item.receivableAmount, item.collectedAmount);
          const kind = priceRange(item.minUnitPrice, item.maxUnitPrice);
          return (
            <TableRow key={item.id}>
              <TableCell className="text-muted-foreground w-14 min-w-14 ps-5 tabular-nums">{index + 1}</TableCell>
              <TableCell>
                <div className="flex flex-col leading-tight">
                  <span className="font-medium">{item.name}</span>
                  <span className="text-muted-foreground text-xs">Tính theo {item.unitName}</span>
                </div>
              </TableCell>
              <TableCell>
                <Badge variant={item.mandatory ? "warning" : "neutral"}>
                  {item.mandatory ? "Bắt buộc" : "Tự chọn"}
                </Badge>
              </TableCell>
              <TableCell className="text-end tabular-nums">
                {kind === "single"
                  ? formatCurrency(item.minUnitPrice)
                  : kind === "range"
                    ? `${formatNumber(item.minUnitPrice)} – ${formatNumber(item.maxUnitPrice)}`
                    : "—"}
              </TableCell>
              <TableCell className="text-end tabular-nums">{item.quantity}</TableCell>
              <TableCell className="text-end tabular-nums">{formatNumber(item.studentCount)}</TableCell>
              <TableCell className="text-end font-semibold tabular-nums">
                {formatCurrency(item.receivableAmount)}
              </TableCell>
              <TableCell className="text-end tabular-nums">{formatCurrency(item.collectedAmount)}</TableCell>
              <TableCell>
                <div className="flex items-center gap-2">
                  <ProgressBar percent={percent} />
                  <span className="text-muted-foreground w-10 text-end text-xs tabular-nums">{percent}%</span>
                </div>
              </TableCell>
            </TableRow>
          );
        })}
        {items && items.length === 0 && (
          <TableRow>
            <TableCell colSpan={9}className="text-muted-foreground py-10 text-center">
              Đợt thu này chưa có khoản thu nào.
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  );
}
