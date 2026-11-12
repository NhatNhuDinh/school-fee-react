import { useCustomMutation, useList } from "@refinedev/core";
import { useMemo, useState } from "react";

import {
  GENDER_LABELS,
  nodeFilters,
  type StructureNode,
  type StudentRow,
} from "@/domains/student/types";
import type { AcademicYear } from "@/domains/academic-year/types";
import { API_URL } from "@/shared/api/constants";
import { formatDate } from "@/shared/lib/format";
import { Button } from "@/shared/ui/button";
import { Checkbox } from "@/shared/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/shared/ui/dialog";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table";
import { StructureTree } from "./structure-tree";
import { useStudentStructure } from "./use-student-structure";

type Props = {
  /** The year the students are carried into: the one being worked in. */
  target: { id: number; name: string };
  /** Every year of the school, newest first. */
  years: AcademicYear[];
  onClose: () => void;
  onDone: () => void;
};

/**
 * Carry the students of an earlier year into the current one. Each is offered
 * once: those already enrolled in the current year are left out by the API. They
 * arrive without a class, to be placed afterwards, as in the Jmix application.
 */
export function ImportDialog({ target, years, onClose, onDone }: Props) {
  // Only years that began before the target can be a source.
  const targetStart = years.find((y) => y.id === target.id)?.startDate ?? "";
  const sources = useMemo(
    () => years.filter((y) => y.id !== target.id && y.startDate < targetStart),
    [years, target.id, targetStart]
  );

  const [sourceId, setSourceId] = useState<string>(sources[0] ? String(sources[0].id) : "");
  const [node, setNode] = useState<StructureNode>({ type: "all" });
  const [search, setSearch] = useState("");
  const [picked, setPicked] = useState<Set<number>>(new Set());

  const { structure } = useStudentStructure(sourceId ? Number(sourceId) : undefined);

  const {
    result: { data: rows, total },
    query: { isLoading },
  } = useList<StudentRow>({
    resource: "students",
    pagination: { currentPage: 1, pageSize: 1000 },
    filters: [
      { field: "academicYear.id", operator: "eq", value: sourceId },
      { field: "notEnrolledIn", operator: "eq", value: String(target.id) },
      ...nodeFilters(node),
      ...(search.trim() ? [{ field: "q", operator: "contains" as const, value: search.trim() }] : []),
    ],
    sorters: [{ field: "student.fullName", order: "asc" }],
    queryOptions: { enabled: Boolean(sourceId) },
  });

  const { mutate, mutation } = useCustomMutation();

  const visible = rows ?? [];
  const allVisible = visible.length > 0 && visible.every((row) => picked.has(row.id));

  function toggleAll() {
    setPicked((current) => {
      const next = new Set(current);
      for (const row of visible) {
        if (allVisible) next.delete(row.id);
        else next.add(row.id);
      }
      return next;
    });
  }

  function toggle(id: number) {
    setPicked((current) => {
      const next = new Set(current);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }

  function submit() {
    mutate(
      {
        url: `${API_URL}/students/import`,
        method: "post",
        // The provider sends a JSON string with no content type of its own, which
        // the API answers 415; the rest of the data layer sets it per call.
        config: { headers: { "Content-Type": "application/json" } },
        values: {
          sourceAcademicYearId: Number(sourceId),
          targetAcademicYearId: target.id,
          ids: [...picked],
        },
        successNotification: (data) => ({
          type: "success",
          message: `Đã nhập ${(data?.data as { count?: number } | undefined)?.count ?? picked.size} học sinh`,
        }),
      },
      {
        onSuccess: () => {
          onDone();
          onClose();
        },
      }
    );
  }

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="flex h-[min(46rem,calc(100dvh-2rem))] flex-col gap-0 overflow-hidden p-0 sm:max-w-6xl">
        <DialogHeader className="border-b px-6 py-4 pe-12">
          <DialogTitle>Lấy học sinh từ năm trước</DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 border-b px-6 py-4 sm:grid-cols-[12rem_12rem_1fr]">
          <div className="grid gap-2">
            <Label id="import-source-label">Năm học nguồn</Label>
            <Select
              value={sourceId}
              onValueChange={(value) => {
                setSourceId(value);
                setNode({ type: "all" });
                setPicked(new Set());
              }}
            >
              <SelectTrigger className="w-full" aria-labelledby="import-source-label">
                <SelectValue placeholder="Chọn năm học" />
              </SelectTrigger>
              <SelectContent>
                {sources.map((year) => (
                  <SelectItem key={year.id} value={String(year.id)}>
                    {year.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="import-target">Năm học đích</Label>
            <Input id="import-target" value={target.name} readOnly disabled />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="import-search">Tìm kiếm học sinh</Label>
            <Input
              id="import-search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm theo mã học sinh hoặc họ tên…"
            />
          </div>
        </div>

        <div className="flex min-h-0 flex-1">
          <StructureTree
            structure={structure}
            selected={node}
            onSelect={setNode}
            className="w-72 shrink-0 border-e"
          />
          <div className="min-w-0 flex-1 overflow-auto">
            {sources.length === 0 ? (
              <p className="text-muted-foreground p-8 text-center text-sm">
                Trường chưa có năm học nào trước {target.name}.
              </p>
            ) : (
              <Table>
                <TableHeader className="bg-surface-subtle sticky top-0">
                  <TableRow>
                    <TableHead className="w-10 ps-4">
                      <Checkbox
                        checked={allVisible}
                        onCheckedChange={toggleAll}
                        aria-label="Chọn tất cả"
                      />
                    </TableHead>
                    <TableHead>Mã HS</TableHead>
                    <TableHead>Họ và tên</TableHead>
                    <TableHead>Ngày sinh</TableHead>
                    <TableHead>Giới tính</TableHead>
                    <TableHead>Lớp năm trước</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {visible.map((row) => (
                    <TableRow key={row.id} data-state={picked.has(row.id) ? "selected" : undefined}>
                      <TableCell className="ps-4">
                        <Checkbox
                          checked={picked.has(row.id)}
                          onCheckedChange={() => toggle(row.id)}
                          aria-label={`Chọn ${row.student.fullName}`}
                        />
                      </TableCell>
                      <TableCell>{row.student.studentCode}</TableCell>
                      <TableCell className="font-medium">{row.student.fullName}</TableCell>
                      <TableCell>
                        {row.student.dateOfBirth ? formatDate(row.student.dateOfBirth) : ""}
                      </TableCell>
                      <TableCell>
                        {row.student.gender ? GENDER_LABELS[row.student.gender] : ""}
                      </TableCell>
                      <TableCell>{row.schoolClass?.name ?? "Chưa xếp lớp"}</TableCell>
                    </TableRow>
                  ))}
                  {!isLoading && visible.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} className="text-muted-foreground py-10 text-center">
                        Không còn học sinh nào để nhập
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            )}
          </div>
        </div>

        <DialogFooter className="border-t px-6 py-4 sm:justify-between">
          <span className="text-muted-foreground self-center text-sm">
            {total ?? 0} học sinh có thể nhập
          </span>
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Hủy
            </Button>
            <Button type="button" disabled={picked.size === 0 || mutation.isPending} onClick={submit}>
              Nhập {picked.size} học sinh
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
