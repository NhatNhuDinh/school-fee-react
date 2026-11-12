/** Display labels for the row-level policy screens. Wire values are never changed. */

const RESOURCE_TITLES: Record<string, string> = {
  organizations: "Đơn vị",
  academic_years: "Năm học",
  priority_categories: "Ưu tiên",
  students: "Học sinh",
  class_groups: "Khối lớp",
  school_classes: "Lớp",
  training_systems: "Hệ đào tạo",
  measurement_units: "Đơn vị tính",
  users: "Người dùng",
  roles: "Vai trò",
  permissions: "Mã quyền",
  row_level_policies: "Quyền dữ liệu",
  notifications: "Thông báo",
};

/** Unknown resources show their raw code. */
export function resourceTitle(resource: string | undefined | null): string {
  if (!resource) return "";
  return RESOURCE_TITLES[resource] ?? resource;
}

const ACTION_LABELS: Record<string, string> = {
  read: "Xem",
  write: "Ghi",
  delete: "Xóa",
};

export function actionLabel(action: string | undefined | null): string {
  if (!action) return "";
  return ACTION_LABELS[action] ?? action;
}

export function kindLabel(kind: string | undefined | null): string {
  if (kind === "SCOPE") return "Phạm vi theo vai trò";
  if (kind === "FILTER") return "Bộ lọc toàn cục";
  return kind ?? "";
}

/** Three-valued logic results are shown in Vietnamese; the logical values are untouched. */
export function truthLabel(value: string): string {
  if (value === "TRUE") return "Đúng";
  if (value === "FALSE") return "Sai";
  if (value === "UNKNOWN") return "Không xác định";
  return value;
}
