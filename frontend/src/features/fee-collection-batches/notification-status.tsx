import {
  COLLECT_STATUS_LABELS,
  NOTIFICATION_STATUS_LABELS,
  NOTIFY_STATUS_LABELS,
  RECIPIENT_STATUS_LABELS,
  type CollectStatus,
  type FeeNotificationStatus,
  type NotificationRecipient,
  type NotifyStatus,
} from "@/domains/fee-notification/types";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";

const NOTIFICATION_VARIANT = {
  DRAFT: "neutral",
  SCHEDULED: "info",
  SENDING: "warning",
  COMPLETED: "success",
  PARTIAL_FAILURE: "warning",
  FAILED: "destructive",
} as const;

/** The status of a đợt thông báo. It follows what the dispatcher has done, never a person's choice. */
export function NotificationStatusBadge({ status }: { status: FeeNotificationStatus }) {
  return <Badge variant={NOTIFICATION_VARIANT[status]}>{NOTIFICATION_STATUS_LABELS[status]}</Badge>;
}

type Tone = "neutral" | "success" | "warning" | "danger";

const DOT: Record<Tone, string> = {
  neutral: "bg-muted-foreground/40",
  success: "bg-success",
  warning: "bg-warning",
  danger: "bg-destructive",
};

/** A coloured dot and a plain word: a status in a column of forty reads better than forty badges. */
export function DotStatus({ tone, children, className }: { tone: Tone; children: React.ReactNode; className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-2 whitespace-nowrap", className)}>
      <span aria-hidden className={cn("size-1.5 shrink-0 rounded-full", DOT[tone])} />
      {children}
    </span>
  );
}

const COLLECT_TONE: Record<CollectStatus, Tone> = {
  NO_DEBT: "neutral",
  UNPAID: "neutral",
  PARTIAL: "warning",
  PAID: "success",
};

export function CollectStatusLabel({ status }: { status: CollectStatus }) {
  return <DotStatus tone={COLLECT_TONE[status]}>{COLLECT_STATUS_LABELS[status]}</DotStatus>;
}

const NOTIFY_TONE: Record<NotifyStatus, Tone> = {
  NONE: "neutral",
  QUEUED: "warning",
  SENT: "success",
  FAILED: "danger",
  MISSING_EMAIL: "danger",
};

export function NotifyStatusLabel({ status }: { status: NotifyStatus }) {
  return <DotStatus tone={NOTIFY_TONE[status]}>{NOTIFY_STATUS_LABELS[status]}</DotStatus>;
}

const RECIPIENT_TONE: Record<NotificationRecipient["status"], Tone> = {
  PENDING: "warning",
  MISSING_EMAIL: "danger",
  SENT: "success",
  FAILED: "danger",
};

export function RecipientStatusLabel({ status }: { status: NotificationRecipient["status"] }) {
  return <DotStatus tone={RECIPIENT_TONE[status]}>{RECIPIENT_STATUS_LABELS[status]}</DotStatus>;
}
