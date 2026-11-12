import { useCan, useInvalidate } from "@refinedev/core";
import { useTable } from "@refinedev/react-table";
import { createColumnHelper } from "@tanstack/react-table";
import { Pencil, Plus } from "lucide-react";
import React from "react";

import {
  GENDER_LABELS,
  nodeFilters,
  type StructureNode,
  type StudentRow,
} from "@/domains/student/types";
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
import {
  ListToolbar,
  ListView,
  ListViewHeader,
} from "@/shared/components/views/list-view";
import { formatDate } from "@/shared/lib/format";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Checkbox } from "@/shared/ui/checkbox";
import { AssignClassDialog } from "./assign-class-dialog";
import { ImportDialog } from "./import-dialog";
import { StructureTree } from "./structure-tree";
import { StudentFormDialog } from "./student-form-dialog";
import { useStudentStructure } from "./use-student-structure";

/**
 * The students of the year chosen in the header: the khối lớp and lớp on the
 * left, the students of whichever is picked on the right. Add, edit, move into a
 * class and carry over from the previous year are all dialogs over this one
 * page, as they were in the Jmix application.
 *
 * The year comes from the header. A system administrator has none, so is told to
 * sign in as a school: a student list across every school is not a screen anyone
 * asked for.
 */
export const StudentList = () => {
  const { enabled, selected: year, years, isLoading: yearsLoading } = useAcademicYear();

  if (!enabled) {
    return (
      <ListView>
        <ListViewHeader />
        <Card className="text-muted-foreground p-10 text-center text-sm">
          Danh sách học sinh thuộc một năm học của một trường. Hãy đăng nhập bằng tài khoản của trường
          để xem.
        </Card>
      </ListView>
    );
  }
  if (!yearsLoading && !year) return <NoAcademicYear />;
  if (!year) return null;

  return <StudentsOfYear year={year} years={years} />;
};

