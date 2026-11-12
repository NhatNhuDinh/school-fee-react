import { useCan, useList } from "@refinedev/core";
import { useTable } from "@refinedev/react-table";
import { createColumnHelper } from "@tanstack/react-table";
import React from "react";

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
import { DeleteButton } from "@/shared/components/buttons/delete";
import type { Organization } from "@/domains/organization/types";
import type { SchoolCatalogItem } from "@/domains/school-catalog/types";
import { formatDate } from "@/shared/lib/format";

export type CatalogListProps = {
  /** Column heading for the name, e.g. "Tên hệ đào tạo". */
  nameLabel: string;
};

/**
 * The list of a catalogue a school shares across its years. One component serves
 * the training systems and the measurement units: both are a name under a
 * school, and the resource comes from the URL.
 */
export const CatalogList = ({ nameLabel }: CatalogListProps) => {
  // Reading organizations is a separate grant from reading the catalogue, so
  // the school filter is offered only to somebody who can populate it.
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
    const columnHelper = createColumnHelper<SchoolCatalogItem>();

    return [
      sttColumn<SchoolCatalogItem>(),
      columnHelper.accessor("name", {
        id: "name",
        enableResizing: true,
        header: nameLabel,
        enableSorting: true,
        cell: ({ getValue }) => <span className="font-medium">{getValue()}</span>,
      }),
      columnHelper.accessor("organization.name", {
        id: "organization",
        enableResizing: true,
        header: "Trường (đơn vị)",
        // Not sortable: `_sort=organization` is not a property the API accepts.
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
  }, [nameLabel]);

  const advancedFilterFields = React.useMemo<AdvancedFilterField[]>(
    () => [
      { field: "name", label: nameLabel, type: "text", placeholder: "Bất kỳ" },
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
    [nameLabel, schools, schoolsLoading, mayReadOrganizations]
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
