import { useCustomMutation } from "@refinedev/core";
import { ArrowLeft, ArrowRight, ChevronDown, ChevronLeft, ChevronRight, Search } from "lucide-react";
import React from "react";

import type { FeeCollectionBatch } from "@/domains/fee-collection-batch/types";
import {
  COLLECT_STATUS_LABELS,
  NOTIFY_STATUS_LABELS,
  type CreatedNotification,
  type SelectionMode,
} from "@/domains/fee-notification/types";
import { API_URL } from "@/shared/api/constants";
import { DataTablePagination } from "@/shared/components/data-table/data-table-pagination";
import { DatePicker } from "@/shared/components/form/date-picker";
import { TimePicker } from "@/shared/components/form/time-picker";
import { formatCurrency, formatNumber } from "@/shared/lib/format";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Checkbox } from "@/shared/ui/checkbox";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/shared/ui/dialog";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/shared/ui/select";
import { Textarea } from "@/shared/ui/textarea";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/shared/ui/table";
import { CollectStatusLabel, NotifyStatusLabel } from "./notification-status";
import { Segmented } from "./segmented";
import {
  fetchCandidateIds,
  useCandidates,
  useDebounced,
  useNotificationDefaults,
  useNotificationPreview,
  useNotificationTree,
  type CandidateView,
} from "./use-notification-api";

type Props = {
  batch: FeeCollectionBatch;
  onClose: () => void;
  /** Called once the API has made the notification; the caller refreshes and says what happened. */
  onCreated: (created: CreatedNotification, draft: boolean) => void;
};

const MODE_OPTIONS: { value: SelectionMode; label: string }[] = [
  { value: "ALL", label: "Cả đợt" },
  { value: "UNNOTIFIED", label: "Chưa thông báo" },
  { value: "DEBT", label: "Còn nợ" },
];

const COLLECT_OPTIONS = [
  { value: "any", label: "Thu tiền: Tất cả" },
  { value: "unpaid", label: `Thu tiền: ${COLLECT_STATUS_LABELS.UNPAID}` },
  { value: "partial", label: `Thu tiền: ${COLLECT_STATUS_LABELS.PARTIAL}` },
  { value: "paid", label: `Thu tiền: ${COLLECT_STATUS_LABELS.PAID}` },
] as const;

const NOTIFY_OPTIONS = [
  { value: "any", label: "Thông báo: Tất cả" },
  { value: "none", label: `Thông báo: ${NOTIFY_STATUS_LABELS.NONE}` },
  { value: "queued", label: `Thông báo: ${NOTIFY_STATUS_LABELS.QUEUED}` },
  { value: "sent", label: `Thông báo: ${NOTIFY_STATUS_LABELS.SENT}` },
  { value: "failed", label: "Thông báo: Gửi lỗi / thiếu email" },
] as const;

type Node = { kind: "all" } | { kind: "group"; id: number; name: string } | { kind: "class"; id: number; name: string };

const pad = (value: number) => String(value).padStart(2, "0");
const localDate = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const localTime = (date: Date) => `${pad(date.getHours())}:${pad(date.getMinutes())}`;

/**
 * Making a đợt thông báo, in two steps.
 *
 * Step one picks the students: the tree of khối and lớp on the left narrows the list on the
 * right, the three tabs above it say which of them to show (everyone, the ones not told yet, the
 * ones who still owe), and the table says what each owes and what each has been told. The picking
 * is a set of ids held here, because "select all" has to reach students the page does not hold: it
 * asks the API for every id the view reaches.
 *
 * Step two writes the words and shows, beside them, the e-mail one of those students would receive
 * — rendered by the API from the same code that sends it, so what is read here is what arrives.
 * Nothing is written until the last button; "lưu nháp" writes a draft, the other schedules it.
 */
