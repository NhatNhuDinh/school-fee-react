"use client";

import { useCan } from "@refinedev/core";
import { CalendarDays, Plus } from "lucide-react";
import { Link } from "react-router";

import { Button } from "@/shared/ui/button";

/**
 * What a school sees on first opening the application, before it has any year.
 *
 * Every screen a school works in belongs to a year, so there is nothing to show
 * until one exists. The way out is a button, but only for somebody who may write
 * years: the others are told who to ask instead of being offered a button that
 * ends in a refusal.
 */
export function NoAcademicYear() {
  const { data: canCreate } = useCan({
    resource: "academic_years",
    action: "create",
  });
  const mayCreate = canCreate?.can ?? false;

  return (
    <div className="flex min-h-[70dvh] flex-col items-center justify-center gap-4 px-4 text-center">
      <div className="bg-primary/10 text-brand flex size-16 items-center justify-center rounded-full">
        <CalendarDays className="size-8" />
      </div>
      <div className="flex flex-col gap-1.5">
        <h1 className="text-2xl font-bold tracking-tight">Chưa có năm học</h1>
        <p className="text-muted-foreground max-w-sm text-sm">
          {mayCreate
            ? "Thêm năm học đầu tiên để bắt đầu làm việc."
            : "Hãy liên hệ quản trị viên của trường để tạo năm học."}
        </p>
      </div>
      {mayCreate && (
        <Button asChild size="lg">
          <Link to="/academic_years/create">
            <Plus className="size-4" />
            Thêm năm học
          </Link>
        </Button>
      )}
    </div>
  );
}
