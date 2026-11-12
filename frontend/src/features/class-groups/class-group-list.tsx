import { useTable } from "@refinedev/react-table";
import { createColumnHelper } from "@tanstack/react-table";
import React from "react";

import type { ClassGroup } from "@/domains/class-group/types";
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
import { formatDate } from "@/shared/lib/format";

const ADVANCED_FILTER_FIELDS: AdvancedFilterField[] = [
  { field: "name", label: "Tên khối lớp", type: "text", placeholder: "Bất kỳ" },
];

/**
 * The khối lớp of the year chosen in the header. A system administrator has no
 * such year, so sees every school's.
 */
export const ClassGroupList = () => {
  const { enabled, selected, isLoading } = useAcademicYear();

  const columns = React.useMemo(() => {
    const columnHelper = createColumnHelper<ClassGroup>();

    return [
      sttColumn<ClassGroup>(),
      columnHelper.accessor("name", {
        id: "name",
        enableResizing: true,
        header: "Tên khối lớp",
        enableSorting: true,
        cell: ({ getValue }) => <span className="font-medium">{getValue()}</span>,
      }),
      columnHelper.accessor("academicYear.name", {
        id: "academicYear",
        enableResizing: true,
        header: "Năm học",
        // Not sortable: `_sort=academicYear` is not a property the API accepts.
        enableSorting: false,
        cell: ({ getValue }) => <span className="text-sm">{getValue()}</span>,
      }),
      columnHelper.accessor("organization.name", {
        id: "organization",
        enableResizing: true,
        header: "Trường (đơn vị)",
        enableSorting: false,
        cell: ({ getValue }) => <span className="text-sm">{getValue()}</span>,
      }),
      columnHelper.accessor("createdAt", {
        id: "createdAt",
        header: "Ngày tạo",
        enableSorting: true,
        cell: ({ getValue }) => (
          <span className="text-muted-foreground text-sm">
            {formatDate(getValue())}
          </span>
        ),
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
