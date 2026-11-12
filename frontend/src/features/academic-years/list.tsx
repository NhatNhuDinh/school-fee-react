import { useCan, useList } from "@refinedev/core";
import { useTable } from "@refinedev/react-table";
import { createColumnHelper } from "@tanstack/react-table";
import React from "react";
import { Link } from "react-router";

import {
  actionsColumnWidth,
  DataTable,
  RESIZABLE_COLUMN_DEFAULTS,
  sttColumn,
} from "@/shared/components/data-table/data-table";
import { DataTableQuickFilter } from "@/shared/components/data-table/data-table-quick-filter";
import {
  DataTableAdvancedFilter,
  DataTableFilterChips,
  type AdvancedFilterField,
} from "@/shared/components/data-table/data-table-advanced-filter";
import {
  ListToolbar,
  ListView,
  ListViewHeader,
} from "@/shared/components/views/list-view";
import { EditButton } from "@/shared/components/buttons/edit";
import { ShowButton } from "@/shared/components/buttons/show";
import { DeleteButton } from "@/shared/components/buttons/delete";
import type { AcademicYear } from "@/domains/academic-year/types";
import type { Organization } from "@/domains/organization/types";
import { formatDate } from "@/shared/lib/format";

export const AcademicYearList = () => {
  // Reading organizations is a separate grant from reading years, so the
  // school filter is offered only to somebody who can populate it.
  const { data: canReadOrganizations } = useCan({
    resource: "organizations",
    action: "list",
  });
  const mayReadOrganizations = canReadOrganizations?.can ?? false;

  const {
    result: { data: schools },
    query: { isLoading: schoolsLoading },
  } = useList<Organization>({
    resource: "organizations",
    pagination: { currentPage: 1, pageSize: 999 },
    sorters: [{ field: "name", order: "asc" }],
    queryOptions: { enabled: mayReadOrganizations },
  });

  const columns = React.useMemo(() => {
    const columnHelper = createColumnHelper<AcademicYear>();

    return [
      sttColumn<AcademicYear>(),
      columnHelper.accessor("name", {
        id: "name",
        header: "Năm học",
        enableSorting: true,
        cell: ({ getValue }) => <span className="font-medium">{getValue()}</span>,
      }),
      columnHelper.accessor("organization.name", {
        id: "organization",
        enableResizing: true,
        header: "Trường (đơn vị)",
        // Not sortable: `_sort=organization` is not a property the API accepts.
        enableSorting: false,
        cell: ({ row }) => (
          <Link
            to={`/organizations/show/${row.original.organization.id}`}
            className="text-sm hover:underline"
          >
            {row.original.organization.name}
          </Link>
        ),
      }),
      columnHelper.accessor("startDate", {
        id: "startDate",
        header: "Ngày bắt đầu",
        enableSorting: true,
        cell: ({ getValue }) => formatDate(getValue()),
      }),
      columnHelper.accessor("endDate", {
        id: "endDate",
        header: "Ngày kết thúc",
        enableSorting: true,
        cell: ({ getValue }) => formatDate(getValue()),
      }),
      columnHelper.display({
        id: "periods",
        header: "Học kỳ",
        enableSorting: false,
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm">
            {row.original.periods.length}
          </span>
        ),
      }),
      columnHelper.display({
        id: "actions",
        header: "Thao tác",
        cell: ({ row }) => (
          <div className="flex gap-1">
            <EditButton recordItemId={row.original.id} iconOnly size="icon-sm" />
            <ShowButton recordItemId={row.original.id} iconOnly size="icon-sm" />
            <DeleteButton recordItemId={row.original.id} iconOnly size="icon-sm" />
          </div>
        ),
        enableSorting: false,
        ...actionsColumnWidth(3),
      }),
    ];
  }, []);

  const advancedFilterFields = React.useMemo<AdvancedFilterField[]>(
    () => [
      { field: "name", label: "Năm học", type: "text", placeholder: "2025-2026" },
      ...(mayReadOrganizations
        ? [
            {
              field: "organization.id",
              label: "Trường (đơn vị)",
              type: "select" as const,
              placeholder: schoolsLoading ? "Đang tải…" : "Tất cả trường",
              options:
                schools?.map((item) => ({
                  label: item.name,
                  value: String(item.id),
                })) ?? [],
            },
          ]
        : []),
    ],
    [schools, schoolsLoading, mayReadOrganizations]
  );

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
    },
  });

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
              filters={<DataTableAdvancedFilter table={table} fields={advancedFilterFields} />}
            />
            <DataTableFilterChips table={table} fields={advancedFilterFields} />
          </>
        }
      />
    </ListView>
  );
};
