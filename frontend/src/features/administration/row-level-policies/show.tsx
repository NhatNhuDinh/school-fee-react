import { useCan, useShow } from "@refinedev/core";
import { TriangleAlert } from "lucide-react";

import { LoadingOverlay } from "@/shared/components/layout/loading-overlay";
import {
  ShowView,
  ShowViewHeader,
} from "@/shared/components/views/show-view";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Badge } from "@/shared/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";
import { Separator } from "@/shared/ui/separator";
import { ExplainPanel } from "./explain-panel";
import { actionLabel, kindLabel, resourceTitle } from "./labels";
import { readCondition } from "@/domains/row-level-policy/read-condition";
import type { RowLevelPolicy } from "@/domains/row-level-policy/types";
import { formatDateTime } from "@/shared/lib/format";

export const RowLevelPolicyShow = () => {
  const { result: record, query } = useShow<RowLevelPolicy>({});
  const { isLoading } = query;

  // The explain and simulate endpoints take somebody else's account id, so they
  // require users:read on top of the policy permission. Without it they would
  // 403, so the panel is not offered rather than offered and then refused.
  const { data: canReadUsers } = useCan({ resource: "users", action: "list" });
  const mayExplain = canReadUsers?.can ?? false;

  return (
    <ShowView>
      <ShowViewHeader />

      <Card>
        <CardHeader>
          <CardTitle className="flex flex-wrap items-center gap-2">
            {record?.name ?? "—"}
            {record && (
              <Badge variant={record.kind === "FILTER" ? "default" : "secondary"}>
                {kindLabel(record.kind)}
              </Badge>
            )}
            {record && !record.enabled && <Badge variant="outline">Đã tắt</Badge>}
          </CardTitle>
          <CardDescription className="font-mono">
            {resourceTitle(record?.resource)} · {actionLabel(record?.action)} ·{" "}
            {record?.role ? record.role.code : "mọi người"}
          </CardDescription>
        </CardHeader>

        <CardContent>
          <LoadingOverlay loading={isLoading}>
            <div className="flex flex-col gap-6">
              {record?.invalidReason && (
                <Alert variant="destructive">
                  <TriangleAlert className="h-4 w-4" />
                  <AlertDescription>
                    Đã bị tắt khi khởi động vì không còn biên dịch được:{" "}
                    {record.invalidReason}
                    {record.kind === "FILTER" && (
                      <>
                        {" "}
                        Vì đây là bộ lọc, mọi thao tác trên{" "}
                        <span className="font-mono">
                          {record.resource}:{record.action}
                        </span>{" "}
                        đều bị từ chối cho đến khi được sửa — một điều cấm ngừng
                        có hiệu lực là một lỗ hổng, và mất một quy tắc trong im
                        lặng còn tệ hơn việc mất cả màn hình một cách rõ ràng.
                      </>
                    )}
                  </AlertDescription>
                </Alert>
              )}

              <div className="flex flex-col gap-1">
                <span className="text-muted-foreground text-xs font-medium uppercase tracking-wide">
                  Chức năng
                </span>
                <p className="text-sm">
                  {record?.kind === "FILTER"
                    ? "Loại bỏ dòng dữ liệu đối với mọi người, bất kể họ giữ vai trò nào. Bộ lọc không gắn với vai trò nào, nên không thể né tránh."
                    : `Mở rộng phạm vi dữ liệu mà ${record?.role?.code ?? "một vai trò"} có thể truy cập. Phạm vi này được hợp với các phạm vi khác của tài khoản, nên giữ thêm một vai trò chỉ làm tăng thêm dòng dữ liệu.`}
                </p>
              </div>

              {record?.description && (
                <div className="flex flex-col gap-1">
                  <span className="text-muted-foreground text-xs font-medium uppercase tracking-wide">
                    Mô tả
                  </span>
                  <p className="text-sm">{record.description}</p>
                </div>
              )}

              <Separator />

              <div className="flex flex-col gap-2">
                <span className="text-muted-foreground text-xs font-medium uppercase tracking-wide">
                  Điều kiện
                </span>
                <p className="text-sm">
                  {record ? readCondition(record.scope) : "—"}
                </p>
                {/*
                  The stored tree as well as the sentence. Anybody debugging a
                  policy eventually wants to see exactly what is in the column,
                  and the alternative is opening devtools to find out.
                */}
                <pre className="bg-muted overflow-x-auto rounded-md p-3 text-xs">
                  {record ? JSON.stringify(record.scope, null, 2) : ""}
                </pre>
              </div>

              {record?.action === "write" && (
                <div className="flex flex-col gap-2">
                  <span className="text-muted-foreground text-xs font-medium uppercase tracking-wide">
                    Trạng thái được phép sau khi lưu
                  </span>
                  {record.checkScope ? (
                    <>
                      <p className="text-sm">{readCondition(record.checkScope)}</p>
                      <pre className="bg-muted overflow-x-auto rounded-md p-3 text-xs">
                        {JSON.stringify(record.checkScope, null, 2)}
                      </pre>
                    </>
                  ) : (
                    <p className="text-sm">
                      Cùng điều kiện đó, áp dụng cho bản ghi sau khi lưu — để
                      bản ghi không bị chuyển đến nơi mà người ghi không nhìn
                      thấy được.
                    </p>
                  )}
                </div>
              )}

              <Separator />

              <div className="text-muted-foreground grid gap-2 text-xs sm:grid-cols-2">
                <span>
                  Tạo bởi {record?.createdBy?.username ?? "—"}
                  {record?.createdAt
                    ? ` vào ${formatDateTime(record.createdAt)}`
                    : ""}
                </span>
                <span>
                  Sửa lần cuối bởi {record?.updatedBy?.username ?? "—"}
                  {record?.updatedAt
                    ? ` vào ${formatDateTime(record.updatedAt)}`
                    : ""}
                </span>
              </div>
            </div>
          </LoadingOverlay>
        </CardContent>
      </Card>

      {record && mayExplain && (
        <ExplainPanel resource={record.resource} action={record.action} />
      )}
    </ShowView>
  );
};