function StudentsOfYear({
  year,
  years,
}: {
  year: { id: number; name: string };
  years: ReturnType<typeof useAcademicYear>["years"];
}) {
  const [node, setNode] = React.useState<StructureNode>({ type: "all" });
  const [editing, setEditing] = React.useState<StudentRow | "new" | null>(null);
  const [assigning, setAssigning] = React.useState<number[] | null>(null);
  const [importing, setImporting] = React.useState(false);

  const { structure, refetch: refetchStructure } = useStudentStructure(year.id);
  const invalidate = useInvalidate();
  const { data: canWrite } = useCan({ resource: "students", action: "create" });
  const { data: canEdit } = useCan({ resource: "students", action: "edit" });

  // The year moved under us: whatever was picked belonged to the old one.
  React.useEffect(() => {
    setNode({ type: "all" });
  }, [year.id]);

  const columns = React.useMemo(() => {
    const columnHelper = createColumnHelper<StudentRow>();

    return [
      columnHelper.display({
        id: "select",
        enableSorting: false,
        header: ({ table }) => (
          <Checkbox
            checked={
              table.getIsAllPageRowsSelected() ||
              (table.getIsSomePageRowsSelected() && "indeterminate")
            }
            onCheckedChange={(value) => table.toggleAllPageRowsSelected(value === true)}
            aria-label="Chọn tất cả"
          />
        ),
        cell: ({ row }) => (
          <Checkbox
            checked={row.getIsSelected()}
            onCheckedChange={(value) => row.toggleSelected(value === true)}
            aria-label={`Chọn ${row.original.student.fullName}`}
          />
        ),
        // `minSize` as well as `size`: RESIZABLE_COLUMN_DEFAULTS floors every
        // column at 80px and `getSize()` clamps to it, so a 40px checkbox column
        // silently rendered at 80 and pushed everything right of it away.
        size: 44,
        minSize: 44,
      }),
      sttColumn<StudentRow>(),
      // Sizes are chosen so every header reads in full beside its sort arrow:
      // an uppercase `text-overline` label runs ~8px a character, plus the arrow
      // and the cell's padding. A header that ends in "…" is a column too narrow.
      columnHelper.accessor("student.studentCode", {
        id: "student.studentCode",
        size: 170,
        enableResizing: true,
        header: "Mã HS",
        enableSorting: true,
        cell: ({ getValue }) => <span className="text-muted-foreground">{getValue()}</span>,
      }),
      columnHelper.accessor("student.fullName", {
        id: "student.fullName",
        size: 210,
        enableResizing: true,
        header: "Họ và tên",
        enableSorting: true,
        cell: ({ getValue }) => <span className="font-medium">{getValue()}</span>,
      }),
      columnHelper.accessor("student.dateOfBirth", {
        id: "student.dateOfBirth",
        size: 130,
        header: "Ngày sinh",
        enableSorting: true,
        cell: ({ getValue }) => (getValue() ? formatDate(getValue() as string) : ""),
      }),
      columnHelper.accessor("student.gender", {
        id: "student.gender",
        size: 130,
        header: "Giới tính",
        enableSorting: true,
        cell: ({ getValue }) => (getValue() ? GENDER_LABELS[getValue()!] : ""),
      }),
      // Guardian and phone are one fact about one person, so they share a cell:
      // the name above, the number under it. Sorts by name.
      columnHelper.accessor("student.guardianName", {
        id: "student.guardianName",
        size: 210,
        enableResizing: true,
        header: "Phụ huynh",
        enableSorting: true,
        cell: ({ row }) => {
          const { guardianName, guardianPhone } = row.original.student;
          if (!guardianName && !guardianPhone) return null;
          return (
            <div className="flex flex-col leading-tight">
              <span>{guardianName}</span>
              {guardianPhone && (
                <span className="text-muted-foreground text-xs tabular-nums">{guardianPhone}</span>
              )}
            </div>
          );
        },
      }),
      columnHelper.accessor((row) => row.schoolClass?.name ?? "", {
        id: "schoolClass.name",
        size: 100,
        header: "Lớp",
        enableSorting: true,
      }),
      columnHelper.display({
        id: "actions",
        header: "Thao tác",
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex gap-1">
            {canEdit?.can && (
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label="Sửa"
                onClick={() => setEditing(row.original)}
              >
                <Pencil />
              </Button>
            )}
            <DeleteButton
              recordItemId={row.original.id}
              iconOnly
              size="icon-sm"
              // The list refetches itself after a delete; the tree beside it does
              // not, and keeps counting the student that is gone.
              onSuccess={() => void refetchStructure()}
            />
          </div>
        ),
        ...actionsColumnWidth(2),
      }),
    ];
  }, [canEdit?.can]);

  const table = useTable({
    columns,
    enableRowSelection: true,
    getRowId: (row) => String(row.id),
    enableColumnResizing: true,
    columnResizeMode: "onChange",
    defaultColumn: RESIZABLE_COLUMN_DEFAULTS,
    initialState: {
      columnPinning: { right: ["actions"] },
    },
    refineCoreProps: {
      resource: "students",
      pagination: { pageSize: 50 },
      filters: {
        permanent: [
          { field: "academicYear.id", operator: "eq", value: year.id },
          ...nodeFilters(node),
        ],
      },
    },
  });

  const selectedIds = Object.keys(table.reactTable.getState().rowSelection).map(Number);

  function pick(next: StructureNode) {
    setNode(next);
    table.reactTable.resetRowSelection();
  }

  /** The list refetches itself after a form save; a bulk action and the tree do not. */
  function changed() {
    void refetchStructure();
    void invalidate({ resource: "students", invalidates: ["list"] });
    table.reactTable.resetRowSelection();
  }

  const defaultClassId = node.type === "class" ? node.id : undefined;
  const groups = structure?.groups ?? [];

  return (
    // Exactly one screen tall — the sticky top bar (h-16) and the shell's own
    // top and bottom gutters come off — so the *page* never scrolls: the pager
    // stays on screen at the foot of the table and only the rows move under the
    // wheel. `flex-1` alone was not enough: the shell is `min-h-svh`, a minimum
    // rather than a height, so a tall table simply made the whole page taller.
    <ListView className="h-[calc(100svh-4rem-2*var(--shell-gutter-y))] min-h-[36rem]">
      <ListViewHeader
        canCreate={false}
        actions={
          canWrite?.can ? (
            <>
              <Button
                type="button"
                variant="outline"
                disabled={selectedIds.length === 0}
                onClick={() => setAssigning(selectedIds)}
              >
                Xếp lớp{selectedIds.length > 0 ? ` (${selectedIds.length})` : ""}
              </Button>
              <Button type="button" variant="outline" onClick={() => setImporting(true)}>
                Lấy học sinh năm trước
              </Button>
              <Button type="button" onClick={() => setEditing("new")}>
                <Plus />
                Thêm học sinh
              </Button>
            </>
          ) : undefined
        }
      />
      {/*
        The two panels take whatever height is left under the title, so they reach
        the bottom of the screen however little they hold, and stop there however
        much they hold: the pager is pinned to the foot of the table and the rows
        scroll between. `min-h-0` lets them shrink below their content, and the
        `minmax(0,1fr)` row keeps long content from stretching the track.
      */}
      <div className="grid min-h-0 flex-1 grid-rows-[minmax(0,1fr)] items-stretch gap-4 lg:grid-cols-[19rem_minmax(0,1fr)]">
        <Card className="h-full min-h-0 gap-0 overflow-hidden py-0">
          <StructureTree structure={structure} selected={node} onSelect={pick} className="min-h-0 flex-1" />
        </Card>

        <DataTable
          fill
          table={table}
          toolbar={
            <ListToolbar
              compact
              table={table}
              search={
                <DataTableQuickFilter
                  table={table}
                  placeholder="Tìm theo mã học sinh, họ tên hoặc số định danh…"
                />
              }
            />
          }
        />
      </div>

      {editing && (
        <StudentFormDialog
          year={year}
          groups={groups}
          record={editing === "new" ? null : editing}
          defaultClassId={defaultClassId}
          onClose={() => setEditing(null)}
          onSaved={changed}
        />
      )}
      {assigning && (
        <AssignClassDialog
          ids={assigning}
          groups={groups}
          onClose={() => setAssigning(null)}
          onDone={changed}
        />
      )}
      {importing && (
        <ImportDialog
          target={year}
          years={years}
          onClose={() => setImporting(false)}
          onDone={changed}
        />
      )}
    </ListView>
  );
}
