/** Where a đợt thông báo is: it only moves forward, except that a retry puts a finished one back to SCHEDULED. */
export type FeeNotificationStatus =
  | "DRAFT"
  | "SCHEDULED"
  | "SENDING"
  | "COMPLETED"
  | "PARTIAL_FAILURE"
  | "FAILED";

export const NOTIFICATION_STATUS_LABELS: Record<FeeNotificationStatus, string> = {
  DRAFT: "Nháp",
  SCHEDULED: "Đã lên lịch",
  SENDING: "Đang gửi",
  COMPLETED: "Hoàn thành",
  PARTIAL_FAILURE: "Có lỗi",
  FAILED: "Gửi lỗi",
};

/** A notification that is still moving: the list keeps asking for it until it settles. */
export const isNotificationBusy = (status: FeeNotificationStatus) =>
  status === "SCHEDULED" || status === "SENDING";

export type FeeNotification = {
  id: number;
  batch: { id: number; name: string; collectionMonth: number };
  subject: string;
  content: string;
  deadline: string;
  sendAt: string | null;
  status: FeeNotificationStatus;
  recipientCount: number;
  sentCount: number;
  failedCount: number;
  missingEmailCount: number;
  errorMessage: string | null;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string | null;
  _can?: { write?: boolean; delete?: boolean };
};

/** What a student has been told so far. */
export type NotifyStatus = "NONE" | "QUEUED" | "SENT" | "FAILED" | "MISSING_EMAIL";

/** How much of what a student owes has come in. */
export type CollectStatus = "NO_DEBT" | "UNPAID" | "PARTIAL" | "PAID";

export const NOTIFY_STATUS_LABELS: Record<NotifyStatus, string> = {
  NONE: "Chưa gửi",
  QUEUED: "Đang chờ gửi",
  SENT: "Đã gửi",
  FAILED: "Gửi lỗi",
  MISSING_EMAIL: "Thiếu email",
};

export const COLLECT_STATUS_LABELS: Record<CollectStatus, string> = {
  NO_DEBT: "Không phải thu",
  UNPAID: "Chưa thu",
  PARTIAL: "Thu một phần",
  PAID: "Đã thu đủ",
};

/** Which students a notification is being picked for. */
export type SelectionMode = "ALL" | "UNNOTIFIED" | "DEBT";

export type NotificationCandidate = {
  batchStudentId: number;
  studentName: string;
  studentCode: string;
  classGroupId: number;
  classGroupName: string;
  schoolClassId: number;
  className: string;
  receivableAmount: number;
  collectedAmount: number;
  outstandingAmount: number;
  collectStatus: CollectStatus;
  notifyStatus: NotifyStatus;
  hasEmail: boolean;
};

export type NotificationCandidatePage = {
  rows: NotificationCandidate[];
  total: number;
  outstandingTotal: number;
  receivableTotal: number;
};

export type NotificationTree = {
  total: number;
  groups: { id: number; name: string; count: number; classes: { id: number; name: string; count: number }[] }[];
};

export type NotificationDefaults = {
  subject: string;
  content: string;
  deadline: string;
  paymentConfigured: boolean;
  senderUnit: string;
};

export type NotificationRecipient = {
  id: number;
  batchStudentId: number;
  studentName: string;
  studentCode: string | null;
  classGroupName: string | null;
  className: string | null;
  guardianName: string | null;
  email: string | null;
  totalAmount: number;
  status: "PENDING" | "MISSING_EMAIL" | "SENT" | "FAILED";
  attempts: number;
  sentAt: string | null;
  errorMessage: string | null;
  orderCode: string | null;
};

export type NotificationRecipientPage = {
  rows: NotificationRecipient[];
  total: number;
  countsByStatus: Record<string, number>;
};

export const RECIPIENT_STATUS_LABELS: Record<NotificationRecipient["status"], string> = {
  PENDING: "Chờ gửi",
  MISSING_EMAIL: "Thiếu email",
  SENT: "Đã gửi",
  FAILED: "Gửi lỗi",
};

export type RenderedNotification = {
  subject: string;
  html: string;
  studentName: string;
  className: string;
  email: string | null;
};

export type CreatedNotification = {
  notification: FeeNotification;
  requested: number;
  skippedAlreadyNotified: number;
  skippedNoDebt: number;
};

/** The pieces of a notification the person writes, as the compose dialog holds them. */
export type NotificationDraft = {
  subject: string;
  content: string;
  deadline: string;
  /** Local date and time; empty means "now". */
  sendDate: string;
  sendTime: string;
};
