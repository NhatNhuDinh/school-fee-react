import { useShow } from "@refinedev/core";
import { Link } from "react-router";

import { LoadingOverlay } from "@/shared/components/layout/loading-overlay";
import {
  ShowView,
  ShowViewHeader,
} from "@/shared/components/views/show-view";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";
import { Separator } from "@/shared/ui/separator";
import type { AcademicYear } from "@/domains/academic-year/types";
import { formatDate, formatDateTime } from "@/shared/lib/format";

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-overline text-muted-foreground">{label}</span>
      <div className="text-sm">{children}</div>
    </div>
  );
}

export const AcademicYearShow = () => {
  const { result: record, query } = useShow<AcademicYear>({});
  const { isLoading } = query;

  return (
    <ShowView>
      <ShowViewHeader />
      <Card>
        <CardHeader>
          <CardTitle>{record?.name ?? "—"}</CardTitle>
          <CardDescription>Mã năm học: {record?.id}</CardDescription>
        </CardHeader>
        <CardContent>
          <LoadingOverlay loading={isLoading}>
            <div className="flex flex-col gap-6">
              <Field label="Trường (đơn vị)">
                {record?.organization ? (
                  <Link
                    to={`/organizations/show/${record.organization.id}`}
                    className="hover:underline"
                  >
                    {record.organization.name}
                  </Link>
                ) : (
                  "—"
                )}
              </Field>

              <div className="grid gap-6 sm:grid-cols-2">
                <Field label="Ngày bắt đầu">{formatDate(record?.startDate)}</Field>
                <Field label="Ngày kết thúc">{formatDate(record?.endDate)}</Field>
              </div>

              <Separator />

              <Field label="Học kỳ">
                {record?.periods?.length ? (
                  <ul className="flex flex-col gap-2">
                    {record.periods.map((period) => (
                      <li
                        key={period.id ?? period.name}
                        className="flex items-baseline justify-between gap-4 rounded-md border px-3 py-2"
                      >
                        <span className="font-medium">{period.name}</span>
                        <span className="text-muted-foreground text-xs">
                          {formatDate(period.startDate)} –{" "}
                          {formatDate(period.endDate)}
                        </span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  "—"
                )}
              </Field>

              <Separator />

              <div className="grid gap-6 sm:grid-cols-2">
                <Field label="Ngày tạo">{formatDateTime(record?.createdAt)}</Field>
                <Field label="Cập nhật lần cuối">
                  {formatDateTime(record?.updatedAt)}
                </Field>
              </div>
            </div>
          </LoadingOverlay>
        </CardContent>
      </Card>
    </ShowView>
  );
};
