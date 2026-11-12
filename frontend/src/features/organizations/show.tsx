import { useShow } from "@refinedev/core";

import { LoadingOverlay } from "@/shared/components/layout/loading-overlay";
import {
  ShowView,
  ShowViewHeader,
} from "@/shared/components/views/show-view";
import { Badge } from "@/shared/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";
import { Separator } from "@/shared/ui/separator";
import type { Organization } from "@/domains/organization/types";
import { STATUS_LABELS } from "@/domains/organization/types";
import { formatDateTime } from "@/shared/lib/format";
import { ORGANIZATION_STATUS_VARIANTS } from "@/shared/lib/status-variants";

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-muted-foreground text-xs font-medium uppercase tracking-wide">
        {label}
      </span>
      <div className="text-sm">{children}</div>
    </div>
  );
}

export const OrganizationShow = () => {
  const { result: record, query } = useShow<Organization>({});
  const { isLoading } = query;

  return (
    <ShowView>
      <ShowViewHeader />
      <div className="flex flex-col gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              {record?.name ?? "—"}
              {record?.status ? (
                <Badge variant={ORGANIZATION_STATUS_VARIANTS[record.status] ?? "secondary"}>
                  {STATUS_LABELS[record.status] ?? record.status}
                </Badge>
              ) : null}
            </CardTitle>
            <CardDescription className="font-mono">
              {record?.code} · Mã đơn vị: {record?.id}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <LoadingOverlay loading={isLoading}>
              <div className="flex flex-col gap-6">
                <Field label="Mô tả">{record?.description ?? "—"}</Field>

                <div className="grid gap-6 sm:grid-cols-2">
                  <Field label="Email">{record?.email ?? "—"}</Field>
                  <Field label="Điện thoại">{record?.phone ?? "—"}</Field>
                </div>

                <Field label="Địa chỉ">{record?.address ?? "—"}</Field>

                <Separator />

                <div className="grid gap-6 sm:grid-cols-2">
                  <Field label="Ngày tạo">
                    {formatDateTime(record?.createdAt)}
                  </Field>
                  <Field label="Cập nhật lần cuối">
                    {formatDateTime(record?.updatedAt)}
                  </Field>
                </div>
              </div>
            </LoadingOverlay>
          </CardContent>
        </Card>
      </div>
    </ShowView>
  );
};
