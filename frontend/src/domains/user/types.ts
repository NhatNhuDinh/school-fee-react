/**
 * The `users` resource as the API reports it — `UserResponse` on the backend.
 *
 * Nothing generates this from the Java record, so the two are kept in step by
 * hand; that is the same bargain the rest of the API contract is on.
 */
export type User = {
  id: number;
  username: string;
  email: string;
  fullName: string;
  status: UserStatus;
  roles: RoleSummary[];
  /** The school the account works for. Null for an account that has not been placed. */
  organization: OrganizationSummary | null;
  createdAt: string;
  updatedAt: string | null;
  lastLoginAt: string | null;
};

/** The role as a user row reports it — no permission list at this depth. */
export type RoleSummary = {
  id: number;
  code: string;
  name: string;
};

/** The school as a user row reports it. A school is an organization. */
export type OrganizationSummary = {
  id: number;
  code: string;
  name: string;
};

/** Mirrors `UserStatus`; these are the lower-case wire values, not the constants. */
export type UserStatus = "active" | "inactive" | "locked";

export const STATUS_LABELS: Record<UserStatus, string> = {
  active: "Hoạt động",
  inactive: "Ngừng hoạt động",
  locked: "Đã khóa",
};

export const STATUS_OPTIONS: { label: string; value: UserStatus }[] = [
  { label: "Hoạt động", value: "active" },
  { label: "Ngừng hoạt động", value: "inactive" },
  { label: "Đã khóa", value: "locked" },
];
