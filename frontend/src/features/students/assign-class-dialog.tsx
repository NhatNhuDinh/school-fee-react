import { useCustomMutation } from "@refinedev/core";
import { useState } from "react";

import type { StructureGroup } from "@/domains/student/types";
import { API_URL } from "@/shared/api/constants";
import { Button } from "@/shared/ui/button";
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

type Props = {
  /** The enrollments to move. */
  ids: number[];
  groups: StructureGroup[];
  onClose: () => void;
  onDone: () => void;
};

const today = () => new Date().toISOString().slice(0, 10);

/** Put the chosen students into one class of their year, from a date. */
export function AssignClassDialog({ ids, groups, onClose, onDone }: Props) {
  const [classId, setClassId] = useState("");
  const [date, setDate] = useState(today());
  const { mutate, mutation } = useCustomMutation();

  function submit(event: React.FormEvent) {
    event.preventDefault();
    mutate(
      {
        url: `${API_URL}/students/assign-class`,
        method: "post",
        // The provider sends a JSON string with no content type of its own, which
        // the API answers 415; the rest of the data layer sets it per call.
        config: { headers: { "Content-Type": "application/json" } },
        values: { ids, schoolClassId: Number(classId), transferInDate: date },
        successNotification: () => ({
          type: "success",
          message: `Đã xếp lớp cho ${ids.length} học sinh`,
        }),
      },
      { onSuccess: () => { onDone(); onClose(); } }
    );
  }

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Xếp/chuyển lớp</DialogTitle>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-5">
          <div className="grid gap-2">
            <Label htmlFor="assign-class">Lớp cần chuyển</Label>
            <Select value={classId} onValueChange={setClassId}>
              <SelectTrigger id="assign-class" className="w-full">
                <SelectValue placeholder="Chọn lớp" />
              </SelectTrigger>
              <SelectContent>
                {groups.flatMap((group) =>
                  group.classes.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>
                      {c.name}
                    </SelectItem>
                  ))
                )}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="assign-date">Ngày chuyển đến</Label>
            <Input id="assign-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={!classId || !date || mutation.isPending}>
              Xác nhận xếp lớp
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
