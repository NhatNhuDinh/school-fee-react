import { useCustom } from "@refinedev/core";
import { Search } from "lucide-react";
import { useMemo, useState } from "react";

import type { StudentStructure } from "@/domains/student/types";
import { API_URL } from "@/shared/api/constants";
import { customResult } from "@/shared/hooks/use-custom-result";
import { cn } from "@/shared/lib/utils";
import { Input } from "@/shared/ui/input";

type Props = {
  academicYearId: number;
  selectedClassId: number | null;
  onSelect: (classId: number) => void;
  className?: string;
};

/**
 * The year's khối and lớp with how many students each holds, for picking the lớp to
 * register. Only lớp can be picked — a registration is for the students of one
 * class — so a khối is a heading, not a choice. It reads the structure the student
 * list reads (`GET /students/structure`), which is where the counts live.
 */
export function ClassTree({ academicYearId, selectedClassId, onSelect, className }: Props) {
  const [search, setSearch] = useState("");

  const { result } = useCustom<StudentStructure>({
    url: `${API_URL}/students/structure`,
    method: "get",
    config: { query: { academicYearId } },
  });
  const structure = customResult(result?.data, "groups");

  const groups = useMemo(() => {
    const term = search.trim().toLowerCase();
    const all = structure?.groups ?? [];
    if (!term) return all;
    return all
      .map((group) => ({
        ...group,
        classes: group.classes.filter((c) => c.name.toLowerCase().includes(term)),
      }))
      .filter((group) => group.classes.length > 0);
  }, [structure, search]);

  return (
    <div className={cn("flex min-h-0 flex-col", className)}>
      <div className="border-b p-3">
        <div className="relative">
          <Search className="text-muted-foreground absolute start-3 top-1/2 size-4 -translate-y-1/2" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Tìm lớp…"
            className="ps-9"
          />
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto py-1" role="tree">
        {groups.map((group) => (
          <div key={group.id}>
            <div className="flex items-center justify-between px-4 py-2 text-sm font-semibold">
              <span className="truncate">{group.name}</span>
              <span className="text-muted-foreground font-normal tabular-nums">{group.studentCount}</span>
            </div>
            {group.classes.map((klass) => {
              const active = klass.id === selectedClassId;
              return (
                <div
                  key={klass.id}
                  role="treeitem"
                  aria-selected={active}
                  tabIndex={0}
                  onClick={() => onSelect(klass.id)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      onSelect(klass.id);
                    }
                  }}
                  className={cn(
                    "flex cursor-pointer items-center justify-between gap-2 py-2 ps-10 pe-4 text-sm",
                    "hover:bg-accent/50 focus-visible:bg-accent/50 outline-none",
                    active && "bg-brand-subtle text-brand font-semibold"
                  )}
                >
                  <span className="truncate">{klass.name}</span>
                  <span className="text-muted-foreground tabular-nums">{klass.studentCount}</span>
                </div>
              );
            })}
          </div>
        ))}
        {structure && groups.length === 0 && (
          <p className="text-muted-foreground px-4 py-6 text-center text-sm">Không có lớp nào</p>
        )}
      </div>
    </div>
  );
}
