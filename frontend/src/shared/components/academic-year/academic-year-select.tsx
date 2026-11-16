"use client";

import { useCan } from "@refinedev/core";
import { CalendarDays, Plus } from "lucide-react";
import { useNavigate } from "react-router";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";
import { useAcademicYear } from "./academic-year-context";

/**
 * The school-year selector in the header.
 *
 * Shown only to somebody who belongs to a school: a system administrator works
 * across schools and has no single year to work in. Disabled, not hidden, when
 * the school has no year yet, so the person can see why nothing is selected --
 * unless they may add one, in which case it stays open for the "Thêm năm học"
 * row at the foot of the list.
 */
/** Not a year id; picking it opens the create screen instead of choosing a year. */
const CREATE_VALUE = "__create__";

export function AcademicYearSelect() {
  const { enabled, years, selected, select, isLoading } = useAcademicYear();
  const navigate = useNavigate();
  const { data: canCreate } = useCan({
    resource: "academic_years",
    action: "create",
  });
  const mayCreate = canCreate?.can ?? false;

  if (!enabled) return null;

  return (
    <Select
      value={selected ? String(selected.id) : ""}
      onValueChange={(value) =>
        value === CREATE_VALUE
          ? navigate("/academic_years/create")
          : select(Number(value))
      }
      disabled={years.length === 0 && !mayCreate}
    >
      <SelectTrigger
        aria-label="Năm học"
        className="w-46 gap-2 [&>span]:flex [&>span]:items-center [&>span]:gap-2"
      >
        <CalendarDays className="text-muted-foreground size-4 shrink-0" />
        <SelectValue
          placeholder={isLoading ? "Đang tải…" : "Chưa có năm học"}
        />
      </SelectTrigger>
      <SelectContent align="end">
        {years.map((year) => (
          <SelectItem key={year.id} value={String(year.id)}>
            {year.name}
          </SelectItem>
        ))}
        {mayCreate && (
          <>
            {years.length > 0 && <SelectSeparator />}
            <SelectItem
              value={CREATE_VALUE}
              className="text-brand font-semibold"
            >
              <Plus className="size-4" />
              Thêm năm học
            </SelectItem>
          </>
        )}
      </SelectContent>
    </Select>
  );
}
