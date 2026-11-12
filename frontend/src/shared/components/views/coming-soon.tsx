import { Hammer } from "lucide-react";

import { ListView, ListViewHeader } from "@/shared/components/views/list-view";

/**
 * The screen behind a menu entry that has been migrated in name only.
 *
 * The title and breadcrumb come from the resource the URL names, so one
 * component serves every placeholder.
 */
export function ComingSoon() {
  return (
    <ListView>
      <ListViewHeader description="Màn hình này đang được chuyển từ hệ thống cũ." />
      <div className="bg-card text-muted-foreground flex min-h-[40dvh] flex-col items-center justify-center gap-3 rounded-lg border border-dashed p-8 text-center">
        <Hammer className="size-8" />
        <p className="text-sm">Sẽ có ở giai đoạn sau.</p>
      </div>
    </ListView>
  );
}
