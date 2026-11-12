import { useCan, useCreate, useList, useUpdate } from "@refinedev/core";
import { Pencil, Plus } from "lucide-react";
import { useState } from "react";

import type { SchoolClass } from "@/domains/class-group/types";
import { DeleteButton } from "@/shared/components/buttons/delete";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table";

/**
 * The lớp of one khối lớp, edited in place on the khối lớp's edit page.
 *
 * Each change is written the moment its dialog is submitted, through
 * `school_classes`, rather than riding on the khối lớp's own Save: a lớp is a
 * record of its own with its own permission, and the khối lớp form has nothing
 * to say about it.
 */
export function ClassesPanel({ classGroupId }: { classGroupId: number }) {
  const {
    result: { data: classes },
    query: { isLoading },
  } = useList<SchoolClass>({
    resource: "school_classes",
    filters: [{ field: "classGroup.id", operator: "eq", value: classGroupId }],
    pagination: { currentPage: 1, pageSize: 200 },
    sorters: [{ field: "name", order: "asc" }],
  });

  const { data: canCreate } = useCan({ resource: "school_classes", action: "create" });
  const { data: canEdit } = useCan({ resource: "school_classes", action: "edit" });

  // `"new"` while adding, the row while renaming, null while the dialog is shut.
  const [editing, setEditing] = useState<SchoolClass | "new" | null>(null);

  return (
    <div className="space-y-3">
      {/*
        The button sits above the panel, not in a band inside it. The table's own
        header row already says what the columns are; a second band reading "Lớp"
        over the "Tên lớp" row was a header on top of a header.
      */}
      {canCreate?.can && (
        <div className="flex justify-end">
          {/* `type="button"`: this panel renders inside the khối lớp's <form>. */}
          <Button type="button" onClick={() => setEditing("new")}>
            <Plus />
            Thêm lớp
          </Button>
        </div>
      )}

      <Card className="gap-0 overflow-hidden py-0">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="px-6">Tên lớp</TableHead>
            <TableHead className="w-24 px-6">Thao tác</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {(classes ?? []).map((row) => (
            <TableRow key={row.id}>
              <TableCell className="px-6 font-medium">{row.name}</TableCell>
              <TableCell className="px-6">
                <div className="flex gap-1">
                  {canEdit?.can && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Sửa"
                      onClick={() => setEditing(row)}
                    >
                      <Pencil />
                    </Button>
                  )}
                  <DeleteButton
                    resource="school_classes"
                    recordItemId={row.id}
                    iconOnly
                    size="icon-sm"
                  />
                </div>
              </TableCell>
            </TableRow>
          ))}
          {!isLoading && (classes ?? []).length === 0 && (
            <TableRow>
              <TableCell colSpan={2} className="text-muted-foreground px-6 py-8 text-center">
                Chưa có lớp nào
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      </Card>

      {editing && (
        <ClassDialog
          classGroupId={classGroupId}
          row={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  );
}

function ClassDialog({
  classGroupId,
  row,
  onClose,
}: {
  classGroupId: number;
  row: SchoolClass | null;
  onClose: () => void;
}) {
  const [name, setName] = useState(row?.name ?? "");
  const { mutate: create, mutation: creating } = useCreate();
  const { mutate: update, mutation: updating } = useUpdate();
  const pending = creating.isPending || updating.isPending;

  function submit(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    // The dialog is portaled out of the khối lớp's form, so this submit cannot
    // reach it; `stopPropagation` is belt and braces for React's synthetic tree.
    event.stopPropagation();

    const options = { onSuccess: onClose };
    if (row) {
      update(
        { resource: "school_classes", id: row.id, values: { name: trimmed } },
        options
      );
    } else {
      create(
        {
          resource: "school_classes",
          values: { classGroup: { id: classGroupId }, name: trimmed },
        },
        options
      );
    }
  }

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{row ? "Sửa lớp" : "Thêm lớp"}</DialogTitle>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-6">
          <div className="grid gap-2">
            <Label htmlFor="class-name">Tên lớp</Label>
            <Input
              id="class-name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="10A1"
              maxLength={255}
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={pending || !name.trim()}>
              {row ? "Lưu" : "Thêm mới"}
            </Button>
            <Button type="button" variant="outline" onClick={onClose}>
              Hủy
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
