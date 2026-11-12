import { useCustom, useCustomMutation, useCan, useWarnAboutChange } from "@refinedev/core";
import { Columns3, Search } from "lucide-react";
import React from "react";

import {
  cellKey,
  sameTicks,
  savePayload,
  ticksOf,
  type FeeRegistrationColumn,
  type FeeRegistrationMatrix,
} from "@/domains/fee-registration/types";
import { API_URL } from "@/shared/api/constants";
import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
import { NoAcademicYear } from "@/shared/components/academic-year/no-academic-year";
import { DataTablePagination } from "@/shared/components/data-table/data-table-pagination";
import { useConfirmDiscard } from "@/shared/components/form/unsaved-changes-guard";
import { ListView, ListViewHeader } from "@/shared/components/views/list-view";
import { customResult } from "@/shared/hooks/use-custom-result";
import { formatCurrency } from "@/shared/lib/format";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Checkbox } from "@/shared/ui/checkbox";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/shared/ui/dropdown-menu";
import { Input } from "@/shared/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table";
import { ClassTree } from "./class-tree";

/**
 * Registering students for the optional khoản thu of one lớp: pick the lớp on the
 * left, tick the cells of the grid, save.
 *
 * Nothing is written until Save. The ticks are held here as a set of cells and
 * compared with what the server reported, so "unsaved" means exactly "different",
 * and ticking a cell and unticking it again leaves the page clean. Changing lớp
 * with ticks unsaved asks first, through the application's own dialog.
 */
export const FeeRegistrationPage = () => {
  const { enabled, selected: year, isLoading } = useAcademicYear();

  if (!enabled) {
    return (
      <ListView>
        <ListViewHeader />
        <Card className="text-muted-foreground p-10 text-center text-sm">
          Đăng ký thu thuộc một năm học của một trường. Hãy đăng nhập bằng tài khoản của trường để xem.
        </Card>
      </ListView>
    );
  }
  if (!isLoading && !year) return <NoAcademicYear />;
  if (!year) return null;

  return <RegistrationOfYear yearId={year.id} />;
};