export function NotificationComposeDialog({ batch, onClose, onCreated }: Props) {
  const [step, setStep] = React.useState<1 | 2>(1);

  // ---- step one: who
  const [mode, setMode] = React.useState<SelectionMode>("UNNOTIFIED");
  const [node, setNode] = React.useState<Node>({ kind: "all" });
  const [collect, setCollect] = React.useState<CandidateView["collect"]>("any");
  const [notify, setNotify] = React.useState<CandidateView["notify"]>("any");
  const [search, setSearch] = React.useState("");
  const [treeSearch, setTreeSearch] = React.useState("");
  const [page, setPage] = React.useState(1);
  const [pageSize, setPageSize] = React.useState(50);
  const [selected, setSelected] = React.useState<Map<number, number>>(new Map());
  const [selecting, setSelecting] = React.useState(false);

  const q = useDebounced(search, 300);
  const view: CandidateView = {
    mode,
    classGroupId: node.kind === "group" ? node.id : null,
    schoolClassId: node.kind === "class" ? node.id : null,
    collect,
    notify,
    q,
  };
  const { data: candidates, isFetching } = useCandidates(batch.id, view, page, pageSize);
  const { tree } = useNotificationTree(batch.id, { ...view, q: "" });
  const rows = candidates?.rows ?? [];

  // The first view opens with everyone who owes something already picked.
  const initialised = React.useRef(false);
  React.useEffect(() => {
    if (initialised.current) return;
    initialised.current = true;
    void selectView({ ...view, classGroupId: null, schoolClassId: null, q: "" }, new Map(), false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function selectView(target: CandidateView, base: Map<number, number>, keepExisting = true) {
    setSelecting(true);
    try {
      const ids = await fetchCandidateIds(batch.id, target, true);
      const next = keepExisting ? new Map(base) : new Map<number, number>();
      for (const { id, outstanding } of ids) next.set(id, outstanding);
      setSelected(next);
    } finally {
      setSelecting(false);
    }
  }

  function changeNode(next: Node) {
    setNode(next);
    setPage(1);
  }

  function toggle(id: number, outstanding: number, on: boolean) {
    setSelected((previous) => {
      const next = new Map(previous);
      if (on) next.set(id, outstanding);
      else next.delete(id);
      return next;
    });
  }

  const pageIds = rows.map((row) => row.batchStudentId);
  const pageSelected = pageIds.filter((id) => selected.has(id)).length;
  const selectedTotal = [...selected.values()].reduce((sum, value) => sum + value, 0);
  const nodeLabel = node.kind === "all" ? "Toàn trường" : node.name;

  // ---- step two: what
  const defaults = useNotificationDefaults(batch.id);
  const [subject, setSubject] = React.useState<string | null>(null);
  const [content, setContent] = React.useState<string | null>(null);
  const [deadline, setDeadline] = React.useState<string | null>(null);
  const [sendDate, setSendDate] = React.useState(() => localDate(new Date(Date.now() + 5 * 60_000)));
  const [sendTime, setSendTime] = React.useState(() => localTime(new Date(Date.now() + 5 * 60_000)));
  const [previewIndex, setPreviewIndex] = React.useState(0);
  const [errors, setErrors] = React.useState<Record<string, string>>({});

  // What the person has typed wins; until then the defaults the API proposes.
  const subjectValue = subject ?? defaults?.subject ?? "";
  const contentValue = content ?? defaults?.content ?? "";
  const deadlineValue = deadline ?? defaults?.deadline ?? "";

  const orderedIds = React.useMemo(() => [...selected.keys()], [selected]);
  const safeIndex = Math.min(previewIndex, Math.max(orderedIds.length - 1, 0));
  const { preview, isFetching: previewing, error: previewError } = useNotificationPreview(
    {
      batchId: batch.id,
      batchStudentId: orderedIds[safeIndex] ?? null,
      subject: subjectValue,
      content: contentValue,
      deadline: deadlineValue,
    },
    step === 2
  );

  const { mutate: create, mutation } = useCustomMutation<CreatedNotification>();

  function submit(draft: boolean) {
    const next: Record<string, string> = {};
    if (!subjectValue.trim()) next.subject = "Nhập tiêu đề";
    if (!contentValue.trim()) next.content = "Nhập nội dung";
    if (!deadlineValue) next.deadline = "Chọn hạn đóng";
    else if (deadlineValue < localDate(new Date())) next.deadline = "Hạn đóng không được ở quá khứ";
    let sendAt: string | null = null;
    if (!draft) {
      const moment = new Date(`${sendDate}T${sendTime}`);
      if (!sendDate || !sendTime || Number.isNaN(moment.getTime())) next.sendAt = "Chọn thời điểm gửi";
      else sendAt = moment.toISOString();
    }
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    create(
      {
        url: `${API_URL}/fee_notifications`,
        method: "post",
        config: { headers: { "Content-Type": "application/json" } },
        values: {
          batch: { id: batch.id },
          batchStudentIds: orderedIds,
          subject: subjectValue.trim(),
          content: contentValue,
          deadline: deadlineValue,
          sendAt,
          draft,
        },
        successNotification: false,
      },
      {
        onSuccess: (response) => {
          const created = (response as { data?: CreatedNotification }).data;
          if (created) onCreated(created, draft);
        },
      }
    );
  }

  const stepLabel = step === 1 ? "Bước 1/2 · Chọn người nhận" : "Bước 2/2 · Soạn nội dung";

  return (
    <Dialog open onOpenChange={(open) => !open && !mutation.isPending && onClose()}>
      <DialogContent
        aria-describedby={undefined}
        className="flex h-[min(760px,calc(100dvh-2rem))] max-w-none flex-col gap-0 overflow-hidden p-0 sm:max-w-[min(1300px,calc(100vw-2rem))]"
      >
        <DialogHeader className="relative flex h-14 shrink-0 flex-row items-center border-b px-6 pe-14">
          <DialogTitle className="text-base">Lập đợt thông báo</DialogTitle>
          <div className="absolute inset-x-0 flex items-center justify-center gap-3" aria-hidden={false}>
            <span className="flex gap-1.5" aria-hidden>
              <span className="bg-primary h-1 w-7 rounded-full" />
              <span className={cn("h-1 w-7 rounded-full", step === 2 ? "bg-primary" : "bg-border")} />
            </span>
            <span className="text-[13px] font-medium">{stepLabel}</span>
          </div>
        </DialogHeader>

        {step === 1 ? (
          <div className="grid min-h-0 flex-1 grid-cols-[380px_minmax(0,1fr)]">
            {/* who: the tree */}
            <div className="flex min-h-0 flex-col gap-3 border-e p-5">
              <Segmented
                label="Chọn học sinh theo"
                size="sm"
                value={mode}
                onChange={(value) => {
                  setMode(value);
                  setPage(1);
                }}
                options={MODE_OPTIONS}
                className="w-full [&>button]:flex-auto [&>button]:px-2"
              />
              <div className="relative">
                <Search className="text-muted-foreground absolute start-3 top-1/2 size-4 -translate-y-1/2" />
                <Input
                  className="ps-9"
                  placeholder="Tìm khối, lớp…"
                  value={treeSearch}
                  onChange={(event) => setTreeSearch(event.target.value)}
                />
              </div>
              <div className="min-h-0 flex-1 overflow-y-auto">
                <GradeTree
                  tree={tree}
                  node={node}
                  filter={treeSearch}
                  onSelect={changeNode}
                  allLabel="Toàn trường"
                />
              </div>
            </div>

            {/* who: the list */}
            <div className="relative flex min-h-0 flex-col">
              <div className="flex shrink-0 flex-wrap items-center gap-2 px-5 py-3">
                <div className="relative w-72">
                  <Search className="text-muted-foreground absolute start-3 top-1/2 size-4 -translate-y-1/2" />
                  <Input
                    className="ps-9"
                    placeholder="Tìm học sinh…"
                    value={search}
                    onChange={(event) => {
                      setSearch(event.target.value);
                      setPage(1);
                    }}
                  />
                </div>
                <FilterSelect
                  value={collect}
                  options={COLLECT_OPTIONS}
                  onChange={(value) => {
                    setCollect(value);
                    setPage(1);
                  }}
                  label="Lọc theo tình trạng thu tiền"
                />
                <FilterSelect
                  value={notify}
                  options={NOTIFY_OPTIONS}
                  onChange={(value) => {
                    setNotify(value);
                    setPage(1);
                  }}
                  label="Lọc theo tình trạng thông báo"
                  className="w-52"
                />
                <span className="text-muted-foreground ms-auto text-xs tabular-nums">
                  {nodeLabel} · {formatNumber(candidates?.total ?? 0)} học sinh
                </span>
              </div>

              <div className="min-h-0 flex-1 overflow-y-auto">
                <Table>
                  <TableHeader sticky>
                    <TableRow>
                      <TableHead className="w-10 ps-5">
                        <Checkbox
                          aria-label="Chọn cả trang"
                          checked={rows.length > 0 && pageSelected === rows.length ? true : pageSelected > 0 ? "indeterminate" : false}
                          onCheckedChange={(on) => {
                            for (const row of rows) toggle(row.batchStudentId, row.outstandingAmount, on === true && row.outstandingAmount > 0);
                          }}
                        />
                      </TableHead>
                      <TableHead className="w-12">STT</TableHead>
                      <TableHead>Học sinh</TableHead>
                      <TableHead className="w-20">Lớp</TableHead>
                      <TableHead className="w-28 text-end">Phải thu</TableHead>
                      <TableHead className="w-28 text-end">Đã thu</TableHead>
                      <TableHead className="w-28 text-end">Còn nợ</TableHead>
                      <TableHead className="w-36">Thu tiền</TableHead>
                      <TableHead className="w-36">Thông báo</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rows.map((row, index) => (
                      <TableRow
                        key={row.batchStudentId}
                        className={cn(row.outstandingAmount <= 0 && "text-muted-foreground")}
                      >
                        <TableCell className="ps-5">
                          <Checkbox
                            aria-label={`Chọn ${row.studentName}`}
                            checked={selected.has(row.batchStudentId)}
                            disabled={row.outstandingAmount <= 0}
                            onCheckedChange={(on) => toggle(row.batchStudentId, row.outstandingAmount, on === true)}
                          />
                        </TableCell>
                        <TableCell className="text-muted-foreground tabular-nums">
                          {(page - 1) * pageSize + index + 1}
                        </TableCell>
                        <TableCell>
                          <div className="flex flex-col leading-tight">
                            <span className="font-medium">{row.studentName}</span>
                            <span className="text-muted-foreground text-xs">
                              {row.studentCode}
                              {!row.hasEmail && " · chưa có email phụ huynh"}
                            </span>
                          </div>
                        </TableCell>
                        <TableCell>{row.className}</TableCell>
                        <TableCell className="text-end tabular-nums">{formatNumber(row.receivableAmount)}</TableCell>
                        <TableCell className="text-end tabular-nums">{formatNumber(row.collectedAmount)}</TableCell>
                        <TableCell className="text-end font-semibold tabular-nums">
                          {formatNumber(row.outstandingAmount)}
                        </TableCell>
                        <TableCell>
                          <CollectStatusLabel status={row.collectStatus} />
                        </TableCell>
                        <TableCell>
                          <NotifyStatusLabel status={row.notifyStatus} />
                        </TableCell>
                      </TableRow>
                    ))}
                    {rows.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={9} className="text-muted-foreground py-10 text-center">
                          {isFetching ? "Đang tải…" : "Không có học sinh nào khớp"}
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </div>

              <div className="shrink-0 border-t px-3 py-2">
                <DataTablePagination
                  currentPage={page}
                  pageCount={Math.max(1, Math.ceil((candidates?.total ?? 0) / pageSize))}
                  setCurrentPage={setPage}
                  pageSize={pageSize}
                  setPageSize={(size) => {
                    setPageSize(size);
                    setPage(1);
                  }}
                  total={candidates?.total ?? 0}
                />
              </div>

              {selected.size > 0 && (
                <div
                  role="status"
                  className="bg-foreground text-background shadow-e3 absolute inset-x-0 bottom-14 mx-auto flex w-fit items-center gap-4 rounded-full px-5 py-2.5 text-[13px]"
                >
                  <span className="font-semibold tabular-nums">{formatNumber(selected.size)} đã chọn</span>
                  <button
                    type="button"
                    disabled={selecting}
                    className="opacity-80 hover:opacity-100 disabled:opacity-50"
                    onClick={() => void selectView(view, selected)}
                  >
                    Thêm tất cả {formatNumber(candidates?.total ?? 0)} học sinh của {nodeLabel}
                  </button>
                  <button
                    type="button"
                    className="font-medium underline-offset-4 hover:underline"
                    onClick={() => setSelected(new Map())}
                  >
                    Bỏ chọn
                  </button>
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="grid min-h-0 flex-1 grid-cols-[560px_minmax(0,1fr)]">
            {/* what: the words */}
            <div className="min-h-0 space-y-5 overflow-y-auto p-6">
              <div className="space-y-1.5">
                <Label className="text-muted-foreground text-xs">Gửi tới</Label>
                <div className="bg-surface-subtle flex items-center justify-between rounded-lg border px-4 py-2.5 text-[13px]">
                  <span>
                    <span className="font-semibold">{formatNumber(selected.size)} học sinh</span>
                    <span className="text-muted-foreground">
                      {" "}
                      · còn phải thu {formatCurrency(selectedTotal)}
                    </span>
                  </span>
                  <button type="button" className="text-brand font-medium" onClick={() => setStep(1)}>
                    Chỉnh sửa
                  </button>
                </div>
              </div>

              <Field label="Tiêu đề" error={errors.subject} required>
                <Input
                  value={subjectValue}
                  maxLength={500}
                  onChange={(event) => setSubject(event.target.value)}
                  aria-invalid={Boolean(errors.subject)}
                />
              </Field>

              <Field
                label="Nội dung"
                error={errors.content}
                required
              >
                <Textarea
                  rows={6}
                  value={contentValue}
                  maxLength={5000}
                  onChange={(event) => setContent(event.target.value)}
                  aria-invalid={Boolean(errors.content)}
                />
              </Field>

              <div className="grid grid-cols-[1fr_1fr_8rem] gap-3">
                <Field label="Hạn đóng" error={errors.deadline} required>
                  <DatePicker value={deadlineValue} onChange={setDeadline} invalid={Boolean(errors.deadline)} />
                </Field>
                <Field label="Thời điểm gửi" error={errors.sendAt} required>
                  <DatePicker value={sendDate} onChange={setSendDate} invalid={Boolean(errors.sendAt)} />
                </Field>
                <Field label="Giờ" required>
                  <TimePicker value={sendTime} onChange={setSendTime} />
                </Field>
              </div>

              <div className="space-y-2">
                <Label className="text-muted-foreground text-xs">Kênh gửi</Label>
                <label className="flex items-center gap-2.5 text-[13px] font-medium">
                  <Checkbox checked disabled aria-label="Email" />
                  Email
                </label>
                <label className="text-muted-foreground flex items-center gap-2.5 text-[13px]">
                  <Checkbox checked={false} disabled aria-label="SMS" />
                  SMS
                  <span className="bg-muted rounded-full px-2 py-0.5 text-[11px]">Sắp hỗ trợ</span>
                </label>
                <p className="text-muted-foreground text-xs">
                  Học sinh chưa có email phụ huynh được ghi nhận là "thiếu email", không gửi được.
                </p>
              </div>
            </div>

            {/* what: the e-mail */}
            <div className="bg-surface-subtle flex min-h-0 flex-col border-s">
              <div className="flex shrink-0 items-center gap-2 border-b px-5 py-3">
                <span className="border-primary text-[13px] font-semibold">Email</span>
                <span className="text-muted-foreground ms-3 text-[13px]">SMS</span>
                <div className="ms-auto flex items-center gap-2">
                  <span className="text-muted-foreground max-w-56 truncate text-xs">
                    {preview ? `${preview.studentName} · ${preview.className}` : "…"} ·{" "}
                    {orderedIds.length === 0 ? 0 : safeIndex + 1} / {orderedIds.length}
                  </span>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    aria-label="Học sinh trước"
                    disabled={safeIndex <= 0}
                    onClick={() => setPreviewIndex(safeIndex - 1)}
                  >
                    <ChevronLeft />
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    aria-label="Học sinh sau"
                    disabled={safeIndex >= orderedIds.length - 1}
                    onClick={() => setPreviewIndex(safeIndex + 1)}
                  >
                    <ChevronRight />
                  </Button>
                </div>
              </div>
              <div className="relative min-h-0 flex-1">
                {preview ? (
                  <iframe
                    title="Xem trước email"
                    sandbox=""
                    srcDoc={preview.html}
                    className={cn("size-full border-0 bg-white transition-opacity", previewing && "opacity-60")}
                  />
                ) : (
                  <div className="text-muted-foreground flex size-full items-center justify-center p-8 text-center text-sm">
                    {previewError?.message ?? "Đang dựng email…"}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        <DialogFooter className="h-[4.5rem] shrink-0 flex-row items-center justify-between border-t px-6 sm:justify-between">
          {step === 1 ? (
            <>
              <span className="text-[13px] font-medium tabular-nums">
                {formatNumber(selected.size)} học sinh · còn phải thu {formatCurrency(selectedTotal)}
              </span>
              <div className="flex gap-2">
                <Button type="button" variant="outline" onClick={onClose}>
                  Hủy
                </Button>
                <Button type="button" disabled={selected.size === 0 || selecting} onClick={() => setStep(2)}>
                  Tiếp tục
                  <ArrowRight />
                </Button>
              </div>
            </>
          ) : (
            <>
              <div className="flex items-center gap-4">
                <Button type="button" variant="outline" disabled={mutation.isPending} onClick={() => setStep(1)}>
                  <ArrowLeft />
                  Quay lại
                </Button>
                {defaults && !defaults.paymentConfigured && (
                  <span className="text-destructive text-xs">Chưa cấu hình tài khoản nhận tiền</span>
                )}
              </div>
              <div className="flex gap-2">
                <Button type="button" variant="outline" disabled={mutation.isPending} onClick={() => submit(true)}>
                  Lưu nháp
                </Button>
                <Button type="button" disabled={mutation.isPending || selected.size === 0} onClick={() => submit(false)}>
                  Gửi thông báo cho {formatNumber(selected.size)} phụ huynh
                </Button>
              </div>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Field({
  label,
  error,
  hint,
  required,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <Label className="text-muted-foreground text-xs">
        {label}
        {required && " *"}
      </Label>
      {children}
      {error ? (
        <p className="text-destructive text-xs">{error}</p>
      ) : (
        hint && <p className="text-muted-foreground text-xs">{hint}</p>
      )}
    </div>
  );
}

function FilterSelect<T extends string>({
  value,
  options,
  onChange,
  label,
  className,
}: {
  value: T;
  options: readonly { value: T; label: string }[];
  onChange: (value: T) => void;
  label: string;
  className?: string;
}) {
  return (
    <Select value={value} onValueChange={(next) => onChange(next as T)}>
      <SelectTrigger className={cn("w-44", className)} aria-label={label}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

/** Toàn trường → khối → lớp, with a count on each; the chosen node narrows the list beside it. */
function GradeTree({
  tree,
  node,
  filter,
  onSelect,
  allLabel,
}: {
  tree: ReturnType<typeof useNotificationTree>["tree"];
  node: Node;
  filter: string;
  onSelect: (node: Node) => void;
  allLabel: string;
}) {
  const [open, setOpen] = React.useState<Set<number>>(new Set());
  const needle = filter.trim().toLowerCase();

  // The khối holding the chosen lớp stays open, so the selection is never hidden.
  React.useEffect(() => {
    if (node.kind !== "class" || !tree) return;
    const owner = tree.groups.find((group) => group.classes.some((klass) => klass.id === node.id));
    if (owner) setOpen((previous) => (previous.has(owner.id) ? previous : new Set(previous).add(owner.id)));
  }, [node, tree]);

  if (!tree) {
    return <div className="text-muted-foreground p-4 text-center text-sm">Đang tải…</div>;
  }

  const rowClass = (active: boolean) =>
    cn(
      "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-start text-[13px] transition-colors",
      active ? "bg-muted text-brand font-semibold" : "hover:bg-muted/60"
    );

  return (
    <ul className="space-y-0.5">
      <li>
        <button type="button" className={rowClass(node.kind === "all")} onClick={() => onSelect({ kind: "all" })}>
          <ChevronDown className="size-3.5 shrink-0 opacity-60" />
          <span className="flex-1 font-semibold">{allLabel}</span>
          <span className="text-muted-foreground text-xs tabular-nums">{formatNumber(tree.total)}</span>
        </button>
      </li>
      {tree.groups.map((group) => {
        const classes = needle
          ? group.classes.filter((klass) => klass.name.toLowerCase().includes(needle))
          : group.classes;
        const matches = !needle || group.name.toLowerCase().includes(needle) || classes.length > 0;
        if (!matches) return null;
        const expanded = open.has(group.id) || (needle !== "" && classes.length > 0);
        return (
          <li key={group.id} className="ps-4">
            <div className="flex items-center">
              <button
                type="button"
                aria-label={expanded ? "Thu gọn" : "Mở rộng"}
                className="text-muted-foreground hover:text-foreground p-1"
                onClick={() =>
                  setOpen((previous) => {
                    const next = new Set(previous);
                    if (next.has(group.id)) next.delete(group.id);
                    else next.add(group.id);
                    return next;
                  })
                }
              >
                {expanded ? <ChevronDown className="size-3.5" /> : <ChevronRight className="size-3.5" />}
              </button>
              <button
                type="button"
                className={rowClass(node.kind === "group" && node.id === group.id)}
                onClick={() => onSelect({ kind: "group", id: group.id, name: group.name })}
              >
                <span className="flex-1 font-semibold">{group.name}</span>
                <span className="text-muted-foreground text-xs tabular-nums">{formatNumber(group.count)}</span>
              </button>
            </div>
            {expanded && (
              <ul className="ms-6 space-y-0.5">
                {classes.map((klass) => (
                  <li key={klass.id}>
                    <button
                      type="button"
                      className={rowClass(node.kind === "class" && node.id === klass.id)}
                      onClick={() => onSelect({ kind: "class", id: klass.id, name: klass.name })}
                    >
                      <span className="flex-1">{klass.name}</span>
                      <span className="text-muted-foreground text-xs tabular-nums">{formatNumber(klass.count)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </li>
        );
      })}
    </ul>
  );
}
