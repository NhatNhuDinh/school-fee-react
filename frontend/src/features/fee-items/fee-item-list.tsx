import { useCan } from "@refinedev/core";
import { useTable } from "@refinedev/react-table";
import { createColumnHelper } from "@tanstack/react-table";
import { Pencil, Plus } from "lucide-react";
import React from "react";

import type { AcademicYear } from "@/domains/academic-year/types";
import {
  FEE_CYCLE_LABELS,
  FEE_RULE_FLAGS,
  scheduleSummary,
  scopeSummary,
  type FeeItem,
} from "@/domains/fee-item/types";
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
import { formatCurrency } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Label } from "@/shared/ui/label";
import { Switch } from "@/shared/ui/switch";
import { FeeItemFormDialog } from "./fee-item-form-dialog";

/** How many rule badges a row shows before it says "+N". */
const RULES_SHOWN = 3;

/**
 * The khoản thu of the year chosen in the header. Add and edit are one dialog over
 * this page, as with students.
 *
 * Only the followed ones are listed unless the switch says otherwise: a khoản thu
 * that has been stopped is history, and keeping it in every list is clutter.
 */
export const FeeItemList = () => {
  const { enabled, selected: year, isLoading: yearsLoading } = useAcademicYear();

  if (!enabled) {
    return (
      <ListView>
        <ListViewHeader />
        <Card className="text-muted-foreground p-10 text-center text-sm">
          Khoản thu thuộc một năm học của một trường. Hãy đăng nhập bằng tài khoản của trường để xem.
        </Card>
      </ListView>
    );
  }
  if (!yearsLoading && !year) return <NoAcademicYear />;
  if (!year) return null;

  return <FeeItemsOfYear year={year} />;
};

function FeeItemsOfYear({ year }: { year: AcademicYear }) {
  const [editing, setEditing] = React.useState<FeeItem | "new" | null>(null);
  const [showInactive, setShowInactive] = React.useState(false);
  const { data: canWrite } = useCan({ resource: "fee_items", action: "create" });
  const { data: canEdit } = useCan({ resource: "fee_items", action: "edit" });

  const columns = React.useMemo(() => {
    const columnHelper = createColumnHelper<FeeItem>();

    return [
      sttColumn<FeeItem>(),
      columnHelper.accessor("name", {
        id: "name",
        size: 280,
        enableResizing: true,
        header: "Khoản thu",
        enableSorting: true,
        cell: ({ row }) => (
          <div className="flex flex-col leading-tight">
            <span className="font-medium">{row.original.name}</span>
            <span className="text-muted-foreground text-xs">{scopeSummary(row.original)}</span>
          </div>
        ),
      }),
      columnHelper.accessor("basePrice", {
        id: "basePrice",
        size: 190,
        header: "Đơn giá",
        enableSorting: true,
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div className="flex flex-col leading-tight">
              <span>
                {item.priceMode === "MULTIPLE"
                  ? "Nhiều đơn giá"
                  : `${formatCurrency(item.basePrice)} / ${item.measurementUnit.name}`}
              </span>
              <span className="text-muted-foreground text-xs">Thuế GTGT {item.vatRate}%</span>
            </div>
          );
        },
      }),
      columnHelper.accessor("collectionCycle", {
        id: "collectionCycle",
        size: 170,
        header: "Kỳ thu",
        enableSorting: true,
        cell: ({ row }) => (
          <div className="flex flex-col leading-tight">
            <span>{FEE_CYCLE_LABELS[row.original.collectionCycle]}</span>
            <span className="text-muted-foreground text-xs">{scheduleSummary(row.original)}</span>
          </div>
        ),
      }),
      columnHelper.display({
        id: "rules",
        header: "Quy tắc áp dụng",
        enableSorting: false,
        size: 280,
        cell: ({ row }) => <RuleBadges item={row.original} />,
      }),
      columnHelper.accessor("active", {
        id: "active",
        size: 130,
        header: "Trạng thái",
        enableSorting: true,
        cell: ({ getValue }) =>
          getValue() ? <Badge variant="success">Theo dõi</Badge> : <Badge variant="neutral">Ngừng</Badge>,
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
            <DeleteButton recordItemId={row.original.id} iconOnly size="icon-sm" />
          </div>
        ),
        ...actionsColumnWidth(2),
      }),
    ];
  }, [canEdit?.can]);

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
      resource: "fee_items",
      pagination: { pageSize: 50 },
      filters: {
        permanent: [
          { field: "academicYear.id", operator: "eq", value: year.id },
          // Always present, with a value that changes: a permanent filter that is
          // dropped from this list is not dropped from the query. "any" restricts nothing.
          { field: "active", operator: "eq", value: showInactive ? "any" : "true" },
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
            <Button type="button" onClick={() => setEditing("new")}>
              <Plus />
              Thêm khoản thu
            </Button>
          ) : undefined
        }
      />
      <div className="min-h-0 flex-1">
        <DataTable
          fill
          table={table}
          toolbar={
            <ListToolbar
              compact
              table={table}
              search={<DataTableQuickFilter table={table} placeholder="Tìm theo tên khoản thu…" />}
              actions={
                <div className="flex items-center gap-2">
                  <Switch
                    id="fee-item-show-inactive"
                    checked={showInactive}
                    onCheckedChange={setShowInactive}
                  />
                  <Label htmlFor="fee-item-show-inactive" className="cursor-pointer font-normal">
                    Hiển thị khoản ngừng theo dõi
                  </Label>
                </div>
              }
            />
          }
        />
      </div>

      {editing && (
        <FeeItemFormDialog
          year={year}
          record={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => undefined}
        />
      )}
    </ListView>
  );
}

/** The rules that apply, as small badges: the first few, then how many more. */
function RuleBadges({ item }: { item: FeeItem }) {
  const on = FEE_RULE_FLAGS.filter((flag) => item[flag.key]);
  if (on.length === 0) return <span className="text-muted-foreground">—</span>;
  const shown = on.slice(0, RULES_SHOWN);
  const rest = on.slice(RULES_SHOWN);

  return (
    <div className="flex items-center gap-1.5">
      {shown.map((flag) => (
        <Badge key={flag.key} variant={flag.key === "mandatory" ? "warning" : "neutral"}>
          {flag.label}
        </Badge>
      ))}
      {rest.length > 0 && (
        <Badge variant="outline" title={rest.map((flag) => flag.label).join(", ")}>
          +{rest.length}
        </Badge>
      )}
    </div>
  );
}