function RegistrationOfYear({ yearId }: { yearId: number }) {
  const [classId, setClassId] = React.useState<number | null>(null);
  const [ticks, setTicks] = React.useState<Set<string>>(new Set());
  const [hiddenColumns, setHiddenColumns] = React.useState<Set<number>>(new Set());
  const [search, setSearch] = React.useState("");
  const [page, setPage] = React.useState(1);
  const [pageSize, setPageSize] = React.useState(50);

  const { data: canWrite } = useCan({ resource: "fee_registrations", action: "edit" });
  const confirmDiscard = useConfirmDiscard();
  const { setWarnWhen } = useWarnAboutChange();

  const { result, query } = useCustom<FeeRegistrationMatrix>({
    url: `${API_URL}/fee_registrations/matrix`,
    method: "get",
    config: { query: { schoolClassId: classId } },
    queryOptions: { enabled: classId !== null },
  });
  const matrix = customResult(result?.data, "feeItems");

  // The year moved under us: the lớp that was picked belonged to the old one.
  React.useEffect(() => {
    setClassId(null);
  }, [yearId]);

  // A matrix has arrived (a lớp was picked, or a save reloaded it): the ticks are
  // its ticks, and the choices made about the last lớp no longer apply.
  React.useEffect(() => {
    setTicks(matrix ? ticksOf(matrix) : new Set());
    setHiddenColumns(new Set());
    setSearch("");
    setPage(1);
  }, [matrix]);

  const saved = React.useMemo(() => (matrix ? ticksOf(matrix) : new Set<string>()), [matrix]);
  const dirty = matrix !== undefined && !sameTicks(ticks, saved);

  // The shell's own guard keys off Refine's flag; raising it is what makes leaving
  // the page, or the browser tab, ask first.
  React.useEffect(() => {
    setWarnWhen(dirty);
    return () => setWarnWhen(false);
  }, [dirty, setWarnWhen]);

  const { mutate, mutation } = useCustomMutation();

  function save() {
    if (!matrix) return;
    mutate(
      {
        url: `${API_URL}/fee_registrations/class/${matrix.schoolClass.id}`,
        method: "put",
        // The provider sends a JSON string with no content type of its own, which the
        // API answers 415; the rest of the data layer sets it per call.
        config: { headers: { "Content-Type": "application/json" } },
        values: savePayload(matrix, ticks),
        successNotification: () => ({ type: "success", message: "Đã lưu đăng ký khoản thu" }),
      },
      { onSuccess: () => void query.refetch() }
    );
  }

  function toggle(enrollmentId: number, feeItemId: number, on: boolean) {
    setTicks((current) => {
      const next = new Set(current);
      const key = cellKey(enrollmentId, feeItemId);
      if (on) next.add(key);
      else next.delete(key);
      return next;
    });
  }

  function pick(id: number) {
    if (id === classId) return;
    confirmDiscard(() => setClassId(id));
  }

  const columns = (matrix?.feeItems ?? []).filter((item) => !hiddenColumns.has(item.id));
  const allStudents = matrix?.students ?? [];
  const students = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return allStudents;
    return allStudents.filter(
      (s) => s.fullName.toLowerCase().includes(term) || s.studentCode.toLowerCase().includes(term)
    );
  }, [allStudents, search]);
  const pageCount = Math.max(1, Math.ceil(students.length / pageSize));
  const pageRows = students.slice((page - 1) * pageSize, page * pageSize);


  return (
    // One screen tall, so the pager stays on screen and only the rows scroll — see
    // CLAUDE.md, "A table fills the screen".
    <ListView className="h-[calc(100svh-4rem-2*var(--shell-gutter-y))] min-h-[36rem]">
      <ListViewHeader
        canCreate={false}
        actions={
          canWrite?.can ? (
            <Button type="button" disabled={!dirty || mutation.isPending} onClick={save}>
              {mutation.isPending ? "Đang lưu…" : "Lưu đăng ký"}
            </Button>
          ) : undefined
        }
      />
      <div className="grid min-h-0 flex-1 grid-rows-[minmax(0,1fr)] items-stretch gap-4 lg:grid-cols-[19rem_minmax(0,1fr)]">
        <Card className="h-full min-h-0 gap-0 overflow-hidden py-0">
          <ClassTree
            academicYearId={yearId}
            selectedClassId={classId}
            onSelect={pick}
            className="min-h-0 flex-1"
          />
        </Card>

        <Card className="h-full min-h-0 gap-0 overflow-hidden py-0">
          {classId === null ? (
            <Hint>Chọn một lớp ở cây bên trái để đăng ký khoản thu tự chọn cho học sinh.</Hint>
          ) : !matrix ? (
            <Hint>Đang tải…</Hint>
          ) : (
            <>
              <div className="flex shrink-0 items-center gap-2 px-4 py-2.5">
                <div className="relative min-w-0 flex-1">
                  <Search className="text-muted-foreground absolute start-3 top-1/2 size-4 -translate-y-1/2" />
                  <Input
                    value={search}
                    onChange={(event) => {
                      setSearch(event.target.value);
                      setPage(1);
                    }}
                    placeholder="Tìm theo mã hoặc họ tên học sinh…"
                    className="ps-9"
                  />
                </div>
                <ColumnPicker
                  items={matrix.feeItems}
                  hidden={hiddenColumns}
                  onChange={setHiddenColumns}
                />
              </div>

              {matrix.feeItems.length === 0 ? (
                <Hint>Lớp này chưa có khoản thu tự chọn nào. Khoản thu bắt buộc không cần đăng ký.</Hint>
              ) : columns.length === 0 ? (
                <Hint>Chưa chọn khoản thu nào. Hãy chọn ít nhất một khoản thu để bắt đầu đăng ký.</Hint>
              ) : matrix.students.length === 0 ? (
                <Hint>Lớp chưa có học sinh.</Hint>
              ) : (
                <Table containerClassName="min-h-0 flex-1 overflow-y-auto" style={{ minWidth: 340 + columns.length * 150 }}>
                  <TableHeader sticky>
                    <TableRow>
                      <TableHead className="sticky left-0 z-20 w-14 ps-5">STT</TableHead>
                      <TableHead className="sticky left-14 z-20 min-w-52">Học sinh</TableHead>
                      {columns.map((item) => (
                        <TableHead key={item.id} className="w-[150px] text-center">
                          <div className="flex flex-col items-center gap-0.5">
                            <span>{item.name}</span>
                            <span className="text-muted-foreground text-[11px] font-normal normal-case tracking-normal">
                              {priceOf(item)}
                            </span>
                          </div>
                        </TableHead>
                      ))}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {pageRows.map((student, index) => (
                      <TableRow key={student.enrollmentId}>
                        <TableCell className="bg-card text-muted-foreground sticky left-0 z-10 ps-5 tabular-nums">
                          {(page - 1) * pageSize + index + 1}
                        </TableCell>
                        <TableCell className="bg-card sticky left-14 z-10">
                          <div className="flex flex-col leading-tight">
                            <span className="font-medium">{student.fullName}</span>
                            <span className="text-muted-foreground text-xs">{student.studentCode}</span>
                          </div>
                        </TableCell>
                        {columns.map((item) => (
                          <TableCell key={item.id} className="text-center">
                            <Checkbox
                              checked={ticks.has(cellKey(student.enrollmentId, item.id))}
                              disabled={!canWrite?.can}
                              onCheckedChange={(value) => toggle(student.enrollmentId, item.id, value === true)}
                              aria-label={`${student.fullName} – ${item.name}`}
                            />
                          </TableCell>
                        ))}
                      </TableRow>
                    ))}
                    {pageRows.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={columns.length + 2} className="text-muted-foreground py-10 text-center">
                          Không có học sinh nào khớp
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              )}

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
                  total={students.length}
                />
              </div>
            </>
          )}
        </Card>
      </div>
    </ListView>
  );
}

