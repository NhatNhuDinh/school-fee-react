import React from "react";
import { useNavigate } from "react-router";

import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
import { NoAcademicYear } from "@/shared/components/academic-year/no-academic-year";
import { ListView, ListViewHeader } from "@/shared/components/views/list-view";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/shared/ui/select";
import { ClassCollect } from "./class-collect";
import { useReceiptPrinter } from "./receipt-print";
import { StudentCollect } from "./student-collect";

type Mode = "student" | "class";

/**
 * Thu tiền at the counter: by student (cash, or a transfer shown as a QR) or by class, and the
 * history of what has been collected. The two modes share the toolbar's right-hand side.
 */
export const FeePaymentPage = () => {
  const { enabled, selected: year, isLoading } = useAcademicYear();

  if (!enabled) {
    return (
      <ListView>
        <ListViewHeader canCreate={false} />
        <Card className="text-muted-foreground p-10 text-center text-sm">
          Thu tiền thuộc một năm học của một trường. Hãy đăng nhập bằng tài khoản của trường để xem.
        </Card>
      </ListView>
    );
  }
  if (!isLoading && !year) return <NoAcademicYear />;
  if (!year) return null;

  return <PaymentsOfYear yearId={year.id} />;
};

function PaymentsOfYear({ yearId }: { yearId: number }) {
  const navigate = useNavigate();
  const [mode, setMode] = React.useState<Mode>("student");
  const { print, node } = useReceiptPrinter();

  const toolbarRight = (
    <>
      <Select value={mode} onValueChange={(value) => setMode(value as Mode)}>
        <SelectTrigger className="w-44">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="student">Thu theo học sinh</SelectItem>
          <SelectItem value="class">Thu theo lớp</SelectItem>
        </SelectContent>
      </Select>
      <Button type="button" variant="outline" onClick={() => navigate("/fee_payments/history")}>
        Lịch sử thu tiền
      </Button>
    </>
  );

  return (
    <ListView className="h-[calc(100svh-4rem-2*var(--shell-gutter-y))] min-h-[36rem]">
      <ListViewHeader canCreate={false} />
      {mode === "student" ? (
        <StudentCollect yearId={yearId} toolbarRight={toolbarRight} printReceipts={print} />
      ) : (
        <ClassCollect yearId={yearId} toolbarRight={toolbarRight} printReceipts={print} />
      )}
      {node}
    </ListView>
  );
}
