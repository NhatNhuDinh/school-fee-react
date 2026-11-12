import { useTable } from "@refinedev/react-table";
import { createColumnHelper } from "@tanstack/react-table";
import React from "react";

import {
  APPLICATION_PERIOD_LABELS,
  formatDiscount,
  formatPeriod,
  type PriorityCategory,
} from "@/domains/priority-category/types";
import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
import { NoAcademicYear } from "@/shared/components/academic-year/no-academic-year";
import { DeleteButton } from "@/shared/components/buttons/delete";
import { EditButton } from "@/shared/components/buttons/edit";
import {
  DataTableAdvancedFilter,
  DataTableFilterChips,
  type AdvancedFilterField,
} from "@/shared/components/data-table/data-table-advanced-filter";
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

const ADVANCED_FILTER_FIELDS: AdvancedFilterField[] = [
  { field: "name", label: "Tên diện ưu tiên", type: "text", placeholder: "Bất kỳ" },
  {
    field: "applicationPeriod",
    label: "Thời gian áp dụng",
    type: "select",
    placeholder: "Tất cả",
    options: Object.entries(APPLICATION_PERIOD_LABELS).map(([value, label]) => ({
      value,
      label,
    })),
  },
];

/**
 * The diện ưu tiên of the year chosen in the header. A system administrator has
 * no such year, so sees every school's.
 */
export const PriorityCategoryList = () => {
  const { enabled, selected, isLoading } = useAcademicYear();

  const columns = React.useMemo(() => {
    const columnHelper = createColumnHelper<PriorityCategory>();

    return [
      sttColumn<PriorityCategory>(),
      columnHelper.accessor("name", {
        id: "name",
        enableResizing: true,
        header: "Tên diện ưu tiên",
        enableSorting: true,
        cell: ({ getValue }) => <span className="font-medium">{getValue()}</span>,
      }),
      columnHelper.display({
        id: "discount",
        header: "Mức miễn giảm",
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-sm tabular-nums">
            {formatDiscount(row.original.discountType, row.original.discountValue)}
          </span>
        ),
      }),
      columnHelper.display({
        id: "applicationPeriod",
        header: "Thời gian áp dụng",
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-sm">
            {formatPeriod(
              row.original.applicationPeriod,
              row.original.fromMonth,
              row.original.toMonth
            )}
          </span>
        ),
      }),
      columnHelper.accessor("academicYear.name", {
        id: "academicYear",
        header: "Năm học",
        enableSorting: false,
        cell: ({ getValue }) => <span className="text-sm">{getValue()}</span>,
      }),
      columnHelper.accessor("organization.name", {
        id: "organization",
        header: "Trường (đơn vị)",
        enableSorting: false,
        cell: ({ getValue }) => <span className="text-sm">{getValue()}</span>,
      }),
      columnHelper.display({
        id: "actions",
        header: "Thao tác",
        cell: ({ row }) => (
          <div className="flex gap-1">
            <EditButton recordItemId={row.original.id} iconOnly size="icon-sm" />
            <DeleteButton recordItemId={row.original.id} iconOnly size="icon-sm" />
          </div>
        ),
        enableSorting: false,
        ...actionsColumnWidth(2),
      }),
    ];
  }, []);

  const yearId = selected?.id;

  const table = useTable({
    columns,
    enableColumnResizing: true,
    columnResizeMode: "onChange",
    defaultColumn: RESIZABLE_COLUMN_DEFAULTS,
    initialState: {
      columnPinning: { right: ["actions"] },
    },
    refineCoreProps: {
      syncWithLocation: true,
      // Always the header's year for a school; nothing for the administrator.
      filters: {
        permanent: yearId
          ? [{ field: "academicYear.id", operator: "eq", value: yearId }]
          : [],
      },
      // Not before the year is known, or the first fetch is every year's.
      queryOptions: { enabled: !enabled || Boolean(yearId) },
    },
  });

  if (enabled && !isLoading && !selected) return <NoAcademicYear />;

  return (
    <ListView>
      <ListViewHeader />
      <DataTable
        table={table}
        toolbar={
          <>
            <ListToolbar
              table={table}
              search={<DataTableQuickFilter table={table} />}
              filters={
                <DataTableAdvancedFilter table={table} fields={ADVANCED_FILTER_FIELDS} />
              }
            />
            <DataTableFilterChips
              table={table}
              fields={ADVANCED_FILTER_FIELDS}
              // The header's year is the list's scope, not a filter to show.
              hiddenFields={["academicYear.id"]}
            />
          </>
        }
      />
    </ListView>
  );
};