/** What a column costs, as its header says it. */
function priceOf(item: FeeRegistrationColumn): string {
  return item.priceMode === "MULTIPLE"
    ? "Nhiều đơn giá"
    : `${formatCurrency(item.basePrice)} / ${item.unit}`;
}

/** The empty and waiting states: one centred sentence filling the panel. */
function Hint({ children }: { children: React.ReactNode }) {
  return (
    <div className="text-muted-foreground flex min-h-0 flex-1 items-center justify-center px-6 text-center text-sm">
      {children}
    </div>
  );
}

/** Which khoản thu are columns on screen: all of them to begin with. */
function ColumnPicker({
  items,
  hidden,
  onChange,
}: {
  items: FeeRegistrationColumn[];
  hidden: Set<number>;
  onChange: (next: Set<number>) => void;
}) {
  const shown = items.length - hidden.size;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="outline">
          <Columns3 />
          Khoản thu hiển thị ({shown}/{items.length})
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="max-h-80 w-64 overflow-y-auto">
        <DropdownMenuLabel>Khoản thu tự chọn</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {items.map((item) => (
          <DropdownMenuCheckboxItem
            key={item.id}
            checked={!hidden.has(item.id)}
            // Radix closes on select, which would mean reopening the menu for every column.
            onSelect={(event) => event.preventDefault()}
            onCheckedChange={(value) => {
              const next = new Set(hidden);
              if (value) next.delete(item.id);
              else next.add(item.id);
              onChange(next);
            }}
          >
            <span className="truncate">{item.name}</span>
          </DropdownMenuCheckboxItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
